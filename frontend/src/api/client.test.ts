import { HttpResponse, http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { servidorDeTeste } from '../teste/servidor'
import { ErroApi } from './erro'
import { pedir } from './client'
import { gravarAcesso, lerAcesso, reiniciarSessaoEmMemoria } from './sessaoEmMemoria'

describe('cliente HTTP', () => {
  it('devolve o corpo json em caso de sucesso', async () => {
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.json([{ nome: 'Ana' }])))

    await expect(pedir<{ nome: string }[]>('/pacientes')).resolves.toEqual([{ nome: 'Ana' }])
  })

  it('transforma o envelope de 400 em ErroApi com os campos', async () => {
    servidorDeTeste.use(
      http.post('/pacientes', () =>
        HttpResponse.json(
          {
            status: 400,
            mensagem: 'Erro de validação',
            erros: [{ campo: 'nome', erro: 'campo obrigatorio' }],
          },
          { status: 400 },
        ),
      ),
    )

    const erro = (await pedir('/pacientes', { method: 'POST' }).catch((e) => e)) as ErroApi

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(400)
    expect(erro.erros).toEqual([{ campo: 'nome', erro: 'campo obrigatorio' }])
    expect(erro.mensagemDoCampo('nome')).toBe('campo obrigatorio')
  })

  it('transforma o 404 em ErroApi com lista de erros vazia', async () => {
    servidorDeTeste.use(
      http.get('/pacientes/xyz', () =>
        HttpResponse.json(
          { status: 404, mensagem: 'Paciente nao encontrado', erros: [] },
          { status: 404 },
        ),
      ),
    )

    const erro = (await pedir('/pacientes/xyz').catch((e) => e)) as ErroApi

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(404)
    expect(erro.erros).toEqual([])
  })

  it('nao estoura quando o corpo de erro nao e json', async () => {
    servidorDeTeste.use(
      http.get('/pacientes', () => new HttpResponse('falha no gateway', { status: 502 })),
    )

    const erro = (await pedir('/pacientes').catch((e) => e)) as ErroApi

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(502)
    expect(erro.mensagem).not.toBe('')
  })

  it('devolve undefined em resposta 204 sem corpo', async () => {
    servidorDeTeste.use(http.post('/auth/logout', () => new HttpResponse(null, { status: 204 })))

    await expect(pedir<void>('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('transforma falha de rede em ErroApi com status 0', async () => {
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.error()))

    const erro = (await pedir('/pacientes').catch((e) => e)) as ErroApi

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(0)
    expect(erro.mensagem).not.toBe('')
  })

  it('preserva a mensagem quando o corpo de erro e uma string simples da API', async () => {
    servidorDeTeste.use(
      http.post('/pacientes', () => new HttpResponse('E-mail já cadastrado', { status: 409 })),
    )

    const erro = (await pedir('/pacientes', { method: 'POST' }).catch((e) => e)) as ErroApi

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(409)
    expect(erro.mensagem).toBe('E-mail já cadastrado')
  })

  it('transforma falha na leitura do corpo de erro em ErroApi', async () => {
    const corpoQueFalha = new ReadableStream({
      start(controller) {
        controller.error(new Error('conexao resetada no meio da resposta'))
      },
    })

    servidorDeTeste.use(
      http.get('/pacientes', () => new HttpResponse(corpoQueFalha, { status: 500 })),
    )

    const erro = (await pedir('/pacientes').catch((e) => e)) as ErroApi

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(500)
  })
})

describe('renovacao de sessao', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())
  afterEach(() => vi.restoreAllMocks())

  it('envia o access token quando existe', async () => {
    gravarAcesso('token-abc')
    const cabecalhos: string[] = []
    servidorDeTeste.use(
      http.get('/pacientes', ({ request }) => {
        cabecalhos.push(request.headers.get('Authorization') ?? '')
        return HttpResponse.json([])
      }),
    )

    await pedir('/pacientes')

    expect(cabecalhos).toEqual(['Bearer token-abc'])
  })

  it('renova em 401 e repete a chamada original', async () => {
    gravarAcesso('token-velho')
    let tentativas = 0

    servidorDeTeste.use(
      http.get('/pacientes', ({ request }) => {
        tentativas += 1
        if (request.headers.get('Authorization') === 'Bearer token-velho') {
          return HttpResponse.json({ status: 401, mensagem: 'expirado', erros: [] }, { status: 401 })
        }
        return HttpResponse.json([{ nome: 'Ana' }])
      }),
      http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-novo' })),
    )

    await expect(pedir('/pacientes')).resolves.toEqual([{ nome: 'Ana' }])
    expect(tentativas).toBe(2)
    expect(lerAcesso()).toBe('token-novo')
  })

  it('seis chamadas simultaneas em 401 produzem UMA renovacao', async () => {
    gravarAcesso('token-velho')
    let renovacoes = 0

    servidorDeTeste.use(
      http.get('/pacientes', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer token-velho'
          ? HttpResponse.json({ status: 401, mensagem: 'expirado', erros: [] }, { status: 401 })
          : HttpResponse.json([]),
      ),
      http.post('/auth/refresh', () => {
        renovacoes += 1
        return HttpResponse.json({ token: 'token-novo' })
      }),
    )

    await Promise.all(Array.from({ length: 6 }, () => pedir('/pacientes')))

    expect(renovacoes).toBe(1)
  })

  it('401 na renovacao encerra a sessao e propaga o erro', async () => {
    gravarAcesso('token-velho')
    servidorDeTeste.use(
      http.get('/pacientes', () =>
        HttpResponse.json({ status: 401, mensagem: 'expirado', erros: [] }, { status: 401 }),
      ),
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sessao invalida', erros: [] }, { status: 401 }),
      ),
    )

    const erro = (await pedir('/pacientes').catch((e) => e)) as ErroApi

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(401)
    expect(lerAcesso()).toBeNull()
  })

  it('401 vindo de /auth nao dispara renovacao', async () => {
    let renovacoes = 0
    servidorDeTeste.use(
      http.post('/auth/login', () =>
        HttpResponse.json({ status: 401, mensagem: 'credencial invalida', erros: [] }, { status: 401 }),
      ),
      http.post('/auth/refresh', () => {
        renovacoes += 1
        return HttpResponse.json({ token: 'nao-deveria' })
      }),
    )

    await pedir('/auth/login', { method: 'POST', body: '{}' }).catch(() => undefined)

    expect(renovacoes).toBe(0)
  })
})
