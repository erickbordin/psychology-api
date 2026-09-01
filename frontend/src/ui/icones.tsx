import type { SVGProps } from 'react'

/**
 * Icone e SVG inline, nao pacote: sao dezesseis desenhos de uma linha so, todos
 * no mesmo traco (1.75, ponta redonda, `currentColor`), e o front nao carrega
 * uma biblioteca inteira para isso. `currentColor` e o que faz o icone herdar a
 * cor do estado do pai — azul no item ativo da barra, cinza no inativo.
 */
type Props = SVGProps<SVGSVGElement> & { tamanho?: number }

function Svg({ tamanho = 20, children, ...resto }: Props) {
  return (
    <svg
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...resto}
    >
      {children}
    </svg>
  )
}

export function IconeCasa(props: Props) {
  return (
    <Svg {...props}>
      <path d="M3 10.5 12 3l9 7.5" />
      <path d="M5 9.5V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5" />
    </Svg>
  )
}

export function IconeCalendario(props: Props) {
  return (
    <Svg {...props}>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </Svg>
  )
}

export function IconePessoas(props: Props) {
  return (
    <Svg {...props}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3 20c0-3 2.7-5 6-5s6 2 6 5" />
      <path d="M16.5 5.6a3.2 3.2 0 0 1 0 6.3M18 14.6c2 .7 3.3 2.3 3.3 4.4" />
    </Svg>
  )
}

export function IconePrancheta(props: Props) {
  return (
    <Svg {...props}>
      <rect x="4" y="4" width="16" height="17" rx="2" />
      <path d="M9 3.5h6v2.5H9zM8.5 11h7M8.5 15h4.5" />
    </Svg>
  )
}

export function IconeSino(props: Props) {
  return (
    <Svg {...props}>
      <path d="M18 9a6 6 0 1 0-12 0c0 4-1.5 5.5-1.5 5.5h15S18 13 18 9Z" />
      <path d="M10.3 18a2 2 0 0 0 3.4 0" />
    </Svg>
  )
}

export function IconeSair(props: Props) {
  return (
    <Svg {...props}>
      <path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3" />
      <path d="M10 8l-4 4 4 4M6 12h9" />
    </Svg>
  )
}

export function IconeLupa(props: Props) {
  return (
    <Svg {...props}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </Svg>
  )
}

export function IconeMais(props: Props) {
  return (
    <Svg {...props}>
      <path d="M12 5v14M5 12h14" />
    </Svg>
  )
}

export function IconeSeta(props: Props) {
  return (
    <Svg {...props}>
      <path d="m9 6 6 6-6 6" />
    </Svg>
  )
}

export function IconeVoltar(props: Props) {
  return (
    <Svg {...props}>
      <path d="M19 12H5M11 6l-6 6 6 6" />
    </Svg>
  )
}

export function IconeTelefone(props: Props) {
  return (
    <Svg {...props}>
      <path d="M6.5 3.5h3l1.5 4-2 1.5a10 10 0 0 0 6 6l1.5-2 4 1.5v3a2 2 0 0 1-2.2 2A16.5 16.5 0 0 1 4.5 5.7 2 2 0 0 1 6.5 3.5Z" />
    </Svg>
  )
}

export function IconeEnvelope(props: Props) {
  return (
    <Svg {...props}>
      <rect x="3" y="5.5" width="18" height="13" rx="2" />
      <path d="m3.8 7 8.2 6 8.2-6" />
    </Svg>
  )
}

export function IconeAnotacao(props: Props) {
  return (
    <Svg {...props}>
      <path d="M5 4h9l5 5v11a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z" />
      <path d="M14 4v5h5M8.5 13h7M8.5 16.5h4.5" />
    </Svg>
  )
}

export function IconeRelogio(props: Props) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </Svg>
  )
}

export function IconeLapis(props: Props) {
  return (
    <Svg {...props}>
      <path d="M4 20h4l11-11a2.5 2.5 0 0 0-3.5-3.5L4.5 16.5 4 20Z" />
      <path d="m14.5 6.5 3 3" />
    </Svg>
  )
}

export function IconeOlho(props: Props) {
  return (
    <Svg {...props}>
      <path d="M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
      <circle cx="12" cy="12" r="2.75" />
    </Svg>
  )
}

export function IconeOlhoFechado(props: Props) {
  return (
    <Svg {...props}>
      <path d="M4 5l16 14" />
      <path d="M9.5 7A9.9 9.9 0 0 1 12 6.7c6 0 9.5 6 9.5 6a17 17 0 0 1-2.6 3.2" />
      <path d="M6.2 8.6A16.6 16.6 0 0 0 2.5 12.7s3.5 6 9.5 6a9.6 9.6 0 0 0 3.4-.6" />
      <path d="M10.2 11a2.8 2.8 0 0 0 3.7 3.9" />
    </Svg>
  )
}

/** A marca. Um cerebro de duas metades, como no desenho da tela de entrada. */
export function IconeCerebro({ tamanho = 34, ...resto }: Props) {
  return (
    <Svg tamanho={tamanho} strokeWidth={1.6} {...resto}>
      <path d="M12 5.5v13" />
      <path d="M12 6.2A3.2 3.2 0 0 0 6.4 8a2.7 2.7 0 0 0-1.6 4.4A3 3 0 0 0 6 17.3a3.1 3.1 0 0 0 6 .9" />
      <path d="M12 6.2A3.2 3.2 0 0 1 17.6 8a2.7 2.7 0 0 1 1.6 4.4A3 3 0 0 1 18 17.3a3.1 3.1 0 0 1-6 .9" />
      <path d="M9 10h1.5M13.5 13H15" />
    </Svg>
  )
}
