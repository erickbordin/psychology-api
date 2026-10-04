import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { TrilhaDeAcesso } from './TrilhaDeAcesso'

const PACIENTE_A = '3f1c2a54-8d0e-4b7a-9c61-2e5d7a1b9f10'
const PACIENTE_B = '7b9e4d12-1a3c-4f58-8e20-5c6d9a0b3e71'

const linhas = [
  {
    id: 'a1b2c3d4-0001-4000-8000-000000000001',
    acao: 'VISUALIZACAO',
    entidade: 'FichaDoPaciente',
    entidadeId: PACIENTE_A,
    createdAt: '2026-10-03T09:05:00',
    pacienteId: PACIENTE_A,
    pacienteNome: 'Paciente Alfa',
    pacienteExcluido: false,
  },
  {
    id: 'a1b2c3d4-0002-4000-8000-000000000002',
    acao: 'EXCLUSAO',
    entidade: 'Paciente',
    entidadeId: PACIENTE_B,
    createdAt: '2026-10-02T18:30:00',
    pacienteId: PACIENTE_B,
    pacienteNome: 'Paciente Beta',
    pacienteExcluido: true,
  },
  {
    id: 'a1b2c3d4-0003-4000-8000-000000000003',
    acao: 'ATUALIZACAO',
    entidade: 'Ajustes',
    entidadeId: 'a1b2c3d4-0099-4000-8000-000000000099',
    createdAt: '2026-10-01T07:00:00',
  },
  {
    id: 'a1b2c3d4-0004-4000-8000-000000000004',
    acao: 'ATUALIZACAO',
    entidade: 'Senha',
    entidadeId: 'a1b2c3d4-0098-4000-8000-000000000098',
    createdAt: '2026-10-03T00:05:00',
  },
]

function pagina(content: object[], number = 0, totalPages = 1) {
  return { content, page: { size: 20, number, totalElements: content.length, totalPages } }
}

function servir(resposta: (url: URL) => object) {
  const pedidos: URL[] = []
  servidor.use(
    http.get('/auditoria', ({ request }) => {
      const url = new URL(request.url)
      pedidos.push(url)
      return HttpResponse.json(resposta(url))
    }),
  )
  return pedidos
}

const lista = () => screen.findByRole('list', { name: 'Trilha de acesso' })

