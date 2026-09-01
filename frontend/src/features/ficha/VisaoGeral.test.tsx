import { screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { RouteObject } from 'react-router-dom'

import { gravarAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComRotas } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { FichaPage } from './FichaPage'
import { VisaoGeral } from './VisaoGeral'

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

const ROTAS: RouteObject[] = [
  {
    path: '/pacientes/:pacienteId',
    element: <FichaPage />,
    children: [{ index: true, element: <VisaoGeral /> }],
  },
  { path: '/pacientes/:pacienteId/anotacoes', element: <p>aba de anotações</p> },
]

function pagina<T>(itens: T[]) {
  return {
    content: itens,
    page: { size: 20, number: 0, totalElements: itens.length, totalPages: 1 },
  }
}

function consulta(id: string, status: string, dia: string) {
  return {
    id,
    pacienteId: ID,
    pacienteNome: 'Ana Moreira',
    dtConsulta: `${dia}T16:00:00`,
    status,
    createdAt: '2026-08-19T10:00:00',
    serieId: null,
  }
}

describe('VisaoGeral da ficha', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    gravarAcesso('token-de-teste')
    servidorDeTeste.use(http.get(`/pacientes/${ID}`, () => HttpResponse.json(PACIENTE)))
  })

  it('separa o que esta agendado do que ja foi realizado na mesma pagina de consultas', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/consultas`, () =>
        HttpResponse.json(
          pagina([
            consulta('c1', 'AGENDADA', '2026-09-02'),
            consulta('c2', 'REALIZADA', '2026-07-21'),
            consulta('c3', 'CANCELADA', '2026-07-14'),
          ]),
        ),
      ),
      http.get(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(
          pagina([
            {
              id: 'a1',
              pacienteId: ID,
              conteudo: 'Paciente relata ansiedade em situações sociais.',
              createdAt: '2026-08-19',
            },
          ]),
        ),
      ),
      http.get(`/pacientes/${ID}/lembretes`, () =>
        HttpResponse.json(
          pagina([
            {
              id: 'l1',
              descricao: 'Confirmar pagamento da consulta.',
              concluido: false,
              createdAt: '2026-08-19T10:00:00',
            },
            {
              id: 'l2',
              descricao: 'Isto já foi feito.',
              concluido: true,
              createdAt: '2026-08-12T10:00:00',
            },
          ]),
        ),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)

    expect(await screen.findByRole('heading', { name: 'Ana Moreira' })).toBeInTheDocument()

    // agendada no cartao de consultas, realizada no de historico, cancelada em
    // nenhum dos dois
    expect(await screen.findByText('qua, 2 set 2026 · 16:00')).toBeInTheDocument()
    expect(screen.getByText('ter, 21 jul 2026 · 16:00')).toBeInTheDocument()
    expect(screen.queryByText('ter, 14 jul 2026 · 16:00')).not.toBeInTheDocument()

    expect(screen.getByText('Paciente relata ansiedade em situações sociais.')).toBeInTheDocument()

    // lembrete concluido nao ocupa espaco no resumo — o cartao e do que falta
    expect(screen.getByText('Confirmar pagamento da consulta.')).toBeInTheDocument()
    expect(screen.queryByText('Isto já foi feito.')).not.toBeInTheDocument()
  })

  it('cada cartao leva para a sua lista, e o historico para a trilha do paciente', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/consultas`, () => HttpResponse.json(pagina([]))),
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(pagina([]))),
      http.get(`/pacientes/${ID}/lembretes`, () => HttpResponse.json(pagina([]))),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)

    expect(await screen.findByRole('link', { name: 'Anotações' })).toHaveAttribute(
      'href',
      `/pacientes/${ID}/anotacoes`,
    )
    expect(screen.getByRole('link', { name: 'Histórico' })).toHaveAttribute(
      'href',
      `/auditoria?entidade=Paciente&entidadeId=${ID}`,
    )
  })
})
