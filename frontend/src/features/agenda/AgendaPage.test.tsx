import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { gravarAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComProvedores } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { AgendaPage } from './AgendaPage'

const PACIENTE_ID = '3f9a1c04-0000-0000-0000-000000000001'

const ANA = {
  idPaciente: PACIENTE_ID,
  idUsuario: 'u-1',
  nome: 'Ana Moreira',
  email: null,
  telefone: null,
  dataNascimento: '1991-04-12',
  createdAt: '2026-03-11T10:00:00',
}

function consulta(mudanca: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'c1',
    pacienteId: PACIENTE_ID,
    pacienteNome: 'Ana Moreira',
    dtConsulta: '2026-09-02T15:00:00',
    status: 'AGENDADA',
    createdAt: '2026-08-19T10:00:00',
    serieId: null,
    ...mudanca,
  }
}

describe('AgendaPage', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    gravarAcesso('token-de-teste')
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.json([ANA])))
  })

  it('o intervalo vem da URL e vai para a API', async () => {
    const intervalos: string[] = []
    servidorDeTeste.use(
      http.get('/consultas', ({ request }) => {
        const busca = new URL(request.url).searchParams
        intervalos.push(`${busca.get('de')}..${busca.get('ate')}`)
        return HttpResponse.json([consulta()])
      }),
    )

    renderizarComProvedores(<AgendaPage />, '/agenda?de=2026-09-01&ate=2026-09-07')

    expect(await screen.findByText('Ana Moreira')).toBeInTheDocument()
    expect(intervalos).toEqual(['2026-09-01..2026-09-07'])
    expect(screen.getByText('15:00')).toBeInTheDocument()
    expect(screen.getByText('agendada')).toBeInTheDocument()
  })

  it('agendar manda pacienteId e dtConsulta no mesmo corpo', async () => {
    const corpos: unknown[] = []
    servidorDeTeste.use(
      http.get('/consultas', () => HttpResponse.json([])),
      http.post('/consultas', async ({ request }) => {
        corpos.push(await request.json())
        return HttpResponse.json(consulta(), { status: 201 })
      }),
    )

    renderizarComProvedores(<AgendaPage />, '/agenda?de=2026-09-01&ate=2026-09-01')
    await userEvent.click(await screen.findByRole('button', { name: 'Nova consulta' }))

    await userEvent.selectOptions(await screen.findByLabelText('Paciente'), PACIENTE_ID)
    fireEvent.change(screen.getByLabelText('Data'), { target: { value: '2026-09-02' } })
    fireEvent.change(screen.getByLabelText('Hora'), { target: { value: '15:00' } })
    await userEvent.click(screen.getByRole('button', { name: 'Agendar' }))

    await waitFor(() =>
      expect(corpos).toEqual([{ pacienteId: PACIENTE_ID, dtConsulta: '2026-09-02T15:00:00' }]),
    )
  })

  it('a serie mostra as datas semanais antes de criar', async () => {
    servidorDeTeste.use(http.get('/consultas', () => HttpResponse.json([])))

    renderizarComProvedores(<AgendaPage />, '/agenda')
    await userEvent.click(await screen.findByRole('button', { name: 'Série semanal' }))

    fireEvent.change(screen.getByLabelText('Primeira sessão'), {
      target: { value: '2026-09-02' },
    })
    const quantidade = screen.getByLabelText('Quantidade de sessões')
    fireEvent.change(quantidade, { target: { value: '3' } })

    expect(await screen.findByText('3 sessões às 15:00')).toBeInTheDocument()
    expect(screen.getByText('qua, 2 set 2026')).toBeInTheDocument()
    expect(screen.getByText('qua, 9 set 2026')).toBeInTheDocument()
    expect(screen.getByText('qua, 16 set 2026')).toBeInTheDocument()
  })

  it('a serie manda o XOR do horizonte: quantidade sem dtLimite', async () => {
    const corpos: unknown[] = []
    servidorDeTeste.use(
      http.get('/consultas', () => HttpResponse.json([])),
      http.post('/consultas/recorrentes', async ({ request }) => {
        corpos.push(await request.json())
        return HttpResponse.json({ serieId: 's1', quantidade: 3, consultas: [] }, { status: 201 })
      }),
    )

    renderizarComProvedores(<AgendaPage />, '/agenda')
    await userEvent.click(await screen.findByRole('button', { name: 'Série semanal' }))
    await userEvent.selectOptions(await screen.findByLabelText('Paciente'), PACIENTE_ID)
    fireEvent.change(screen.getByLabelText('Primeira sessão'), { target: { value: '2026-09-02' } })
    fireEvent.change(screen.getByLabelText('Quantidade de sessões'), { target: { value: '3' } })
    await userEvent.click(screen.getByRole('button', { name: 'Criar série' }))

    await waitFor(() =>
      expect(corpos).toEqual([
        {
          pacienteId: PACIENTE_ID,
          dtPrimeiraConsulta: '2026-09-02T15:00:00',
          quantidadeSessoes: 3,
        },
      ]),
    )
  })

  /**
   * O caso que a spec nomeia. Um 409 silencioso numa serie de doze sessoes e a
   * pior falha possivel desta tela: o psicologo precisa saber que NADA foi
   * criado, e a agenda atras nao pode ganhar nenhuma ocorrencia.
   */
  it('409 da serie diz que nada foi criado e a agenda continua vazia', async () => {
    let listagens = 0
    servidorDeTeste.use(
      http.get('/consultas', () => {
        listagens += 1
        return HttpResponse.json([])
      }),
      http.post('/consultas/recorrentes', () =>
        HttpResponse.json(
          {
            status: 409,
            mensagem: 'Já existe consulta para este paciente em 09/09/2026 às 15:00.',
            erros: [],
          },
          { status: 409 },
        ),
      ),
    )

    renderizarComProvedores(<AgendaPage />, '/agenda')
    await userEvent.click(await screen.findByRole('button', { name: 'Série semanal' }))
    await userEvent.selectOptions(await screen.findByLabelText('Paciente'), PACIENTE_ID)
    fireEvent.change(screen.getByLabelText('Primeira sessão'), { target: { value: '2026-09-02' } })
    fireEvent.change(screen.getByLabelText('Quantidade de sessões'), { target: { value: '12' } })

    const antesDoEnvio = listagens
    await userEvent.click(screen.getByRole('button', { name: 'Criar série' }))

    expect(
      await screen.findByText(/Conflito de horário: nenhuma sessão foi criada\./),
    ).toBeInTheDocument()
    expect(screen.getByText(/09\/09\/2026/)).toBeInTheDocument()
    // o dialogo continua aberto, com o que foi digitado, e a agenda nao foi refeita
    expect(screen.getByRole('button', { name: 'Criar série' })).toBeInTheDocument()
    expect(listagens).toBe(antesDoEnvio)
    expect(screen.getByText('Nenhuma consulta neste intervalo.')).toBeInTheDocument()
  })

  it('mudar o status manda PUT com a data e o status', async () => {
    const corpos: unknown[] = []
    servidorDeTeste.use(
      http.get('/consultas', () => HttpResponse.json([consulta()])),
      http.put('/consultas/c1', async ({ request }) => {
        corpos.push(await request.json())
        return HttpResponse.json(consulta({ status: 'REALIZADA' }))
      }),
    )

    renderizarComProvedores(<AgendaPage />, '/agenda?de=2026-09-02&ate=2026-09-02')
    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }))
    await userEvent.selectOptions(await screen.findByLabelText('Status'), 'REALIZADA')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() =>
      expect(corpos).toEqual([{ dtConsulta: '2026-09-02T15:00:00', status: 'REALIZADA' }]),
    )
  })

  it('cancelar a serie inteira exige um segundo passo', async () => {
    let cancelou = false
    servidorDeTeste.use(
      http.get('/consultas', () => HttpResponse.json([consulta({ serieId: 's1' })])),
      http.delete('/consultas/series/s1', () => {
        cancelou = true
        return HttpResponse.json({ serieId: 's1', ocorrenciasRemovidas: 7 })
      }),
    )

    renderizarComProvedores(<AgendaPage />, '/agenda?de=2026-09-02&ate=2026-09-02')
    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar série semanal' }))

    expect(cancelou).toBe(false)
    expect(
      await screen.findByRole('heading', { name: 'Cancelar a série inteira?' }),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar série' }))
    await waitFor(() => expect(cancelou).toBe(true))
  })
})
