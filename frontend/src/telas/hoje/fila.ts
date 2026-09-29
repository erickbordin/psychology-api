import type { PendenciaDoPainel, SessaoDoDia } from './queries'

export type ItemDaFila = {
  consultaId: string
  dtConsulta: string
  pacienteNome: string
  pacienteId: string | null
  tipo: PendenciaDoPainel['tipo'] | null
  status: SessaoDoDia['status'] | null
}

export type PainelReconciliado = {
  fila: ItemDaFila[]
  resolvidas: SessaoDoDia[]
}

export function reconciliar(
  sessoes: SessaoDoDia[],
  pendencias: PendenciaDoPainel[],
): PainelReconciliado {
  const porId = new Map<string, ItemDaFila>()

  for (const s of sessoes) {
    if (s.status !== 'AGENDADA') continue
    porId.set(s.consultaId, {
      consultaId: s.consultaId,
      dtConsulta: s.dtConsulta,
      pacienteNome: s.pacienteNome,
      pacienteId: s.pacienteId,
      tipo: null,
      status: s.status,
    })
  }

  const sessaoPorId = new Map(sessoes.map((s) => [s.consultaId, s]))

  for (const p of pendencias) {
    const par = sessaoPorId.get(p.consultaId)
    porId.set(p.consultaId, {
      consultaId: p.consultaId,
      dtConsulta: p.dtConsulta,
      pacienteNome: p.pacienteNome,
      pacienteId: par ? par.pacienteId : null,
      tipo: p.tipo,
      status: par ? par.status : null,
    })
  }

  const fila = [...porId.values()].sort((a, b) => a.dtConsulta.localeCompare(b.dtConsulta))
  const resolvidas = sessoes.filter((s) => !porId.has(s.consultaId))

  return { fila, resolvidas }
}
