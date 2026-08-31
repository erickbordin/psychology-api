import { pedir } from '../client'
import type { Anotacao, Pagina } from '../tipos'

export function listarAnotacoes(pacienteId: string, pagina = 0): Promise<Pagina<Anotacao>> {
  return pedir<Pagina<Anotacao>>(`/pacientes/${pacienteId}/anotacoes?page=${pagina}`)
}

/**
 * O corpo leva `anotacao`, nao `conteudo`: o DTO de cadastro da API usa esse
 * nome, e a resposta usa `conteudo`. Foi exatamente essa divergencia de nome que
 * quebrou a criacao de anotacao no backend uma vez — nomear errado aqui produz
 * 400 de campo obrigatorio, sem pista melhor.
 */
export function criarAnotacao(pacienteId: string, anotacao: string): Promise<Anotacao> {
  return pedir<Anotacao>(`/pacientes/${pacienteId}/anotacoes`, {
    method: 'POST',
    body: JSON.stringify({ anotacao }),
  })
}

/** A exclusao e soft delete no servidor: some da lista, permanece na auditoria. */
export function excluirAnotacao(anotacaoId: string): Promise<void> {
  return pedir<void>(`/anotacoes/${anotacaoId}`, { method: 'DELETE' })
}
