import { z } from 'zod'

/**
 * Espelha `PacienteCadastroDTO`. As mensagens sao as MESMAS do servidor, de
 * proposito: a regra e a mesma nos dois lados (spec 6.3), e duas redacoes
 * diferentes para a mesma recusa fazem o usuario achar que sao dois problemas.
 *
 * zod 4: `z.email(msg)` e `z.guid(msg)` sao a forma atual — a encadeada
 * `z.string().email()` esta deprecada. Nao volte para ela.
 *
 * `nome` usa `.pipe()` pelo mesmo motivo do e-mail do login: o backend tem
 * DUAS constraints com DUAS mensagens — `@NotBlank` ("Informe o nome do
 * paciente.") e `@Size(min=2,max=100)` ("O nome deve ter de 2 a 100
 * caracteres.") — e um `.min(2, msg-de-tamanho)` sozinho devolveria a
 * mensagem de tamanho tambem para o campo vazio, que e a mensagem errada
 * para aquele caso (verificado com `safeParse`: sem o `.pipe()`, `''` caia
 * em "O nome deve ter de 2 a 100 caracteres." em vez de "Informe o nome do
 * paciente.").
 */
export const esquemaDoPaciente = z
  .object({
    nome: z
      .string()
      .min(1, 'Informe o nome do paciente.')
      .pipe(
        z
          .string()
          .min(2, 'O nome deve ter de 2 a 100 caracteres.')
          .max(100, 'O nome deve ter de 2 a 100 caracteres.'),
      ),

    // opcional de verdade: string vazia do formulario vira ausencia.
    // O `.max(100)` espelha a coluna: a 6.3 poe o limite nos DOIS lados, e sem
    // ele o cliente deixa passar um endereco que o servidor recusa — o usuario
    // descobre apanhando, que e o defeito que o redesenho existe para matar.
    email: z.union([
      z.literal(''),
      z
        .email('Informe um e-mail em formato válido.')
        .max(100, 'O e-mail deve ter no máximo 100 caracteres.'),
    ]),

    // conta digitos, nao policia mascara — a mesma decisao do @TelefoneBR
    // tomada na Fatia 1 depois de a revisao medir o custo de policiar
    // pontuacao: recusava "(51) 99811-2345 " e aceitava "(5199811234"
    telefone: z
      .string()
      .max(20, 'O telefone deve ter no máximo 20 caracteres.')
      .refine(
        (v) => v === '' || [10, 11, 12, 13].includes(v.replace(/\D/g, '').length),
        'Informe um telefone com DDD.',
      ),

    dataNascimento: z.string(),

    // "> 0, 2 casas" (spec 6.3) — as DUAS metades, nao so a primeira. O
    // servidor recusa a terceira casa com @Digits(fraction = 2); sem o mesmo
    // limite aqui, digitar 180.005 passa no cliente e volta 400.
    valorSessao: z
      .string()
      .refine(
        (v) => v === '' || Number(v) > 0,
        'O valor da sessão deve ser maior que zero.',
      )
      .refine(
        (v) => v === '' || /^\d+([.,]\d{1,2})?$/.test(v.trim()),
        'O valor da sessão deve ter no máximo duas casas decimais.',
      ),

    canalAviso: z.enum(['NENHUM', 'EMAIL', 'WHATSAPP']),
  })
  // A regra condicional de 5.4 le DOIS campos, entao vive no objeto e nao num
  // campo. `path` aponta o erro para `email`: o envelope da casa entrega campo
  // culpado e o formulario poe o foco nele — erro sem campo nao tem onde pousar.
  .refine((v) => v.canalAviso !== 'EMAIL' || v.email !== '', {
    message: 'Informe o e-mail do paciente para avisar por e-mail.',
    path: ['email'],
  })

export type DadosDoPaciente = z.infer<typeof esquemaDoPaciente>
