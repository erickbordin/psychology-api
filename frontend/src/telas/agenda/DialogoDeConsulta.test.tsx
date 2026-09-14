import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { DialogoDeConsulta } from './DialogoDeConsulta'

const abrir = (props = {}) =>
  renderizar(
    <DialogoDeConsulta
      aberto
      dtInicial="2026-12-02T14:00:00"
      aoFechar={vi.fn()}
      {...props}
    />,
  )

describe('dialogo de consulta', () => {
  it('avisa do conflito ANTES do envio, dizendo QUEM ocupa', async () => {
    servidor.use(
      http.get('/consultas/conflito', () =>
        HttpResponse.json({
          livre: false,
          pacienteNome: 'Otavio Lins',
          consultaId: '33333333-3333-3333-3333-333333333333',
        }),
      ),
    )
    abrir()

    // o aviso aparece sem o usuario ter clicado em salvar
    expect(await screen.findByText(/otavio lins/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /salvar/i })).toBeEnabled()
  })

  it('horario livre nao mostra aviso nenhum', async () => {
    servidor.use(
      http.get('/consultas/conflito', () =>
        HttpResponse.json({ livre: true, pacienteNome: null, consultaId: null }),
      ),
    )
    abrir()

    expect(await screen.findByLabelText(/data e hora/i)).toBeInTheDocument()
    expect(screen.queryByText(/ja esta ocupado/i)).not.toBeInTheDocument()
  })

  it('manda a data no formato que a API aceita, sem offset', async () => {
    servidor.use(
      http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })),
    )
    let corpo: { dtConsulta?: string } = {}
    servidor.use(
      http.post('/consultas', async ({ request }) => {
        corpo = (await request.json()) as { dtConsulta?: string }
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.selectOptions(await screen.findByLabelText(/paciente/i), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(corpo.dtConsulta).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)
    expect(corpo.dtConsulta).not.toContain('Z')
  })

  it('erro sem campo culpado aparece, em vez de o botao morrer em silencio', async () => {
    // Achado pelo e2e rodando contra a API REAL: o POST devolvia 404
    // "Paciente nao encontrado" e o dialogo nao mostrava nada. Erro sem
    // `campo` nao entra em campo nenhum, entao precisa de lugar proprio —
    // a 6.2 chama isso de "botao nunca morre em silencio", e era o defeito
    // que o redesenho existia para matar.
    servidor.use(
      http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })),
    )
    servidor.use(
      http.post('/consultas', () =>
        HttpResponse.json(
          { status: 404, mensagem: 'Paciente nao encontrado.', erros: [] },
          { status: 404 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.selectOptions(
      await screen.findByLabelText(/paciente/i),
      'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    )
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    // Consulta pelo TEXTO e nao por `role="alert"`: ha duas regioes vivas no
    // formulario, e as duas estao certas. O `ResumoDeErros` mantem a dele
    // sempre no DOM, ainda que vazia, porque leitor de tela so anuncia
    // conteudo que entra numa regiao que JA existia. Buscar por role casaria
    // nas duas; buscar pelo texto e depois afirmar o role prova as duas
    // coisas que importam — a mensagem esta na tela E e anunciada.
    const aviso = await screen.findByText(/paciente nao encontrado/i)
    expect(aviso).toHaveAttribute('role', 'alert')
  })
})
