/**
 * A API nao tem `/auth/me`, e o token que ela emite carrega so o `sub` com o
 * e-mail — nenhum nome. O painel precisa de um jeito de chamar quem entrou, e
 * este modulo e esse jeito: le o `sub` do access token que esta em memoria e
 * deriva um primeiro nome do trecho antes do arroba.
 *
 * E derivacao, nao verdade: "ana.moreira@x.com" vira "Ana Moreira", mas
 * "contato@x.com" vira "Contato". Quando a API passar a devolver o nome do
 * profissional, e este arquivo que sai.
 */
export function emailDoToken(token: string | null): string | null {
  if (!token) return null

  const payload = token.split('.')[1]
  if (!payload) return null

  try {
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    const corpo = JSON.parse(json) as { sub?: unknown }
    return typeof corpo.sub === 'string' ? corpo.sub : null
  } catch {
    // token opaco ou payload que nao e json: sem nome, e a tela cai na saudacao
    // curta em vez de quebrar
    return null
  }
}

export function nomeDeExibicao(email: string | null): string | null {
  if (!email) return null

  const local = email.split('@')[0]
  if (!local) return null

  const partes = local
    .split(/[._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase() + parte.slice(1))

  return partes.length > 0 ? partes.join(' ') : null
}
