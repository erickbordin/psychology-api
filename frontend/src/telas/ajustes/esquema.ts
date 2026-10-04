import { z } from 'zod'

export const esquemaDosAjustes = z
  .object({
    nome: z
      .string()
      .trim()
      .min(1, 'Informe seu nome.')
      .pipe(
        z
          .string()
          .min(2, 'O nome deve ter entre 2 e 100 caracteres.')
          .max(100, 'O nome deve ter entre 2 e 100 caracteres.'),
      ),
    valorPadraoSessao: z
      .string()
      .refine(
        (v) => v === '' || /^\d+(\.\d{1,2})?$/.test(v.trim()),
        'O valor da sessão deve ter no máximo duas casas decimais.',
      )
      .refine((v) => v === '' || Number(v) > 0, 'O valor da sessão deve ser maior que zero.'),
    antecedencia: z.enum(['H24', 'H2', 'VESPERA'], 'Escolha a antecedência do aviso.'),
    blocosAviso: z.array(z.enum(['REMARCAR', 'CHEGADA', 'DURACAO'])),
    tituloNoEmail: z.boolean(),
    tituloProfissional: z.string().max(80, 'O título deve ter no máximo 80 caracteres.'),
    resumoDiario: z.boolean(),
  })
  .refine((v) => !v.tituloNoEmail || v.tituloProfissional.trim() !== '', {
    message: 'Informe o título profissional para incluí-lo no e-mail.',
    path: ['tituloProfissional'],
  })

export type DadosDosAjustes = z.infer<typeof esquemaDosAjustes>
