import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { DialogoDeValor } from './DialogoDeValor'

const SESSAO = {
  consultaId: '11111111-1111-4111-8111-111111111111',
  dtConsulta: '2026-12-02T15:00:00',
  pacienteId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  pacienteNome: 'Bruna Teixeira',
  pacienteExcluido: false,
  valor: null,
  pagoEm: null,
}

const abrir = (aoFechar = vi.fn(), sessao = SESSAO) => {
  renderizar(<DialogoDeValor sessao={sessao} aoFechar={aoFechar} />)
  return aoFechar
}

describe('dialogo de valor', () => {
  it('salvar vazio pede o valor e nao envia', async () => {
    let enviou = false
    servidor.use(
      http.patch('/consultas/:id/valor', () => {
        enviou = true
        return HttpResponse.json({})
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Informe o valor da sessão.', { selector: 'p' })).toBeInTheDocument()
    expect(enviou).toBe(false)
  })

  it('recusa tres casas e recusa zero', async () => {
    const usuario = userEvent.setup()
    abrir()
    const campo = screen.getByLabelText(/^valor da sessão/i)

    await usuario.type(campo, '10.123')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(
      await screen.findByText('O valor da sessão deve ter no máximo duas casas decimais.', { selector: 'p' }),
    ).toBeInTheDocument()

    await usuario.clear(campo)
    await usuario.type(campo, '0')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(
      await screen.findByText('O valor da sessão deve ser maior que zero.', { selector: 'p' }),
    ).toBeInTheDocument()
  })

  it('valor valido manda PATCH com o numero e fecha', async () => {
    let corpo = ''
    servidor.use(
      http.patch('/consultas/:id/valor', async ({ request, params }) => {
        corpo = `${params.id} ${await request.text()}`
        return HttpResponse.json({})
      }),
    )
    const usuario = userEvent.setup()
    const aoFechar = abrir()

    await usuario.type(screen.getByLabelText(/^valor da sessão/i), '90.5')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    await vi.waitFor(() => expect(aoFechar).toHaveBeenCalled())
    expect(corpo).toBe(`${SESSAO.consultaId} {"valor":90.5}`)
  })

  it('409 sem campo aparece como alerta e 400 com campo vai para o campo', async () => {
    servidor.use(
      http.patch('/consultas/:id/valor', () =>
        HttpResponse.json(
          { status: 409, mensagem: 'Sessão já recebeu baixa.', erros: [] },
          { status: 409 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/^valor da sessão/i), '90')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))
    const aviso = await screen.findByText('Sessão já recebeu baixa.')
    expect(aviso).toHaveAttribute('role', 'alert')

    servidor.use(
      http.patch('/consultas/:id/valor', () =>
        HttpResponse.json(
          { status: 400, mensagem: 'Dados inválidos.', erros: [{ campo: 'valor', erro: 'Valor recusado pelo servidor.' }] },
          { status: 400 },
        ),
      ),
    )
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(await screen.findByText('Valor recusado pelo servidor.', { selector: 'p' })).toBeInTheDocument()
  })

  it('cancelar fecha sem requisicao', async () => {
    let enviou = false
    servidor.use(
      http.patch('/consultas/:id/valor', () => {
        enviou = true
        return HttpResponse.json({})
      }),
    )
    const usuario = userEvent.setup()
    const aoFechar = abrir()

    await usuario.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(aoFechar).toHaveBeenCalled()
    expect(enviou).toBe(false)
  })
})
