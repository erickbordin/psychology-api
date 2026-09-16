import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { PacientesPage } from './PacientesPage'

const HELENA = {
  idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  nome: 'Helena Vaz',
  email: 'helena@example.com',
  telefone: '51998112345',
  dataNascimento: '1992-03-14',
  valorSessao: 180.0,
  canalAviso: 'NENHUM',
  consentimentoAvisoEm: null,
  createdAt: '2026-03-02T10:00:00',
}

const RUI = { ...HELENA, idPaciente: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', nome: 'Rui Sampaio' }

const comPacientes = (lista: unknown[] = [HELENA, RUI]) =>
  servidor.use(http.get('/pacientes', () => HttpResponse.json(lista)))

describe('tela de pacientes', () => {
  it('lista quem esta cadastrado', async () => {
    comPacientes()
    renderizar(<PacientesPage />)

    expect(await screen.findByText('Helena Vaz')).toBeInTheDocument()
    expect(screen.getByText('Rui Sampaio')).toBeInTheDocument()
  })

  it('busca manda o termo para a API', async () => {
    let recebido: string | null = null
    servidor.use(
      http.get('/pacientes', ({ request }) => {
        recebido = new URL(request.url).searchParams.get('nome')
        return HttpResponse.json([HELENA])
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<PacientesPage />)

    await usuario.type(await screen.findByLabelText(/buscar/i), 'Helena')

    // NAO ha debounce: a tela dispara uma requisicao por tecla, e o
    // TanStack cacheia por queryKey. Esperar o efeito (e nao cravar um tempo)
    // vale de qualquer forma — e o que torna o teste indiferente a essa
    // decisao, se ela mudar.
    expect(await screen.findByText('Helena Vaz')).toBeInTheDocument()
    await screen.findByText('Helena Vaz')
    expect(recebido).toBe('Helena')
  })

  it('dia 1 do psicologo: diz o que fazer, nao fica em branco', async () => {
    comPacientes([])
    renderizar(<PacientesPage />)

    expect(await screen.findByText(/nenhum paciente/i)).toBeInTheDocument()
  })

  it('busca sem resultado nao finge que o cadastro esta vazio', async () => {
    const usuario = userEvent.setup()
    comPacientes([])
    renderizar(<PacientesPage />)

    await usuario.type(await screen.findByLabelText(/buscar/i), 'Ninguem')

    expect(await screen.findByText(/nenhum paciente com esse nome/i)).toBeInTheDocument()
  })

  it('cada paciente leva para a ficha dele', async () => {
    comPacientes()
    renderizar(<PacientesPage />)

    const link = await screen.findByRole('link', { name: /helena vaz/i })
    expect(link).toHaveAttribute('href', `/pacientes/${HELENA.idPaciente}`)
  })
})
