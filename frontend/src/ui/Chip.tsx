import type { ReactNode } from 'react'

type Variante = 'realizada' | 'faltou' | 'alerta' | 'neutro'

/** Status da sessao. NAO e pilula: pilula e acao (ver tokens.css). Chip e
 *  rotulo de estado, e a forma tem que separar os dois. `variante` escolhe a
 *  cor semantica (`.chip.realizada`/`.chip.faltou`/`.chip.alerta` de
 *  estilo/estilo.css); sem ela o chip fica no tom neutro (ex.: AGENDADA).
 *
 *  `alerta` e o estado que PEDE atencao sem ser erro — hoje so o aviso nao
 *  autorizado da ficha (prototipo/index.html:94). Sem ele, o estado que pede
 *  atencao sai da mesma cor do estado neutro, e a diferenca some. */
export function Chip({ children, variante = 'neutro' }: { children: ReactNode; variante?: Variante }) {
  const classes = variante === 'neutro' ? 'chip' : `chip ${variante}`
  return (
    <span data-chip="" className={classes}>
      {children}
    </span>
  )
}
