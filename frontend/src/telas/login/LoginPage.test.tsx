import { useEffect, useState } from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes, useNavigate } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { api } from '../../api/cliente'
import { servidor } from '../../teste/servidor'
import { renderizar } from '../../teste/renderizar'
import { RotaProtegida } from '../../sessao/RotaProtegida'
import { SessaoProvider } from '../../sessao/SessaoProvider'
import { LoginPage } from './LoginPage'

const renderizarLogin = () =>
  renderizar(
    <SessaoProvider>
      <LoginPage />
    </SessaoProvider>,
  )

/**
 * A tela "Hoje" de verdade ainda nao existe nesta fatia — em `rotas.tsx` ela
 * e so o `<p>Hoje</p>` provisorio (fatia futura). Este stub fica local a
 * este teste e representa esse destino so o suficiente para provar que
 * `navegar('/')` realmente leva la E que a chamada seguinte carrega o
 * header — sem isso o teste "prova" o token com uma chamada manual que nada
 * tem a ver com a navegacao que o proprio nome do teste promete.
 */
function PainelDoDiaDeTeste() {
  const [carregado, setCarregado] = useState(false)
  useEffect(() => {
    api('/painel/hoje').then(() => setCarregado(true))
  }, [])
  return <p>{carregado ? 'painel do dia carregado' : 'carregando'}</p>
}

describe('login', () => {
  it('nao envia com campo vazio, e diz o que falta', async () => {
    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.click(screen.getByRole('button', { name: /entrar/i }))

    // a mesma mensagem aparece DUAS vezes por design (spec 6.2): no <p> do
    // Campo e no <li> do ResumoDeErros (o anuncio em aria-live). Um
    // findByText sem escopo acha as duas ocorrencias e falha por
    // ambiguidade — `{ selector: 'p' }` mira so o erro do campo.
    expect(
      await screen.findByText(/informe um e-mail/i, { selector: 'p' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('avisa que o e-mail esta mal formado antes de ir ao servidor', async () => {
    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.type(screen.getByLabelText(/e-mail/i), 'helena-arroba-nada')
    await usuario.tab()

    expect(
      await screen.findByText(/e-mail em formato válido/i, { selector: 'p' }),
    ).toBeInTheDocument()
  })

  it('mostra a mensagem do SERVIDOR quando a credencial esta errada', async () => {
    servidor.use(
      http.post('/auth/login', () =>
        HttpResponse.json(
          { status: 401, mensagem: 'E-mail ou senha incorretos.', erros: [] },
          { status: 401 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.type(screen.getByLabelText(/e-mail/i), 'marina@example.com')
    await usuario.type(screen.getByLabelText(/senha/i), 'senha-que-nao-e-a-dela')
    await usuario.click(screen.getByRole('button', { name: /entrar/i }))

    expect(await screen.findByText(/e-mail ou senha incorretos/i)).toBeInTheDocument()
  })

  it('login que da certo guarda o token e leva para o dia', async () => {
    // o corpo e `{ token }` — o login NAO devolve o usuario (TokenResponseDTO)
    const jwt = [
      'eyJhbGciOiJIUzI1NiJ9',
      btoa(JSON.stringify({ sub: 'marina@example.com' })).replace(/=+$/, ''),
      'assinatura-que-o-cliente-nao-verifica',
    ].join('.')

    servidor.use(http.post('/auth/login', () => HttpResponse.json({ token: jwt })))

    let autorizacao: string | null = null
    servidor.use(
      http.get('/painel/hoje', ({ request }) => {
        autorizacao = request.headers.get('Authorization')
        return HttpResponse.json({ proxima: null, sessoes: [], pendencias: [] })
      }),
    )

    const usuario = userEvent.setup()
    // rota propria, so para este teste: `renderizarLogin()` monta a
    // LoginPage sozinha, sem `<Routes>` — sem elas, `navegar('/')` no fim
    // do login nao leva a lugar nenhum e nenhuma chamada seguinte acontece.
    renderizar(
      <SessaoProvider>
        <Routes>
          <Route path="/entrar" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <RotaProtegida>
                <PainelDoDiaDeTeste />
              </RotaProtegida>
            }
          />
        </Routes>
      </SessaoProvider>,
      { rota: '/entrar' },
    )

    await usuario.type(screen.getByLabelText(/e-mail/i), 'marina@example.com')
    await usuario.type(screen.getByLabelText(/senha/i), 'uma-senha-comprida-o-bastante')
    await usuario.click(screen.getByRole('button', { name: /entrar/i }))

    // a prova de que a fundacao inteira funciona: o token do corpo virou
    // header na chamada seguinte. Sem isso, tudo depois do login da 401
    await waitFor(() => expect(autorizacao).toBe(`Bearer ${jwt}`))
  })

  it('Criar conta leva para o cadastro', async () => {
    renderizarLogin()

    expect(screen.getByRole('link', { name: 'Criar conta' })).toHaveAttribute('href', '/cadastro')
  })

  it('a senha nao aparece na tela', async () => {
    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.type(screen.getByLabelText(/senha/i), 'segredo')
    expect(screen.getByLabelText(/senha/i)).toHaveAttribute('type', 'password')
  })

  describe('sessao expirada', () => {
    function IrParaEntrarExpirado() {
      const navegar = useNavigate()
      useEffect(() => {
        navegar('/entrar', { state: { expirou: true, de: { pathname: '/pacientes' } } })
      }, [navegar])
      return null
    }

    function rotas() {
      return (
        <SessaoProvider>
          <Routes>
            <Route path="/" element={<IrParaEntrarExpirado />} />
            <Route path="/entrar" element={<LoginPage />} />
            <Route path="/pacientes" element={<p>Pacientes</p>} />
          </Routes>
        </SessaoProvider>
      )
    }

    it('avisa que a sessao expirou', async () => {
      renderizar(rotas())

      expect(await screen.findByRole('status')).toHaveTextContent(
        'Sua sessão expirou. Entre novamente.',
      )
    })

    it('nao mostra o aviso quando nao expirou', () => {
      renderizarLogin()

      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    })

    it('depois de entrar volta para a rota em que estava', async () => {
      const jwt = [
        'eyJhbGciOiJIUzI1NiJ9',
        btoa(JSON.stringify({ sub: 'marina@example.com' })).replace(/=+$/, ''),
        'assinatura',
      ].join('.')
      servidor.use(http.post('/auth/login', () => HttpResponse.json({ token: jwt })))
      const usuario = userEvent.setup()
      renderizar(rotas())

      await usuario.type(await screen.findByLabelText(/e-mail/i), 'marina@example.com')
      await usuario.type(screen.getByLabelText(/senha/i), 'uma-senha-comprida-o-bastante')
      await usuario.click(screen.getByRole('button', { name: /entrar/i }))

      expect(await screen.findByText('Pacientes')).toBeInTheDocument()
    })
  })
})
