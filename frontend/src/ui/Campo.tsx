import { useId, useState } from 'react'
import type { ReactNode } from 'react'

import { IconeOlho, IconeOlhoFechado } from './icones'

type Props = {
  rotulo: string
  valor: string
  aoMudar: (valor: string) => void
  tipo?: 'text' | 'password' | 'date' | 'time' | 'number' | 'email' | 'tel'
  erro?: string
  exemplo?: string
  autoPreenchimento?: string
  /** Esconde o rotulo do olho e o mantem para leitor de tela — a busca. */
  rotuloOculto?: boolean
  /** Enfeite a esquerda do campo, dentro da borda: a lupa da busca. */
  adorno?: ReactNode
}

/**
 * O erro fica FORA do `<label>`, ligado por `aria-describedby`. Enquanto ele
 * morava dentro, o nome acessivel do campo virava "E-mail campo obrigatorio" —
 * leitor de tela anunciava a falha como se fosse parte do rotulo, e qualquer
 * busca exata por rotulo parava de achar o input assim que havia erro.
 */
export function Campo({
  rotulo,
  valor,
  aoMudar,
  tipo = 'text',
  erro,
  exemplo,
  autoPreenchimento,
  rotuloOculto = false,
  adorno,
}: Props) {
  const id = useId()
  const idDoErro = `${id}-erro`
  const [revelada, setRevelada] = useState(false)
  const ehSenha = tipo === 'password'

  return (
    <div className="flex flex-col gap-1.5">
      <label
        htmlFor={id}
        className={
          rotuloOculto ? 'sr-only' : 'text-sm font-medium text-tinta'
        }
      >
        {rotulo}
      </label>

      <div className="relative">
        {adorno ? (
          <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-tinta-3">
            {adorno}
          </span>
        ) : null}
        <input
          id={id}
          type={ehSenha && revelada ? 'text' : tipo}
          value={valor}
          placeholder={exemplo}
          autoComplete={autoPreenchimento}
          aria-invalid={erro ? true : undefined}
          aria-describedby={erro ? idDoErro : undefined}
          onChange={(evento) => aoMudar(evento.target.value)}
          /* o anel de foco substitui a troca de espessura de borda, que empurraria
             o layout meio pixel a cada foco */
          className={`h-11 w-full rounded-xl border bg-superficie text-base outline-none transition-colors placeholder:text-tinta-3 focus:ring-4 ${
            adorno ? 'pl-10' : 'pl-3.5'
          } ${ehSenha ? 'pr-11' : 'pr-3.5'} ${
            erro
              ? 'border-perigo focus:ring-perigo/15'
              : 'border-linha focus:border-acento focus:ring-acento/15'
          }`}
        />
        {ehSenha ? (
          <button
            type="button"
            aria-label={revelada ? 'Ocultar senha' : 'Mostrar senha'}
            title={revelada ? 'Ocultar senha' : 'Mostrar senha'}
            onClick={() => setRevelada((antes) => !antes)}
            className="absolute right-1.5 top-1/2 inline-flex size-8 -translate-y-1/2 items-center justify-center rounded-lg text-tinta-3 transition-colors hover:text-tinta focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
          >
            {revelada ? <IconeOlhoFechado tamanho={18} /> : <IconeOlho tamanho={18} />}
          </button>
        ) : null}
      </div>

      {erro ? (
        <span id={idDoErro} className="text-sm text-perigo">
          {erro}
        </span>
      ) : null}
    </div>
  )
}
