import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { DialogoDePaciente } from './DialogoDePaciente'

const abrir = (props = {}) =>
  renderizar(<DialogoDePaciente aberto aoFechar={vi.fn()} {...props} />)

const CAIXA = 'Paciente autorizou receber lembretes por e-mail'

const PACIENTE = {
  idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  nome: 'Helena Vaz',
  email: null,
  telefone: null,
  dataNascimento: null,
  valorSessao: 180.0,
  canalAviso: 'NENHUM',
  consentimentoAvisoEm: null,
  descadastradoEm: null,
}

describe('dialogo de paciente', () => {
  it('nome vazio nao envia, e o erro diz o que fazer', async () => {
    let enviou = false
    servidor.use(
      http.post('/pacientes', () => {
        enviou = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    // a mesma mensagem aparece DUAS vezes por design (spec 6.2): no <p> do
    // Campo e no <li> do ResumoDeErros — mesma ambiguidade documentada em
    // LoginPage.test.tsx. `{ selector: 'p' }` mira so o erro do campo.
    expect(
      await screen.findByText('Informe o nome do paciente.', { selector: 'p' }),
    ).toBeInTheDocument()
    expect(enviou).toBe(false)
  })

  it('caixa marcada sem e-mail e recusada ANTES do envio — 5.4', async () => {
    let enviou = false
    servidor.use(
      http.post('/pacientes', () => {
        enviou = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.click(screen.getByLabelText(CAIXA))
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(
      await screen.findByText('Informe o e-mail do paciente para avisar por e-mail.', {
        selector: 'p',
      }),
    ).toBeInTheDocument()
    expect(enviou).toBe(false)
  })

  it('caixa desmarcada sem e-mail passa e manda NENHUM', async () => {
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.post('/pacientes', async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    await waitFor(() => expect(corpo.nome).toBe('Helena Vaz'))
    expect(corpo.canalAviso).toBe('NENHUM')
  })

  it('caixa marcada com e-mail manda EMAIL', async () => {
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.post('/pacientes', async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.type(screen.getByLabelText(/e-mail/i, { selector: 'input[type="email"]' }), 'helena@exemplo.com')
    await usuario.click(screen.getByLabelText(CAIXA))
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    await waitFor(() => expect(corpo.canalAviso).toBe('EMAIL'))
  })

  it('paciente com EMAIL e consentimento abre com a caixa marcada', () => {
    abrir({ paciente: { ...PACIENTE, email: 'helena@exemplo.com', canalAviso: 'EMAIL', consentimentoAvisoEm: '2026-10-01T09:00:00' } })

    expect(screen.getByLabelText(CAIXA)).toBeChecked()
  })

  it('paciente legado com EMAIL e sem data de consentimento abre com a caixa DESMARCADA', () => {
    abrir({ paciente: { ...PACIENTE, email: 'helena@exemplo.com', canalAviso: 'EMAIL', consentimentoAvisoEm: null } })

    expect(screen.getByLabelText(CAIXA)).not.toBeChecked()
  })

  it('paciente com descadastradoEm mostra o aviso com a data', () => {
    abrir({ paciente: { ...PACIENTE, descadastradoEm: '2026-09-15T10:30:00' } })

    expect(
      screen.getByText(
        'Este paciente parou de receber lembretes pelo link do e-mail em 15/09/2026. Marque só se ele autorizou de novo.',
      ),
    ).toBeInTheDocument()
  })

  it('paciente sem descadastro nao mostra o aviso', () => {
    abrir({ paciente: PACIENTE })

    expect(screen.queryByText(/parou de receber lembretes/)).not.toBeInTheDocument()
  })

  it('erro do servidor sem campo culpado aparece, o botao nao morre em silencio', async () => {
    servidor.use(
      http.post('/pacientes', () =>
        HttpResponse.json(
          { status: 409, mensagem: 'Ja existe um paciente com esse e-mail.', erros: [] },
          { status: 409 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    const aviso = await screen.findByText(/ja existe um paciente com esse e-mail/i)
    expect(aviso).toHaveAttribute('role', 'alert')
  })

  it('valor com tres casas e recusado ANTES do envio — a 6.3 vale nos dois lados', async () => {
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.type(screen.getByLabelText(/valor/i), '180.005')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(
      await screen.findByText('O valor da sessão deve ter no máximo duas casas decimais.', {
        selector: 'p',
      }),
    ).toBeInTheDocument()
  })

  it('editar abre com os campos preenchidos e manda PUT', async () => {
    let metodo = ''
    servidor.use(
      http.put('/pacientes/:id', ({ request }) => {
        metodo = request.method
        return HttpResponse.json({}, { status: 200 })
      }),
    )
    const usuario = userEvent.setup()
    abrir({ paciente: PACIENTE })

    expect(screen.getByLabelText(/nome/i)).toHaveValue('Helena Vaz')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(metodo).toBe('PUT')
  })
})
