import * as Dialog from '@radix-ui/react-dialog'

import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { esquemaDoPaciente, type DadosDoPaciente } from './esquema'
import { useSalvarPaciente, type Paciente } from './queries'

type Props = {
  aberto: boolean
  aoFechar: () => void
  /** presente na EDICAO; ausente e cadastro novo */
  paciente?: Paciente
}

export function DialogoDePaciente({ aberto, aoFechar, paciente }: Props) {
  const salvar = useSalvarPaciente(paciente?.idPaciente)

  const f = useFormularioDoProduto(
    esquemaDoPaciente,
    async (valores: DadosDoPaciente) => {
      await salvar.mutateAsync(valores)
      aoFechar()
    },
    {
      defaultValues: {
        nome: paciente?.nome ?? '',
        email: paciente?.email ?? '',
        telefone: paciente?.telefone ?? '',
        dataNascimento: paciente?.dataNascimento ?? '',
        valorSessao: paciente?.valorSessao != null ? String(paciente.valorSessao) : '',
        canalAviso: paciente?.canalAviso ?? 'NENHUM',
      },
    },
  )

  return (
    <Dialog.Root open={aberto} onOpenChange={(a) => !a && aoFechar()}>
      <Dialog.Portal>
        <Dialog.Overlay className="mascara" />
        <Dialog.Content aria-describedby={undefined} className="dialogo">
          <div className="dialogo-cabecalho">
            <Dialog.Title className="dialogo-cabecalho-titulo">
              {paciente ? 'Editar paciente' : 'Novo paciente'}
            </Dialog.Title>
          </div>

          <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
            <div className="dialogo-corpo">
              <ResumoDeErros erros={f.formState.errors} />

              {/* erro sem campo culpado: 409 de duplicado, 401 de sessao
                  expirada. Sem isto o botao morre em silencio — a 6.2 */}
              {f.erroDeEnvio && f.erroDeEnvio.erros.length === 0 && (
                <p className="anuncio" role="alert">
                  {f.erroDeEnvio.mensagem}
                </p>
              )}

              <Campo
                rotulo="Nome"
                obrigatorio
                erro={f.formState.errors.nome?.message}
                {...f.register('nome')}
              />
              <Campo
                rotulo="E-mail"
                type="email"
                erro={f.formState.errors.email?.message}
                {...f.register('email')}
              />
              <Campo
                rotulo="Telefone"
                erro={f.formState.errors.telefone?.message}
                {...f.register('telefone')}
              />
              <Campo
                rotulo="Data de nascimento"
                type="date"
                erro={f.formState.errors.dataNascimento?.message}
                {...f.register('dataNascimento')}
              />
              <Campo
                rotulo="Valor da sessao"
                type="number"
                step="0.01"
                erro={f.formState.errors.valorSessao?.message}
                {...f.register('valorSessao')}
              />

              <div className="campo">
                <label className="campo-rotulo" htmlFor="canal">
                  Canal de aviso
                </label>
                <select id="canal" {...f.register('canalAviso')}>
                  <option value="NENHUM">Nenhum</option>
                  <option value="EMAIL">E-mail</option>
                </select>
              </div>
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
