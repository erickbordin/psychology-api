import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Chip } from './Chip'

describe('Chip', () => {
  it('realizada e faltou se distinguem sem depender de cor', () => {
    const { rerender } = render(<Chip variante="realizada">REALIZADA</Chip>)
    const realizada = screen.getByText('REALIZADA').closest('[data-chip]')
    const glifoRealizada = realizada?.querySelector('svg')

    expect(glifoRealizada).not.toBeNull()
    const htmlRealizada = glifoRealizada?.innerHTML

    rerender(<Chip variante="faltou">FALTOU</Chip>)
    const faltou = screen.getByText('FALTOU').closest('[data-chip]')
    const glifoFaltou = faltou?.querySelector('svg')

    expect(glifoFaltou).not.toBeNull()
    expect(glifoFaltou?.innerHTML).not.toEqual(htmlRealizada)
  })

  it('o glifo nao e lido em voz alta, porque a palavra ja esta escrita', () => {
    render(<Chip variante="realizada">REALIZADA</Chip>)
    const svg = screen.getByText('REALIZADA').closest('[data-chip]')?.querySelector('svg')
    expect(svg?.getAttribute('aria-hidden')).toBe('true')
  })

  it('neutro nao ganha glifo', () => {
    render(<Chip>AGENDADA</Chip>)
    const svg = screen.getByText('AGENDADA').closest('[data-chip]')?.querySelector('svg')
    expect(svg).toBeNull()
  })
})
