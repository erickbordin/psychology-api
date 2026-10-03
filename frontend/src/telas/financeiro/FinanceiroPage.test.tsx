import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { FinanceiroPage } from './FinanceiroPage'

import '../../estilo/estilo.css'

const BRUNA = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const CLARA = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
const DAVI = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
const S1 = '11111111-1111-4111-8111-111111111111'
const S2 = '22222222-2222-4222-8222-222222222222'
const S3 = '33333333-3333-4333-8333-333333333333'
const S4 = '44444444-4444-4444-8444-444444444444'
const S5 = '55555555-5555-4555-8555-555555555555'

const FINANCEIRO = {
  ano: 2026,
  mes: 12,
  faturado: 560,
  recebido: 200,
  aReceber: 360,
  sessoesSemValor: 1,
  sessoes: [
    { consultaId: S1, dtConsulta: '2026-12-02T09:00:00', pacienteId: BRUNA, pacienteNome: 'Bruna Teixeira', pacienteExcluido: false, valor: 180, pagoEm: null },
    { consultaId: S2, dtConsulta: '2026-12-03T09:00:00', pacienteId: BRUNA, pacienteNome: 'Bruna Teixeira', pacienteExcluido: false, valor: 180, pagoEm: null },
    { consultaId: S3, dtConsulta: '2026-12-04T10:00:00', pacienteId: CLARA, pacienteNome: 'Clara Nunes', pacienteExcluido: true, valor: 200, pagoEm: '2026-12-05T10:00:00' },
    { consultaId: S4, dtConsulta: '2026-12-05T11:00:00', pacienteId: DAVI, pacienteNome: 'Davi Prado', pacienteExcluido: false, valor: null, pagoEm: null },
  ],
  devendo: [
    { pacienteId: BRUNA, pacienteNome: 'Bruna Teixeira', pacienteExcluido: false, quantidadeSessoes: 2, total: 360, consultaIds: [S1, S2] },
    { pacienteId: CLARA, pacienteNome: 'Clara Nunes', pacienteExcluido: true, quantidadeSessoes: 1, total: 200, consultaIds: [S5] },
  ],
}

const PAINEL = { ano: 2026, mes: 12, realizadas: 3, faltas: 1, canceladas: 0, taxaDeFalta: 0.25, pacientesAtivos: 2 }

const VAZIO = { ...FINANCEIRO, faturado: 0, recebido: 0, aReceber: 0, sessoesSemValor: 0, sessoes: [], devendo: [] }

const comDados = (
  financeiro: Record<string, unknown> = FINANCEIRO,
  painel: Record<string, unknown> = PAINEL,
  pedidos: string[] = [],
) =>
  servidor.use(
    http.get('/financeiro/mes', ({ request }) => {
      pedidos.push(new URL(request.url).search)
      return HttpResponse.json(financeiro)
    }),
    http.get('/painel/mes', ({ request }) => {
      pedidos.push(new URL(request.url).search)
      return HttpResponse.json(painel)
    }),
  )

const fixarData = (data: Date) => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(data)
}

afterEach(() => {
  vi.useRealTimers()
})

