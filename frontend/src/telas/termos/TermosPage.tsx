import { ErroDaApi } from '../../api/erro'
import { useTermosAtuais } from './queries'

const TITULOS = {
  termos: 'Termos de Uso',
  privacidade: 'Política de Privacidade',
} as const

export function paragrafosDoTexto(texto: string) {
  return texto
    .split(/\n\s*\n/)
    .map((paragrafo) => paragrafo.trim())
    .filter((paragrafo) => paragrafo.length > 0)
}

export function TermosPage({ documento }: { documento: keyof typeof TITULOS }) {
  const consulta = useTermosAtuais()
  const naoPublicado = consulta.error instanceof ErroDaApi && consulta.error.status === 404

  return (
    <main className="tela tela-login">
      <div className="cartao cartao-login cartao-texto">
        <h1>{TITULOS[documento]}</h1>

        {consulta.isPending && <p>Carregando…</p>}

        {naoPublicado && <p>Ainda não publicado.</p>}

        {consulta.isError && !naoPublicado && (
          <p className="anuncio" role="alert">
            Não foi possível carregar. Tente de novo em instantes.
          </p>
        )}

        {consulta.data && (
          <>
            <p className="sub">Versão {consulta.data.versao}</p>
            <div className="texto-legal">
              {paragrafosDoTexto(consulta.data[documento]).map((paragrafo, indice) => (
                <p key={indice}>{paragrafo}</p>
              ))}
            </div>
          </>
        )}
      </div>
    </main>
  )
}
