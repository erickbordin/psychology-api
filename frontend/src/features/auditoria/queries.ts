import { useQuery } from '@tanstack/react-query'

import * as recurso from '../../api/recursos/auditoria'
import type { FiltroDeAuditoria } from '../../api/recursos/auditoria'

export const chavesDeAuditoria = {
  todas: ['auditoria'] as const,
  lista: (filtro: FiltroDeAuditoria) =>
    ['auditoria', filtro.entidade ?? '', filtro.entidadeId ?? '', filtro.pagina ?? 0] as const,
}

export function useAuditoria(filtro: FiltroDeAuditoria) {
  return useQuery({
    queryKey: chavesDeAuditoria.lista(filtro),
    queryFn: () => recurso.listarAuditoria(filtro),
  })
}
