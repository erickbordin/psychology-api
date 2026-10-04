import { useParams } from 'react-router-dom'

import { ErroDaApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
import { useConfirmarDescadastro, useDescadastro } from './queries'

const PRONTO = 'Pronto. Você não vai mais receber lembretes por e-mail.'

export function DescadastroPage() {
  const { token = '' } = useParams()
  const consulta = useDescadastro(token)
  const confirmacao = useConfirmarDescadastro(token)

  const link = consulta.error instanceof ErroDaApi && consulta.error.status === 404

  return (
    <main className="tela tela-login">
      <div className="cartao cartao-login">
        <h1>Lembretes de agendamento</h1>

        {consulta.isPending && <p>Carregando…</p>}

        {link && <p>Este link não é mais válido.</p>}

        {consulta.isError && !link && (
          <p className="anuncio" role="alert">
            Não foi possível carregar. Tente de novo em instantes.
          </p>
        )}

        {consulta.data?.ativo === false && <p role="status">{PRONTO}</p>}

        {consulta.data?.ativo === true && (
          <>
            <p>Você recebe lembretes de agendamento por e-mail.</p>
            {confirmacao.isError && (
              <p className="anuncio" role="alert">
                Não foi possível carregar. Tente de novo em instantes.
              </p>
            )}
            <Botao
              type="button"
              disabled={confirmacao.isPending}
              onClick={() => {
                if (!confirmacao.isPending) confirmacao.mutate()
              }}
            >
              Parar de receber lembretes
            </Botao>
          </>
        )}
      </div>
    </main>
  )
}
