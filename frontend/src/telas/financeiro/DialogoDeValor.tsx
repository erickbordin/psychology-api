import * as Dialog from '@radix-ui/react-dialog'

import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { esquemaDoValor, type DadosDoValor } from './esquema'
import { useAlterarValor, type SessaoFinanceira } from './queries'

type Props = {
  sessao: SessaoFinanceira
  aoFechar: () => void
}

export function DialogoDeValor({ sessao, aoFechar }: Props) {
  const alterar = useAlterarValor()

  const f = useFormularioDoProduto(
    esquemaDoValor,
    async (valores: DadosDoValor) => {
      await alterar.mutateAsync({ consultaId: sessao.consultaId, valor: Number(valores.valor) })
      aoFechar()
    },
    {
      defaultValues: { valor: sessao.valor != null ? String(sessao.valor) : '' },
    },
  )

  return (
    <Dialog.Root open onOpenChange={(a) => !a && aoFechar()}>
      <Dialog.Portal>
        <Dialog.Overlay className="mascara" />
        <Dialog.Content aria-describedby={undefined} className="dialogo">
          <div className="dialogo-cabecalho">
            <Dialog.Title className="dialogo-cabecalho-titulo">
              {sessao.valor == null ? 'Definir valor da sessão' : 'Alterar valor da sessão'}
            </Dialog.Title>
          </div>

          <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
            <div className="dialogo-corpo">
              <ResumoDeErros erros={f.formState.errors} />

              {f.erroDeEnvio && f.erroDeEnvio.erros.length === 0 && (
                <p className="anuncio" role="alert">
                  {f.erroDeEnvio.mensagem}
                </p>
              )}

              <Campo
                rotulo="Valor da sessão"
                obrigatorio
                type="number"
                step="0.01"
                erro={f.formState.errors.valor?.message}
                {...f.register('valor')}
              />
            </div>

            <div className="dialogo-rodape">
              <Botao variante="secundario" type="button" onClick={aoFechar}>
                Cancelar
              </Botao>
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
