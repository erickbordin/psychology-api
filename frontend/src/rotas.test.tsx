import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { reiniciarSessaoEmMemoria } from './api/sessaoEmMemoria'
import { definicaoDeRotas } from './rotas'
import { renderizarComRotas } from './teste/renderizar'
import { servidorDeTeste } from './teste/servidor'

describe('rotas', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())

  it('sem sessao, a rota protegida manda para o login', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )

    renderizarComRotas(definicaoDeRotas, '/')

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('com sessao, a rota protegida mostra o layout', async () => {
    servidorDeTeste.use(http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-bom' })))

    renderizarComRotas(definicaoDeRotas, '/')

    expect(await screen.findByRole('navigation')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
  })

  /**
   * Guarda de regressao do defeito que o E2E de fumaca achou: o LoginPage ficava
   * numa tela morta depois de autenticar, em vez de entrar na aplicacao.
   */
  it('com sessao, quem chega no login entra na aplicacao', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-bom' })),
      http.get('/consultas', () => HttpResponse.json([])),
    )

    renderizarComRotas(definicaoDeRotas, '/login')

    expect(await screen.findByRole('heading', { name: 'Agenda' })).toBeInTheDocument()
  })

  it('a raiz cai na agenda do dia', async () => {
    const intervalos: string[] = []
    servidorDeTeste.use(
      http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-bom' })),
      http.get('/consultas', ({ request }) => {
        const busca = new URL(request.url).searchParams
        intervalos.push(`${busca.get('de')}..${busca.get('ate')}`)
        return HttpResponse.json([])
      }),
    )

    renderizarComRotas(definicaoDeRotas, '/')

    expect(await screen.findByRole('heading', { name: 'Agenda' })).toBeInTheDocument()
    const hoje = new Date()
    const iso = [
      hoje.getFullYear(),
      String(hoje.getMonth() + 1).padStart(2, '0'),
      String(hoje.getDate()).padStart(2, '0'),
    ].join('-')
    // o cabecalho aparece antes de a chamada chegar no handler do MSW: sem o
    // waitFor a assercao corre cedo e o teste falha um dia sim, outro nao
    await waitFor(() => expect(intervalos).toContain(`${iso}..${iso}`))
  })

  it('mostra carregando enquanto o refresh do boot nao resolve', () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )

    renderizarComRotas(definicaoDeRotas, '/')

    expect(screen.getByText('Carregando…')).toBeInTheDocument()
  })

  /**
   * Sem isso, quem abre um link direto para a ficha de um paciente e obrigado a
   * entrar cai sempre na lista, e tem de reencontrar o paciente na mao.
   */
  it('depois de entrar, volta para o destino que exigiu a sessao', async () => {
    let renovou = false
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        renovou
          ? HttpResponse.json({ token: 'token-bom' })
          : HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
      http.post('/auth/login', () => {
        renovou = true
        return HttpResponse.json({ token: 'token-bom' })
      }),
      http.get('/pacientes/p-1', () =>
        HttpResponse.json({
          idPaciente: 'p-1',
          idUsuario: 'u-1',
          nome: 'Ana Moreira',
          email: null,
          telefone: null,
          dataNascimento: '1991-04-12',
          createdAt: '2026-03-11T10:00:00',
        }),
      ),
      http.get('/pacientes/p-1/anotacoes', () =>
        HttpResponse.json({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 1 },
        }),
      ),
    )

    renderizarComRotas(definicaoDeRotas, '/pacientes/p-1')

    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'senhaforte123')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Ana Moreira' })).toBeInTheDocument()
  })

  it('caminho desconhecido mostra a mensagem de rota inexistente', async () => {
    servidorDeTeste.use(http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-bom' })))

    renderizarComRotas(definicaoDeRotas, '/inexistente')

    expect(await screen.findByText('Página não encontrada.')).toBeInTheDocument()
  })
})
