import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { SessaoProvider } from '../../sessao/SessaoProvider'
import { LoginPage } from '../login/LoginPage'
import { CadastroPage } from './CadastroPage'

const TERMOS = { versao: '2026-10-01', termos: 'Texto dos termos.', privacidade: 'Texto da privacidade.' }
const ROTULO_ACEITE = 'Li e aceito os Termos de Uso e a Política de Privacidade'
const SENHA = 'uma-senha-comprida-o-bastante'

const jwt = [
  'eyJhbGciOiJIUzI1NiJ9',
  btoa(JSON.stringify({ sub: 'marina@example.com' })).replace(/=+$/, ''),
  'assinatura',
].join('.')

const envelope = (status: number, mensagem: string, erros: { campo: string; erro: string }[] = []) =>
  HttpResponse.json({ status, mensagem, erros }, { status })

const termosNoAr = () => servidor.use(http.get('/termos/atual', () => HttpResponse.json(TERMOS)))

const montar = () =>
  renderizar(
    <SessaoProvider>
      <Routes>
        <Route path="/cadastro" element={<CadastroPage />} />
        <Route path="/entrar" element={<LoginPage />} />
        <Route path="/termos" element={<p>pagina dos termos</p>} />
        <Route path="/privacidade" element={<p>pagina da privacidade</p>} />
        <Route path="/" element={<p>Inicio</p>} />
      </Routes>
    </SessaoProvider>,
    { rota: '/cadastro' },
  )

async function preencher(usuario: ReturnType<typeof userEvent.setup>, aceitar = true) {
  await usuario.type(await screen.findByLabelText(/código de convite/i), 'convite-de-teste')
  await usuario.type(screen.getByLabelText(/nome/i), 'Marina Teste')
  await usuario.type(screen.getByLabelText(/e-mail/i), 'marina@example.com')
  await usuario.type(screen.getByLabelText(/^senha/i), SENHA)
  if (aceitar) await usuario.click(screen.getByLabelText(ROTULO_ACEITE))
}

