import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { aoExpirar, api, definirToken } from './cliente'
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

  describe('prefixo da API', () => {
    afterEach(() => {
      vi.unstubAllEnvs()
      vi.resetModules()
    })

    it('antepoe VITE_API_URL ao caminho quando a variavel existe', async () => {
      vi.stubEnv('VITE_API_URL', '/api')
      vi.resetModules()
      const { api: apiComPrefixo } = await import('./cliente')
      servidor.use(http.get('/api/painel/hoje', () => HttpResponse.json({ prefixado: true })))

      await expect(apiComPrefixo('/painel/hoje')).resolves.toEqual({ prefixado: true })
    })
  })

  describe('renovacao da sessao', () => {
    afterEach(() => {
      definirToken(null)
      aoExpirar(null)
    })

    const naoAutorizado = () =>
      HttpResponse.json({ status: 401, mensagem: 'Sessao expirada.', erros: [] }, { status: 401 })

    it('401 faz um refresh, repete a chamada com o token novo e devolve o dado', async () => {
      definirToken('token-velho')
      let refreshes = 0
      const autorizacoes: Array<string | null> = []
      servidor.use(
        http.post('/auth/refresh', () => {
          refreshes++
          return HttpResponse.json({ token: 'token-novo' })
        }),
        http.get('/painel/hoje', ({ request }) => {
          const autorizacao = request.headers.get('Authorization')
          autorizacoes.push(autorizacao)
          return autorizacao === 'Bearer token-novo'
            ? HttpResponse.json({ sessoes: [1] })
            : naoAutorizado()
        }),
      )

      await expect(api('/painel/hoje')).resolves.toEqual({ sessoes: [1] })

      expect(refreshes).toBe(1)
      expect(autorizacoes).toEqual(['Bearer token-velho', 'Bearer token-novo'])
    })

    it('tres chamadas paralelas com 401 dividem um unico POST /auth/refresh', async () => {
      definirToken('token-velho')
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', async () => {
          refreshes++
          await new Promise((resolver) => setTimeout(resolver, 30))
          return HttpResponse.json({ token: 'token-novo' })
        }),
        http.get('/painel/:parte', ({ request, params }) =>
          request.headers.get('Authorization') === 'Bearer token-novo'
            ? HttpResponse.json({ parte: params.parte })
            : naoAutorizado(),
        ),
      )

      const resultados = await Promise.all([api('/painel/a'), api('/painel/b'), api('/painel/c')])

      expect(refreshes).toBe(1)
      expect(resultados).toEqual([{ parte: 'a' }, { parte: 'b' }, { parte: 'c' }])
    })

    it('repete uma vez so: segundo 401 depois da renovacao nao entra em laco', async () => {
      definirToken('token-velho')
      let refreshes = 0
      let chamadas = 0
      servidor.use(
        http.post('/auth/refresh', () => {
          refreshes++
          return HttpResponse.json({ token: 'token-novo' })
        }),
        http.get('/painel/hoje', () => {
          chamadas++
          return naoAutorizado()
        }),
      )

      await expect(api('/painel/hoje')).rejects.toMatchObject({ status: 401 })

      expect(refreshes).toBe(1)
      expect(chamadas).toBe(2)
    })

    it('refresh recusado chama aoExpirar uma vez e rejeita com o erro original', async () => {
      definirToken('token-velho')
      const aoSerExpirada = vi.fn()
      aoExpirar(aoSerExpirada)
      servidor.use(
        http.post('/auth/refresh', () => new HttpResponse(null, { status: 401 })),
        http.get('/painel/hoje', naoAutorizado),
      )

      const erro = await api('/painel/hoje').catch((e: unknown) => e)

      expect(erro).toBeInstanceOf(ErroDaApi)
      expect(erro).toMatchObject({ status: 401, mensagem: 'Sessao expirada.' })
      expect(aoSerExpirada).toHaveBeenCalledTimes(1)
    })

    it('varias chamadas que falham juntas disparam aoExpirar uma vez so', async () => {
      definirToken('token-velho')
      const aoSerExpirada = vi.fn()
      aoExpirar(aoSerExpirada)
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', async () => {
          refreshes++
          await new Promise((resolver) => setTimeout(resolver, 30))
          return new HttpResponse(null, { status: 401 })
        }),
        http.get('/painel/:parte', naoAutorizado),
      )

      const resultados = await Promise.allSettled([api('/painel/a'), api('/painel/b'), api('/painel/c')])

      expect(resultados.map((r) => r.status)).toEqual(['rejected', 'rejected', 'rejected'])
      expect(refreshes).toBe(1)
      expect(aoSerExpirada).toHaveBeenCalledTimes(1)
    })

    it('refresh que cai em 502 nao expira a sessao nem apaga o token', async () => {
      definirToken('token-velho')
      const aoSerExpirada = vi.fn()
      aoExpirar(aoSerExpirada)
      const autorizacoes: Array<string | null> = []
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', () => {
          refreshes++
          return new HttpResponse('Bad Gateway', { status: 502 })
        }),
        http.get('/painel/hoje', ({ request }) => {
          autorizacoes.push(request.headers.get('Authorization'))
          return naoAutorizado()
        }),
      )

      await expect(api('/painel/hoje')).rejects.toMatchObject({ status: 401 })
      await expect(api('/painel/hoje')).rejects.toMatchObject({ status: 401 })

      expect(aoSerExpirada).not.toHaveBeenCalled()
      expect(refreshes).toBe(2)
      expect(autorizacoes).toEqual(['Bearer token-velho', 'Bearer token-velho'])
    })

    it('401 tardio com token ja trocado repete sem novo refresh', async () => {
      definirToken('token-velho')
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', () => {
          refreshes++
          return HttpResponse.json({ token: 'token-novo' })
        }),
        http.get('/painel/lento', async ({ request }) => {
          if (request.headers.get('Authorization') === 'Bearer token-novo') {
            return HttpResponse.json({ ok: true })
          }
          await new Promise((resolver) => setTimeout(resolver, 60))
          return naoAutorizado()
        }),
        http.get('/painel/rapido', ({ request }) =>
          request.headers.get('Authorization') === 'Bearer token-novo'
            ? HttpResponse.json({ ok: true })
            : naoAutorizado(),
        ),
      )

      const resultados = await Promise.all([api('/painel/lento'), api('/painel/rapido')])

      expect(resultados).toEqual([{ ok: true }, { ok: true }])
      expect(refreshes).toBe(1)
    })

    it('401 em /auth/login nao tenta renovar', async () => {
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', () => {
          refreshes++
          return HttpResponse.json({ token: 'x' })
        }),
        http.post('/auth/login', naoAutorizado),
      )

      await expect(api('/auth/login', { method: 'POST' })).rejects.toMatchObject({ status: 401 })

      expect(refreshes).toBe(0)
    })

    it('401 em /descadastro/x nao tenta renovar', async () => {
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', () => {
          refreshes++
          return HttpResponse.json({ token: 'x' })
        }),
        http.get('/descadastro/x', naoAutorizado),
      )

      await expect(api('/descadastro/x')).rejects.toMatchObject({ status: 401 })

      expect(refreshes).toBe(0)
    })

    it('401 em /termos/x nao tenta renovar', async () => {
      let refreshes = 0
      servidor.use(
        http.post('/auth/refresh', () => {
          refreshes++
          return HttpResponse.json({ token: 'x' })
        }),
        http.get('/termos/x', naoAutorizado),
      )

      await expect(api('/termos/x')).rejects.toMatchObject({ status: 401 })

      expect(refreshes).toBe(0)
    })
  })
})
