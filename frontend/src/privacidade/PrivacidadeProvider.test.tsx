import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { SessaoProvider } from '../sessao/SessaoProvider'
import { renderizar } from '../teste/renderizar'
import { Layout } from '../ui/Layout'
import { PrivacidadeProvider } from './PrivacidadeProvider'
import { usePrivacidade } from './usePrivacidade'

function Sonda({ nome }: { nome: string }) {
  const { privacidade, alternar } = usePrivacidade()
  return (
    <div>
      <span data-borrado={privacidade ? 'true' : undefined}>{nome}</span>
      <button type="button" aria-pressed={privacidade} onClick={alternar}>
        Modo privacidade
      </button>
    </div>
  )
}

function TelaComLink({ nome, para }: { nome: string; para: string }) {
  const { privacidade } = usePrivacidade()
  return (
    <div>
      <span data-borrado={privacidade ? 'true' : undefined}>{nome}</span>
      <Link to={para}>Ir para {para}</Link>
    </div>
  )
}

describe('PrivacidadeProvider', () => {
  it('comeca desligado', () => {
    render(
      <PrivacidadeProvider>
        <Sonda nome="Paciente Um" />
      </PrivacidadeProvider>,
    )
    expect(screen.getByRole('button', { name: 'Modo privacidade' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })

  it('o estado e o mesmo para duas telas irmas', async () => {
    const usuario = userEvent.setup()
    render(
      <PrivacidadeProvider>
        <Sonda nome="Paciente Um" />
        <Sonda nome="Paciente Dois" />
      </PrivacidadeProvider>,
    )

    await usuario.click(screen.getAllByRole('button', { name: 'Modo privacidade' })[0])

    for (const b of screen.getAllByRole('button', { name: 'Modo privacidade' })) {
      expect(b).toHaveAttribute('aria-pressed', 'true')
    }
    expect(screen.getByText('Paciente Um')).toHaveAttribute('data-borrado', 'true')
    expect(screen.getByText('Paciente Dois')).toHaveAttribute('data-borrado', 'true')
  })

  it('usar o hook fora do provider e erro, nao silencio', () => {
    expect(() => render(<Sonda nome="Paciente Um" />)).toThrow()
  })

  it('sobrevive a troca de rota dentro do Layout', async () => {
    const usuario = userEvent.setup()
    renderizar(
      <SessaoProvider>
        <Routes>
          <Route path="/" element={<Layout />}>
            <Route index element={<TelaComLink nome="Paciente Um" para="/pacientes" />} />
            <Route path="/pacientes" element={<TelaComLink nome="Paciente Dois" para="/" />} />
          </Route>
        </Routes>
      </SessaoProvider>,
    )

    await usuario.click(screen.getByRole('button', { name: 'Modo privacidade' }))
    expect(screen.getByRole('button', { name: 'Modo privacidade' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByText('Paciente Um')).toHaveAttribute('data-borrado', 'true')

    await usuario.click(screen.getByText('Ir para /pacientes'))

    expect(await screen.findByText('Paciente Dois')).toHaveAttribute('data-borrado', 'true')
    expect(screen.getByRole('button', { name: 'Modo privacidade' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })
})
