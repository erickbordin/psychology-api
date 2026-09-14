import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { AgendaPage } from './AgendaPage'

describe('agenda', () => {
  it('mostra carregando enquanto busca a semana', async () => {
    servidor.use(
      http.get('/consultas', async () => {
        await new Promise((resolver) => setTimeout(resolver, 20))
        return HttpResponse.json([])
      }),
    )
    renderizar(<AgendaPage />)

    expect(screen.getByText(/carregando/i)).toBeInTheDocument()
    expect(await screen.findByRole('grid', { name: /semana/i })).toBeInTheDocument()
  })

  it('semana que falha ao carregar avisa, em vez de desenhar 91 celulas vazias mentindo que a semana esta livre', async () => {
    // Critico 4 da revisao do conjunto: `= []` engolia `isError`, e uma
    // falha de rede/API virava uma grade cheia de celulas vazias e
    // clicaveis — indistinguivel de uma semana genuinamente livre.
    servidor.use(http.get('/consultas', () => new HttpResponse(null, { status: 500 })))
    renderizar(<AgendaPage />)

    const aviso = await screen.findByText(/nao foi possivel carregar a semana/i)
    expect(aviso).toHaveAttribute('role', 'alert')
    expect(screen.queryByRole('grid')).not.toBeInTheDocument()
  })

  it('mostra a semana, nao o dia — o dia ja e a tela Hoje', async () => {
    servidor.use(http.get('/consultas', () => HttpResponse.json([])))
    renderizar(<AgendaPage />)

    const grade = await screen.findByRole('grid', { name: /semana/i })
    // sete colunas de dia
    expect(within(grade).getAllByRole('columnheader')).toHaveLength(7)
  })

  it('clicar num horario abre o dialogo ja com aquele horario', async () => {
    servidor.use(http.get('/consultas', () => HttpResponse.json([])))
    servidor.use(http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })))
    const usuario = userEvent.setup()
    renderizar(<AgendaPage />)

    const celulas = await screen.findAllByRole('gridcell')
    await usuario.click(celulas[0])

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('mostra as consultas da semana na grade', async () => {
    // `de` vem do proprio pedido (segunda da semana corrente, qualquer que
    // ela seja no dia em que a suite roda) — a resposta usa isso para cair
    // exatamente na primeira celula (segunda, 07h), sem depender de fake
    // timers nem de uma data fixa.
    servidor.use(
      http.get('/consultas', ({ request }) => {
        const de = new URL(request.url).searchParams.get('de')
        return HttpResponse.json([
          {
            id: '44444444-4444-4444-4444-444444444444',
            pacienteNome: 'Otavio Lins',
            dtConsulta: `${de}T07:00:00`,
            status: 'AGENDADA',
          },
        ])
      }),
    )
    renderizar(<AgendaPage />)

    expect(await screen.findByText(/otavio lins/i)).toBeInTheDocument()
  })

  it('consulta cancelada nao ocupa a grade', async () => {
    servidor.use(
      http.get('/consultas', ({ request }) => {
        const de = new URL(request.url).searchParams.get('de')
        return HttpResponse.json([
          {
            id: '55555555-5555-5555-5555-555555555555',
            pacienteNome: 'Helena Vaz',
            dtConsulta: `${de}T07:00:00`,
            status: 'CANCELADA',
          },
          {
            id: '66666666-6666-6666-6666-666666666666',
            pacienteNome: 'Rui Sampaio',
            dtConsulta: `${de}T08:00:00`,
            status: 'AGENDADA',
          },
        ])
      }),
    )
    renderizar(<AgendaPage />)

    // espera o mesmo ciclo assentar antes de checar a ausencia: a consulta
    // AGENDADA (Rui, 8h) so aparece depois que a MESMA resposta que trouxe a
    // CANCELADA (Helena, 7h) foi processada e a grade re-renderizou
    expect(await screen.findByText(/rui sampaio/i)).toBeInTheDocument()
    expect(screen.queryByText(/helena vaz/i)).not.toBeInTheDocument()
  })
})
