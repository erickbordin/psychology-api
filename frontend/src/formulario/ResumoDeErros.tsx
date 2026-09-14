import type { FieldErrors } from 'react-hook-form'

/**
 * O anuncio em aria-live da spec 6.2. Sem ele, quem usa leitor de tela aperta
 * "Salvar" e nao acontece nada audivel — o botao morre em silencio para essa
 * pessoa mesmo com o erro desenhado na tela.
 */
export function ResumoDeErros({ erros }: { erros: FieldErrors }) {
  const mensagens = Object.values(erros)
    .map((e) => e?.message)
    .filter((m): m is string => typeof m === 'string')

  return (
    <div role="alert" aria-live="assertive" className="resumo-erros">
      {mensagens.length > 0 && (
        <>
          <p>Confira os campos abaixo:</p>
          <ul>
            {mensagens.map((m) => (
              <li key={m}>{m}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
