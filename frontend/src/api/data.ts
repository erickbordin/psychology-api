/**
 * A API aceita EXATAMENTE `2026-12-02T14:00:00`: hora local, sem offset, sem
 * milissegundos (spec 5.9 — fuso unico America/Sao_Paulo). `toISOString()`
 * produz `...T17:00:00.000Z` e a API responde 400 — e quando NAO responde, e
 * pior: grava a sessao em outra hora.
 *
 * Por isso a formatacao e manual. Nao troque por `toISOString().slice(0, 19)`:
 * aquilo converte para UTC antes de cortar, e o corte esconde a conversao.
 */
const doisDigitos = (n: number) => String(n).padStart(2, '0')

export function paraApi(data: Date): string {
  const ano = data.getFullYear()
  const mes = doisDigitos(data.getMonth() + 1)
  const dia = doisDigitos(data.getDate())
  const hora = doisDigitos(data.getHours())
  const minuto = doisDigitos(data.getMinutes())
  const segundo = doisDigitos(data.getSeconds())
  return `${ano}-${mes}-${dia}T${hora}:${minuto}:${segundo}`
}

/** A API devolve no mesmo formato. `new Date(texto)` sem `Z` ja interpreta
 *  como hora local, que e o que se quer. */
export function deApi(texto: string): Date {
  return new Date(texto)
}
