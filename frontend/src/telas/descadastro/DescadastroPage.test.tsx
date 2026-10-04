import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { DescadastroPage } from './DescadastroPage'

const TOKEN = '3f9c2b7e-5d41-4a8e-9b6c-0e7d1a2f4c88'
const CAMINHO = `/descadastro/${TOKEN}`

const montar = () =>
  renderizar(
    <Routes>
      <Route path="/descadastro/:token" element={<DescadastroPage />} />
    </Routes>,
    { rota: CAMINHO },
  )

const erro = (status: number, mensagem: string) =>
  HttpResponse.json({ status, mensagem, erros: [] }, { status })

describe('descadastro', () => {
  it('mostra o carregando e depois o texto e o botao quando o aviso esta ativo', async () => {
    servidor.use(http.get(CAMINHO, () => HttpResponse.json({ ativo: true })))
    montar()

    expect(screen.getByRole('heading', { name: 'Lembretes de agendamento' })).toBeInTheDocument()
    expect(screen.getByText('Carregando…')).toBeInTheDocument()
    expect(await screen.findByText('Você recebe lembretes de agendamento por e-mail.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Parar de receber lembretes' })).toBeEnabled()
  })

  it('mostra a confirmacao sem botao quando ja esta inativo', async () => {
    servidor.use(http.get(CAMINHO, () => HttpResponse.json({ ativo: false })))
    montar()

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Pronto. Você não vai mais receber lembretes por e-mail.',
    )
    expect(screen.queryByRole('button', { name: 'Parar de receber lembretes' })).not.toBeInTheDocument()
  })

  it('envia o POST ao clicar e passa para a confirmacao', async () => {
    const usuario = userEvent.setup()
    const metodos: string[] = []
    servidor.use(
      http.get(CAMINHO, () => HttpResponse.json({ ativo: true })),
      http.post(CAMINHO, () => {
        metodos.push('POST')
        return HttpResponse.json({ ativo: false })
      }),
    )
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Parar de receber lembretes' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Pronto. Você não vai mais receber lembretes por e-mail.',
    )
    expect(metodos).toEqual(['POST'])
    expect(screen.queryByRole('button', { name: 'Parar de receber lembretes' })).not.toBeInTheDocument()
  })

  it('trava o botao em voo e duplo clique envia um POST so', async () => {
    const usuario = userEvent.setup()
    let posts = 0
    servidor.use(
      http.get(CAMINHO, () => HttpResponse.json({ ativo: true })),
      http.post(CAMINHO, async () => {
        posts += 1
        await delay(100)
        return HttpResponse.json({ ativo: false })
      }),
    )
    montar()

    const botao = await screen.findByRole('button', { name: 'Parar de receber lembretes' })
    await usuario.dblClick(botao)

    expect(botao).toBeDisabled()
    expect(await screen.findByRole('status')).toBeInTheDocument()
    expect(posts).toBe(1)
  })

  it('token desconhecido mostra o link invalido', async () => {
    servidor.use(http.get(CAMINHO, () => erro(404, 'Link inexistente')))
    montar()

    expect(await screen.findByText('Este link não é mais válido.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('outro erro no GET mostra o aviso generico em alerta', async () => {
    servidor.use(http.get(CAMINHO, () => erro(500, 'Erro interno')))
    montar()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar. Tente de novo em instantes.',
    )
  })

  it('POST que falha mostra o alerta e mantem o botao usavel', async () => {
    const usuario = userEvent.setup()
    servidor.use(
      http.get(CAMINHO, () => HttpResponse.json({ ativo: true })),
      http.post(CAMINHO, () => erro(500, 'Erro interno')),
    )
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Parar de receber lembretes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar. Tente de novo em instantes.',
    )
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Parar de receber lembretes' })).toBeEnabled(),
    )
  })

  it('so chama o prefixo /descadastro e nao envia Authorization', async () => {
    const usuario = userEvent.setup()
    const requisicoes: { caminho: string; autorizacao: string | null }[] = []
    servidor.events.on('request:start', ({ request }) => {
      requisicoes.push({
        caminho: new URL(request.url).pathname,
        autorizacao: request.headers.get('authorization'),
      })
    })
    servidor.use(
      http.get(CAMINHO, () => HttpResponse.json({ ativo: true })),
      http.post(CAMINHO, () => HttpResponse.json({ ativo: false })),
    )
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Parar de receber lembretes' }))
    await screen.findByRole('status')
    servidor.events.removeAllListeners()

    expect(requisicoes).toHaveLength(2)
    expect(requisicoes.every((r) => r.caminho.startsWith('/descadastro/'))).toBe(true)
    expect(requisicoes.every((r) => r.autorizacao === null)).toBe(true)
  })
})
