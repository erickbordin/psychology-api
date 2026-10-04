import { useMutation, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'

import { api } from '../../api/cliente'

export const esquemaDaSenha = z.object({
  senhaAtual: z.string().min(1, 'Informe a senha atual.'),
  novaSenha: z
    .string()
    .min(1, 'Informe uma senha.')
    .min(10, 'A senha precisa de pelo menos 10 caracteres.')
    .max(64, 'A senha deve ter no máximo 64 caracteres.')
    .refine((v) => new TextEncoder().encode(v).length <= 72, {
      message: 'A senha deve ter no máximo 64 caracteres.',
    }),
})

export type DadosDaSenha = z.infer<typeof esquemaDaSenha>

export function useTrocarSenha() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (valores: DadosDaSenha) =>
      api<void>('/ajustes/senha', {
        method: 'POST',
        body: JSON.stringify({ senhaAtual: valores.senhaAtual, novaSenha: valores.novaSenha }),
      }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['trilha'] }),
    gcTime: 0,
  })
}
