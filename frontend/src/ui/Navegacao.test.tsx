import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { Navegacao } from './Navegacao'

describe('Navegacao', () => {
  it('os cinco destinos continuam com rotulo escrito', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Navegacao />
      </MemoryRouter>,
    )

    for (const rotulo of ['Hoje', 'Agenda', 'Pacientes', 'Financeiro', 'Ajustes']) {
      expect(screen.getByRole('link', { name: rotulo })).toBeInTheDocument()
    }
  })

  it('o item ativo e identificavel sem depender de cor', () => {
    render(
      <MemoryRouter initialEntries={['/agenda']}>
        <Navegacao />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Agenda' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })
})
