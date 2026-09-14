import { z } from 'zod'

/**
 * Espelha o `ConsultaCadastroDTO` do backend.
 *
 * zod 4: a forma encadeada `.uuid(...)` em cima de `z.string()` esta marcada
 * `@deprecated` — a forma atual e top-level. Mas `z.uuid(...)` exige um UUID
 * RFC 9562/4122 de verdade (nibble de versao 1-8, nibble de variante
 * 8/9/a/b), e o id de teste usado nesta fatia inteira —
 * `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`, tambem em HojePage.test.tsx — nao
 * satisfaz isso (nibble de versao 'a'). `z.uuid()` rejeitava a selecao no
 * dialogo e o POST nunca saia. `z.guid(...)` e o formato certo aqui: mesma
 * forma 8-4-4-4-12 em hex, sem exigir versao/variante — o suficiente para
 * client-side ("preencheu um identificador plausivel"), com a mesma
 * mensagem como primeiro argumento (idiom de `z.email(...)` do login). O
 * servidor continua sendo a autoridade sobre o formato exato. Uma string
 * vazia (o `<option value="">` do select) falha o formato e cai nesta
 * mesma mensagem, entao nao precisa de um `.min(1)` separado como no login.
 */
export const esquemaDaConsulta = z.object({
  pacienteId: z.guid('Escolha o paciente.'),
  dtConsulta: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/, 'Informe data e hora.'),
})

export type DadosDaConsulta = z.infer<typeof esquemaDaConsulta>
