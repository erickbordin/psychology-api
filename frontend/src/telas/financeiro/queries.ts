import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'

type SessaoDTO = components['schemas']['SessaoFinanceiraDTO']
type DevedorDTO = components['schemas']['DevedorDTO']
type FinanceiroDTO = components['schemas']['FinanceiroMesDTO']
type PainelMesDTO = components['schemas']['PainelMesDTO']
type ConsultaDTO = components['schemas']['ConsultaResponseDTO']

export type SessaoFinanceira = {
  consultaId: NonNullable<SessaoDTO['consultaId']>
  dtConsulta: NonNullable<SessaoDTO['dtConsulta']>
  pacienteId: NonNullable<SessaoDTO['pacienteId']>
  pacienteNome: NonNullable<SessaoDTO['pacienteNome']>
  pacienteExcluido: NonNullable<SessaoDTO['pacienteExcluido']>
  valor: NonNullable<SessaoDTO['valor']> | null
  pagoEm: NonNullable<SessaoDTO['pagoEm']> | null
}

export type Devedor = {
  pacienteId: NonNullable<DevedorDTO['pacienteId']>
  pacienteNome: NonNullable<DevedorDTO['pacienteNome']>
  pacienteExcluido: NonNullable<DevedorDTO['pacienteExcluido']>
  quantidadeSessoes: NonNullable<DevedorDTO['quantidadeSessoes']>
  total: NonNullable<DevedorDTO['total']>
  consultaIds: NonNullable<DevedorDTO['consultaIds']>
}

export type FinanceiroDoMes = {
  ano: NonNullable<FinanceiroDTO['ano']>
  mes: NonNullable<FinanceiroDTO['mes']>
  faturado: NonNullable<FinanceiroDTO['faturado']>
  recebido: NonNullable<FinanceiroDTO['recebido']>
  aReceber: NonNullable<FinanceiroDTO['aReceber']>
  sessoesSemValor: NonNullable<FinanceiroDTO['sessoesSemValor']>
  sessoes: SessaoFinanceira[]
  devendo: Devedor[]
}

export type PainelDoMes = {
  ano: NonNullable<PainelMesDTO['ano']>
  mes: NonNullable<PainelMesDTO['mes']>
  realizadas: NonNullable<PainelMesDTO['realizadas']>
  faltas: NonNullable<PainelMesDTO['faltas']>
  canceladas: NonNullable<PainelMesDTO['canceladas']>
  taxaDeFalta: NonNullable<PainelMesDTO['taxaDeFalta']> | null
  pacientesAtivos: NonNullable<PainelMesDTO['pacientesAtivos']>
}

export type ConsultaComDinheiro = Pick<Required<ConsultaDTO>, 'id' | 'status'>

const parametros = (ano: number, mes: number) => new URLSearchParams({ ano: String(ano), mes: String(mes) })

export function useFinanceiroDoMes(ano: number, mes: number) {
  return useQuery({
    queryKey: ['financeiro', 'mes', ano, mes],
    queryFn: () => api<FinanceiroDoMes>(`/financeiro/mes?${parametros(ano, mes)}`),
  })
}

export function usePainelDoMes(ano: number, mes: number) {
  return useQuery({
    queryKey: ['painel', 'mes', ano, mes],
    queryFn: () => api<PainelDoMes>(`/painel/mes?${parametros(ano, mes)}`),
  })
}

export async function invalidarDinheiro(cliente: QueryClient) {
  await Promise.all([
    cliente.invalidateQueries({ queryKey: ['financeiro'] }),
    cliente.invalidateQueries({ queryKey: ['painel'] }),
    cliente.invalidateQueries({ queryKey: ['ficha'] }),
  ])
}

export function useDarBaixa() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (consultaId: string) =>
      api<ConsultaComDinheiro>(`/consultas/${consultaId}/baixa`, { method: 'PATCH' }),
    onSuccess: () => invalidarDinheiro(cliente),
  })
}

export function useDesfazerBaixa() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (consultaId: string) =>
      api<ConsultaComDinheiro>(`/consultas/${consultaId}/baixa`, { method: 'DELETE' }),
    onSuccess: () => invalidarDinheiro(cliente),
  })
}

export function useDarBaixaEmLote() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (consultaIds: string[]) =>
      api<{ consultasBaixadas: number }>('/consultas/baixas', {
        method: 'POST',
        body: JSON.stringify({ consultaIds }),
      }),
    onSuccess: () => invalidarDinheiro(cliente),
  })
}

export function useAlterarValor() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ consultaId, valor }: { consultaId: string; valor: number }) =>
      api<ConsultaComDinheiro>(`/consultas/${consultaId}/valor`, {
        method: 'PATCH',
        body: JSON.stringify({ valor }),
      }),
    onSuccess: () => invalidarDinheiro(cliente),
  })
}
