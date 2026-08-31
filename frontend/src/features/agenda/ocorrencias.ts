import { somarDias } from '../../ui/data'

/** Os mesmos limites do `OcorrenciasSemanais` da API. */
export const MIN_OCORRENCIAS = 2
export const MAX_OCORRENCIAS = 52

/**
 * Preview do que a serie vai gerar, para o psicologo ver as datas ANTES de criar
 * doze sessoes. Quem gera as ocorrencias de verdade e a API — isto e a unica
 * aritmetica de data do cliente.
 *
 * O teto existe para o preview por data limite nao virar laco infinito nem uma
 * lista de mil linhas quando alguem digita 2099.
 */
export function ocorrenciasSemanais(
  primeira: string,
  quantidade?: number,
  limite?: string,
): string[] {
  if (!primeira) return []

  if (quantidade && quantidade > 0) {
    const teto = Math.min(quantidade, MAX_OCORRENCIAS)
    return Array.from({ length: teto }, (_vazio, indice) => somarDias(primeira, indice * 7))
  }

  if (limite) {
    const datas: string[] = []
    let atual = primeira
    while (atual <= limite && datas.length <= MAX_OCORRENCIAS) {
      datas.push(atual)
      atual = somarDias(atual, 7)
    }
    return datas
  }

  return []
}
