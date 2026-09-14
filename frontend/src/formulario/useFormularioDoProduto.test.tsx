import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { z } from 'zod'

import { renderizar } from '../teste/renderizar'
import { Botao } from '../ui/Botao'
import { Campo } from '../ui/Campo'
import { ResumoDeErros } from './ResumoDeErros'
import { useFormularioDoProduto } from './useFormularioDoProduto'

const esquema = z.object({
  nome: z.string().min(2, 'O nome precisa ter ao menos 2 letras.'),
})

function FormularioDeTeste({ aoEnviar = vi.fn() }) {
  const f = useFormularioDoProduto(esquema, aoEnviar)
  return (
    <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
      <ResumoDeErros erros={f.formState.errors} />
      <Campo rotulo="Nome" obrigatorio erro={f.formState.errors.nome?.message} {...f.register('nome')} />
      <Botao type="submit">Salvar</Botao>
    </form>
  )
}

describe('molde de formulario', () => {
  it('nao acusa erro antes do primeiro toque', async () => {
    renderizar(<FormularioDeTeste />)
    expect(screen.queryByText(/ao menos 2 letras/i)).not.toBeInTheDocument()
  })

  it('acusa no blur depois do primeiro toque', async () => {
    const usuario = userEvent.setup()
    renderizar(<FormularioDeTeste />)

    await usuario.click(screen.getByLabelText(/nome/i))
    await usuario.tab()

    // `{ selector: 'p' }` mira o erro do campo (Campo.tsx). O mesmo texto
    // tambem aparece no <li> do ResumoDeErros (o anuncio em aria-live) —
    // as duas coisas por design (spec 6.2: erro no campo E anuncio), entao
    // um findByText sem escopo acha as duas ocorrencias e falha por
    // ambiguidade. Ver ResumoDeErros.tsx.
    expect(await screen.findByText(/ao menos 2 letras/i, { selector: 'p' })).toBeInTheDocument()
  })

  it('revalida a cada tecla depois que o campo ja errou', async () => {
    const usuario = userEvent.setup()
    renderizar(<FormularioDeTeste />)

    await usuario.click(screen.getByLabelText(/nome/i))
    await usuario.tab()
    await screen.findByText(/ao menos 2 letras/i, { selector: 'p' })

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena')

    await waitFor(() =>
      expect(screen.queryByText(/ao menos 2 letras/i)).not.toBeInTheDocument(),
    )
  })

  it('submit invalido nao morre em silencio: erro, foco e anuncio', async () => {
    const usuario = userEvent.setup()
    const aoEnviar = vi.fn()
    renderizar(<FormularioDeTeste aoEnviar={aoEnviar} />)

    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(aoEnviar).not.toHaveBeenCalled()
    expect(await screen.findByText(/ao menos 2 letras/i, { selector: 'p' })).toBeInTheDocument()
    expect(screen.getByLabelText(/nome/i)).toHaveFocus()
    expect(screen.getByRole('alert')).toHaveTextContent(/ao menos 2 letras/i)
  })

  it('marca obrigatorio ANTES do erro, para o usuario nao descobrir errando', () => {
    renderizar(<FormularioDeTeste />)
    expect(screen.getByLabelText(/nome/i)).toBeRequired()
  })
})
