import { z } from 'zod'

export const esquemaDoValor = z.object({
  valor: z
    .string()
    .refine((v) => v.trim() !== '', 'Informe o valor da sessão.')
    .refine(
      (v) => v.trim() === '' || /^\d+(\.\d{1,2})?$/.test(v.trim()),
      'O valor da sessão deve ter no máximo duas casas decimais.',
    )
    .refine((v) => v.trim() === '' || Number(v) > 0, 'O valor da sessão deve ser maior que zero.'),
})

export type DadosDoValor = z.infer<typeof esquemaDoValor>
