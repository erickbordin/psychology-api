import { pedir } from '../client'
import type { TokenResposta } from '../tipos'

export function login(emailUsuario: string, senha: string): Promise<TokenResposta> {
  return pedir<TokenResposta>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ emailUsuario, senha }),
  })
}

export function registrar(nome: string, email: string, senha: string): Promise<unknown> {
  return pedir('/auth/registrar', {
    method: 'POST',
    body: JSON.stringify({ nome, email, senha }),
  })
}

export function logout(): Promise<void> {
  return pedir<void>('/auth/logout', { method: 'POST' })
}
