import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { SessaoProvider } from '../sessao/SessaoProvider'
import { renderizar } from '../teste/renderizar'
import { servidor } from '../teste/servidor'
import { Layout } from './Layout'

describe('Layout', () => {
  it('Sair chama o logout e tira o psicologo da area protegida', async () => {
    // Importante 2 da revisao do conjunto: `sair()` existe em `SessaoProvider`
    // desde a Task 1, testado, e nenhuma tela o chamava — a unica forma de
    // encerrar a sessao era fechar a aba. Este teste prova as duas metades:
    // o endpoint de logout e chamado de verdade (nao um mock de `sair`), e a
    // area protegida some da tela.
    let logoutChamado = false
    servidor.use(
      http.post('/auth/logout', () => {
        logoutChamado = true
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const usuario = userEvent.setup()
    renderizar(
      <SessaoProvider>
        <Routes>
          <Route path="/entrar" element={<p>Entrar</p>} />
          <Route path="/" element={<Layout />}>
            <Route index element={<p>conteudo protegido</p>} />
          </Route>
        </Routes>
      </SessaoProvider>,
    )

    await screen.findByText('conteudo protegido')

    await usuario.click(screen.getByRole('button', { name: /sair/i }))

    expect(await screen.findByText('Entrar')).toBeInTheDocument()
    expect(screen.queryByText('conteudo protegido')).not.toBeInTheDocument()
    expect(logoutChamado).toBe(true)
  })
})
