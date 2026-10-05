import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { StrictMode } from 'react'
import { Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'

import { api, definirToken } from '../api/cliente'
import { clienteDeTeste, renderizar } from '../teste/renderizar'
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

  it.each([
    ['o logout funciona', 204],
    ['o logout do servidor falha', 401],
  ])('sair esvazia o cache de consultas quando %s', async (_, status) => {
    const ator = userEvent.setup()
    const cliente = clienteDeTeste()
    cliente.setQueryData(['ajustes'], { nome: 'dados da sessao anterior' })
    servidor.use(
      http.post('/auth/login', () =>
        HttpResponse.json({ token: tokenFalso('psicologa@exemplo.com') }),
      ),
      http.post('/auth/logout', () => new HttpResponse(null, { status })),
    )

    renderizar(
      <SessaoProvider>
        <TelaDeTeste />
      </SessaoProvider>,
      { cliente },
    )

    await ator.click(screen.getByText('entrar'))
    await screen.findByText('psicologa@exemplo.com')
    expect(cliente.getQueryCache().getAll().length).toBeGreaterThan(0)

    await ator.click(screen.getByText('sair'))

    expect(await screen.findByText('sem sessao')).toBeInTheDocument()
    expect(cliente.getQueryCache().getAll().length).toBe(0)
  })

  describe('restauracao no boot', () => {
    function Restauracao() {
      const { usuario, restaurando } = useSessao()
      return (
        <div>
          <p>{restaurando ? 'restaurando' : 'restaurada'}</p>
          <p>{usuario ? usuario.email : 'sem sessao'}</p>
        </div>
      )
    }

    it('refresh 200 devolve o usuario sem passar pelo login', async () => {
      servidor.use(
        http.post('/auth/refresh', () =>
          HttpResponse.json({ token: tokenFalso('psicologa@exemplo.com') }),
        ),
      )

      renderizar(
        <SessaoProvider>
          <Restauracao />
        </SessaoProvider>,
      )

      expect(screen.getByText('restaurando')).toBeInTheDocument()
      expect(await screen.findByText('psicologa@exemplo.com')).toBeInTheDocument()
      expect(screen.getByText('restaurada')).toBeInTheDocument()
    })

    it('refresh 401 termina a restauracao sem usuario', async () => {
      servidor.use(http.post('/auth/refresh', () => new HttpResponse(null, { status: 401 })))

      renderizar(
        <SessaoProvider>
          <Restauracao />
        </SessaoProvider>,
      )

      expect(await screen.findByText('restaurada')).toBeInTheDocument()
      expect(screen.getByText('sem sessao')).toBeInTheDocument()
    })

    it('manda um unico refresh no boot', async () => {
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', () => {
          refreshes++
          return new HttpResponse(null, { status: 401 })
        }),
      )

      renderizar(
        <StrictMode>
          <SessaoProvider>
            <Restauracao />
          </SessaoProvider>
        </StrictMode>,
      )

      await screen.findByText('restaurada')
      expect(refreshes).toBe(1)
    })
  })

  describe('sessao que expira no meio do uso', () => {
    function Origem() {
      const { usuario, restaurando } = useSessao()
      if (restaurando) return <p>restaurando</p>
      return (
        <button onClick={() => api('/painel/hoje').catch(() => undefined)}>
          {usuario?.email ?? 'sem sessao'}
        </button>
      )
    }

    function Entrar() {
      const estado = useLocation().state as { expirou?: boolean; de?: { pathname: string } } | null
      return (
        <p>
          entrar expirou={String(estado?.expirou)} de={estado?.de?.pathname}
        </p>
      )
    }

    it('limpa usuario e cache e vai para /entrar guardando a origem', async () => {
      const ator = userEvent.setup()
      const cliente = clienteDeTeste()
      cliente.setQueryData(['pacientes'], [{ id: 1 }])
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', () =>
          refreshes++ === 0
            ? HttpResponse.json({ token: tokenFalso('psicologa@exemplo.com') })
            : new HttpResponse(null, { status: 401 }),
        ),
        http.get('/painel/hoje', () =>
          HttpResponse.json(
            { status: 401, mensagem: 'Sessao expirada.', erros: [] },
            { status: 401 },
          ),
        ),
      )

      renderizar(
        <SessaoProvider>
          <Routes>
            <Route path="/entrar" element={<Entrar />} />
            <Route path="/pacientes" element={<Origem />} />
          </Routes>
        </SessaoProvider>,
        { rota: '/pacientes', cliente },
      )

      await ator.click(await screen.findByText('psicologa@exemplo.com'))

      expect(await screen.findByText('entrar expirou=true de=/pacientes')).toBeInTheDocument()
      expect(cliente.getQueryCache().getAll().length).toBe(0)
    })
  })
})
