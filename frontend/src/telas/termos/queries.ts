import { useQuery } from '@tanstack/react-query'

import { api } from '../../api/cliente'

export type TermosAtuais = {
  versao: string
  termos: string
  privacidade: string
}

export const chaveDosTermos = ['termos', 'atual'] as const

export function useTermosAtuais() {
  return useQuery({
    queryKey: chaveDosTermos,
    queryFn: () => api<TermosAtuais>('/termos/atual'),
    retry: false,
  })
}
