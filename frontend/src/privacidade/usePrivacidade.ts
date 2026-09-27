import { useContext } from 'react'

import { ContextoDePrivacidade, type Privacidade } from './PrivacidadeProvider'

export function usePrivacidade(): Privacidade {
  const valor = useContext(ContextoDePrivacidade)
  if (!valor) throw new Error('usePrivacidade precisa estar dentro de PrivacidadeProvider.')
  return valor
}
