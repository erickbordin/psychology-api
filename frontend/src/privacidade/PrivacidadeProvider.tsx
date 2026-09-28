import { createContext, useCallback, useMemo, useState, type ReactNode } from 'react'

export type Privacidade = { privacidade: boolean; alternar: () => void }

export const ContextoDePrivacidade = createContext<Privacidade | null>(null)

export function PrivacidadeProvider({
  children,
  inicial = false,
}: {
  children: ReactNode
  inicial?: boolean
}) {
  const [privacidade, setPrivacidade] = useState(inicial)
  const alternar = useCallback(() => setPrivacidade((p) => !p), [])
  const valor = useMemo(() => ({ privacidade, alternar }), [privacidade, alternar])

  return (
    <ContextoDePrivacidade.Provider value={valor}>{children}</ContextoDePrivacidade.Provider>
  )
}
