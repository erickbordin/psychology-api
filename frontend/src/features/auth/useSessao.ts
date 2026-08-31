import { useContext } from 'react'

import { ContextoDeSessao } from './SessaoProvider'
import type { Sessao } from './SessaoProvider'

export function useSessao(): Sessao {
  const sessao = useContext(ContextoDeSessao)
  if (!sessao) {
    throw new Error('useSessao exige um SessaoProvider acima na arvore')
  }
  return sessao
}
