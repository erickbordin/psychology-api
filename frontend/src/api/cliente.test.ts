import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { api, definirToken } from './cliente'
import { ErroDaApi } from './erro'
import { servidor } from '../teste/servidor'

describe('cliente', () => {
  it('devolve o corpo quando da certo', async () => {
    servidor.use(http.get('/painel/hoje', () => HttpResponse.json({ sessoes: [] })))

    await expect(api('/painel/hoje')).resolves.toEqual({ sessoes: [] })
  })

  it('transforma o envelope de erro em ErroDaApi', async () => {
    servidor.use(
      http.post('/auth/login', () =>
        HttpResponse.json(
          { status: 401, mensagem: 'E-mail ou senha incorretos.', erros: [] },
          { status: 401 },
        ),
      ),
    )

    await expect(api('/auth/login', { method: 'POST' })).rejects.toBeInstanceOf(ErroDaApi)
  })

  it('sobrevive a resposta de erro que NAO vem no envelope', async () => {
    // acontece de verdade: ha modos de falha do backend que escapam do
    // ErroRespostaDTO (laudo do conjunto, I1). O cliente nao pode quebrar por
    // isso — tem que virar um ErroDaApi com mensagem generica
    servidor.use(http.get('/painel/hoje', () => new HttpResponse('502 Bad Gateway', { status: 502 })))

    await expect(api('/painel/hoje')).rejects.toBeInstanceOf(ErroDaApi)
  })

  it('manda o token como Authorization: Bearer depois de definido', async () => {
    definirToken('um-jwt-qualquer')
    let recebido: string | null = null
    servidor.use(
      http.get('/painel/hoje', ({ request }) => {
        recebido = request.headers.get('Authorization')
        return HttpResponse.json({})
      }),
    )

    await api('/painel/hoje')

    // sem este header o backend responde 401 em TUDO que nao e /auth
    expect(recebido).toBe('Bearer um-jwt-qualquer')
    definirToken(null)
  })

  it('devolve undefined em 204, sem tentar parsear corpo vazio', async () => {
    servidor.use(http.post('/auth/logout', () => new HttpResponse(null, { status: 204 })))

    await expect(api('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })
})
