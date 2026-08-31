/**
 * O access token vive so aqui. Quem aguenta o recarregamento e o cookie HttpOnly
 * de refresh, que o JavaScript nao alcanca.
 *
 * Modulo, e nao contexto React, porque o client.ts precisa do token e nao pode
 * usar hook.
 */
let acesso: string | null = null
let aoPerder: (() => void) | null = null

export function lerAcesso(): string | null {
  return acesso
}

export function gravarAcesso(token: string | null): void {
  const perdeu = acesso !== null && token === null
  acesso = token
  if (perdeu) {
    aoPerder?.()
  }
}

export function registrarPerdaDeSessao(callback: () => void): void {
  aoPerder = callback
}

/** Só para teste: zera o estado do módulo entre casos. */
export function reiniciarSessaoEmMemoria(): void {
  acesso = null
  aoPerder = null
}
