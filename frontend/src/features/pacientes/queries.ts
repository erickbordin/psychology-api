import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import * as recurso from '../../api/recursos/paciente'
import type { AtualizacaoPaciente, NovoPaciente } from '../../api/tipos'

export const chavesDePaciente = {
  /** Prefixo sem o filtro: invalidar aqui derruba a lista de qualquer busca. */
  todas: ['pacientes'] as const,
  lista: (nome: string) => ['pacientes', nome] as const,
  um: (pacienteId: string) => ['paciente', pacienteId] as const,
}

export function usePacientes(nome = '') {
  return useQuery({
    queryKey: chavesDePaciente.lista(nome),
    queryFn: () => recurso.listarPacientes(nome || undefined),
  })
}

export function usePaciente(pacienteId: string) {
  return useQuery({
    queryKey: chavesDePaciente.um(pacienteId),
    queryFn: () => recurso.buscarPaciente(pacienteId),
  })
}

/**
 * Invalida tambem a auditoria: toda mutacao grava um log no servidor, e a trilha
 * e justamente a tela em que estar desatualizada e pior.
 */
export function useCriarPaciente() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (novo: NovoPaciente) => recurso.criarPaciente(novo),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: chavesDePaciente.todas })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}

export function useAtualizarPaciente() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ pacienteId, mudanca }: { pacienteId: string; mudanca: AtualizacaoPaciente }) =>
      recurso.atualizarPaciente(pacienteId, mudanca),
    onSuccess: (_paciente, { pacienteId }) => {
      void client.invalidateQueries({ queryKey: chavesDePaciente.todas })
      void client.invalidateQueries({ queryKey: chavesDePaciente.um(pacienteId) })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}

export function useExcluirPaciente() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (pacienteId: string) => recurso.excluirPaciente(pacienteId),
    onSuccess: (_vazio, pacienteId) => {
      void client.invalidateQueries({ queryKey: chavesDePaciente.todas })
      void client.removeQueries({ queryKey: chavesDePaciente.um(pacienteId) })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}
