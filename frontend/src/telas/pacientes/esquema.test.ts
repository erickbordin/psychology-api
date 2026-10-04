import { describe, expect, it } from 'vitest'

import { esquemaDoPaciente } from './esquema'

/**
 * Teste de ESQUEMA, nao de tela: as duas regras conferidas aqui (data de
 * nascimento e forma do valor) sao inalcancaveis pelo dialogo, porque
 * `<input type="date">` e `<input type="number">` normalizam a entrada antes
 * de ela chegar ao zod. O usuario alcanca as duas por colagem, autofill,
 * teclado de celular ou navegador sem suporte ao tipo — e a 6.3 poe a regra
 * nos DOIS lados justamente para que o servidor nao seja o unico a saber.
 */
const base = {
  nome: 'Helena Vaz',
  email: '',
  telefone: '',
  dataNascimento: '',
  valorSessao: '',
  autorizaAviso: false,
}

/** A mensagem do campo pedido, ou `null` se o esquema aceitou aquele valor. */
function mensagemDe(campo: string, valores: Record<string, unknown>): string | null {
  const r = esquemaDoPaciente.safeParse({ ...base, ...valores })
  if (r.success) return null
  return r.error.issues.find((i) => i.path[0] === campo)?.message ?? null
}

describe('esquema do paciente · data de nascimento (6.3)', () => {
  it('vazia passa — nascimento e opcional', () => {
    expect(mensagemDe('dataNascimento', { dataNascimento: '' })).toBeNull()
  })

  it('data de nascimento comum passa', () => {
    expect(mensagemDe('dataNascimento', { dataNascimento: '1992-03-14' })).toBeNull()
  })

  it('data no futuro e recusada com a mensagem do @Past', () => {
    expect(mensagemDe('dataNascimento', { dataNascimento: '2099-01-01' })).toBe(
      'A data de nascimento deve estar no passado.',
    )
  })

  it('idade acima de 120 anos e recusada com a mensagem do @IdadeMaxima', () => {
    expect(mensagemDe('dataNascimento', { dataNascimento: '1800-05-09' })).toBe(
      'Confira a data de nascimento.',
    )
  })

  it('data que nao existe no calendario e recusada', () => {
    expect(mensagemDe('dataNascimento', { dataNascimento: '2026-02-31' })).toBe(
      'Confira a data de nascimento.',
    )
  })
})

describe('esquema do paciente · valor da sessao (6.3)', () => {
  it('virgula e recusada pelo FORMATO, nao por "maior que zero"', () => {
    // O defeito M1: o refine de `> 0` rodava antes e `Number('180,50')` e NaN,
    // entao a virgula levava a mensagem de valor onde o problema era de forma.
    expect(mensagemDe('valorSessao', { valorSessao: '180,50' })).toBe(
      'O valor da sessão deve ter no máximo duas casas decimais.',
    )
  })

  it('tres casas decimais continuam recusadas pela mesma mensagem', () => {
    expect(mensagemDe('valorSessao', { valorSessao: '180.005' })).toBe(
      'O valor da sessão deve ter no máximo duas casas decimais.',
    )
  })

  it('zero e recusado por "maior que zero" — a mensagem certa para o caso dele', () => {
    expect(mensagemDe('valorSessao', { valorSessao: '0' })).toBe(
      'O valor da sessão deve ser maior que zero.',
    )
  })

  it('valor com duas casas passa', () => {
    expect(mensagemDe('valorSessao', { valorSessao: '180.50' })).toBeNull()
  })
})

describe('esquema do paciente · caixa de autorizacao do aviso', () => {
  it('marcada sem e-mail e recusada no campo e-mail', () => {
    expect(mensagemDe('email', { autorizaAviso: true, email: '' })).toBe(
      'Informe o e-mail do paciente para avisar por e-mail.',
    )
  })

  it('marcada com e-mail passa', () => {
    expect(mensagemDe('email', { autorizaAviso: true, email: 'helena@exemplo.com' })).toBeNull()
  })

  it('desmarcada sem e-mail passa', () => {
    expect(mensagemDe('email', { autorizaAviso: false, email: '' })).toBeNull()
  })
})
