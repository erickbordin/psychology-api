import type { ReactNode } from 'react'

/**
 * Tela vazia e convite, nao lapide: o texto diz o que fazer em seguida.
 */
export function EstadoVazio({ children }: { children: ReactNode }) {
  return <p className="border-t border-linha py-16 text-center text-sm text-tinta-2">{children}</p>
}
