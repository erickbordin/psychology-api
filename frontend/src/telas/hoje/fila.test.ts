import { describe, expect, it } from 'vitest'

import { reconciliar } from './fila'
import type { PendenciaDoPainel, SessaoDoDia } from './queries'

const sessao = (over: Partial<SessaoDoDia>): SessaoDoDia => ({
  consultaId: 'c1',
  dtConsulta: '2026-09-28T10:00:00',
  pacienteId: 'p1',
  pacienteNome: 'Paciente Um',
  status: 'AGENDADA',
  valor: null,
  pagoEm: null,
  ...over,
})

const pendencia = (over: Partial<PendenciaDoPainel>): PendenciaDoPainel => ({
  consultaId: 'c1',
  dtConsulta: '2026-09-28T10:00:00',
  pacienteNome: 'Paciente Um',
  tipo: 'SEM_STATUS',
  ...over,
})

describe('reconciliar', () => {
  it('a sessao agendada entra na fila, a realizada desce para as resolvidas', () => {
    const r = reconciliar(
      [
        sessao({ consultaId: 'a', status: 'AGENDADA' }),
        sessao({ consultaId: 'b', status: 'REALIZADA' }),
      ],
      [],
    )
    expect(r.fila.map((i) => i.consultaId)).toEqual(['a'])
    expect(r.resolvidas.map((s) => s.consultaId)).toEqual(['b'])
  })

  it('a mesma consulta nos dois arrays vira UMA linha', () => {
    const r = reconciliar(
      [sessao({ consultaId: 'a', status: 'REALIZADA' })],
      [pendencia({ consultaId: 'a', tipo: 'SEM_ANOTACAO' })],
    )
    expect(r.fila).toHaveLength(1)
    expect(r.resolvidas).toHaveLength(0)
  })

  it('no empate, a pendencia decide a acao e a sessao empresta o pacienteId', () => {
    const r = reconciliar(
      [sessao({ consultaId: 'a', status: 'REALIZADA', pacienteId: 'p9' })],
      [pendencia({ consultaId: 'a', tipo: 'SEM_ANOTACAO' })],
    )
    expect(r.fila[0].tipos).toEqual(['SEM_ANOTACAO'])
    expect(r.fila[0].pacienteId).toBe('p9')
  })

  it('pendencia de dia anterior nao tem par, entra na fila sem pacienteId', () => {
    const r = reconciliar(
      [],
      [pendencia({ consultaId: 'velha', dtConsulta: '2026-09-27T08:00:00' })],
    )
    expect(r.fila).toHaveLength(1)
    expect(r.fila[0].pacienteId).toBeNull()
  })

  it('a fila sai ordenada por hora, misturando as duas origens', () => {
    const r = reconciliar(
      [sessao({ consultaId: 'tarde', dtConsulta: '2026-09-28T16:00:00' })],
      [pendencia({ consultaId: 'cedo', dtConsulta: '2026-09-28T08:00:00' })],
    )
    expect(r.fila.map((i) => i.consultaId)).toEqual(['cedo', 'tarde'])
  })

  it('nenhuma consulta some: toda entrada aparece na fila OU nas resolvidas, nunca nas duas', () => {
    const sessoes = [
      sessao({ consultaId: 'a', status: 'AGENDADA' }),
      sessao({ consultaId: 'b', status: 'REALIZADA' }),
      sessao({ consultaId: 'c', status: 'FALTOU' }),
    ]
    const pendencias = [
      pendencia({ consultaId: 'b', tipo: 'SEM_ANOTACAO' }),
      pendencia({ consultaId: 'z', dtConsulta: '2026-09-27T09:00:00' }),
    ]
    const r = reconciliar(sessoes, pendencias)

    const naFila = new Set(r.fila.map((i) => i.consultaId))
    const resolvidas = new Set(r.resolvidas.map((s) => s.consultaId))

    expect([...naFila].filter((id) => resolvidas.has(id))).toEqual([])
    expect(new Set([...naFila, ...resolvidas])).toEqual(new Set(['a', 'b', 'c', 'z']))
  })

  it('a mesma sessao com duas pendencias vira UMA linha com as duas, na ordem recebida', () => {
    const r = reconciliar(
      [sessao({ consultaId: 'a', status: 'REALIZADA' })],
      [
        pendencia({ consultaId: 'a', tipo: 'SEM_ANOTACAO' }),
        pendencia({ consultaId: 'a', tipo: 'SEM_BAIXA' }),
      ],
    )
    expect(r.fila).toHaveLength(1)
    expect(r.fila[0].tipos).toEqual(['SEM_ANOTACAO', 'SEM_BAIXA'])
    expect(r.resolvidas).toHaveLength(0)
  })

  it('sessao agendada sem pendencia entra com tipos vazio', () => {
    const r = reconciliar([sessao({ consultaId: 'a', status: 'AGENDADA' })], [])
    expect(r.fila[0].tipos).toEqual([])
  })
})
