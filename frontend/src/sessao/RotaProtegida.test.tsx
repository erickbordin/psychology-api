import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../teste/renderizar'
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
})
