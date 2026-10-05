import { z } from 'zod'

export const esquemaDoCadastro = z.object({
  codigoConvite: z.string().trim().min(1, 'Informe o código de convite.'),
  nome: z.string().trim().min(1, 'Informe o nome.'),
  email: z
    .string()
    .min(1, 'Informe um e-mail.')
    .pipe(z.email('Informe um e-mail em formato válido.')),
  senha: z
    .string()
    .min(1, 'Informe uma senha.')
    .min(10, 'A senha precisa de pelo menos 10 caracteres.')
    .max(64, 'A senha deve ter no máximo 64 caracteres.')
    .refine((valor) => new TextEncoder().encode(valor).length <= 72, {
      message: 'A senha deve ter no máximo 64 caracteres.',
    }),
  aceiteTermos: z.boolean().refine((aceito) => aceito, {
    message: 'Aceite os termos para criar a conta.',
  }),
})

export type DadosDoCadastro = z.infer<typeof esquemaDoCadastro>
