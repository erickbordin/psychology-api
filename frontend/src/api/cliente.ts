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
 *
 * Em memoria significa que recarregar a aba perde o token — e o `/auth/refresh`
 * com o cookie httpOnly que resolve isso. Esse fluxo NAO esta nesta fatia
 * (ver "O que este plano NAO faz"): por enquanto, recarregar leva ao login.
 */
let tokenDeAcesso: string | null = null

export function definirToken(token: string | null) {
  tokenDeAcesso = token
}

export async function api<T>(caminho: string, opcoes: RequestInit = {}): Promise<T> {
  const resposta = await fetch(caminho, {
    ...opcoes,
    // o refresh vem em cookie httpOnly: sem isto ele nao acompanha a chamada
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(tokenDeAcesso ? { Authorization: `Bearer ${tokenDeAcesso}` } : {}),
      ...opcoes.headers,
    },
  })

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
