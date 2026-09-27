import { createContext, useCallback, useMemo, useState, type ReactNode } from 'react'

export type Privacidade = { privacidade: boolean; alternar: () => void }

export const ContextoDePrivacidade = createContext<Privacidade | null>(null)

export function PrivacidadeProvider({ children }: { children: ReactNode }) {
  const [privacidade, setPrivacidade] = useState(false)
  const alternar = useCallback(() => setPrivacidade((p) => !p), [])
  const valor = useMemo(() => ({ privacidade, alternar }), [privacidade, alternar])

  return (
    <ContextoDePrivacidade.Provider value={valor}>{children}</ContextoDePrivacidade.Provider>
  )
}
