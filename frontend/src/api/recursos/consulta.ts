import { pedir } from '../client'
import type {
  AtualizacaoConsulta,
  CancelamentoDeSerie,
  Consulta,
  NovaConsulta,
  NovaSerie,
  Pagina,
  Serie,
} from '../tipos'

/** `de` e `ate` são `LocalDate` (`2026-09-02`), não `LocalDateTime`. */
export function listarAgenda(de?: string, ate?: string): Promise<Consulta[]> {
  const parametros = new URLSearchParams()
  if (de) parametros.set('de', de)
  if (ate) parametros.set('ate', ate)
  const busca = parametros.toString()
  return pedir<Consulta[]>(`/consultas${busca ? `?${busca}` : ''}`)
}

export function criarConsulta(nova: NovaConsulta): Promise<Consulta> {
  return pedir<Consulta>('/consultas', { method: 'POST', body: JSON.stringify(nova) })
}

export function atualizarConsulta(id: string, mudanca: AtualizacaoConsulta): Promise<Consulta> {
  return pedir<Consulta>(`/consultas/${id}`, { method: 'PUT', body: JSON.stringify(mudanca) })
}

export function excluirConsulta(id: string): Promise<Consulta> {
  return pedir<Consulta>(`/consultas/${id}`, { method: 'DELETE' })
}

export function criarSerie(nova: NovaSerie): Promise<Serie> {
  return pedir<Serie>('/consultas/recorrentes', { method: 'POST', body: JSON.stringify(nova) })
}

export function cancelarSerie(serieId: string): Promise<CancelamentoDeSerie> {
  return pedir<CancelamentoDeSerie>(`/consultas/series/${serieId}`, { method: 'DELETE' })
}

export function listarConsultasDoPaciente(
  pacienteId: string,
  pagina = 0,
): Promise<Pagina<Consulta>> {
  return pedir<Pagina<Consulta>>(`/pacientes/${pacienteId}/consultas?page=${pagina}`)
}
