import type { ReactNode } from 'react'

/** Estado vazio diz o que fazer, nao so que esta vazio. "Nenhuma sessao hoje"
 *  sozinho deixa o psicologo sem saida; com o atalho para a agenda, ele tem
 *  uma. */
export function EstadoVazio({
  titulo,
  apoio,
  acao,
}: {
  titulo: string
  apoio?: string
  acao?: ReactNode
}) {
  return (
    <div className="estado-vazio">
      <p className="titulo">{titulo}</p>
      {apoio && <p className="apoio">{apoio}</p>}
      {acao}
    </div>
  )
}
