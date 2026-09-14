import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'

import { definirToken } from '../api/cliente'
import { renderizar } from '../teste/renderizar'
import { servidor } from '../teste/servidor'
import { SessaoProvider } from './SessaoProvider'
import { useSessao } from './useSessao'

/** JWT de mentira: so precisa ter tres partes separadas por ponto e um
 *  payload com `sub` em base64 — e o que `emailDoToken` sabe ler. */
function tokenFalso(sub: string) {
  const cabecalho = btoa(JSON.stringify({ alg: 'none' }))
  const corpo = btoa(JSON.stringify({ sub }))
  return `${cabecalho}.${corpo}.assinatura-fake`
}

function TelaDeTeste() {
  const { usuario, carregando, entrar, sair } = useSessao()
  return (
    <div>
      <p>{carregando ? 'carregando' : 'ocioso'}</p>
      <p>{usuario ? usuario.email : 'sem sessao'}</p>
      <button onClick={() => entrar({ emailUsuario: 'psicologa@exemplo.com', senha: '123456' })}>
        entrar
      </button>
      <button onClick={() => sair()}>sair</button>
    </div>
  )
}

describe('SessaoProvider', () => {
  afterEach(() => {
    // o token vive no modulo do cliente HTTP, fora do React — sem isto, um
    // teste que faz login vaza sessao para o proximo
    definirToken(null)
  })

  it('comeca sem usuario e fora do estado de carregamento', () => {
    renderizar(
      <SessaoProvider>
        <TelaDeTeste />
      </SessaoProvider>,
    )

    expect(screen.getByText('sem sessao')).toBeInTheDocument()
    expect(screen.getByText('ocioso')).toBeInTheDocument()
  })

  it('entrar guarda o e-mail lido do claim sub do JWT devolvido pelo login', async () => {
    const ator = userEvent.setup()
    servidor.use(
      http.post('/auth/login', () =>
        HttpResponse.json({ token: tokenFalso('psicologa@exemplo.com') }),
      ),
    )

    renderizar(
      <SessaoProvider>
        <TelaDeTeste />
      </SessaoProvider>,
    )

    await ator.click(screen.getByText('entrar'))

    expect(await screen.findByText('psicologa@exemplo.com')).toBeInTheDocument()
  })

  it('sair chama o logout e limpa a sessao', async () => {
    const ator = userEvent.setup()
    servidor.use(
      http.post('/auth/login', () =>
        HttpResponse.json({ token: tokenFalso('psicologa@exemplo.com') }),
      ),
      http.post('/auth/logout', () => new HttpResponse(null, { status: 204 })),
    )

    renderizar(
      <SessaoProvider>
        <TelaDeTeste />
      </SessaoProvider>,
    )

    await ator.click(screen.getByText('entrar'))
    await screen.findByText('psicologa@exemplo.com')

    await ator.click(screen.getByText('sair'))

    expect(await screen.findByText('sem sessao')).toBeInTheDocument()
  })

  it('sair limpa a sessao local mesmo quando o logout do servidor falha', async () => {
    // Importante 2 da revisao do conjunto: `sair()` nao tinha `try/catch` —
    // um `/auth/logout` que falhasse (401 de token ja expirado, rede) fazia
    // `await sair()` lancar ANTES de `definirToken(null)`/`setUsuario(null)`,
    // e quem clicava em "Sair" continuava logado, sem nenhum aviso. Este
    // teste prova que a sessao local cai de qualquer jeito.
    const ator = userEvent.setup()
    servidor.use(
      http.post('/auth/login', () =>
        HttpResponse.json({ token: tokenFalso('psicologa@exemplo.com') }),
      ),
      http.post('/auth/logout', () => new HttpResponse(null, { status: 401 })),
    )

    renderizar(
      <SessaoProvider>
        <TelaDeTeste />
      </SessaoProvider>,
    )

    await ator.click(screen.getByText('entrar'))
    await screen.findByText('psicologa@exemplo.com')

    // `sair()` nao pode rejeitar — se rejeitasse, este `click` nunca
    // resolveria a promise dentro do handler e o teste travaria no timeout
    await ator.click(screen.getByText('sair'))

    expect(await screen.findByText('sem sessao')).toBeInTheDocument()
  })
})
