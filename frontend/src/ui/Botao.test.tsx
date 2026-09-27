import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Botao } from './Botao'

describe('Botao', () => {
  it('desabilitado e anunciado como desabilitado', () => {
    render(<Botao disabled>Salvar</Botao>)
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeDisabled()
  })

  it('o estado ligado e exposto por aria-pressed, nao so por cor', () => {
    render(<Botao aria-pressed="true">Modo privacidade</Botao>)
    const b = screen.getByRole('button', { name: 'Modo privacidade' })
    expect(b).toHaveAttribute('aria-pressed', 'true')
  })

  it('secundario continua sendo o mesmo botao para quem le a tela', () => {
    render(<Botao variante="secundario">Faltou</Botao>)
    expect(screen.getByRole('button', { name: 'Faltou' })).toBeInTheDocument()
  })
})
