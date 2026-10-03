import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'
import { invalidarDinheiro } from '../financeiro/queries'

/**
 * O contrato e gerado do springdoc, que nao marca `required` — todo campo sai
 * opcional. `Required<>` deriva do schema real (renomeou um campo no backend,
 * o build quebra aqui) sem redeclarar a forma a mao.
 */
type SessaoDoDiaDTO = components['schemas']['SessaoDoDiaDTO']

export type SessaoDoDia = {
  consultaId: NonNullable<SessaoDoDiaDTO['consultaId']>
  dtConsulta: NonNullable<SessaoDoDiaDTO['dtConsulta']>
  pacienteId: NonNullable<SessaoDoDiaDTO['pacienteId']>
  pacienteNome: NonNullable<SessaoDoDiaDTO['pacienteNome']>
  status: NonNullable<SessaoDoDiaDTO['status']>
  valor: NonNullable<SessaoDoDiaDTO['valor']> | null
  pagoEm: NonNullable<SessaoDoDiaDTO['pagoEm']> | null
}
export type PendenciaDoPainel = Required<components['schemas']['PendenciaDoPainelDTO']>

/**
 * O container e declarado aqui em vez de derivado, e os MEMBROS e que vem do
 * contrato. Duas razoes:
 *
 * - `Required<>` e raso: ele nao alcanca os campos de dentro de `sessoes[]`
 * - `proxima` e nulavel de verdade — nula no dia 1 do psicologo e quando nao
 *   ha mais sessao hoje. O contrato nao consegue expressar isso, e um
 *   `Required` cru afirmaria que sempre existe uma proxima sessao
 *
 * O que importava no ruling original continua valendo: nome de campo e valor
 * de enum vem do contrato, entao renomear no backend quebra o build aqui.
 * Sao tres chaves; o custo de escreve-las e menor que o de um tipo que mente.
 */
export type PainelHoje = {
  proxima: SessaoDoDia | null
  sessoes: SessaoDoDia[]
  pendencias: PendenciaDoPainel[]
}

export const CHAVE_DO_PAINEL = ['painel', 'hoje'] as const

/** Uma chamada devolve a tela inteira (spec 5.6). Nao quebre em tres. */
export function usePainelDeHoje() {
  return useQuery({
    queryKey: CHAVE_DO_PAINEL,
    queryFn: () => api<PainelHoje>('/painel/hoje'),
  })
}

/**
 * So REALIZADA e FALTOU: a API recusa os outros dois com 409, e a razao e de
 * seguranca — reabrir uma sessao cancelada por aqui reocuparia um horario que
 * ja foi dado a outra pessoa.
 */
export function useMarcarStatus() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'REALIZADA' | 'FALTOU' }) =>
      api(`/consultas/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      }),
    // o painel inteiro se refaz: marcar presenca tira a sessao da faixa de
    // pendencias, e as duas metades da tela nao podem discordar
    onSuccess: () => invalidarDinheiro(cliente),
  })
}
