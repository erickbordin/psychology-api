import { pedir } from '../client'
import type { AtualizacaoPaciente, NovoPaciente, Paciente } from '../tipos'

export function listarPacientes(nome?: string): Promise<Paciente[]> {
  const filtro = nome ? `?nome=${encodeURIComponent(nome)}` : ''
  return pedir<Paciente[]>(`/pacientes${filtro}`)
}

export function buscarPaciente(pacienteId: string): Promise<Paciente> {
  return pedir<Paciente>(`/pacientes/${pacienteId}`)
}

export function criarPaciente(novo: NovoPaciente): Promise<Paciente> {
  return pedir<Paciente>('/pacientes', { method: 'POST', body: JSON.stringify(novo) })
}

export function atualizarPaciente(
  pacienteId: string,
  mudanca: AtualizacaoPaciente,
): Promise<Paciente> {
  return pedir<Paciente>(`/pacientes/${pacienteId}`, {
    method: 'PUT',
    body: JSON.stringify(mudanca),
  })
}

export function excluirPaciente(pacienteId: string): Promise<void> {
  return pedir<void>(`/pacientes/${pacienteId}`, { method: 'DELETE' })
}
