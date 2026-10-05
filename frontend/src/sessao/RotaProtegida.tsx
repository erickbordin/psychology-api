import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { useSessao } from './useSessao'

export function RotaProtegida({ children }: { children: ReactNode }) {
  const { usuario, restaurando } = useSessao()
  const localizacao = useLocation()
  if (restaurando) {
    return <p>Carregando…</p>
  }
  if (!usuario) {
    return <Navigate to="/entrar" replace state={{ de: localizacao }} />
  }
  return <>{children}</>
}
