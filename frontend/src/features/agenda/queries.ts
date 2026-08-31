import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import * as recurso from '../../api/recursos/consulta'
import type { AtualizacaoConsulta, NovaConsulta, NovaSerie } from '../../api/tipos'

export const chavesDeConsulta = {
  todas: ['consultas'] as const,
  agenda: (de: string, ate: string) => ['consultas', de, ate] as const,
}

export function useAgenda(de: string, ate: string) {
  return useQuery({
    queryKey: chavesDeConsulta.agenda(de, ate),
    queryFn: () => recurso.listarAgenda(de, ate),
  })
}

/**
 * Toda escrita de consulta derruba as tres: a agenda, o historico da ficha do
 * paciente e a trilha de auditoria. Esquecer `consultasDoPaciente` faria a ficha
 * mostrar uma consulta que a agenda ja sabe cancelada.
 */
function invalidarTudo(client: ReturnType<typeof useQueryClient>) {
  void client.invalidateQueries({ queryKey: chavesDeConsulta.todas })
  void client.invalidateQueries({ queryKey: ['consultasDoPaciente'] })
  void client.invalidateQueries({ queryKey: ['auditoria'] })
}

export function useCriarConsulta() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (nova: NovaConsulta) => recurso.criarConsulta(nova),
    onSuccess: () => invalidarTudo(client),
  })
}

export function useAtualizarConsulta() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, mudanca }: { id: string; mudanca: AtualizacaoConsulta }) =>
      recurso.atualizarConsulta(id, mudanca),
    onSuccess: () => invalidarTudo(client),
  })
}

export function useExcluirConsulta() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => recurso.excluirConsulta(id),
    onSuccess: () => invalidarTudo(client),
  })
}

export function useCriarSerie() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (nova: NovaSerie) => recurso.criarSerie(nova),
    onSuccess: () => invalidarTudo(client),
  })
}

export function useCancelarSerie() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (serieId: string) => recurso.cancelarSerie(serieId),
    onSuccess: () => invalidarTudo(client),
  })
}
