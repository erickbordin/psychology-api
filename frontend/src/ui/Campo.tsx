import { forwardRef, useId, type InputHTMLAttributes } from 'react'

type Props = InputHTMLAttributes<HTMLInputElement> & {
  rotulo: string
  erro?: string
  obrigatorio?: boolean
}

/** Obrigatorio e VISIVEL antes do erro (spec 6.2): campo obrigatorio marcado,
 *  opcional escrito "opcional". O usuario nao descobre a regra errando. */
export const Campo = forwardRef<HTMLInputElement, Props>(function Campo(
  { rotulo, erro, obrigatorio = false, ...resto },
  ref,
) {
  const id = useId()
  const idDoErro = `${id}-erro`

  return (
    <div className="campo">
      <label className="campo-rotulo" htmlFor={id}>
        {rotulo}
        {obrigatorio ? (
          <span aria-hidden="true"> *</span>
        ) : (
          <span className="campo-opcional"> (opcional)</span>
        )}
      </label>
      <input
        id={id}
        ref={ref}
        required={obrigatorio}
        aria-invalid={erro ? true : undefined}
        aria-describedby={erro ? idDoErro : undefined}
        {...resto}
      />
      {erro && (
        <p className="campo-erro" id={idDoErro}>
          {erro}
        </p>
      )}
    </div>
  )
})
