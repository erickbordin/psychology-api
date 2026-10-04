import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'

type DescadastroDTO = components['schemas']['DescadastroDTO']

export type Descadastro = { ativo: NonNullable<DescadastroDTO['ativo']> }

export function useDescadastro(token: string) {
  return useQuery({
    queryKey: ['descadastro', token],
    queryFn: () => api<Descadastro>(`/descadastro/${token}`),
    retry: false,
  })
}

export function useConfirmarDescadastro(token: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: () => api<Descadastro>(`/descadastro/${token}`, { method: 'POST' }),
    onSuccess: () => cliente.setQueryData<Descadastro>(['descadastro', token], { ativo: false }),
  })
}
