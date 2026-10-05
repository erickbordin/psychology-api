import { ErroDaApi, type CampoComErro } from './erro'

/** Mensagem para quando a resposta de erro nao veio no envelope. Acontece: ha
 *  modos de falha do backend que escapam dele. O usuario nunca pode ver um
 *  corpo cru do Spring. */
const ERRO_SEM_ENVELOPE = 'Nao foi possivel completar a acao. Tente de novo.'

/**
 * O access token vive AQUI, em memoria do modulo — nunca em localStorage.
 * `POST /auth/login` devolve `{ token }` no CORPO, e o filtro do backend le
 * `Authorization: Bearer <token>`. Sem este header, toda chamada autenticada
 * responde 401, e o sintoma parece problema de sessao e nao de cliente.
 */
let tokenDeAcesso: string | null = null

const BASE = import.meta.env.VITE_API_URL ?? ''

const SEM_RENOVACAO = ['/auth/', '/descadastro/', '/termos/']

type ResultadoDaRenovacao = { token: string } | 'recusada' | 'indisponivel'

let renovacao: Promise<ResultadoDaRenovacao> | null = null
let expiracaoNotificada = false
let aoExpirarSessao: (() => void) | null = null

export function definirToken(token: string | null) {
  tokenDeAcesso = token
}

export function aoExpirar(funcao: (() => void) | null) {
  aoExpirarSessao = funcao
}

async function renovar(): Promise<ResultadoDaRenovacao> {
  expiracaoNotificada = false
  try {
    const resposta = await fetch(BASE + '/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    })
    if (resposta.status === 401 || resposta.status === 403) {
      definirToken(null)
      return 'recusada'
    }
    if (!resposta.ok) {
      return 'indisponivel'
    }
    const { token } = (await resposta.json()) as { token: string }
    definirToken(token)
    return { token }
  } catch {
    return 'indisponivel'
  } finally {
    renovacao = null
  }
}

function iniciarRenovacao(): Promise<ResultadoDaRenovacao> {
  renovacao ??= renovar()
  return renovacao
}

export async function renovarSessao(): Promise<string | null> {
  const resultado = await iniciarRenovacao()
  return typeof resultado === 'object' ? resultado.token : null
}

function executar(caminho: string, opcoes: RequestInit): Promise<Response> {
  return fetch(BASE + caminho, {
    ...opcoes,
    // o refresh vem em cookie httpOnly: sem isto ele nao acompanha a chamada
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(tokenDeAcesso ? { Authorization: `Bearer ${tokenDeAcesso}` } : {}),
      ...opcoes.headers,
    },
  })
}

export async function api<T>(caminho: string, opcoes: RequestInit = {}): Promise<T> {
  const tokenEnviado = tokenDeAcesso
  let resposta = await executar(caminho, opcoes)

  if (resposta.status === 401 && !SEM_RENOVACAO.some((prefixo) => caminho.startsWith(prefixo))) {
    if (tokenDeAcesso && tokenDeAcesso !== tokenEnviado) {
      resposta = await executar(caminho, opcoes)
    } else {
      const resultado = await iniciarRenovacao()
      if (typeof resultado === 'object') {
        resposta = await executar(caminho, opcoes)
      } else if (resultado === 'recusada' && !expiracaoNotificada) {
        expiracaoNotificada = true
        aoExpirarSessao?.()
      }
    }
  }

  if (!resposta.ok) {
    throw await lerErro(resposta)
  }

  if (resposta.status === 204) {
    return undefined as T
  }

  return (await resposta.json()) as T
}

async function lerErro(resposta: Response): Promise<ErroDaApi> {
  try {
    const corpo = (await resposta.json()) as {
      mensagem?: string
      erros?: CampoComErro[]
    }
    return new ErroDaApi(
      resposta.status,
      corpo.mensagem ?? ERRO_SEM_ENVELOPE,
      corpo.erros ?? [],
    )
  } catch {
    // corpo nao era JSON — 502 de proxy, HTML de erro, resposta vazia
    return new ErroDaApi(resposta.status, ERRO_SEM_ENVELOPE, [])
  }
}
