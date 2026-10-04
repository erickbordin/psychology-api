import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { AgendaPage } from './AgendaPage'

const AJUSTES = {
  nome: 'Ana Souza',
  email: 'ana@exemplo.local',
  valorPadraoSessao: null,
  antecedencia: 'H24',
  blocosAviso: [],
  tituloNoEmail: false,
  tituloProfissional: null,
  resumoDiario: false,
  expedienteInicio: null,
  expedienteFim: null,
}

function servirAjustes(corpo: object = AJUSTES) {
  servidor.use(http.get('/ajustes', () => HttpResponse.json(corpo)))
}

function servirSemana(sessoes: { hora: string; status: string }[]) {
  servidor.use(
    http.get('/consultas', ({ request }) => {
      const de = new URL(request.url).searchParams.get('de')
      return HttpResponse.json(
        sessoes.map((s, i) => ({
          id: `77777777-7777-7777-7777-77777777777${i}`,
          pacienteNome: `Cliente_00${i + 1}`,
          dtConsulta: `${de}T${s.hora}:00`,
          status: s.status,
        })),
      )
    }),
  )
}

describe('agenda', () => {
  beforeEach(() => {
    servirAjustes()
  })

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
            status: 'REALIZADA',
          },
        ])
      }),
    )
    renderizar(<AgendaPage />)

    expect(await screen.findByText(/otavio lins/i)).toBeInTheDocument()
  })

  it('o bloco diz o estado da sessao, nao so o nome', async () => {
    servidor.use(
      http.get('/consultas', ({ request }) => {
        const de = new URL(request.url).searchParams.get('de')
        return HttpResponse.json([
          {
            id: '44444444-4444-4444-4444-444444444444',
            pacienteNome: 'Otavio Lins',
            dtConsulta: `${de}T07:00:00`,
            status: 'REALIZADA',
          },
        ])
      }),
    )
    renderizar(<AgendaPage />)

    await screen.findByRole('grid', { name: /semana/i })
    const bloco = (await screen.findByText(/otavio lins/i)).closest<HTMLElement>('[role="gridcell"]')!
    expect(within(bloco).getByText(/realizada/i)).toBeInTheDocument()
  })

  it('o bloco ocupado carrega o horario da sessao, pronto pra revelar no foco', async () => {
    servidor.use(
      http.get('/consultas', ({ request }) => {
        const de = new URL(request.url).searchParams.get('de')
        return HttpResponse.json([
          {
            id: '44444444-4444-4444-4444-444444444444',
            pacienteNome: 'Otavio Lins',
            dtConsulta: `${de}T07:00:00`,
            status: 'REALIZADA',
          },
        ])
      }),
    )
    renderizar(<AgendaPage />)

    await screen.findByRole('grid', { name: /semana/i })
    const bloco = (await screen.findByText(/otavio lins/i)).closest<HTMLElement>('[role="gridcell"]')!
    expect(within(bloco).getByText('07:00')).toBeInTheDocument()
  })

  it('a grade nao perde nem ganha celula ao mostrar estado', async () => {
    servidor.use(http.get('/consultas', () => HttpResponse.json([])))
    renderizar(<AgendaPage />)

    const grade = await screen.findByRole('grid', { name: /semana/i })
    expect(within(grade).getAllByRole('columnheader')).toHaveLength(7)
    expect(within(grade).getAllByRole('rowheader')).toHaveLength(13)
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

  it('com expediente de 8 a 18 a grade vai de 08:00 a 17:00', async () => {
    servirAjustes({ ...AJUSTES, expedienteInicio: 8, expedienteFim: 18 })
    servirSemana([])
    renderizar(<AgendaPage />)

    const grade = await screen.findByRole('grid', { name: /semana/i })
    expect(within(grade).getByRole('rowheader', { name: '08:00' })).toBeInTheDocument()
    expect(within(grade).getByRole('rowheader', { name: '17:00' })).toBeInTheDocument()
    expect(within(grade).queryByRole('rowheader', { name: '07:00' })).not.toBeInTheDocument()
    expect(within(grade).queryByRole('rowheader', { name: '18:00' })).not.toBeInTheDocument()
  })

  it('sessao agendada fora do expediente estica a grade ate ela', async () => {
    servirAjustes({ ...AJUSTES, expedienteInicio: 8, expedienteFim: 18 })
    servirSemana([{ hora: '06:00', status: 'AGENDADA' }])
    renderizar(<AgendaPage />)

    const grade = await screen.findByRole('grid', { name: /semana/i })
    expect(within(grade).getByRole('rowheader', { name: '06:00' })).toBeInTheDocument()
  })

  it('sessao cancelada fora do expediente nao estica a grade', async () => {
    servirAjustes({ ...AJUSTES, expedienteInicio: 8, expedienteFim: 18 })
    servirSemana([{ hora: '06:00', status: 'CANCELADA' }])
    renderizar(<AgendaPage />)

    const grade = await screen.findByRole('grid', { name: /semana/i })
    expect(within(grade).getByRole('rowheader', { name: '08:00' })).toBeInTheDocument()
    expect(within(grade).queryByRole('rowheader', { name: '06:00' })).not.toBeInTheDocument()
  })

  it('expediente ate 24 termina na linha das 23:00', async () => {
    servirAjustes({ ...AJUSTES, expedienteInicio: 20, expedienteFim: 24 })
    servirSemana([])
    renderizar(<AgendaPage />)

    const grade = await screen.findByRole('grid', { name: /semana/i })
    expect(within(grade).getAllByRole('rowheader')).toHaveLength(4)
    expect(within(grade).getByRole('rowheader', { name: '23:00' })).toBeInTheDocument()
  })

  it('espera os ajustes antes de desenhar a grade', async () => {
    servirSemana([])
    servidor.use(
      http.get('/ajustes', async () => {
        await new Promise((resolver) => setTimeout(resolver, 20))
        return HttpResponse.json({ ...AJUSTES, expedienteInicio: 9, expedienteFim: 12 })
      }),
    )
    renderizar(<AgendaPage />)

    expect(screen.getByText('Carregando a semana…')).toBeInTheDocument()
    expect(screen.queryByRole('grid')).not.toBeInTheDocument()
    const grade = await screen.findByRole('grid', { name: /semana/i })
    expect(within(grade).getAllByRole('rowheader')).toHaveLength(3)
  })

  it('se os ajustes falham a grade padrao de 07:00 a 19:00 aparece', async () => {
    servirSemana([])
    servidor.use(http.get('/ajustes', () => new HttpResponse(null, { status: 500 })))
    renderizar(<AgendaPage />)

    const grade = await screen.findByRole('grid', { name: /semana/i })
    expect(within(grade).getByRole('rowheader', { name: '07:00' })).toBeInTheDocument()
    expect(within(grade).getByRole('rowheader', { name: '19:00' })).toBeInTheDocument()
    expect(within(grade).getAllByRole('rowheader')).toHaveLength(13)
  })
})
