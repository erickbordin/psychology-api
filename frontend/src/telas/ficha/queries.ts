import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'
import type { Paciente } from '../pacientes/queries'

type SessaoDTO = components['schemas']['SessaoDaFichaDTO']
type AnotacaoDTO = components['schemas']['AnotacaoDaSessaoDTO']

export type AnotacaoDaSessao = {
  anotacaoId: NonNullable<AnotacaoDTO['anotacaoId']>
  conteudo: NonNullable<AnotacaoDTO['conteudo']>
  createdAt: NonNullable<AnotacaoDTO['createdAt']>
}

/** `anotacoes` e LISTA porque nada no modelo impede duas anotacoes na mesma
 *  consulta, e esconder a segunda seria esconder prontuario (spec 7.3). */
export type SessaoDaFicha = {
  consultaId: NonNullable<SessaoDTO['consultaId']>
  dtConsulta: NonNullable<SessaoDTO['dtConsulta']>
  status: NonNullable<SessaoDTO['status']>
  anotacoes: AnotacaoDaSessao[]
}

export type Ficha = {
  paciente: Paciente
  linhaDoTempo: SessaoDaFicha[]
  pendencias: { id: string; descricao: string; concluido: boolean; createdAt: string }[]
}

export const CHAVE_DA_FICHA = (pacienteId: string) => ['ficha', pacienteId] as const

/** Uma chamada devolve a tela inteira (spec 5.6 e 4(3)). Nao quebre em tres —
 *  correlacionar sessao com anotacao no cliente e exatamente o defeito que a
 *  ficha de pagina unica existe para matar. */
export function useFicha(pacienteId: string) {
  return useQuery({
    queryKey: CHAVE_DA_FICHA(pacienteId),
    queryFn: () => api<Ficha>(`/pacientes/${pacienteId}/ficha`),
  })
}

/**
 * `consultaId` NAO e opcional aqui, embora seja no DTO do backend: uma
 * anotacao escrita a partir de uma linha da ficha e sempre daquela sessao.
 * Anotacao avulsa existe no modelo (spec 5.1) e nao tem tela nesta fatia.
 */
export function useRegistrarAnotacao(pacienteId: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (dados: { anotacao: string; consultaId: string }) =>
      api(`/pacientes/${pacienteId}/anotacoes`, {
        method: 'POST',
        body: JSON.stringify(dados),
      }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: CHAVE_DA_FICHA(pacienteId) }),
  })
}
