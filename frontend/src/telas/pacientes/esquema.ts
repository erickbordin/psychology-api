import { z } from 'zod'

/**
 * As tres perguntas de `dataNascimento`, separadas porque o servidor as separa.
 *
 * A data chega como `yyyy-MM-dd` (o `<input type="date">` nao produz outra
 * coisa) e e comparada POR PARTES, nunca via `new Date(iso)`: o construtor le
 * "1992-03-14" como meia-noite UTC e, a oeste de Greenwich, isso vira o dia 13
 * local — o mesmo cuidado que `telas/ficha/FichaPage.tsx` documenta em `idade`.
 */
function partes(iso: string): [number, number, number] | null {
  const casou = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!casou) return null
  return [Number(casou[1]), Number(casou[2]), Number(casou[3])]
}

/** "Data real" da 6.3: 2026-02-31 tem a forma certa e nao existe no calendario. */
function ehDataReal(iso: string): boolean {
  const p = partes(iso)
  if (!p) return false
  const [ano, mes, dia] = p
  const d = new Date(ano, mes - 1, dia)
  return d.getFullYear() === ano && d.getMonth() === mes - 1 && d.getDate() === dia
}

/** `@Past`: hoje NAO e passado — nascer hoje nao e caso de uso do cadastro. */
function ehPassado(iso: string): boolean {
  const [ano, mes, dia] = partes(iso)!
  const hoje = new Date()
  const nascimento = new Date(ano, mes - 1, dia)
  hoje.setHours(0, 0, 0, 0)
  return nascimento.getTime() < hoje.getTime()
}

/** Mesma conta do `IdadeMaximaValidator` (`Period.between(...).getYears()`). */
function idadeEmAnos(iso: string): number {
  const [ano, mes, dia] = partes(iso)!
  const hoje = new Date()
  let anos = hoje.getFullYear() - ano
  const aindaNaoFezAniversario =
    hoje.getMonth() + 1 < mes || (hoje.getMonth() + 1 === mes && hoje.getDate() < dia)
  if (aindaNaoFezAniversario) anos -= 1
  return anos
}

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

    // "data real, no passado, idade <= 120" (spec 6.3) — a linha que faltava
    // no cliente enquanto o DTO ja a cumpria com `@Past` + `@IdadeMaxima`.
    // As DUAS mensagens sao as do servidor, verbatim, e sao DUAS de proposito:
    // ele distingue "data no futuro" de "data velha demais", e uma redacao so
    // faria o usuario achar que sao o mesmo problema.
    //
    // Vazio continua passando: nascimento e OPCIONAL (decisao do dono, spec
    // 5.8) — o mesmo `v === ''` dos outros opcionais deste esquema.
    //
    // A ordem espelha a do servidor: `IdadeMaximaValidator` devolve `true`
    // para data futura de proposito, deixando o futuro inteiro para o `@Past`.
    dataNascimento: z
      .string()
      .refine((v) => v === '' || ehDataReal(v), 'Confira a data de nascimento.')
      .refine(
        (v) => v === '' || !ehDataReal(v) || ehPassado(v),
        'A data de nascimento deve estar no passado.',
      )
      .refine(
        (v) => v === '' || !ehDataReal(v) || !ehPassado(v) || idadeEmAnos(v) <= 120,
        'Confira a data de nascimento.',
      ),

    // "> 0, 2 casas" (spec 6.3) — as DUAS metades, nao so a primeira. O
    // servidor recusa a terceira casa com @Digits(fraction = 2); sem o mesmo
    // limite aqui, digitar 180.005 passa no cliente e volta 400.
    //
    // O FORMATO vem primeiro, e a virgula saiu do regex (M1 da revisao): o
    // `[.,]` prometia aceitar "180,50", mas o `Number(v) > 0` que rodava antes
    // ja recusava — `Number('180,50')` e `NaN` — e o usuario levava "deve ser
    // maior que zero" para um problema que nao era de valor, e sim de forma.
    // Com o formato na frente, cada recusa carrega a mensagem do seu proprio
    // motivo. A virgula simplesmente nao e aceita, e isso e verdade tambem no
    // servidor (`BigDecimal` do JSON nao le virgula).
    valorSessao: z
      .string()
      .refine(
        (v) => v === '' || /^\d+(\.\d{1,2})?$/.test(v.trim()),
        'O valor da sessão deve ter no máximo duas casas decimais.',
      )
      .refine(
        (v) => v === '' || Number(v) > 0,
        'O valor da sessão deve ser maior que zero.',
      ),

    autorizaAviso: z.boolean(),
  })
  // A regra condicional de 5.4 le DOIS campos, entao vive no objeto e nao num
  // campo. `path` aponta o erro para `email`: o envelope da casa entrega campo
  // culpado e o formulario poe o foco nele — erro sem campo nao tem onde pousar.
  .refine((v) => !v.autorizaAviso || v.email !== '', {
    message: 'Informe o e-mail do paciente para avisar por e-mail.',
    path: ['email'],
  })

export type DadosDoPaciente = z.infer<typeof esquemaDoPaciente>
