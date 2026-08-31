import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { gravarAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComProvedores } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { AuditoriaPage } from './AuditoriaPage'

const LOG = {
  id: 'log-1',
  acao: 'CRIACAO',
  entidade: 'Paciente',
  entidadeId: '3f9a1c04-0000-0000-0000-000000000001',
  createdAt: '2026-08-19T10:32:00',
}

function pagina(itens: unknown[], total = itens.length) {
  return {
    content: itens,
    page: { size: 20, number: 0, totalElements: total, totalPages: Math.max(1, Math.ceil(total / 20)) },
  }
}

describe('AuditoriaPage', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    gravarAcesso('token-de-teste')
  })

  it('mostra a acao em portugues, com data e hora', async () => {
    servidorDeTeste.use(http.get('/auditoria', () => HttpResponse.json(pagina([LOG]))))

    renderizarComProvedores(<AuditoriaPage />, '/auditoria')

    expect(await screen.findByText('criou')).toBeInTheDocument()
    expect(screen.getByText('qua, 19 ago 2026 · 10:32')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Registro' })).toBeInTheDocument()
  })

  it('os filtros e a pagina vem da URL e viajam para a API', async () => {
    const pedidos: string[] = []
    servidorDeTeste.use(
      http.get('/auditoria', ({ request }) => {
        const busca = new URL(request.url).searchParams
        pedidos.push(
          `${busca.get('entidade')}|${busca.get('entidadeId')}|${busca.get('page')}`,
        )
        return HttpResponse.json(pagina([LOG], 42))
      }),
    )

    renderizarComProvedores(
      <AuditoriaPage />,
      '/auditoria?entidade=Consulta&entidadeId=abc&page=2',
    )

    await waitFor(() => expect(pedidos).toEqual(['Consulta|abc|2']))
    expect(await screen.findByText('página 3 de 3 · 42 registros')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled()
    expect(screen.getByLabelText('Identificador')).toHaveValue('abc')
  })

  /** Filtrar na pagina 3 e continuar na pagina 3 mostra uma lista vazia sem motivo. */
  it('trocar o filtro volta para a primeira pagina', async () => {
    const pedidos: string[] = []
    servidorDeTeste.use(
      http.get('/auditoria', ({ request }) => {
        const busca = new URL(request.url).searchParams
        pedidos.push(`${busca.get('entidade')}|${busca.get('page')}`)
        return HttpResponse.json(pagina([LOG], 42))
      }),
    )

    renderizarComProvedores(<AuditoriaPage />, '/auditoria?page=2')
    await waitFor(() => expect(pedidos).toContain('null|2'))

    await userEvent.selectOptions(screen.getByLabelText('Tipo de registro'), 'Lembrete')

    await waitFor(() => expect(pedidos).toContain('Lembrete|0'))
  })

  it('sem registro nenhum, convida em vez de mostrar tabela vazia', async () => {
    servidorDeTeste.use(http.get('/auditoria', () => HttpResponse.json(pagina([]))))

    renderizarComProvedores(<AuditoriaPage />, '/auditoria')

    expect(await screen.findByText('A trilha começa no primeiro cadastro.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
})
