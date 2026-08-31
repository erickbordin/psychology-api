import { pedir } from '../client'
import type { LogAuditoria, Pagina } from '../tipos'

export type FiltroDeAuditoria = { entidade?: string; entidadeId?: string; pagina?: number }

export function listarAuditoria({
  entidade,
  entidadeId,
  pagina = 0,
}: FiltroDeAuditoria = {}): Promise<Pagina<LogAuditoria>> {
  const parametros = new URLSearchParams({ page: String(pagina) })
  if (entidade) parametros.set('entidade', entidade)
  if (entidadeId) parametros.set('entidadeId', entidadeId)
  return pedir<Pagina<LogAuditoria>>(`/auditoria?${parametros.toString()}`)
}
