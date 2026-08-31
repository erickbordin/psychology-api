import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  variante?: 'primario' | 'texto' | 'discreto'
}

const FOCO =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento'

export function Botao({ children, variante = 'primario', ...resto }: Props) {
  const estilo =
    variante === 'primario'
      ? `inline-flex h-11 items-center justify-center bg-tinta px-6 text-sm font-medium text-superficie transition-opacity hover:opacity-85 disabled:pointer-events-none disabled:opacity-40 ${FOCO}`
      : variante === 'discreto'
        ? `inline-flex items-center text-sm text-tinta-3 underline-offset-4 hover:text-perigo hover:underline disabled:pointer-events-none disabled:opacity-40 ${FOCO}`
        : `inline-flex items-center text-sm font-medium text-acento underline-offset-4 hover:underline disabled:pointer-events-none disabled:opacity-40 ${FOCO}`

  return (
    <button type="button" className={estilo} {...resto}>
      {children}
    </button>
  )
}
