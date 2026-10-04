import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'
import { CHAVE_DO_PAINEL } from '../hoje/queries'
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
  valor: NonNullable<SessaoDTO['valor']> | null
  pagoEm: NonNullable<SessaoDTO['pagoEm']> | null
  lembrete: NonNullable<SessaoDTO['lembrete']> | null
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
    // A ficha, porque a anotacao nasce nela. E o PAINEL, porque registrar a
    // anotacao resolve a pendencia `SEM_ANOTACAO` de `/painel/hoje`: sem esta
    // linha a tela Hoje segue cobrando uma anotacao que ja existe ate o
    // proximo `refetchOnMount`. A convencao da casa (frontend-design.md) e
    // declarar toda chave afetada aqui, e nao depender do conserto que
    // acontece sozinho — o que se conserta sozinho hoje deixa de se consertar
    // no dia em que alguem ajustar `staleTime`.
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: CHAVE_DA_FICHA(pacienteId) })
      await cliente.invalidateQueries({ queryKey: CHAVE_DO_PAINEL })
    },
  })
}
