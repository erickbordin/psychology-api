import { useId } from 'react'

type Props = {
  rotulo: string
  valor: string
  aoMudar: (valor: string) => void
  tipo?: 'text' | 'password' | 'date' | 'time' | 'number'
  erro?: string
  exemplo?: string
  autoPreenchimento?: string
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
}: Props) {
  const id = useId()
  const idDoErro = `${id}-erro`

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-tinta-2">
        {rotulo}
      </label>
      <input
        id={id}
        type={tipo}
        value={valor}
        placeholder={exemplo}
        autoComplete={autoPreenchimento}
        aria-invalid={erro ? true : undefined}
        aria-describedby={erro ? idDoErro : undefined}
        onChange={(evento) => aoMudar(evento.target.value)}
        /* o campo em foco acende: mudanca de cor da regua mais o papel atras,
           sem alterar espessura de borda, que empurraria o layout */
        className={`border-b bg-superficie px-3 py-2.5 text-base outline-none transition-colors placeholder:text-tinta-3 ${
          erro ? 'border-perigo' : 'border-linha focus:border-acento'
        }`}
      />
      {erro ? (
        <span id={idDoErro} className="text-sm text-perigo">
          {erro}
        </span>
      ) : null}
    </div>
  )
}
