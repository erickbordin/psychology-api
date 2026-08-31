import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { lerAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComProvedores } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { SessaoProvider } from './SessaoProvider'
import { useSessao } from './useSessao'

function Sonda() {
  const { autenticado, carregando, entrar, sair } = useSessao()

  if (carregando) return <p>carregando</p>

  return (
    <div>
      <p>{autenticado ? 'autenticado' : 'visitante'}</p>
      <button onClick={() => void entrar('ana@teste.com', 'senhaforte123')}>entrar</button>
      <button onClick={() => void sair()}>sair</button>
    </div>
  )
}

function renderizarSonda() {
  return renderizarComProvedores(
    <SessaoProvider>
      <Sonda />
    </SessaoProvider>,
  )
}

describe('SessaoProvider', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())

  it('restaura a sessao quando o refresh do boot responde 200', async () => {
    servidorDeTeste.use(http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-do-cookie' })))

    renderizarSonda()

    expect(await screen.findByText('autenticado')).toBeInTheDocument()
    expect(lerAcesso()).toBe('token-do-cookie')
  })

  it('trata como visitante quando o refresh do boot responde 401', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )

    renderizarSonda()

    expect(await screen.findByText('visitante')).toBeInTheDocument()
    expect(lerAcesso()).toBeNull()
  })

  it('mostra carregando antes do refresh do boot resolver', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )

    renderizarSonda()

    expect(screen.getByText('carregando')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('visitante')).toBeInTheDocument())
  })

  it('entrar guarda o access token e autentica', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
      http.post('/auth/login', () => HttpResponse.json({ token: 'token-do-login' })),
    )

    renderizarSonda()
    await screen.findByText('visitante')

    await userEvent.click(screen.getByRole('button', { name: 'entrar' }))

    expect(await screen.findByText('autenticado')).toBeInTheDocument()
    expect(lerAcesso()).toBe('token-do-login')
  })

  it('sair chama o logout e volta a visitante', async () => {
    let chamouLogout = false
    servidorDeTeste.use(
      http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-do-cookie' })),
      http.post('/auth/logout', () => {
        chamouLogout = true
        return new HttpResponse(null, { status: 204 })
      }),
    )

    renderizarSonda()
    await screen.findByText('autenticado')

    await userEvent.click(screen.getByRole('button', { name: 'sair' }))

    expect(await screen.findByText('visitante')).toBeInTheDocument()
    expect(chamouLogout).toBe(true)
    expect(lerAcesso()).toBeNull()
  })

  it('perder a sessao no meio do uso volta a visitante', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-do-cookie' })),
    )

    renderizarSonda()
    await screen.findByText('autenticado')

    const { gravarAcesso } = await import('../../api/sessaoEmMemoria')
    await act(async () => {
      gravarAcesso(null)
    })

    expect(await screen.findByText('visitante')).toBeInTheDocument()
  })
})
