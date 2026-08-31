import { pedir } from '../client'
import type { Lembrete, NovoLembrete, Pagina } from '../tipos'

export function listarLembretes(pacienteId: string, pagina = 0): Promise<Pagina<Lembrete>> {
  return pedir<Pagina<Lembrete>>(`/pacientes/${pacienteId}/lembretes?page=${pagina}`)
}

export function criarLembrete(pacienteId: string, novo: NovoLembrete): Promise<Lembrete> {
  return pedir<Lembrete>(`/pacientes/${pacienteId}/lembretes`, {
    method: 'POST',
    body: JSON.stringify(novo),
  })
}

/** Mão única: a API grava `concluido = true`, não alterna. */
export function concluirLembrete(lembreteId: string): Promise<Lembrete> {
  return pedir<Lembrete>(`/lembretes/${lembreteId}/concluir`, { method: 'PATCH' })
}

export function excluirLembrete(lembreteId: string): Promise<void> {
  return pedir<void>(`/lembretes/${lembreteId}`, { method: 'DELETE' })
}
