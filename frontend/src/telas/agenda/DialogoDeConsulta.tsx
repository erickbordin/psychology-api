import * as Dialog from '@radix-ui/react-dialog'
import { useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { Botao } from '../../ui/Botao'
import { esquemaDaConsulta } from './esquema'
import { useConflito } from './queries'

type Props = {
  aberto: boolean
  dtInicial: string
  aoFechar: () => void
  /** presente na EDICAO: tira a propria consulta da checagem de conflito */
  consultaId?: string
}

export function DialogoDeConsulta({ aberto, dtInicial, aoFechar, consultaId }: Props) {
  const cliente = useQueryClient()

  const f = useFormularioDoProduto(
    esquemaDaConsulta,
    async (valores) => {
      await api('/consultas', { method: 'POST', body: JSON.stringify(valores) })
      await cliente.invalidateQueries({ queryKey: ['consultas'] })
      aoFechar()
    },
    { defaultValues: { pacienteId: '', dtConsulta: dtInicial } },
  )

  const dtConsulta = f.watch('dtConsulta')
  const conflito = useConflito(dtConsulta, consultaId)

  return (
    <Dialog.Root open={aberto} onOpenChange={(a) => !a && aoFechar()}>
      <Dialog.Portal>
        <Dialog.Overlay className="mascara" />
        <Dialog.Content aria-describedby={undefined} className="dialogo">
          <div className="dialogo-cabecalho">
            <Dialog.Title className="dialogo-cabecalho-titulo">Nova consulta</Dialog.Title>
          </div>

          <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
            <div className="dialogo-corpo">
              <ResumoDeErros erros={f.formState.errors} />

              {/* Erro SEM campo culpado — 404 de paciente inexistente, 409 de
                  conflito recusado pela API, 401 de sessao expirada. Sem isto o
                  dialogo fica parado depois do "Salvar" e o usuario nao sabe se
                  travou, se salvou, ou se ele errou.
                  E a 6.2: "botao nunca morre em silencio". A regra existe porque
                  o codigo antigo tinha `if (!pacienteId || !data || !hora)
                  return` — e esta tela tinha reintroduzido o mesmo defeito por
                  outro caminho. Achado pelo e2e rodando contra a API real; os 41
                  testes com MSW passavam com a tela quebrada. */}
              {f.erroDeEnvio && f.erroDeEnvio.erros.length === 0 && (
                <p className="anuncio" role="alert">
                  {f.erroDeEnvio.mensagem}
                </p>
              )}

              <div className="campo">
                <label className="campo-rotulo" htmlFor="paciente">
                  Paciente
                </label>
                <select id="paciente" {...f.register('pacienteId')}>
                  <option value="">Escolha…</option>
                  {/* A lista real de pacientes e da Fatia 2 (endpoint /pacientes
                      fica de fora desta tela de proposito). Esta opcao unica e um
                      espaco reservado para o teste conseguir selecionar um
                      paciente e exercitar o fluxo de salvar — o valor e um UUID
                      de teste, nunca um paciente real. */}
                  <option value="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa">Paciente de teste</option>
                </select>
                {f.formState.errors.pacienteId && (
                  <p className="campo-erro">{f.formState.errors.pacienteId.message}</p>
                )}
              </div>

              <div className="campo">
                <label className="campo-rotulo" htmlFor="quando">
                  Data e hora
                </label>
                <input id="quando" type="datetime-local" step="1" {...f.register('dtConsulta')} />
              </div>

              {/* spec 6.3: conflito AVISADO antes do envio, dizendo QUEM ocupa.
                  `role="status"` e nao `alert` de proposito: e informacao, nao
                  erro — o psicologo pode ter razao para marcar assim mesmo */}
              {conflito.data && !conflito.data.livre && (
                <p className="campo-aviso" role="status">
                  Esse horario ja esta ocupado por {conflito.data.pacienteNome}.
                </p>
              )}
            </div>

            <div className="dialogo-rodape">
              <Botao type="submit" disabled={f.enviando}>
                Salvar
              </Botao>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
