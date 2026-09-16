import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { DialogoDePaciente } from './DialogoDePaciente'

const abrir = (props = {}) =>
  renderizar(<DialogoDePaciente aberto aoFechar={vi.fn()} {...props} />)

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

  it('canal EMAIL sem e-mail e recusado ANTES do envio — 5.4', async () => {
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.selectOptions(screen.getByLabelText(/canal/i), 'EMAIL')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    // mesma ambiguidade de findByText documentada em LoginPage.test.tsx —
    // `{ selector: 'p' }` mira so o erro do campo, nao o <li> do resumo.
    expect(
      await screen.findByText('Informe o e-mail do paciente para avisar por e-mail.', {
        selector: 'p',
      }),
    ).toBeInTheDocument()
  })

  it('canal NENHUM sem e-mail passa — e-mail continua opcional', async () => {
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

    expect(corpo.nome).toBe('Helena Vaz')
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
    abrir({
      paciente: {
        idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        nome: 'Helena Vaz',
        email: null,
        telefone: null,
        dataNascimento: null,
        valorSessao: 180.0,
        canalAviso: 'NENHUM',
        consentimentoAvisoEm: null,
      },
    })

    expect(screen.getByLabelText(/nome/i)).toHaveValue('Helena Vaz')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(metodo).toBe('PUT')
  })
})
