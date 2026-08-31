import { Botao } from './Botao'

type Props = {
  pagina: number
  totalPaginas: number
  /** Ja pluralizado pelo chamador: "42 anotações", "1 lembrete". */
  contagem: string
  aoMudar: (pagina: number) => void
}

/**
 * As pontas ficam desabilitadas: sem isso a tela pede a pagina -1 ou uma pagina
 * alem da ultima, e a API responde uma lista vazia que parece perda de dado.
 */
export function Paginacao({ pagina, totalPaginas, contagem, aoMudar }: Props) {
  if (totalPaginas <= 1) return null

  return (
    <div className="flex flex-wrap items-center gap-6">
      <Botao variante="texto" disabled={pagina === 0} onClick={() => aoMudar(pagina - 1)}>
        Anterior
      </Botao>
      <Botao
        variante="texto"
        disabled={pagina >= totalPaginas - 1}
        onClick={() => aoMudar(pagina + 1)}
      >
        Próxima
      </Botao>
      <span className="text-sm tabular-nums text-tinta-2">
        {`página ${pagina + 1} de ${totalPaginas} · ${contagem}`}
      </span>
    </div>
  )
}
