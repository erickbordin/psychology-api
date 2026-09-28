import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'

import { PrivacidadeProvider } from '../privacidade/PrivacidadeProvider'

/** O cliente padrao da suite, exportado para quem precisa de uma referencia
 *  propria — ver o parametro `cliente` de `renderizar`. */
export function clienteDeTeste(opcoes: ConstructorParameters<typeof QueryClient>[0] = {}) {
  return new QueryClient({
    ...opcoes,
    defaultOptions: {
      ...opcoes.defaultOptions,
      queries: { retry: false, ...opcoes.defaultOptions?.queries },
      mutations: { retry: false, ...opcoes.defaultOptions?.mutations },
    },
  })
}

/**
 * Todo teste de tela passa por aqui. `retry: false` e obrigatorio: com o
 * padrao do TanStack, um teste de caminho de erro espera tres tentativas e
 * estoura o timeout, e o sintoma parece lentidao e nao configuracao.
 *
 * `cliente` e opcional e serve a dois casos que o cliente anonimo nao atende:
 * espionar `invalidateQueries` (provar a invalidacao declarada) e ajustar
 * `gcTime` para reproduzir um cache que ja expirou.
 */
export function renderizar(
  ui: ReactElement,
  {
    rota = '/',
    cliente = clienteDeTeste(),
    privacidadeLigada = false,
  }: { rota?: string; cliente?: QueryClient; privacidadeLigada?: boolean } = {},
) {
  function Envolvido({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={cliente}>
        <MemoryRouter initialEntries={[rota]}>
          <PrivacidadeProvider inicial={privacidadeLigada}>{children}</PrivacidadeProvider>
        </MemoryRouter>
      </QueryClientProvider>
    )
  }

  return render(ui, { wrapper: Envolvido })
}
