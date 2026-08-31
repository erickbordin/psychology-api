import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { gravarAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComProvedores } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { PacientesPage } from './PacientesPage'

const ANA = {
  idPaciente: '3f9a1c04-0000-0000-0000-000000000001',
  idUsuario: 'u-1',
  nome: 'Ana Moreira',
  email: 'ana@exemplo.br',
  telefone: '(51) 99612-0184',
  dataNascimento: '1991-04-12',
  createdAt: '2026-03-11T10:00:00',
}

describe('PacientesPage', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    gravarAcesso('token-de-teste')
  })

  it('lista os pacientes com contagem', async () => {
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.json([ANA])))

    renderizarComProvedores(<PacientesPage />)

    expect(await screen.findByText('Ana Moreira')).toBeInTheDocument()
    expect(screen.getByText('1 paciente')).toBeInTheDocument()
  })

  it('mostra estado vazio quando nao ha paciente', async () => {
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.json([])))

    renderizarComProvedores(<PacientesPage />)

    expect(await screen.findByText('Nenhum paciente cadastrado ainda.')).toBeInTheDocument()
  })

  it('cadastra e a lista passa a mostrar o novo paciente', async () => {
    let cadastrados = 0
    servidorDeTeste.use(
      http.get('/pacientes', () => HttpResponse.json(cadastrados === 0 ? [] : [ANA])),
      http.post('/pacientes', async ({ request }) => {
        const corpo = (await request.json()) as { nome: string; dataNascimento: string }
        expect(corpo.nome).toBe('Ana Moreira')
        expect(corpo.dataNascimento).toBe('1991-04-12')
        cadastrados += 1
        return HttpResponse.json(ANA, { status: 201 })
      }),
    )

    renderizarComProvedores(<PacientesPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo paciente' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Ana Moreira')
    await userEvent.type(screen.getByLabelText('Data de nascimento'), '1991-04-12')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))

    expect(await screen.findByText('Ana Moreira')).toBeInTheDocument()
  })

  it('marca o campo culpado quando a API devolve 400', async () => {
    servidorDeTeste.use(
      http.get('/pacientes', () => HttpResponse.json([])),
      http.post('/pacientes', () =>
        HttpResponse.json(
          {
            status: 400,
            mensagem: 'Erro de validação',
            erros: [{ campo: 'dataNascimento', erro: 'A data de nascimento é obrigatória.' }],
          },
          { status: 400 },
        ),
      ),
    )

    renderizarComProvedores(<PacientesPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo paciente' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Ana Moreira')
    await userEvent.type(screen.getByLabelText('Data de nascimento'), '12/04/1991')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))

    await waitFor(() =>
      expect(screen.getByText('A data de nascimento é obrigatória.')).toBeInTheDocument(),
    )
  })

  it('o filtro vem da URL e vai para a API, entao sobrevive ao recarregamento', async () => {
    const filtros: (string | null)[] = []
    servidorDeTeste.use(
      http.get('/pacientes', ({ request }) => {
        filtros.push(new URL(request.url).searchParams.get('nome'))
        return HttpResponse.json([ANA])
      }),
    )

    renderizarComProvedores(<PacientesPage />, '/pacientes?nome=ana')

    expect(await screen.findByText('Ana Moreira')).toBeInTheDocument()
    expect(filtros).toEqual(['ana'])
    expect(screen.getByLabelText('Buscar por nome')).toHaveValue('ana')
  })

  it('buscar sem resultado diz que a busca falhou, nao que a lista esta vazia', async () => {
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.json([])))

    renderizarComProvedores(<PacientesPage />, '/pacientes?nome=zzz')

    expect(await screen.findByText(/Nenhum paciente com/)).toBeInTheDocument()
    expect(screen.queryByText('Nenhum paciente cadastrado ainda.')).not.toBeInTheDocument()
  })

  it('editar manda PUT com os campos do cadastro', async () => {
    const corpos: unknown[] = []
    servidorDeTeste.use(
      http.get('/pacientes', () => HttpResponse.json([ANA])),
      http.put(`/pacientes/${ANA.idPaciente}`, async ({ request }) => {
        corpos.push(await request.json())
        return HttpResponse.json({ ...ANA, nome: 'Ana Moreira Lima' })
      }),
    )

    renderizarComProvedores(<PacientesPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }))

    const campoNome = screen.getByLabelText('Nome')
    await userEvent.clear(campoNome)
    await userEvent.type(campoNome, 'Ana Moreira Lima')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() =>
      expect(corpos).toEqual([
        {
          nome: 'Ana Moreira Lima',
          telefone: '(51) 99612-0184',
          email: 'ana@exemplo.br',
          dataNascimento: '1991-04-12',
        },
      ]),
    )
  })

  it('excluir paciente exige um segundo passo antes de chamar a API', async () => {
    let excluiu = false
    servidorDeTeste.use(
      http.get('/pacientes', () => HttpResponse.json([ANA])),
      http.delete(`/pacientes/${ANA.idPaciente}`, () => {
        excluiu = true
        return new HttpResponse(null, { status: 204 })
      }),
    )

    renderizarComProvedores(<PacientesPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }))
    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))

    expect(excluiu).toBe(false)
    expect(await screen.findByRole('heading', { name: 'Excluir paciente?' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Excluir paciente' }))
    await waitFor(() => expect(excluiu).toBe(true))
  })
})
