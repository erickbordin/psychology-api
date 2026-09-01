import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  variante?: 'primario' | 'secundario' | 'texto' | 'discreto'
  /** Icone a esquerda do rotulo — o "+" de "Nova consulta", por exemplo. */
  icone?: ReactNode
  /** Ocupa a largura do pai: o botao de entrar do login. */
  largo?: boolean
}

const FOCO =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento'

const BASE = `inline-flex h-10 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold transition-colors disabled:pointer-events-none disabled:opacity-50 ${FOCO}`

export function Botao({
  children,
  variante = 'primario',
  icone,
  largo = false,
  ...resto
}: Props) {
  const estilo =
    variante === 'primario'
      ? `${BASE} bg-acento text-white shadow-[0_1px_2px_rgba(37,99,235,0.35)] hover:bg-acento-escuro`
      : variante === 'secundario'
        ? `${BASE} border border-linha bg-superficie text-tinta hover:bg-superficie-2`
        : variante === 'discreto'
          ? `inline-flex items-center gap-1.5 text-sm font-medium text-tinta-3 underline-offset-4 hover:text-perigo hover:underline disabled:pointer-events-none disabled:opacity-50 ${FOCO}`
          : `inline-flex items-center gap-1.5 rounded-sm text-sm font-medium text-acento underline-offset-4 hover:underline disabled:pointer-events-none disabled:opacity-50 ${FOCO}`

  return (
    <button
      type="button"
      className={`${estilo}${largo ? ' w-full' : ''}`}
      {...resto}
    >
      {icone}
      {children}
    </button>
  )
}

/**
 * Botao de icone puro — editar numa linha da agenda, mostrar a senha. O nome
 * acessivel vem do `rotulo`, obrigatorio: icone sem nome e um botao mudo para
 * quem usa leitor de tela.
 */
export function BotaoDeIcone({
  rotulo,
  icone,
  ...resto
}: ButtonHTMLAttributes<HTMLButtonElement> & { rotulo: string; icone: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={rotulo}
      title={rotulo}
      className={`inline-flex size-9 shrink-0 items-center justify-center rounded-lg text-tinta-3 transition-colors hover:bg-superficie-2 hover:text-tinta disabled:pointer-events-none disabled:opacity-50 ${FOCO}`}
      {...resto}
    >
      {icone}
    </button>
  )
}
