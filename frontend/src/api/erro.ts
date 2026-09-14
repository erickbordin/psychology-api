export type CampoComErro = { campo: string; erro: string }

/** Espelha o `ErroRespostaDTO { status, mensagem, erros[{campo, erro}] }` que
 *  o backend garante para todo erro. */
export class ErroDaApi extends Error {
  constructor(
    readonly status: number,
    readonly mensagem: string,
    readonly erros: CampoComErro[] = [],
  ) {
    super(mensagem)
    this.name = 'ErroDaApi'
  }
}

/** A mensagem que o SERVIDOR deu para um campo, ou undefined. O texto do
 *  servidor vence o do zod: ele conhece regras que o cliente nao tem como
 *  checar (senha vazada, conflito de horario). */
export function erroDoCampo(erro: unknown, campo: string): string | undefined {
  if (!(erro instanceof ErroDaApi)) return undefined
  return erro.erros.find((e) => e.campo === campo)?.erro
}
