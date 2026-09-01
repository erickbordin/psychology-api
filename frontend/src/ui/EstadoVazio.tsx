import type { ReactNode } from 'react'

/**
 * Tela vazia e convite, nao lapide: o texto diz o que fazer em seguida.
 */
export function EstadoVazio({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-2xl border border-dashed border-linha bg-superficie px-6 py-14 text-center text-sm text-tinta-2">
      {children}
    </p>
  )
}
