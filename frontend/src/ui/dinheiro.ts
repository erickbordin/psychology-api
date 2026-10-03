export function emReais(valor: number): string {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function mesPorExtenso(ano: number, mes: number): string {
  return new Date(ano, mes - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
}

export function mesVizinho(ano: number, mes: number, passo: -1 | 1): { ano: number; mes: number } {
  const data = new Date(ano, mes - 1 + passo, 1)
  return { ano: data.getFullYear(), mes: data.getMonth() + 1 }
}
