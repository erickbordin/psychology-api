import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComProvedores } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { LoginPage } from './LoginPage'
import { SessaoProvider } from './SessaoProvider'

function renderizarLogin() {
  return renderizarComProvedores(
    <SessaoProvider>
      <LoginPage />
    </SessaoProvider>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )
  })

  it('exige e-mail e senha antes de chamar a API', async () => {
    let chamou = false
    servidorDeTeste.use(
      http.post('/auth/login', () => {
        chamou = true
        return HttpResponse.json({ token: 'x' })
      }),
    )

    renderizarLogin()
    await userEvent.click(await screen.findByRole('button', { name: 'Entrar' }))

    // e-mail e senha estao vazios ao mesmo tempo, entao a mensagem aparece
    // uma vez sob cada campo — daí findAllByText em vez de findByText.
    expect(await screen.findAllByText('campo obrigatorio')).toHaveLength(2)
    expect(chamou).toBe(false)
  })

  it('mostra a mensagem do envelope quando a credencial e invalida', async () => {
    servidorDeTeste.use(
      http.post('/auth/login', () =>
        HttpResponse.json(
          { status: 401, mensagem: 'Usuario e/ou senha incorretos!', erros: [] },
          { status: 401 },
        ),
      ),
    )

    renderizarLogin()
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'errada')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Usuario e/ou senha incorretos!')).toBeInTheDocument()
  })

  it('marca o campo culpado quando o registro devolve 400 com erros', async () => {
    servidorDeTeste.use(
      http.post('/auth/registrar', () =>
        HttpResponse.json(
          {
            status: 400,
            mensagem: 'Erro de validação',
            erros: [{ campo: 'email', erro: 'formato de email invalido' }],
          },
          { status: 400 },
        ),
      ),
    )

    renderizarLogin()
    await userEvent.click(await screen.findByRole('button', { name: 'Criar conta' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Ana Moreira')
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana-arroba-teste')
    await userEvent.type(screen.getByLabelText('Senha'), 'senhaforte123')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))

    expect(await screen.findByText('formato de email invalido')).toBeInTheDocument()
  })

  it('entra com credencial valida', async () => {
    servidorDeTeste.use(http.post('/auth/login', () => HttpResponse.json({ token: 'token-bom' })))

    renderizarLogin()
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'senhaforte123')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    const { lerAcesso } = await import('../../api/sessaoEmMemoria')
    await waitFor(() => expect(lerAcesso()).toBe('token-bom'))
    expect(screen.queryByRole('heading', { name: 'Entrar' })).not.toBeInTheDocument()
  })

  it('Enter no ultimo campo envia, sem exigir o mouse', async () => {
    let chamou = false
    servidorDeTeste.use(
      http.post('/auth/login', () => {
        chamou = true
        return HttpResponse.json({ token: 'token-bom' })
      }),
    )

    renderizarLogin()
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'senhaforte123{Enter}')

    await waitFor(() => expect(chamou).toBe(true))
  })

  it('a marca de campo obrigatorio sai assim que o campo e preenchido', async () => {
    renderizarLogin()
    await userEvent.click(await screen.findByRole('button', { name: 'Entrar' }))
    expect(await screen.findAllByText('campo obrigatorio')).toHaveLength(2)

    await userEvent.type(screen.getByLabelText('E-mail'), 'ana@teste.com')

    expect(screen.getAllByText('campo obrigatorio')).toHaveLength(1)
  })

  it('desabilita o envio enquanto a chamada esta em voo, para nao cadastrar duas vezes', async () => {
    let liberar: () => void = () => {}
    const emVoo = new Promise<void>((resolve) => {
      liberar = resolve
    })
    servidorDeTeste.use(
      http.post('/auth/login', async () => {
        await emVoo
        return HttpResponse.json({ token: 'token-bom' })
      }),
    )

    renderizarLogin()
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'senhaforte123')
    const enviar = screen.getByRole('button', { name: 'Entrar' })
    await userEvent.click(enviar)

    await waitFor(() => expect(enviar).toBeDisabled())
    liberar()
  })

  it('avisa que o servidor esta fora do ar quando o login nao chega la, em vez de dizer credencial invalida', async () => {
    servidorDeTeste.use(http.post('/auth/login', () => HttpResponse.error()))

    renderizarLogin()
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'senhaforte123')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(
      await screen.findByText('Nao foi possivel conectar ao servidor. Tente novamente em instantes.'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/e\/ou senha/i)).not.toBeInTheDocument()
  })
})
