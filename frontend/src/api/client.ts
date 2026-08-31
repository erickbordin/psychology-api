import { ErroApi } from './erro'
import { gravarAcesso, lerAcesso } from './sessaoEmMemoria'
import type { EnvelopeDeErro, TokenResposta } from './tipos'

/**
 * Em desenvolvimento a base e vazia: o proxy do Vite espelha os caminhos da API,
 * o que torna tudo same-origin e faz o cookie Path=/auth continuar valendo.
 * Em producao a variavel VITE_API_URL aponta para a origem da API.
 */
const BASE = import.meta.env.VITE_API_URL ?? ''

/**
 * UMA renovacao por vez, compartilhada por todas as chamadas que tomaram 401
 * juntas. A API rotaciona o refresh a cada uso e revoga a cadeia inteira quando
 * ve um refresh reapresentado — rotacoes concorrentes seriam lidas como token
 * roubado e derrubariam a sessao de um usuario legitimo.
 */
let renovacaoEmVoo: Promise<boolean> | null = null

async function envelopeDe(resposta: Response): Promise<EnvelopeDeErro> {
  let bruto = ''
  try {
    bruto = await resposta.text()
  } catch {
    // conexao caiu no meio da leitura do corpo: segue sem texto, cai no sintetico abaixo
  }

  try {
    const corpo = JSON.parse(bruto)
    if (corpo && typeof corpo.status === 'number' && typeof corpo.mensagem === 'string') {
      return corpo as EnvelopeDeErro
    }
    // corpo json valido mas que nao e o envelope — ex.: uma string json pura,
    // quando a API responde so com a mensagem entre aspas em vez do envelope
    if (typeof corpo === 'string' && corpo) {
      return { status: resposta.status, mensagem: corpo, erros: [] }
    }
  } catch {
    // corpo nao e json: cai no texto puro (se houver) ou no envelope sintetico abaixo
  }

  if (bruto) {
    return { status: resposta.status, mensagem: bruto, erros: [] }
  }

  return { status: resposta.status, mensagem: `Falha na requisição (${resposta.status})`, erros: [] }
}

function disparar(caminho: string, opcoes: RequestInit): Promise<Response> {
  const acesso = lerAcesso()
  return fetch(`${BASE}${caminho}`, {
    ...opcoes,
    credentials: 'include',
    headers: {
      ...(opcoes.body ? { 'Content-Type': 'application/json' } : {}),
      ...(acesso ? { Authorization: `Bearer ${acesso}` } : {}),
      ...opcoes.headers,
    },
  })
}

async function renovar(): Promise<boolean> {
  let resposta: Response
  try {
    resposta = await fetch(`${BASE}/auth/refresh`, { method: 'POST', credentials: 'include' })
  } catch {
    gravarAcesso(null)
    return false
  }

  if (!resposta.ok) {
    gravarAcesso(null)
    return false
  }

  try {
    const { token } = (await resposta.json()) as TokenResposta
    gravarAcesso(token)
    return true
  } catch {
    gravarAcesso(null)
    return false
  }
}

/**
 * Compartilha a MESMA promise entre todas as chamadas concorrentes que
 * tomaram 401 ao mesmo tempo — a primeira dispara `renovar()`, as demais so
 * aguardam o resultado. So depois que a promise resolve (`.finally`) e que o
 * proximo 401 pode iniciar uma nova renovacao.
 */
function renovarUmaVez(): Promise<boolean> {
  renovacaoEmVoo ??= renovar().finally(() => {
    renovacaoEmVoo = null
  })
  return renovacaoEmVoo
}

async function corpoDe<T>(resposta: Response): Promise<T> {
  if (resposta.status === 204) {
    return undefined as T
  }
  return (await resposta.json()) as T
}

export async function pedir<T>(caminho: string, opcoes: RequestInit = {}): Promise<T> {
  let resposta: Response
  try {
    resposta = await disparar(caminho, opcoes)
  } catch {
    throw new ErroApi({ status: 0, mensagem: 'Nao foi possivel conectar ao servidor', erros: [] })
  }

  // /auth nunca dispara renovacao: um 401 do proprio /auth/login ou
  // /auth/refresh e credencial invalida ou sessao morta, nao token expirado —
  // tentar renovar aqui e o que criaria o loop de renovacao renovando renovacao.
  const podeRenovar = resposta.status === 401 && !caminho.startsWith('/auth')
  if (podeRenovar && (await renovarUmaVez())) {
    try {
      resposta = await disparar(caminho, opcoes)
    } catch {
      throw new ErroApi({ status: 0, mensagem: 'Nao foi possivel conectar ao servidor', erros: [] })
    }
  }

  if (!resposta.ok) {
    throw new ErroApi(await envelopeDe(resposta))
  }

  try {
    return await corpoDe<T>(resposta)
  } catch {
    throw new ErroApi({
      status: resposta.status,
      mensagem: 'Falha ao interpretar a resposta do servidor',
      erros: [],
    })
  }
}

/** Renovação do boot da aplicação, antes de qualquer tela. */
export function renovarNoBoot(): Promise<boolean> {
  return renovarUmaVez()
}
