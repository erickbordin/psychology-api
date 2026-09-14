import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'

/**
 * Todo teste de tela passa por aqui. `retry: false` e obrigatorio: com o
 * padrao do TanStack, um teste de caminho de erro espera tres tentativas e
 * estoura o timeout, e o sintoma parece lentidao e nao configuracao.
 */
export function renderizar(ui: ReactElement, { rota = '/' } = {}) {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  function Envolvido({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={cliente}>
        <MemoryRouter initialEntries={[rota]}>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }

  return render(ui, { wrapper: Envolvido })
}
