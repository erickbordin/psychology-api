import type { ReactNode } from 'react'

/** Estado vazio diz o que fazer, nao so que esta vazio. "Nenhuma sessao hoje"
 *  sozinho deixa o psicologo sem saida; com o atalho para a agenda, ele tem
 *  uma. */
export function EstadoVazio({ titulo, acao }: { titulo: string; acao?: ReactNode }) {
  return (
    <div className="estado-vazio">
      <p className="titulo">{titulo}</p>
      {acao}
    </div>
  )
}
