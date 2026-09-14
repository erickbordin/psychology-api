import { useContext } from 'react'

import { ContextoDaSessao } from './SessaoProvider'

export function useSessao() {
  const sessao = useContext(ContextoDaSessao)
  if (!sessao) {
    throw new Error('useSessao precisa estar dentro de um SessaoProvider')
  }
  return sessao
}
