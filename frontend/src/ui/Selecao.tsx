import { useId } from 'react'

type Opcao = { valor: string; rotulo: string }

type Props = {
  rotulo: string
  valor: string
  aoMudar: (valor: string) => void
  opcoes: Opcao[]
  vazio?: string
  erro?: string
}

export function Selecao({ rotulo, valor, aoMudar, opcoes, vazio, erro }: Props) {
  const id = useId()
  const idDoErro = `${id}-erro`

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-tinta-2">
        {rotulo}
      </label>
      <select
        id={id}
        value={valor}
        aria-invalid={erro ? true : undefined}
        aria-describedby={erro ? idDoErro : undefined}
        onChange={(evento) => aoMudar(evento.target.value)}
        className={`border-b bg-superficie px-3 py-2.5 text-base outline-none transition-colors ${
          erro ? 'border-perigo' : 'border-linha focus:border-acento'
        }`}
      >
        {vazio ? <option value="">{vazio}</option> : null}
        {opcoes.map((opcao) => (
          <option key={opcao.valor} value={opcao.valor}>
            {opcao.rotulo}
          </option>
        ))}
      </select>
      {erro ? (
        <span id={idDoErro} className="text-sm text-perigo">
          {erro}
        </span>
      ) : null}
    </div>
  )
}
