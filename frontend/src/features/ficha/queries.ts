import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import * as recursoAnotacao from '../../api/recursos/anotacao'
import * as recursoConsulta from '../../api/recursos/consulta'
import * as recursoLembrete from '../../api/recursos/lembrete'
import type { Lembrete, Pagina } from '../../api/tipos'

export const chavesDeAnotacao = {
  lista: (pacienteId: string, pagina: number) => ['anotacoes', pacienteId, pagina] as const,
  todas: (pacienteId: string) => ['anotacoes', pacienteId] as const,
}

export const chavesDeLembrete = {
  lista: (pacienteId: string, pagina: number) => ['lembretes', pacienteId, pagina] as const,
  todas: (pacienteId: string) => ['lembretes', pacienteId] as const,
}

export const chavesDeConsultaDoPaciente = {
  lista: (pacienteId: string, pagina: number) =>
    ['consultasDoPaciente', pacienteId, pagina] as const,
  todas: (pacienteId: string) => ['consultasDoPaciente', pacienteId] as const,
}

export function useAnotacoes(pacienteId: string, pagina: number) {
  return useQuery({
    queryKey: chavesDeAnotacao.lista(pacienteId, pagina),
    queryFn: () => recursoAnotacao.listarAnotacoes(pacienteId, pagina),
  })
}

export function useCriarAnotacao(pacienteId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (anotacao: string) => recursoAnotacao.criarAnotacao(pacienteId, anotacao),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: chavesDeAnotacao.todas(pacienteId) })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}

export function useExcluirAnotacao(pacienteId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (anotacaoId: string) => recursoAnotacao.excluirAnotacao(anotacaoId),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: chavesDeAnotacao.todas(pacienteId) })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}

export function useLembretes(pacienteId: string, pagina: number) {
  return useQuery({
    queryKey: chavesDeLembrete.lista(pacienteId, pagina),
    queryFn: () => recursoLembrete.listarLembretes(pacienteId, pagina),
  })
}

export function useCriarLembrete(pacienteId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (descricao: string) => recursoLembrete.criarLembrete(pacienteId, { descricao }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: chavesDeLembrete.todas(pacienteId) })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}

/**
 * A unica mutacao otimista do projeto. Concluir e um toque barato de mao unica, e
 * a resposta imediata e o que faz a lista parecer viva. Criar anotacao e agendar
 * consulta ficam de fora de proposito: as duas falham por regra de negocio real
 * (409 de conflito), e fingir sucesso ali seria mentir sobre prontuario.
 */
export function useConcluirLembrete(pacienteId: string, pagina: number) {
  const client = useQueryClient()
  const chave = chavesDeLembrete.lista(pacienteId, pagina)

  return useMutation({
    mutationFn: (lembreteId: string) => recursoLembrete.concluirLembrete(lembreteId),
    onMutate: async (lembreteId: string) => {
      await client.cancelQueries({ queryKey: chave })
      const anterior = client.getQueryData<Pagina<Lembrete>>(chave)
      if (anterior) {
        client.setQueryData<Pagina<Lembrete>>(chave, {
          ...anterior,
          content: anterior.content.map((item) =>
            item.id === lembreteId ? { ...item, concluido: true } : item,
          ),
        })
      }
      return { anterior }
    },
    onError: (_erro, _lembreteId, contexto) => {
      if (contexto?.anterior) client.setQueryData(chave, contexto.anterior)
    },
    onSettled: () => {
      void client.invalidateQueries({ queryKey: chavesDeLembrete.todas(pacienteId) })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}

export function useExcluirLembrete(pacienteId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (lembreteId: string) => recursoLembrete.excluirLembrete(lembreteId),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: chavesDeLembrete.todas(pacienteId) })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}

export function useConsultasDoPaciente(pacienteId: string, pagina: number) {
  return useQuery({
    queryKey: chavesDeConsultaDoPaciente.lista(pacienteId, pagina),
    queryFn: () => recursoConsulta.listarConsultasDoPaciente(pacienteId, pagina),
  })
}
