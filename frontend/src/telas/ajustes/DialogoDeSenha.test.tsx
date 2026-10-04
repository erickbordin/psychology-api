import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { DialogoDeSenha } from './DialogoDeSenha'

import '../../estilo/estilo.css'

const erro = (status: number, mensagem: string, erros: { campo: string; erro: string }[] = []) =>
  HttpResponse.json({ status, mensagem, erros }, { status })

function Casca({ aoTrocar }: { aoTrocar: () => void }) {
  const [aberto, setAberto] = useState(false)
  return (
    <>
      <button type="button" onClick={() => setAberto(true)}>
        Abrir
      </button>
      <DialogoDeSenha aberto={aberto} aoFechar={() => setAberto(false)} aoTrocar={aoTrocar} />
    </>
  )
}

async function abrir(aoTrocar: () => void = () => undefined) {
  const usuario = userEvent.setup()
  renderizar(<Casca aoTrocar={aoTrocar} />)
  await usuario.click(screen.getByRole('button', { name: 'Abrir' }))
  await screen.findByRole('dialog', { name: 'Trocar senha' })
  return usuario
}

const atual = () => screen.getByLabelText(/^Senha atual/)
const nova = () => screen.getByLabelText(/^Nova senha/)

describe('DialogoDeSenha', () => {
  it('mostra os dois campos de senha com autocomplete certo', async () => {
    await abrir()

    expect(atual()).toHaveAttribute('type', 'password')
    expect(atual()).toHaveAttribute('autocomplete', 'current-password')
    expect(nova()).toHaveAttribute('type', 'password')
    expect(nova()).toHaveAttribute('autocomplete', 'new-password')
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument()
  })

  it('envia senhaAtual e novaSenha, fecha e avisa', async () => {
    let corpo: unknown = null
    servidor.use(
      http.post('/ajustes/senha', async ({ request }) => {
        corpo = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const aoTrocar = vi.fn()
    const usuario = await abrir(aoTrocar)

    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.type(nova(), 'senha-nova-muito-boa')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    await vi.waitFor(() => expect(aoTrocar).toHaveBeenCalledTimes(1))
    expect(corpo).toEqual({ senhaAtual: 'senha-antiga-1', novaSenha: 'senha-nova-muito-boa' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('400 em senhaAtual pousa no campo Senha atual', async () => {
    servidor.use(
      http.post('/ajustes/senha', () =>
        erro(400, 'Dados inválidos.', [{ campo: 'senhaAtual', erro: 'A senha atual não confere.' }]),
      ),
    )
    const usuario = await abrir()

    await usuario.type(atual(), 'errada-errada')
    await usuario.type(nova(), 'senha-nova-muito-boa')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('A senha atual não confere.')).toBeInTheDocument()
    expect(atual()).toHaveAccessibleDescription('A senha atual não confere.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('400 em novaSenha pousa no campo Nova senha', async () => {
    servidor.use(
      http.post('/ajustes/senha', () =>
        erro(400, 'Dados inválidos.', [
          { campo: 'novaSenha', erro: 'A nova senha precisa ser diferente da atual.' },
        ]),
      ),
    )
    const usuario = await abrir()

    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.type(nova(), 'senha-antiga-1')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('A nova senha precisa ser diferente da atual.')).toBeInTheDocument()
    expect(nova()).toHaveAccessibleDescription('A nova senha precisa ser diferente da atual.')
  })

  it('erro sem campo vira alerta dentro do dialogo', async () => {
    servidor.use(http.post('/ajustes/senha', () => erro(500, 'Erro inesperado.')))
    const usuario = await abrir()

    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.type(nova(), 'senha-nova-muito-boa')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Erro inesperado.')
    expect(screen.getByRole('dialog')).toContainElement(alerta)
  })

  it('campos vazios mostram as mensagens do cliente sem chamar a API', async () => {
    let posts = 0
    servidor.use(
      http.post('/ajustes/senha', () => {
        posts += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const usuario = await abrir()

    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Informe a senha atual.')).toBeInTheDocument()
    expect(screen.getByText('Informe uma senha.')).toBeInTheDocument()
    expect(posts).toBe(0)
  })

  it('nova senha com menos de 10 caracteres nao chama a API', async () => {
    let posts = 0
    servidor.use(
      http.post('/ajustes/senha', () => {
        posts += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const usuario = await abrir()

    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.type(nova(), 'curta')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('A senha precisa de pelo menos 10 caracteres.')).toBeInTheDocument()
    expect(posts).toBe(0)
  })

  it.each([
    ['65 caracteres', 'a'.repeat(65)],
    ['40 caracteres com acento (80 bytes)', 'ã'.repeat(40)],
  ])('nova senha longa demais (%s) mostra a mensagem e nao chama a API', async (_, senha) => {
    let posts = 0
    servidor.use(
      http.post('/ajustes/senha', () => {
        posts += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const usuario = await abrir()

    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.click(nova())
    await usuario.paste(senha)
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('A senha deve ter no máximo 64 caracteres.')).toBeInTheDocument()
    expect(nova()).toHaveAccessibleDescription('A senha deve ter no máximo 64 caracteres.')
    expect(posts).toBe(0)
  })

  it('Cancelar fecha sem chamar a API e os campos vem vazios na proxima vez', async () => {
    let posts = 0
    servidor.use(
      http.post('/ajustes/senha', () => {
        posts += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const usuario = await abrir()

    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.type(nova(), 'senha-nova-muito-boa')
    await usuario.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Abrir' }))
    await screen.findByRole('dialog', { name: 'Trocar senha' })
    expect(atual()).toHaveValue('')
    expect(nova()).toHaveValue('')
    expect(posts).toBe(0)
  })

  it('Esc fecha sem chamar a API', async () => {
    let posts = 0
    servidor.use(
      http.post('/ajustes/senha', () => {
        posts += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const usuario = await abrir()

    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(posts).toBe(0)
  })

  it('Salvar trava em voo e duplo clique manda um POST', async () => {
    let posts = 0
    let liberar: () => void = () => undefined
    const preso = new Promise<void>((resolve) => {
      liberar = resolve
    })
    servidor.use(
      http.post('/ajustes/senha', async () => {
        posts += 1
        await preso
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const usuario = await abrir()

    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.type(nova(), 'senha-nova-muito-boa')
    const salvar = screen.getByRole('button', { name: 'Salvar' })
    await usuario.dblClick(salvar)

    await vi.waitFor(() => expect(salvar).toBeDisabled())
    liberar()
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(posts).toBe(1)
  })

  it('depois de um 400 em senhaAtual o usuario corrige e salva com sucesso', async () => {
    let posts = 0
    servidor.use(
      http.post('/ajustes/senha', () => {
        posts += 1
        return posts === 1
          ? erro(400, 'Dados inválidos.', [{ campo: 'senhaAtual', erro: 'A senha atual não confere.' }])
          : new HttpResponse(null, { status: 204 })
      }),
    )
    const aoTrocar = vi.fn()
    const usuario = await abrir(aoTrocar)

    await usuario.type(atual(), 'errada-errada')
    await usuario.type(nova(), 'senha-nova-muito-boa')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(await screen.findByText('A senha atual não confere.')).toBeInTheDocument()

    await usuario.clear(atual())
    await usuario.type(atual(), 'senha-antiga-1')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    await vi.waitFor(() => expect(aoTrocar).toHaveBeenCalledTimes(1))
    expect(posts).toBe(2)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
