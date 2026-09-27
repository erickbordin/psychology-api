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
const GLIFOS: Record<Exclude<Variante, 'neutro'>, ReactNode> = {
  realizada: <path d="M3 7.2 5.6 10 11 3.6" />,
  faltou: <path d="M3.6 3.6 10.4 10.4M10.4 3.6 3.6 10.4" />,
  alerta: <path d="M7 3.2v4.4M7 10.3v.5" />,
}

export function Chip({ children, variante = 'neutro' }: { children: ReactNode; variante?: Variante }) {
  const classes = variante === 'neutro' ? 'chip' : `chip ${variante}`
  return (
    <span data-chip="" className={classes}>
      {variante !== 'neutro' && (
        <svg
          aria-hidden="true"
          focusable="false"
          className="chip-glifo"
          viewBox="0 0 14 14"
          width="12"
          height="12"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          {GLIFOS[variante]}
        </svg>
      )}
      {children}
    </span>
  )
}
