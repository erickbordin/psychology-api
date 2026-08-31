import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createBrowserRouter } from 'react-router-dom'

import { SessaoProvider } from './features/auth/SessaoProvider'
import { definicaoDeRotas } from './rotas'
import './estilo/tokens.css'

const queryClient = new QueryClient()
const router = createBrowserRouter(definicaoDeRotas)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <SessaoProvider>
        <RouterProvider router={router} />
      </SessaoProvider>
    </QueryClientProvider>
  </StrictMode>,
)
