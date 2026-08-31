import * as Modal from '@radix-ui/react-dialog'
import type { ReactNode } from 'react'

type Props = {
  aberto: boolean
  aoFechar: () => void
  titulo: string
  descricao?: string
  children: ReactNode
}

/**
 * Radix por causa do que nao aparece na tela: foco presi dentro do modal, Escape,
 * `aria-modal` e o retorno do foco para quem abriu. Escrever isso a mao e onde
 * dialogo caseiro erra.
 */
export function Dialogo({ aberto, aoFechar, titulo, descricao, children }: Props) {
  return (
    <Modal.Root open={aberto} onOpenChange={(estado) => (estado ? null : aoFechar())}>
      <Modal.Portal>
        <Modal.Overlay className="fixed inset-0 bg-tinta/25" />
        <Modal.Content className="fixed left-1/2 top-1/2 max-h-[calc(100vh-3rem)] w-[min(30rem,calc(100vw-3rem))] -translate-x-1/2 -translate-y-1/2 overflow-y-auto border border-linha bg-fundo p-8 shadow-lg focus:outline-none">
          <Modal.Title className="font-serif text-2xl leading-tight">{titulo}</Modal.Title>
          {descricao ? (
            <Modal.Description className="mt-2 text-sm text-tinta-2">{descricao}</Modal.Description>
          ) : (
            <Modal.Description className="sr-only">{titulo}</Modal.Description>
          )}
          <div className="mt-6 flex flex-col gap-6">{children}</div>
        </Modal.Content>
      </Modal.Portal>
    </Modal.Root>
  )
}
