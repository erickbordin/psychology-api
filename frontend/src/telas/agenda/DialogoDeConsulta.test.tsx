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
      http.get('/pacientes', () =>
        HttpResponse.json([
          { idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', nome: 'Paciente de teste' },
        ]),
      ),
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

    // o <select> ja existe no DOM antes da lista de pacientes chegar —
    // esperar so pelo rotulo selecionaria uma option que ainda nao existe.
    await screen.findByRole('option', { name: 'Paciente de teste' })
    await usuario.selectOptions(screen.getByLabelText(/paciente/i), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
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
      http.get('/pacientes', () =>
        HttpResponse.json([
          { idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', nome: 'Paciente de teste' },
        ]),
      ),
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

    // o <select> ja existe no DOM antes da lista de pacientes chegar —
    // esperar so pelo rotulo selecionaria uma option que ainda nao existe.
    await screen.findByRole('option', { name: 'Paciente de teste' })
    await usuario.selectOptions(
      screen.getByLabelText(/paciente/i),
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

  it('o select traz os pacientes cadastrados, nao um id de teste', async () => {
    servidor.use(
      http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })),
      http.get('/pacientes', () =>
        HttpResponse.json([
          { idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', nome: 'Helena Vaz' },
          { idPaciente: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', nome: 'Rui Sampaio' },
        ]),
      ),
    )
    abrir()

    expect(await screen.findByRole('option', { name: 'Helena Vaz' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Rui Sampaio' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /paciente de teste/i })).not.toBeInTheDocument()
  })

  it('lista de pacientes que falha avisa, em vez de parecer cadastro vazio', async () => {
    // Um select vazio por ERRO de rede e um select vazio por cadastro vazio
    // sao visualmente iguais e significam coisas opostas. Sem este aviso, o
    // psicologo conclui que perdeu os pacientes.
    servidor.use(
      http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })),
      http.get('/pacientes', () =>
        HttpResponse.json({ status: 401, mensagem: 'Sessao expirada.', erros: [] }, { status: 401 }),
      ),
    )
    abrir()

    const aviso = await screen.findByText(/nao foi possivel carregar os pacientes/i)
    expect(aviso).toHaveAttribute('role', 'alert')
    expect(screen.queryByText(/cadastre um paciente/i)).not.toBeInTheDocument()
  })

  it('sem paciente cadastrado, o dialogo diz o que fazer em vez de oferecer lista vazia', async () => {
    servidor.use(
      http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })),
      http.get('/pacientes', () => HttpResponse.json([])),
    )
    abrir()

    expect(await screen.findByText(/cadastre um paciente/i)).toBeInTheDocument()
  })
})