describe('TrilhaDeAcesso', () => {
  it('mostra data, frase e nome do paciente em cada linha', async () => {
    servir(() => pagina(linhas))
    renderizar(<TrilhaDeAcesso />)

    const itens = within(await lista()).getAllByRole('listitem')
    expect(itens).toHaveLength(4)
    expect(itens[0]).toHaveTextContent('03/10 09:05 · Abriu a ficha · Paciente Alfa')
    expect(itens[1]).toHaveTextContent('02/10 18:30 · Excluiu paciente · Paciente Beta')
    expect(itens[2]).toHaveTextContent('01/10 07:00 · Alterou os ajustes')
    expect(itens[2].textContent).toBe('01/10 07:00 · Alterou os ajustes')
    expect(itens[3].textContent).toBe('03/10 00:05 · Trocou a senha')
  })

  it('o nome é link para a ficha quando o paciente não foi excluído', async () => {
    servir(() => pagina(linhas))
    renderizar(<TrilhaDeAcesso />)

    await lista()
    expect(screen.getByRole('link', { name: 'Paciente Alfa' })).toHaveAttribute(
      'href',
      `/pacientes/${PACIENTE_A}`,
    )
  })

  it('paciente excluído aparece sem link', async () => {
    servir(() => pagina(linhas))
    renderizar(<TrilhaDeAcesso />)

    await lista()
    expect(screen.getByText('Paciente Beta')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Paciente Beta' })).not.toBeInTheDocument()
  })

  it('com privacidade ligada só o nome ganha data-borrado', async () => {
    servir(() => pagina(linhas))
    renderizar(<TrilhaDeAcesso />, { privacidadeLigada: true })

    await lista()
    expect(screen.getByText('Paciente Alfa').closest('[data-borrado]')).not.toBeNull()
    expect(screen.getByText('Paciente Beta').closest('[data-borrado]')).not.toBeNull()
    const itens = screen.getAllByRole('listitem')
    expect(itens[2].querySelector('[data-borrado]')).toBeNull()
    expect(itens[0].querySelectorAll('[data-borrado]')).toHaveLength(1)
    expect(itens[0].querySelector('[data-borrado]')).toHaveTextContent(/^Paciente Alfa$/)
  })

  it('sem privacidade o nome não tem data-borrado', async () => {
    servir(() => pagina(linhas))
    renderizar(<TrilhaDeAcesso />)

    await lista()
    expect(document.querySelector('[data-borrado]')).toBeNull()
  })

  it('pede a primeira página com 20 itens e sem entidade', async () => {
    const pedidos = servir(() => pagina(linhas))
    renderizar(<TrilhaDeAcesso />)

    await lista()
    expect(pedidos[0].searchParams.get('page')).toBe('0')
    expect(pedidos[0].searchParams.get('size')).toBe('20')
    expect(pedidos[0].searchParams.has('entidade')).toBe(false)
  })

  it('o filtro Sessões pede entidade=Consulta e volta à página 0', async () => {
    const pedidos = servir((url) => pagina(linhas, Number(url.searchParams.get('page')), 3))
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    await usuario.click(screen.getByRole('button', { name: 'Mais antigos' }))
    await esperarPedido(pedidos, (u) => u.searchParams.get('page') === '1')

    await usuario.selectOptions(screen.getByLabelText('Tipo'), 'Sessões')
    await esperarPedido(pedidos, (u) => u.searchParams.get('entidade') === 'Consulta')

    const ultimo = pedidos[pedidos.length - 1]
    expect(ultimo.searchParams.get('entidade')).toBe('Consulta')
    expect(ultimo.searchParams.get('page')).toBe('0')
  })

  it.each([
    ['Fichas', 'FichaDoPaciente'],
    ['Pacientes', 'Paciente'],
    ['Anotações', 'Anotacao'],
    ['Ajustes', 'Ajustes'],
  ])('o filtro %s pede entidade=%s', async (opcao, entidade) => {
    const pedidos = servir(() => pagina(linhas))
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    await usuario.selectOptions(screen.getByLabelText('Tipo'), opcao)
    await esperarPedido(pedidos, (u) => u.searchParams.get('entidade') === entidade)
  })

  it('voltar para Tudo tira o parâmetro entidade', async () => {
    const pedidos = servir(() => pagina(linhas))
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    await usuario.selectOptions(screen.getByLabelText('Tipo'), 'Fichas')
    await esperarPedido(pedidos, (u) => u.searchParams.get('entidade') === 'FichaDoPaciente')
    await usuario.selectOptions(screen.getByLabelText('Tipo'), 'Tudo')
    await esperarPedido(
      pedidos,
      (u) => !u.searchParams.has('entidade') && pedidos.indexOf(u) > 0,
    )
  })

  it('a lista fica aria-busy enquanto a página do novo filtro carrega', async () => {
    let liberar: () => void = () => {}
    const portao = new Promise<void>((resolve) => {
      liberar = resolve
    })
    servidor.use(
      http.get('/auditoria', async ({ request }) => {
        if (new URL(request.url).searchParams.has('entidade')) await portao
        return HttpResponse.json(pagina(linhas))
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    expect(await lista()).toHaveAttribute('aria-busy', 'false')
    await usuario.selectOptions(screen.getByLabelText('Tipo'), 'Sessões')
    await waitFor(() =>
      expect(screen.getByRole('list', { name: 'Trilha de acesso' })).toHaveAttribute(
        'aria-busy',
        'true',
      ),
    )
    liberar()
    await waitFor(() =>
      expect(screen.getByRole('list', { name: 'Trilha de acesso' })).toHaveAttribute(
        'aria-busy',
        'false',
      ),
    )
  })

  it('Mais antigos pede a página 1', async () => {
    const pedidos = servir((url) => pagina(linhas, Number(url.searchParams.get('page')), 3))
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    await usuario.click(screen.getByRole('button', { name: 'Mais antigos' }))
    await esperarPedido(pedidos, (u) => u.searchParams.get('page') === '1')
  })

  it('Mais recentes volta uma página', async () => {
    const pedidos = servir((url) => pagina(linhas, Number(url.searchParams.get('page')), 3))
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    await usuario.click(screen.getByRole('button', { name: 'Mais antigos' }))
    await esperarPedido(pedidos, (u) => u.searchParams.get('page') === '1')
    await usuario.click(screen.getByRole('button', { name: 'Mais recentes' }))
    await esperarPedido(pedidos, (u) => pedidos.indexOf(u) > 1 && u.searchParams.get('page') === '0')
  })

  it('Mais recentes fica desabilitado na primeira página', async () => {
    servir(() => pagina(linhas, 0, 3))
    renderizar(<TrilhaDeAcesso />)

    await lista()
    expect(screen.getByRole('button', { name: 'Mais recentes' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Mais antigos' })).toBeEnabled()
  })

  it('Mais antigos fica desabilitado na última página', async () => {
    servir(() => pagina(linhas, 0, 1))
    renderizar(<TrilhaDeAcesso />)

    await lista()
    expect(screen.getByRole('button', { name: 'Mais antigos' })).toBeDisabled()
  })

  it('enquanto a página do novo filtro carrega, os dois botões ficam desabilitados', async () => {
    let liberar: () => void = () => {}
    const portao = new Promise<void>((resolve) => {
      liberar = resolve
    })
    servidor.use(
      http.get('/auditoria', async ({ request }) => {
        const url = new URL(request.url)
        if (url.searchParams.has('entidade')) {
          await portao
          return HttpResponse.json(pagina(linhas, 0, 1))
        }
        return HttpResponse.json(pagina(linhas, 0, 3))
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    expect(screen.getByRole('button', { name: 'Mais antigos' })).toBeEnabled()
    await usuario.selectOptions(screen.getByLabelText('Tipo'), 'Sessões')
    await waitFor(() =>
      expect(screen.getByRole('list', { name: 'Trilha de acesso' })).toHaveAttribute(
        'aria-busy',
        'true',
      ),
    )
    expect(screen.getByRole('button', { name: 'Mais antigos' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Mais recentes' })).toBeDisabled()
    liberar()
    await waitFor(() =>
      expect(screen.getByRole('list', { name: 'Trilha de acesso' })).toHaveAttribute(
        'aria-busy',
        'false',
      ),
    )
    expect(screen.getByRole('button', { name: 'Mais antigos' })).toBeDisabled()
  })

  it('ao chegar na última página o foco vai para Mais recentes', async () => {
    servir((url) => pagina(linhas, Number(url.searchParams.get('page')), 2))
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    await usuario.click(screen.getByRole('button', { name: 'Mais antigos' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Mais antigos' })).toBeDisabled())
    expect(screen.getByRole('button', { name: 'Mais recentes' })).toHaveFocus()
  })

  it('ao voltar para a primeira página o foco vai para Mais antigos', async () => {
    servir((url) => pagina(linhas, Number(url.searchParams.get('page')), 2))
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    await usuario.click(screen.getByRole('button', { name: 'Mais antigos' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Mais recentes' })).toHaveFocus())
    await usuario.click(screen.getByRole('button', { name: 'Mais recentes' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Mais recentes' })).toBeDisabled())
    expect(screen.getByRole('button', { name: 'Mais antigos' })).toHaveFocus()
  })

  it('no meio do caminho o foco fica no botão clicado', async () => {
    servir((url) => pagina(linhas, Number(url.searchParams.get('page')), 3))
    const usuario = userEvent.setup()
    renderizar(<TrilhaDeAcesso />)

    await lista()
    await usuario.click(screen.getByRole('button', { name: 'Mais antigos' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Mais recentes' })).toBeEnabled())
    expect(screen.getByRole('button', { name: 'Mais antigos' })).toHaveFocus()
  })

  it('anuncia o carregamento', async () => {
    servir(() => pagina(linhas))
    renderizar(<TrilhaDeAcesso />)

    expect(screen.getByText('Carregando a trilha…')).toBeInTheDocument()
    await lista()
  })

  it('mostra o estado vazio', async () => {
    servir(() => pagina([]))
    renderizar(<TrilhaDeAcesso />)

    expect(await screen.findByText('Nenhum registro ainda.')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Trilha de acesso' })).not.toBeInTheDocument()
  })

  it('mostra o erro em alerta', async () => {
    servidor.use(
      http.get('/auditoria', () =>
        HttpResponse.json({ status: 500, mensagem: 'Falha', erros: [] }, { status: 500 }),
      ),
    )
    renderizar(<TrilhaDeAcesso />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar a trilha. Tente de novo.',
    )
  })
})

async function esperarPedido(pedidos: URL[], condicao: (u: URL) => boolean) {
  await waitFor(() => expect(pedidos.some(condicao)).toBe(true))
}