describe('cadastro', () => {
  it('mostra o titulo, os campos, a caixa de aceite e os links', async () => {
    termosNoAr()
    montar()

    expect(screen.getByRole('heading', { name: 'Criar conta' })).toBeInTheDocument()
    expect(await screen.findByLabelText(/código de convite/i)).toHaveAttribute('autocomplete', 'off')
    expect(screen.getByLabelText(/nome/i)).toHaveAttribute('autocomplete', 'name')
    expect(screen.getByLabelText(/e-mail/i)).toHaveAttribute('autocomplete', 'email')
    expect(screen.getByLabelText(/^senha/i)).toHaveAttribute('autocomplete', 'new-password')
    expect(screen.getByLabelText(/^senha/i)).toHaveAttribute('type', 'password')
    expect(screen.getByLabelText(ROTULO_ACEITE)).not.toBeChecked()
    expect(screen.getByRole('link', { name: 'Termos de Uso' })).toHaveAttribute('href', '/termos')
    expect(screen.getByRole('link', { name: 'Política de Privacidade' })).toHaveAttribute(
      'href',
      '/privacidade',
    )
    expect(screen.getByRole('button', { name: 'Criar conta' })).toBeEnabled()
    expect(screen.getByRole('link', { name: 'Já tenho conta' })).toHaveAttribute('href', '/entrar')
  })

  it('os links dentro do rotulo nao marcam a caixa e levam para a mesma aba', async () => {
    termosNoAr()
    const usuario = userEvent.setup()
    montar()

    await screen.findByLabelText(ROTULO_ACEITE)
    const termos = screen.getByRole('link', { name: 'Termos de Uso' })
    expect(termos).not.toHaveAttribute('target')
    await usuario.click(termos)

    expect(await screen.findByText('pagina dos termos')).toBeInTheDocument()
  })

  it('clicar no link nao alterna a caixa de aceite', async () => {
    termosNoAr()
    const usuario = userEvent.setup()
    renderizar(
      <SessaoProvider>
        <CadastroPage />
      </SessaoProvider>,
      { rota: '/cadastro' },
    )

    const caixa = await screen.findByLabelText(ROTULO_ACEITE)
    await usuario.click(screen.getByRole('link', { name: 'Política de Privacidade' }))

    expect(caixa).not.toBeChecked()
  })

  it('Ja tenho conta leva para a entrada', async () => {
    termosNoAr()
    const usuario = userEvent.setup()
    montar()

    await usuario.click(await screen.findByRole('link', { name: 'Já tenho conta' }))

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('sem os termos publicados mostra o aviso e nao mostra o formulario', async () => {
    servidor.use(http.get('/termos/atual', () => envelope(404, 'Não encontrado.')))
    montar()

    expect(await screen.findByText('Cadastro ainda não está disponível.')).toHaveAttribute('role', 'status')
    expect(screen.queryByLabelText(/código de convite/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Criar conta' })).not.toBeInTheDocument()
  })

  it('campos vazios: nao envia e diz o que falta, com as mensagens do servidor', async () => {
    termosNoAr()
    let enviou = false
    servidor.use(
      http.post('/auth/registrar', () => {
        enviou = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByText('Informe o código de convite.', { selector: 'p.campo-erro' })).toBeInTheDocument()
    expect(screen.getByText('Informe o nome.', { selector: 'p.campo-erro' })).toBeInTheDocument()
    expect(screen.getByText('Informe um e-mail.', { selector: 'p.campo-erro' })).toBeInTheDocument()
    expect(screen.getByText('Informe uma senha.', { selector: 'p.campo-erro' })).toBeInTheDocument()
    expect(
      screen.getByText('Aceite os termos para criar a conta.', { selector: 'p.campo-erro' }),
    ).toBeInTheDocument()
    expect(enviou).toBe(false)
  })

  it('e-mail mal formado e senha curta sao recusados antes do servidor', async () => {
    termosNoAr()
    const usuario = userEvent.setup()
    montar()

    await usuario.type(await screen.findByLabelText(/e-mail/i), 'marina-arroba-nada')
    await usuario.tab()
    await usuario.type(screen.getByLabelText(/^senha/i), 'curta')
    await usuario.tab()

    expect(
      await screen.findByText('Informe um e-mail em formato válido.', { selector: 'p.campo-erro' }),
    ).toBeInTheDocument()
    expect(
      await screen.findByText('A senha precisa de pelo menos 10 caracteres.', { selector: 'p.campo-erro' }),
    ).toBeInTheDocument()
  })

  it('senha com mais de 64 caracteres ou mais de 72 bytes e recusada', async () => {
    termosNoAr()
    const usuario = userEvent.setup()
    montar()

    const senha = await screen.findByLabelText(/^senha/i)
    await usuario.click(senha)
    await usuario.paste('a'.repeat(65))
    await usuario.tab()
    expect(
      await screen.findByText('A senha deve ter no máximo 64 caracteres.', { selector: 'p.campo-erro' }),
    ).toBeInTheDocument()

    await usuario.clear(senha)
    await usuario.paste('é'.repeat(40))
    await usuario.tab()
    expect(
      await screen.findByText('A senha deve ter no máximo 64 caracteres.', { selector: 'p.campo-erro' }),
    ).toBeInTheDocument()
  })

  it('envia o corpo com convite, aceite e a versao dos termos', async () => {
    termosNoAr()
    let corpo: unknown = null
    servidor.use(
      http.post('/auth/registrar', async ({ request }) => {
        corpo = await request.json()
        return HttpResponse.json({ id: '3f9c2b7e-5d41-4a8e-9b6c-0e7d1a2f4c88' }, { status: 201 })
      }),
      http.post('/auth/login', () => HttpResponse.json({ token: jwt })),
    )
    const usuario = userEvent.setup()
    montar()

    await preencher(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))

    await waitFor(() => expect(corpo).not.toBeNull())
    expect(corpo).toEqual({
      codigoConvite: 'convite-de-teste',
      nome: 'Marina Teste',
      email: 'marina@example.com',
      senha: SENHA,
      aceiteTermos: true,
      versaoTermos: '2026-10-01',
    })
  })

  it('201 entra com o mesmo e-mail e senha e vai para o inicio', async () => {
    termosNoAr()
    let login: unknown = null
    servidor.use(
      http.post('/auth/registrar', () =>
        HttpResponse.json({ id: '3f9c2b7e-5d41-4a8e-9b6c-0e7d1a2f4c88' }, { status: 201 }),
      ),
      http.post('/auth/login', async ({ request }) => {
        login = await request.json()
        return HttpResponse.json({ token: jwt })
      }),
    )
    const usuario = userEvent.setup()
    montar()

    await preencher(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByText('Inicio')).toBeInTheDocument()
    expect(login).toEqual({ emailUsuario: 'marina@example.com', senha: SENHA })
  })

  it('mostra Criando… enquanto envia', async () => {
    termosNoAr()
    let liberar: () => void = () => {}
    const portao = new Promise<void>((resolver) => {
      liberar = resolver
    })
    servidor.use(
      http.post('/auth/registrar', async () => {
        await portao
        return HttpResponse.json({}, { status: 201 })
      }),
      http.post('/auth/login', () => HttpResponse.json({ token: jwt })),
    )
    const usuario = userEvent.setup()
    montar()

    await preencher(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByRole('button', { name: 'Criando…' })).toBeDisabled()
    liberar()
    expect(await screen.findByText('Inicio')).toBeInTheDocument()
  })

  it('403 mostra Codigo de convite invalido', async () => {
    termosNoAr()
    servidor.use(http.post('/auth/registrar', () => envelope(403, 'Código de convite inválido.')))
    const usuario = userEvent.setup()
    montar()

    await preencher(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByText('Código de convite inválido.')).toBeInTheDocument()
    expect(screen.queryByText('Inicio')).not.toBeInTheDocument()
  })

  it('409 de e-mail repetido mostra a mensagem do servidor como veio', async () => {
    termosNoAr()
    servidor.use(
      http.post('/auth/registrar', () =>
        envelope(409, 'Já existe um usuário cadastrado com este e-mail.'),
      ),
    )
    const usuario = userEvent.setup()
    montar()

    await preencher(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(
      await screen.findByText(/Já existe um usuário cadastrado com este e-mail\./),
    ).toBeInTheDocument()
  })

  it('400 do servidor marca o campo culpado', async () => {
    termosNoAr()
    servidor.use(
      http.post('/auth/registrar', () =>
        envelope(400, 'Erro de validação', [
          { campo: 'senha', erro: 'A senha é fraca demais.' },
        ]),
      ),
    )
    const usuario = userEvent.setup()
    montar()

    await preencher(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(
      await screen.findByText('A senha é fraca demais.', { selector: 'p.campo-erro' }),
    ).toBeInTheDocument()
  })

  it('409 de termos recarrega a versao e avisa; o proximo envio leva a nova versao', async () => {
    let versaoAtual = '2026-10-01'
    let leituras = 0
    const versoesEnviadas: string[] = []
    servidor.use(
      http.get('/termos/atual', () => {
        leituras += 1
        return HttpResponse.json({ ...TERMOS, versao: versaoAtual })
      }),
      http.post('/auth/registrar', async ({ request }) => {
        const { versaoTermos } = (await request.json()) as { versaoTermos: string }
        versoesEnviadas.push(versaoTermos)
        if (versaoTermos !== '2026-11-01') {
          versaoAtual = '2026-11-01'
          return envelope(409, 'Os termos mudaram. Leia a versão atual.')
        }
        return HttpResponse.json({}, { status: 201 })
      }),
      http.post('/auth/login', () => HttpResponse.json({ token: jwt })),
    )
    const usuario = userEvent.setup()
    montar()

    await preencher(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByText('Os termos mudaram. Leia a versão atual.')).toBeInTheDocument()
    await waitFor(() => expect(leituras).toBe(2))
    expect(screen.getByLabelText(ROTULO_ACEITE)).not.toBeChecked()

    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))
    expect(
      await screen.findByText('Aceite os termos para criar a conta.', { selector: 'p.campo-erro' }),
    ).toBeInTheDocument()
    expect(versoesEnviadas).toEqual(['2026-10-01'])

    await usuario.click(screen.getByLabelText(ROTULO_ACEITE))
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))
    expect(await screen.findByText('Inicio')).toBeInTheDocument()
    expect(versoesEnviadas).toEqual(['2026-10-01', '2026-11-01'])
  })

  it('503 mostra Cadastro ainda nao esta disponivel', async () => {
    termosNoAr()
    servidor.use(
      http.post('/auth/registrar', () => envelope(503, 'Cadastro ainda não está disponível.')),
    )
    const usuario = userEvent.setup()
    montar()

    await preencher(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByText('Cadastro ainda não está disponível.')).toBeInTheDocument()
  })
})
