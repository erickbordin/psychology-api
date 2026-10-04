export function horasDaGrade(inicio: number | null, fim: number | null, horasOcupadas: number[]): number[] {
  const de = inicio !== null && fim !== null ? inicio : 7
  const ate = inicio !== null && fim !== null ? fim : 20
  const primeira = Math.min(de, ...horasOcupadas)
  const depoisDaUltima = Math.max(ate, ...horasOcupadas.map((h) => h + 1))
  return Array.from({ length: depoisDaUltima - primeira }, (_, i) => primeira + i)
}
