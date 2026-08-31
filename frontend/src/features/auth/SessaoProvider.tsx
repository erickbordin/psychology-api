import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'

import { renovarNoBoot } from '../../api/client'
import * as auth from '../../api/recursos/auth'
import { gravarAcesso, registrarPerdaDeSessao } from '../../api/sessaoEmMemoria'

export type Sessao = {
  autenticado: boolean
  carregando: boolean
  entrar: (emailUsuario: string, senha: string) => Promise<void>
  sair: () => Promise<void>
}

export const ContextoDeSessao = createContext<Sessao | null>(null)

export function SessaoProvider({ children }: { children: ReactNode }) {
  const [autenticado, setAutenticado] = useState(false)
  const [carregando, setCarregando] = useState(true)
  const queryClient = useQueryClient()

  /**
   * Um refresh antes de renderizar rota nenhuma: o cookie HttpOnly viaja sozinho,
   * e e assim que um F5 nao desloga sem token persistido em lugar nenhum.
   *
   * Enquanto essa promise nao resolve, `carregando` fica true — "ainda nao se
   * sabe" e um terceiro estado, distinto de autenticado/visitante. Resolver
   * cedo demais (ex.: comecar com autenticado=false) faria um usuario logado
   * ver a tela de visitante piscar antes do refresh confirmar a sessao.
   */
  useEffect(() => {
    let vivo = true
    renovarNoBoot()
      .then((renovou) => {
        if (vivo) setAutenticado(renovou)
      })
      .finally(() => {
        if (vivo) setCarregando(false)
      })
    return () => {
      vivo = false
    }
  }, [])

  useEffect(() => {
    registrarPerdaDeSessao(() => setAutenticado(false))
  }, [])

  const entrar = useCallback(async (emailUsuario: string, senha: string) => {
    const { token } = await auth.login(emailUsuario, senha)
    gravarAcesso(token)
    setAutenticado(true)
  }, [])

  /**
   * Limpar o cache faz parte de sair: sem isso, dado de paciente fica em memoria
   * para o proximo login na mesma aba.
   */
  const sair = useCallback(async () => {
    try {
      await auth.logout()
    } finally {
      gravarAcesso(null)
      setAutenticado(false)
      queryClient.clear()
    }
  }, [queryClient])

  return (
    <ContextoDeSessao.Provider value={{ autenticado, carregando, entrar, sair }}>
      {children}
    </ContextoDeSessao.Provider>
  )
}
