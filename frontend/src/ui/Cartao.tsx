import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/**
 * O cartao e a unica moldura do produto: papel branco, filete de 1px, canto de
 * 14px. Tudo o que e um bloco de conteudo usa este componente, e por isso as
 * seis telas parecem a mesma tela.
 */
export function Cartao({
  children,
  className = '',
  tom = 'claro',
}: {
  children: ReactNode
  className?: string
  tom?: 'claro' | 'atencao'
}) {
  const fundo =
    tom === 'atencao'
      ? 'border-atencao-linha bg-atencao-suave'
      : 'border-linha bg-superficie'

  return (
    <section className={`rounded-2xl border ${fundo} shadow-[0_1px_2px_rgba(15,23,42,0.04)] ${className}`}>
      {children}
    </section>
  )
}

/**
 * Cabecalho de cartao de secao: icone em quadrado de tinta suave, titulo, e o
 * rodape "Ver todas" como link de verdade — a ficha inteira e navegavel sem
 * mouse por causa disso.
 */
export function CartaoDeSecao({
  titulo,
  icone,
  para,
  verTodos = 'Ver todas',
  tom = 'claro',
  children,
}: {
  titulo: string
  icone: ReactNode
  para: string
  verTodos?: string
  tom?: 'claro' | 'atencao'
  children: ReactNode
}) {
  const corDoIcone =
    tom === 'atencao'
      ? 'bg-white text-atencao'
      : 'bg-acento-suave text-acento'

  return (
    <Cartao tom={tom} className="flex flex-col gap-4 p-5">
      <div className="flex items-center gap-3">
        <span className={`inline-flex size-8 items-center justify-center rounded-lg ${corDoIcone}`}>
          {icone}
        </span>
        <h2 className="text-base font-semibold">
          <Link
            to={para}
            className="rounded-sm underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
          >
            {titulo}
          </Link>
        </h2>
      </div>

      <div className="flex-1 text-sm text-tinta-2">{children}</div>

      <Link
        to={para}
        className="w-fit text-sm font-medium text-acento underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
      >
        {verTodos}
      </Link>
    </Cartao>
  )
}
