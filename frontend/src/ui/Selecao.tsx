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
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-tinta">
        {rotulo}
      </label>
      <select
        id={id}
        value={valor}
        aria-invalid={erro ? true : undefined}
        aria-describedby={erro ? idDoErro : undefined}
        onChange={(evento) => aoMudar(evento.target.value)}
        className={`h-11 rounded-xl border bg-superficie px-3 text-base outline-none transition-colors focus:ring-4 ${
          erro
            ? 'border-perigo focus:ring-perigo/15'
            : 'border-linha focus:border-acento focus:ring-acento/15'
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
