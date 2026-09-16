import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { RegistroDaSessao } from './RegistroDaSessao'

const ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
const SESSAO = {
  consultaId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
  dtConsulta: '2026-11-25T09:00:00',
  status: 'REALIZADA' as const,
  anotacoes: [],
}

describe('registro da anotacao da sessao', () => {
  it('manda o consultaId junto — a anotacao e DAQUELA sessao', async () => {
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.post(`/pacientes/${ID}/anotacoes`, async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<RegistroDaSessao pacienteId={ID} sessao={SESSAO} />)

    await usuario.type(screen.getByLabelText(/anotação/i), 'Trabalhamos a exposicao gradual.')
    await usuario.click(screen.getByRole('button', { name: /registrar/i }))

    expect(corpo.consultaId).toBe(SESSAO.consultaId)
    expect(corpo.anotacao).toBe('Trabalhamos a exposicao gradual.')
  })

  it('anotacao vazia nao envia, e diz o que fazer', async () => {
    let enviou = false
    servidor.use(
      http.post(`/pacientes/${ID}/anotacoes`, () => {
        enviou = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<RegistroDaSessao pacienteId={ID} sessao={SESSAO} />)

    await usuario.click(screen.getByRole('button', { name: /registrar/i }))

    // a mesma mensagem aparece DUAS vezes por design (spec 6.2): no <p> do
    // campo e no <li> do ResumoDeErros — mesma ambiguidade documentada em
    // LoginPage.test.tsx e DialogoDePaciente.test.tsx. `{ selector: 'p' }`
    // mira so o erro do campo. A mensagem tem acento de verdade
    // ("conteúdo", "anotação") — o regex sem acento do plano nunca casaria.
    expect(
      await screen.findByText(/informe o conteúdo da anotação/i, { selector: 'p' }),
    ).toBeInTheDocument()
    expect(enviou).toBe(false)
  })

  it('erro do servidor aparece, o botao nao morre em silencio', async () => {
    servidor.use(
      http.post(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(
          {
            status: 409,
            mensagem: 'Esta sessão é de outro paciente. A anotação não pode ser presa a ela.',
            erros: [],
          },
          { status: 409 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    renderizar(<RegistroDaSessao pacienteId={ID} sessao={SESSAO} />)

    await usuario.type(screen.getByLabelText(/anotação/i), 'Qualquer coisa.')
    await usuario.click(screen.getByRole('button', { name: /registrar/i }))

    // idem: "é" tem acento na mensagem real do servidor.
    const aviso = await screen.findByText(/é de outro paciente/i)
    expect(aviso).toHaveAttribute('role', 'alert')
  })
})
