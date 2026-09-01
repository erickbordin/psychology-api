import { screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { RouteObject } from 'react-router-dom'

import { reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComRotas } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { InicioPage } from './InicioPage'

const ROTAS: RouteObject[] = [{ path: '/', element: <InicioPage /> }]

/** Um JWT de mentira, so com o `sub`: e de la que sai o nome da saudacao. */
function tokenCom(email: string): string {
  const payload = btoa(JSON.stringify({ sub: email }))
  return `cabecalho.${payload}.assinatura`
}

function paciente(id: string) {
  return {
    idPaciente: id,
    idUsuario: 'u-1',
    nome: `Paciente ${id}`,
    email: null,
    telefone: null,
    dataNascimento: '1991-04-12',
    createdAt: '2026-03-11T10:00:00',
  }
}

function consulta(id: string) {
  return {
    id,
    pacienteId: 'p-1',
    pacienteNome: 'Ana Moreira',
    dtConsulta: '2026-09-02T15:00:00',
    status: 'AGENDADA',
    createdAt: '2026-08-19T10:00:00',
    serieId: null,
  }
}

describe('InicioPage', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())

  it('conta as consultas de hoje e os pacientes cadastrados', async () => {
    const intervalos: string[] = []
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ token: tokenCom('ana.moreira@teste.com') }),
      ),
      http.get('/consultas', ({ request }) => {
        const busca = new URL(request.url).searchParams
        intervalos.push(`${busca.get('de')}..${busca.get('ate')}`)
        return HttpResponse.json([consulta('c1'), consulta('c2')])
      }),
      http.get('/pacientes', () =>
        HttpResponse.json([paciente('p-1'), paciente('p-2'), paciente('p-3')]),
      ),
    )

    renderizarComRotas(ROTAS, '/')

    // o nome sai do `sub` do token, que e o unico lugar onde a API o entrega
    expect(await screen.findByRole('heading', { name: 'Olá, Ana Moreira' })).toBeInTheDocument()
    expect(await screen.findByText('2')).toBeInTheDocument()
    expect(await screen.findByText('3')).toBeInTheDocument()

    const hoje = new Date()
    const iso = [
      hoje.getFullYear(),
      String(hoje.getMonth() + 1).padStart(2, '0'),
      String(hoje.getDate()).padStart(2, '0'),
    ].join('-')
    expect(intervalos).toEqual([`${iso}..${iso}`])
  })

  /**
   * O painel fica aberto em tela cheia num consultorio: enquanto as contagens
   * nao chegam, ele mostra um travessao — nunca um zero, que seria lido como
   * "nao tenho consulta hoje".
   */
  it('sem resposta ainda, nao inventa zero', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
      http.get('/consultas', () => HttpResponse.json([])),
      http.get('/pacientes', () => HttpResponse.json([])),
    )

    renderizarComRotas(ROTAS, '/')

    expect(await screen.findByRole('heading', { name: 'Olá' })).toBeInTheDocument()
    expect(screen.getAllByText('—')).toHaveLength(2)
  })
})