describe('tela Financeiro', () => {
  it('mostra o mes corrente e pede os dois endpoints com ano e mes', async () => {
    fixarData(new Date(2026, 11, 15))
    const pedidos: string[] = []
    comDados(FINANCEIRO, PAINEL, pedidos)
    renderizar(<FinanceiroPage />)

    expect(screen.getByRole('heading', { name: 'Financeiro' })).toBeInTheDocument()
    expect(screen.getByText('dezembro de 2026')).toBeInTheDocument()
    await screen.findByRole('region', { name: 'O mês' })
    await vi.waitFor(() => expect(pedidos).toHaveLength(2))
    expect(pedidos.every((p) => p === '?ano=2026&mes=12')).toBe(true)
  })

  it('o painel do mes mostra contagens e a taxa de falta', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados()
    renderizar(<FinanceiroPage />)

    const regiao = await screen.findByRole('region', { name: 'O mês' })
    await within(regiao).findByText('Taxa de falta')
    expect(within(regiao).getByText('Realizadas').parentElement).toHaveTextContent('Realizadas3')
    expect(within(regiao).getByText('Taxa de falta').parentElement).toHaveTextContent('Taxa de falta25%')
    expect(within(regiao).getByText('Pacientes ativos').parentElement).toHaveTextContent('Pacientes ativos2')
  })

  it('taxa de falta nula vira traco', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados(FINANCEIRO, { ...PAINEL, taxaDeFalta: null })
    renderizar(<FinanceiroPage />)

    const regiao = await screen.findByRole('region', { name: 'O mês' })
    await within(regiao).findByText('Taxa de falta')
    expect(within(regiao).getByText('Taxa de falta').parentElement).toHaveTextContent('Taxa de falta—')
  })

  it('os totais usam reais e avisam das sessoes sem valor', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados()
    renderizar(<FinanceiroPage />)

    const regiao = await screen.findByRole('region', { name: 'Totais' })
    expect(await within(regiao).findByText('R$ 560,00')).toBeInTheDocument()
    expect(within(regiao).getByText('R$ 200,00')).toBeInTheDocument()
    expect(within(regiao).getByText('R$ 360,00')).toBeInTheDocument()
    expect(within(regiao).getByText('1 sessão sem valor definido, fora dos totais')).toBeInTheDocument()
  })

  it('o aviso vai para o plural e some em zero', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados({ ...FINANCEIRO, sessoesSemValor: 2 })
    const primeiro = renderizar(<FinanceiroPage />)
    expect(await screen.findByText('2 sessões sem valor definido, fora dos totais')).toBeInTheDocument()
    primeiro.unmount()

    comDados({ ...FINANCEIRO, sessoesSemValor: 0 })
    renderizar(<FinanceiroPage />)
    await screen.findByRole('region', { name: 'Totais' })
    await screen.findByText('R$ 560,00')
    expect(screen.queryByText(/sem valor definido, fora dos totais/)).not.toBeInTheDocument()
  })

  it('devedor excluido nao tem link da ficha e o outro tem', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados()
    renderizar(<FinanceiroPage />)

    const clara = await screen.findByRole('listitem', { name: 'Débito de Clara Nunes' })
    expect(clara).toHaveTextContent('(paciente excluído)')
    expect(within(clara).queryByRole('link', { name: 'Abrir a ficha' })).not.toBeInTheDocument()
    const bruna = screen.getByRole('listitem', { name: 'Débito de Bruna Teixeira' })
    expect(within(bruna).getByRole('link', { name: 'Abrir a ficha' })).toHaveAttribute('href', `/pacientes/${BRUNA}`)
  })

  it('dar baixa em todas manda o lote e anuncia', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados()
    let corpo: { consultaIds?: string[] } = {}
    servidor.use(
      http.post('/consultas/baixas', async ({ request }) => {
        corpo = (await request.json()) as { consultaIds: string[] }
        return HttpResponse.json({ consultasBaixadas: 2 })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<FinanceiroPage />)

    const bruna = await screen.findByRole('listitem', { name: 'Débito de Bruna Teixeira' })
    await usuario.click(within(bruna).getByRole('button', { name: 'Dar baixa em todas (2)' }))

    expect(await screen.findByText(/2 sessões de/)).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('2 sessões de Bruna Teixeira com baixa.')
    expect(corpo.consultaIds).toEqual([S1, S2])
  })

  it('lote recusado mostra a mensagem do servidor na linha do devedor', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados()
    servidor.use(
      http.post('/consultas/baixas', () =>
        HttpResponse.json(
          { status: 409, mensagem: 'Uma das sessões já recebeu baixa.', erros: [] },
          { status: 409 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    renderizar(<FinanceiroPage />)

    const bruna = await screen.findByRole('listitem', { name: 'Débito de Bruna Teixeira' })
    await usuario.click(within(bruna).getByRole('button', { name: 'Dar baixa em todas (2)' }))

    expect(await within(bruna).findByRole('alert')).toHaveTextContent('Uma das sessões já recebeu baixa.')
  })

  it('ninguem devendo diz o que aparece ali', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados(VAZIO)
    renderizar(<FinanceiroPage />)

    expect(await screen.findByText('Ninguém devendo')).toBeInTheDocument()
    expect(screen.getByText('Sessões realizadas sem baixa aparecem aqui.')).toBeInTheDocument()
  })

  it('cada sessao do mes tem o chip e a acao do seu estado', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados()
    let metodoDaBaixa = ''
    let corpoDaBaixa = ' '
    let desfez = ''
    servidor.use(
      http.patch('/consultas/:id/baixa', async ({ request, params }) => {
        metodoDaBaixa = String(params.id)
        corpoDaBaixa = await request.text()
        return HttpResponse.json({})
      }),
      http.delete('/consultas/:id/baixa', ({ params }) => {
        desfez = String(params.id)
        return HttpResponse.json({})
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<FinanceiroPage />)

    const paga = await screen.findByRole('listitem', { name: 'Sessão de Clara Nunes' })
    expect(within(paga).getByText('Pago')).toBeInTheDocument()
    await usuario.click(within(paga).getByRole('button', { name: 'Desfazer baixa' }))
    await vi.waitFor(() => expect(desfez).toBe(S3))

    const aReceber = screen.getAllByRole('listitem', { name: 'Sessão de Bruna Teixeira' })[0]
    expect(within(aReceber).getByText('A receber')).toBeInTheDocument()
    expect(within(aReceber).getByRole('button', { name: 'Alterar valor' })).toBeInTheDocument()
    await usuario.click(within(aReceber).getByRole('button', { name: 'Dar baixa' }))
    await vi.waitFor(() => expect(metodoDaBaixa).toBe(S1))
    expect(corpoDaBaixa).toBe('')

    const semValor = screen.getByRole('listitem', { name: 'Sessão de Davi Prado' })
    expect(within(semValor).getByText('Sem valor definido')).toBeInTheDocument()
    expect(within(semValor).getByRole('button', { name: 'Definir valor' })).toBeInTheDocument()
    expect(within(semValor).queryByRole('button', { name: 'Dar baixa' })).not.toBeInTheDocument()
  })

  it('mes sem sessoes diz que nao houve', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados(VAZIO)
    renderizar(<FinanceiroPage />)

    expect(await screen.findByText('Nenhuma sessão realizada em dezembro de 2026.')).toBeInTheDocument()
  })

  it('os botoes de mes andam para o vizinho', async () => {
    fixarData(new Date(2027, 0, 15))
    const pedidos: string[] = []
    comDados(FINANCEIRO, PAINEL, pedidos)
    const usuario = userEvent.setup()
    const primeiro = renderizar(<FinanceiroPage />)

    await usuario.click(screen.getByRole('button', { name: 'Mês anterior' }))
    expect(await screen.findByText('dezembro de 2026')).toBeInTheDocument()
    await vi.waitFor(() => expect(pedidos).toContain('?ano=2026&mes=12'))
    primeiro.unmount()

    vi.setSystemTime(new Date(2026, 11, 15))
    pedidos.length = 0
    renderizar(<FinanceiroPage />)
    await usuario.click(screen.getByRole('button', { name: 'Próximo mês' }))
    expect(await screen.findByText('janeiro de 2027')).toBeInTheDocument()
    await vi.waitFor(() => expect(pedidos).toContain('?ano=2027&mes=1'))
  })

  it('privacidade borra nomes e valores, nao chips nem porcentagem', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados()
    renderizar(<FinanceiroPage />, { privacidadeLigada: true })

    const borrao = 'blur(6px) saturate(0.7)'
    const debito = await screen.findByRole('listitem', { name: 'Débito de Bruna Teixeira' })
    expect(getComputedStyle(within(debito).getByText('Bruna Teixeira')).filter).toBe(borrao)
    const totais = await screen.findByRole('region', { name: 'Totais' })
    expect(getComputedStyle(await within(totais).findByText('R$ 560,00')).filter).toBe(borrao)
    expect(getComputedStyle(screen.getByText('Pago')).filter).not.toBe(borrao)
    expect(getComputedStyle(await screen.findByText('25%')).filter).not.toBe(borrao)
  })

  it('erro de carga avisa', async () => {
    fixarData(new Date(2026, 11, 15))
    servidor.use(
      http.get('/financeiro/mes', () =>
        HttpResponse.json({ status: 500, mensagem: 'Erro interno.', erros: [] }, { status: 500 }),
      ),
      http.get('/painel/mes', () => HttpResponse.json(PAINEL)),
    )
    renderizar(<FinanceiroPage />)

    expect(await screen.findByText('Não foi possível carregar o financeiro. Tente de novo.')).toBeInTheDocument()
  })

  it('alterar valor abre o dialogo com o valor atual', async () => {
    fixarData(new Date(2026, 11, 15))
    comDados()
    const usuario = userEvent.setup()
    renderizar(<FinanceiroPage />)

    const linha = (await screen.findAllByRole('listitem', { name: 'Sessão de Bruna Teixeira' }))[0]
    await usuario.click(within(linha).getByRole('button', { name: 'Alterar valor' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Alterar valor da sessão' })
    expect(within(dialogo).getByLabelText(/valor da sessão/i)).toHaveValue(180)
  })
})
