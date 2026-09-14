import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'

import { useSessao } from './useSessao'

export function RotaProtegida({ children }: { children: ReactNode }) {
  const { usuario } = useSessao()
  if (!usuario) {
    return <Navigate to="/entrar" replace />
  }
  return <>{children}</>
}
