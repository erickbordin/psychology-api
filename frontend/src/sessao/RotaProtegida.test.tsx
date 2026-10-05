import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../teste/renderizar'
import { servidor } from '../teste/servidor'
import { RotaProtegida } from './RotaProtegida'
import { SessaoProvider } from './SessaoProvider'

describe('rota protegida', () => {
  it('manda para o login quem nao tem sessao', async () => {
    // o `<Navigate>` dentro de `RotaProtegida` so troca a localizacao do
    // router — quem decide o que aparece nela e o `<Routes>`. Sem uma rota
    // "/entrar" de verdade aqui, a navegacao acontece por baixo dos panos e a
    // tela continua vazia; por isso o `<Routes>` abaixo espelha o que a
    // Step 4 monta em `rotas.tsx`, em vez de renderizar `RotaProtegida` solta.
    renderizar(
      <SessaoProvider>
        <Routes>
          <Route path="/entrar" element={<p>Entrar</p>} />
          <Route
            path="/"
            element={
              <RotaProtegida>
                <p>conteudo secreto</p>
              </RotaProtegida>
            }
          />
        </Routes>
      </SessaoProvider>,
    )

    expect(await screen.findByText(/entrar/i)).toBeInTheDocument()
    expect(screen.queryByText('conteudo secreto')).not.toBeInTheDocument()
  })

  it('mostra Carregando… enquanto a sessao esta sendo restaurada', async () => {
    servidor.use(
      http.post('/auth/refresh', async () => {
        await new Promise((resolver) => setTimeout(resolver, 50))
        return new HttpResponse(null, { status: 401 })
      }),
    )

    renderizar(
      <SessaoProvider>
        <Routes>
          <Route path="/entrar" element={<p>Entrar</p>} />
          <Route
            path="/"
            element={
              <RotaProtegida>
                <p>conteudo secreto</p>
              </RotaProtegida>
            }
          />
        </Routes>
      </SessaoProvider>,
    )

    expect(screen.getByText('Carregando…')).toBeInTheDocument()
    expect(screen.queryByText('Entrar')).not.toBeInTheDocument()
    expect(await screen.findByText('Entrar')).toBeInTheDocument()
  })

  it('manda para /entrar guardando a rota de origem', async () => {
    function Entrar() {
      const estado = useLocation().state as { de?: { pathname: string } } | null
      return <p>entrar vindo de {estado?.de?.pathname}</p>
    }

    renderizar(
      <SessaoProvider>
        <Routes>
          <Route path="/entrar" element={<Entrar />} />
          <Route
            path="/pacientes"
            element={
              <RotaProtegida>
                <p>conteudo secreto</p>
              </RotaProtegida>
            }
          />
        </Routes>
      </SessaoProvider>,
      { rota: '/pacientes' },
    )

    expect(await screen.findByText('entrar vindo de /pacientes')).toBeInTheDocument()
  })
})
