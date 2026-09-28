import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EstadoVazio } from './EstadoVazio'

describe('EstadoVazio', () => {
  it('o titulo e o apoio sao textos distintos, nao um paragrafo so', () => {
    render(<EstadoVazio titulo="Nenhum paciente cadastrado ainda." apoio="Cadastre o primeiro." />)
    expect(screen.getByText('Nenhum paciente cadastrado ainda.')).toBeInTheDocument()
    expect(screen.getByText('Cadastre o primeiro.')).toBeInTheDocument()
  })

  it('sem apoio, nao renderiza paragrafo vazio', () => {
    const { container } = render(<EstadoVazio titulo="Nenhuma sessao registrada ainda." />)
    expect(container.querySelectorAll('p')).toHaveLength(1)
  })
})
