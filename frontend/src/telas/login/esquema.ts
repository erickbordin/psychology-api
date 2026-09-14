import { z } from 'zod'

/**
 * Espelha o `UsuarioLoginDTO` do backend. As mensagens sao as MESMAS que o
 * servidor usa — assim o usuario le a mesma frase vindo do cliente ou do
 * servidor, e nao percebe qual dos dois recusou.
 *
 * Aqui NAO ha regra de forca de senha: o login so precisa saber que o campo
 * foi preenchido. Exigir 10 caracteres na tela de entrada recusaria quem tem
 * senha antiga e ainda valida.
 *
 * zod 4: a forma encadeada `.email(...)` no proprio `z.string()` esta
 * marcada `@deprecated` — a forma atual e o top-level `z.email(...)`, e
 * `.pipe()` e quem compoe as duas validacoes SEM perder as duas mensagens.
 * `.pipe()` so roda o lado direito se o esquerdo (`.min(1, ...)`) passou, e
 * cada lado guarda a sua propria mensagem — verificado com `safeParse`:
 * `''` cai so em "Informe um e-mail.", e `'helena-arroba-nada'` cai so em
 * "Informe um e-mail em formato válido.".
 */
export const esquemaDoLogin = z.object({
  emailUsuario: z
    .string()
    .min(1, 'Informe um e-mail.')
    .pipe(z.email('Informe um e-mail em formato válido.')),
  senha: z.string().min(1, 'Informe a senha.'),
})

export type DadosDoLogin = z.infer<typeof esquemaDoLogin>
