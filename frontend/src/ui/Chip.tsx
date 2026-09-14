import type { ReactNode } from 'react'

type Variante = 'realizada' | 'faltou' | 'neutro'

/** Status da sessao. NAO e pilula: pilula e acao (ver tokens.css). Chip e
 *  rotulo de estado, e a forma tem que separar os dois. `variante` escolhe a
 *  cor semantica (`.chip.realizada`/`.chip.faltou` de estilo/estilo.css);
 *  sem ela o chip fica no tom neutro (ex.: AGENDADA). */
export function Chip({ children, variante = 'neutro' }: { children: ReactNode; variante?: Variante }) {
  const classes = variante === 'neutro' ? 'chip' : `chip ${variante}`
  return (
    <span data-chip="" className={classes}>
      {children}
    </span>
  )
}
