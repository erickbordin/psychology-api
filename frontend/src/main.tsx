import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'

import './estilo/tokens.css'
import './estilo/base.css'
import './estilo/estilo.css'
import './estilo/dialogo.css'
import { Rotas } from './rotas'
import { SessaoProvider } from './sessao/SessaoProvider'

const cliente = new QueryClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={cliente}>
      <BrowserRouter>
        <SessaoProvider>
          <Rotas />
        </SessaoProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
