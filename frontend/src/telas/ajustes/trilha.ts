import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'

type LinhaDTO = components['schemas']['LogAuditoriaResponseDTO']

export type LinhaDaTrilha = {
  id: NonNullable<LinhaDTO['id']>
  acao: NonNullable<LinhaDTO['acao']>
  entidade: NonNullable<LinhaDTO['entidade']>
  entidadeId: NonNullable<LinhaDTO['entidadeId']>
  createdAt: NonNullable<LinhaDTO['createdAt']>
  pacienteId?: NonNullable<LinhaDTO['pacienteId']> | null
  pacienteNome?: NonNullable<LinhaDTO['pacienteNome']> | null
  pacienteExcluido?: NonNullable<LinhaDTO['pacienteExcluido']> | null
}

export type PaginaDaTrilha = {
  content: LinhaDaTrilha[]
  page: { number: number; totalPages: number }
}

export const TAMANHO_DA_TRILHA = 20

export function useTrilha(pagina: number, entidade: string | undefined) {
  return useQuery({
    queryKey: ['trilha', pagina, entidade ?? 'tudo'],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(pagina), size: String(TAMANHO_DA_TRILHA) })
      if (entidade) params.set('entidade', entidade)
      return api<PaginaDaTrilha>(`/auditoria?${params.toString()}`)
    },
    placeholderData: keepPreviousData,
  })
}
