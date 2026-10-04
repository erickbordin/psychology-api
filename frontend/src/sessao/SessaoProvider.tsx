import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useMemo, useState, type ReactNode } from 'react'

import { api, definirToken } from '../api/cliente'

/**
 * So o e-mail: **nao existe rota que devolva o usuario logado** — conferido,
 * nao ha `UsuarioController` nem `/auth/eu`. O e-mail sai do `sub` do proprio
 * JWT que ja temos em maos.
 *
 * Ler um claim de um token que ja e nosso e legitimo; o que seria errado e
 * CONFIAR nele para autorizar. Autorizacao e do servidor, sempre. Isto e so
 * para escrever o nome de quem esta logado na tela.
 *
 * O nome do psicologo fica faltando na Fatia 1. Quando a tela de Ajustes
 * chegar (Fatia 4, spec 4-5), ela vai precisar de um endpoint de perfil —
 * vale abrir essa conversa com o dono antes, nao depois.
 */
export type Usuario = { email: string }

/** Le o `sub` do payload do JWT. Nao valida assinatura — nao e para isso. */
function emailDoToken(token: string): string | undefined {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return (JSON.parse(json) as { sub?: string }).sub
  } catch {
    return undefined
  }
}

export type Sessao = {
  usuario: Usuario | null
  carregando: boolean
  entrar: (dto: { emailUsuario: string; senha: string }) => Promise<void>
  sair: () => Promise<void>
}

export const ContextoDaSessao = createContext<Sessao | null>(null)

/**
 * O access token NAO e guardado aqui nem em localStorage — ele vive na memoria
 * do cliente HTTP pela duracao da aba. O refresh vem em cookie httpOnly, que o
 * JS nao le. Guardar token em localStorage num produto com prontuario
 * transforma qualquer XSS em vazamento de prontuario.
 */
export function SessaoProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null)
  const [carregando, setCarregando] = useState(false)
  const cliente = useQueryClient()

  const entrar = useCallback(async (dto: { emailUsuario: string; senha: string }) => {
    setCarregando(true)
    try {
      // o login devolve `{ token }` — NAO devolve o usuario. Conferido no
      // `TokenResponseDTO` e no `AuthController`
      const { token } = await api<{ token: string }>('/auth/login', {
        method: 'POST',
        body: JSON.stringify(dto),
      })
      definirToken(token)
      setUsuario({ email: emailDoToken(token) ?? dto.emailUsuario })
    } finally {
      setCarregando(false)
    }
  }, [])

  /**
   * A sessao LOCAL cai independente do `/auth/logout` do servidor ter dado
   * certo. Antes deste conserto, um 401 (token ja expirado) ou uma falha de
   * rede fazia `await api(...)` lancar, e o `definirToken(null)` /
   * `setUsuario(null)` abaixo nunca rodavam — quem clicava em "Sair" via o
   * clique nao fazer nada e continuava logado, com o token ainda vivo em
   * memoria. Num produto com prontuario, usado em consultorio com terceiro
   * na sala, ficar "preso" logado por causa de uma falha do servidor e pior
   * do que o servidor nao saber que a sessao acabou — por isso o catch nao
   * relanca: o pior resultado aceitavel e o logout parecer silencioso para
   * quem clicou, nunca deixar a pessoa logada na tela.
   */
  const sair = useCallback(async () => {
    try {
      await api('/auth/logout', { method: 'POST' })
    } catch {
      // falha do servidor (rede, 401 de token ja expirado, etc). Nao
      // relanca: a sessao local precisa cair de qualquer jeito, ver acima.
    } finally {
      definirToken(null)
      setUsuario(null)
      cliente.clear()
    }
  }, [cliente])

  const valor = useMemo(
    () => ({ usuario, carregando, entrar, sair }),
    [usuario, carregando, entrar, sair],
  )

  return <ContextoDaSessao.Provider value={valor}>{children}</ContextoDaSessao.Provider>
}
