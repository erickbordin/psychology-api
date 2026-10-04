import * as Dialog from '@radix-ui/react-dialog'
import { useRef, type FormEvent, type RefObject } from 'react'

import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { esquemaDaSenha, useTrocarSenha, type DadosDaSenha } from './senha'

type Props = {
  aberto: boolean
  aoFechar: () => void
  aoTrocar: () => void
  focoAoFechar?: RefObject<HTMLElement | null>
}

const CAMPOS_NO_DIALOGO = ['senhaAtual', 'novaSenha']

function FormularioDeSenha({ aoFechar, aoTrocar }: Omit<Props, 'aberto'>) {
  const trocar = useTrocarSenha()
  const emVoo = useRef(false)

  const f = useFormularioDoProduto(
    esquemaDaSenha,
    async (valores: DadosDaSenha) => {
      await trocar.mutateAsync(valores)
      trocar.reset()
      aoTrocar()
      aoFechar()
    },
    { defaultValues: { senhaAtual: '', novaSenha: '' } },
  )

  const submeter = f.handleSubmit(f.enviar)

  async function aoSubmeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (emVoo.current) return
    emVoo.current = true
    try {
      await submeter()
    } finally {
      emVoo.current = false
    }
  }

  const alertaGeral =
    f.erroDeEnvio !== null &&
    (f.erroDeEnvio.erros.length === 0 ||
      f.erroDeEnvio.erros.some((e) => !CAMPOS_NO_DIALOGO.includes(e.campo)))

  return (
    <form onSubmit={aoSubmeter} noValidate>
      <div className="dialogo-corpo">
        {alertaGeral && f.erroDeEnvio && (
          <p className="anuncio" role="alert">
            {f.erroDeEnvio.mensagem}
          </p>
        )}
        <Campo
          rotulo="Senha atual"
          obrigatorio
          type="password"
          autoComplete="current-password"
          erro={f.formState.errors.senhaAtual?.message}
          {...f.register('senhaAtual')}
        />
        <Campo
          rotulo="Nova senha"
          obrigatorio
          type="password"
          autoComplete="new-password"
          erro={f.formState.errors.novaSenha?.message}
          {...f.register('novaSenha')}
        />
      </div>

      <div className="dialogo-rodape">
        <Botao type="button" variante="secundario" onClick={aoFechar}>
          Cancelar
        </Botao>
        <Botao type="submit" disabled={f.enviando}>
          Salvar
        </Botao>
      </div>
    </form>
  )
}

export function DialogoDeSenha({ aberto, aoFechar, aoTrocar, focoAoFechar }: Props) {
  return (
    <Dialog.Root open={aberto} onOpenChange={(a) => !a && aoFechar()}>
      <Dialog.Portal>
        <Dialog.Overlay className="mascara" />
        <Dialog.Content
          aria-describedby={undefined}
          className="dialogo"
          onCloseAutoFocus={(evento) => {
            if (focoAoFechar?.current) {
              evento.preventDefault()
              focoAoFechar.current.focus()
            }
          }}
        >
          <div className="dialogo-cabecalho">
            <Dialog.Title className="dialogo-cabecalho-titulo">Trocar senha</Dialog.Title>
          </div>
          <FormularioDeSenha aoFechar={aoFechar} aoTrocar={aoTrocar} />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
