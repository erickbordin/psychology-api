import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { RouteObject } from 'react-router-dom'
import { Navigate } from 'react-router-dom'

import { gravarAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComRotas } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { AnotacoesTab } from './AnotacoesTab'
import { ConsultasTab } from './ConsultasTab'
import { FichaPage } from './FichaPage'
import { LembretesTab } from './LembretesTab'

const ID = '3f9a1c04-0000-0000-0000-000000000001'

const PACIENTE = {
  idPaciente: ID,
  idUsuario: 'u-1',
  nome: 'Ana Moreira',
  email: 'ana@exemplo.br',
  telefone: '(51) 99612-0184',
  dataNascimento: '1991-04-12',
  createdAt: '2026-03-11T10:00:00',
}

function pagina<T>(itens: T[], total = itens.length) {
  return {
    content: itens,
    page: { size: 20, number: 0, totalElements: total, totalPages: Math.max(1, Math.ceil(total / 20)) },
  }
}

function anotacoesEm(itens: { id: string; conteudo: string; createdAt: string }[], total?: number) {
  return pagina(
    itens.map((item) => ({ ...item, pacienteId: ID })),
    total,
  )
}

const ROTAS: RouteObject[] = [
  {
    path: '/pacientes/:pacienteId',
    element: <FichaPage />,
    children: [
      { index: true, element: <Navigate to="anotacoes" replace /> },
      { path: 'anotacoes', element: <AnotacoesTab /> },
      { path: 'lembretes', element: <LembretesTab /> },
      { path: 'consultas', element: <ConsultasTab /> },
    ],
  },
  { path: '/pacientes', element: <p>lista de pacientes</p> },
]

