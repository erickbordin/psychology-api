import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { renderizar } from './teste/renderizar'
import { Rotas } from './rotas'
import { ContextoDaSessao } from './sessao/SessaoProvider'

describe('esqueleto', () => {
  it('sobe e mostra a navegacao com rotulo escrito, nao so icone', () => {
    // desde a Task 4, `Rotas` embrulha o `Layout` num `RotaProtegida` — quem
    // nao tem sessao e mandado para /entrar. Este teste e sobre os rotulos da
    // navegacao (a 4 do spec), nao sobre sessao, entao a sessao aqui e uma
    // logada de mentira via `ContextoDaSessao` direto, sem passar pelo login
    renderizar(
      <ContextoDaSessao.Provider
        value={{
          usuario: { email: 'psicologa@exemplo.com' },
          carregando: false,
          entrar: async () => {},
          sair: async () => {},
        }}
      >
        <Rotas />
      </ContextoDaSessao.Provider>,
    )

    // a 4 do spec: cinco destinos, cada um com rotulo LEGIVEL. O icon-only
    // saiu de proposito — quatro icones mudos numa coluna e adivinhacao
    expect(screen.getByRole('link', { name: /hoje/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /agenda/i })).toBeInTheDocument()
  })
})