describe('FichaPage', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    gravarAcesso('token-de-teste')
    servidorDeTeste.use(http.get(`/pacientes/${ID}`, () => HttpResponse.json(PACIENTE)))
  })

  it('mostra o paciente e as anotacoes', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(
          anotacoesEm([{ id: 'a1', conteudo: 'Retomou o registro de sono.', createdAt: '2026-08-19' }]),
        ),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)

    expect(await screen.findByRole('heading', { name: 'Ana Moreira' })).toBeInTheDocument()
    expect(await screen.findByText('Retomou o registro de sono.')).toBeInTheDocument()
    expect(screen.getByText('1 anotação')).toBeInTheDocument()
  })

  it('a ficha sem aba na URL cai em anotacoes', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(anotacoesEm([]))),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)

    expect(await screen.findByLabelText('Anotação da sessão')).toBeInTheDocument()
  })

  it('envia o campo anotacao no corpo, como o DTO exige', async () => {
    const corpos: unknown[] = []
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(anotacoesEm([]))),
      http.post(`/pacientes/${ID}/anotacoes`, async ({ request }) => {
        corpos.push(await request.json())
        return HttpResponse.json(
          { id: 'nova', conteudo: 'Sessão de hoje.', createdAt: '2026-08-26', pacienteId: ID },
          { status: 201 },
        )
      }),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/anotacoes`)
    await userEvent.type(await screen.findByLabelText('Anotação da sessão'), 'Sessão de hoje.')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar anotação' }))

    await waitFor(() => expect(corpos).toEqual([{ anotacao: 'Sessão de hoje.' }]))
  })

  it('limpa o rascunho depois de registrar', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(anotacoesEm([]))),
      http.post(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(
          { id: 'nova', conteudo: 'Sessão de hoje.', createdAt: '2026-08-26', pacienteId: ID },
          { status: 201 },
        ),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/anotacoes`)
    const campo = await screen.findByLabelText('Anotação da sessão')
    await userEvent.type(campo, 'Sessão de hoje.')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar anotação' }))

    await waitFor(() => expect(campo).toHaveValue(''))
  })

  /**
   * Anotacao nao tem PUT: uma gravacao duplicada por duplo clique fica no
   * historico e so sai por exclusao, que por sua vez vira log de auditoria.
   */
  it('trava o registro enquanto o POST esta em voo', async () => {
    let liberar: () => void = () => {}
    const emVoo = new Promise<void>((resolve) => {
      liberar = resolve
    })
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(anotacoesEm([]))),
      http.post(`/pacientes/${ID}/anotacoes`, async () => {
        await emVoo
        return HttpResponse.json(
          { id: 'nova', conteudo: 'Sessão de hoje.', createdAt: '2026-08-26', pacienteId: ID },
          { status: 201 },
        )
      }),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/anotacoes`)
    await userEvent.type(await screen.findByLabelText('Anotação da sessão'), 'Sessão de hoje.')
    const registrar = screen.getByRole('button', { name: 'Registrar anotação' })
    await userEvent.click(registrar)

    await waitFor(() => expect(registrar).toBeDisabled())
    liberar()
  })

  it('mostra o rodape de paginacao lendo page.totalElements', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(anotacoesEm([{ id: 'a1', conteudo: 'Uma.', createdAt: '2026-08-19' }], 42)),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/anotacoes`)

    expect(await screen.findByText('página 1 de 3 · 42 anotações')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeEnabled()
  })

  it('a pagina fica na URL, entao sobrevive ao recarregamento', async () => {
    const paginasPedidas: string[] = []
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, ({ request }) => {
        paginasPedidas.push(new URL(request.url).searchParams.get('page') ?? '')
        return HttpResponse.json(
          anotacoesEm([{ id: 'a1', conteudo: 'Uma.', createdAt: '2026-08-19' }], 42),
        )
      }),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/anotacoes?page=2`)

    await waitFor(() => expect(paginasPedidas).toContain('2'))
    expect(await screen.findByText('página 3 de 3 · 42 anotações')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled()
  })

  it('apagar anotacao pede confirmacao antes de chamar a API', async () => {
    let apagou = false
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(anotacoesEm([{ id: 'a1', conteudo: 'Uma.', createdAt: '2026-08-19' }])),
      ),
      http.delete('/anotacoes/a1', () => {
        apagou = true
        return new HttpResponse(null, { status: 204 })
      }),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/anotacoes`)
    await userEvent.click(await screen.findByRole('button', { name: 'Apagar' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(apagou).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Apagar anotação' }))
    await waitFor(() => expect(apagou).toBe(true))
  })

  it('avisa antes de sair com rascunho pendente', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(anotacoesEm([]))),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/anotacoes`)
    await userEvent.type(await screen.findByLabelText('Anotação da sessão'), 'texto nao enviado')
    await userEvent.click(screen.getByRole('link', { name: 'Voltar' }))

    expect(
      await screen.findByText('Você tem uma anotação não enviada. Sair perde o texto.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('lista de pacientes')).not.toBeInTheDocument()
  })

  it('sem rascunho, sair navega direto', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(anotacoesEm([]))),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/anotacoes`)
    await screen.findByLabelText('Anotação da sessão')
    await userEvent.click(screen.getByRole('link', { name: 'Voltar' }))

    expect(await screen.findByText('lista de pacientes')).toBeInTheDocument()
  })

  it('a aba de lembretes e uma rota, e conclui de forma otimista', async () => {
    let liberar: () => void = () => {}
    const emVoo = new Promise<void>((resolve) => {
      liberar = resolve
    })
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/lembretes`, () =>
        HttpResponse.json(
          pagina([
            { id: 'l1', descricao: 'Retomar o registro de sono.', concluido: false, createdAt: '2026-08-19T10:00:00' },
          ]),
        ),
      ),
      http.patch('/lembretes/l1/concluir', async () => {
        await emVoo
        return HttpResponse.json({
          id: 'l1',
          descricao: 'Retomar o registro de sono.',
          concluido: true,
          createdAt: '2026-08-19T10:00:00',
        })
      }),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/lembretes`)
    await userEvent.click(await screen.findByRole('button', { name: 'Concluir' }))

    // otimista: a marca aparece antes de a API responder
    expect(await screen.findByText('concluído')).toBeInTheDocument()
    liberar()
  })

  it('concluir que falha volta atras', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/lembretes`, () =>
        HttpResponse.json(
          pagina([
            { id: 'l1', descricao: 'Retomar o registro de sono.', concluido: false, createdAt: '2026-08-19T10:00:00' },
          ]),
        ),
      ),
      http.patch('/lembretes/l1/concluir', () =>
        HttpResponse.json({ status: 404, mensagem: 'Lembrete nao encontrado', erros: [] }, { status: 404 }),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/lembretes`)
    await userEvent.click(await screen.findByRole('button', { name: 'Concluir' }))

    await waitFor(() => expect(screen.queryByText('concluído')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Concluir' })).toBeInTheDocument()
  })

  it('a aba de consultas mostra o status pelo enum da API', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/consultas`, () =>
        HttpResponse.json(
          pagina([
            {
              id: 'c1',
              pacienteId: ID,
              pacienteNome: 'Ana Moreira',
              dtConsulta: '2026-09-02T15:00:00',
              status: 'AGENDADA',
              createdAt: '2026-08-19T10:00:00',
              serieId: null,
            },
          ]),
        ),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}/consultas`)

    expect(await screen.findByText('qua, 2 set 2026 · 15:00')).toBeInTheDocument()
    expect(screen.getByText('agendada')).toBeInTheDocument()
  })
})
