import { useEffect, useId } from 'react'
import { z } from 'zod'

import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { registrarRascunho } from '../../navegacao/guardaDeSaida'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { Botao } from '../../ui/Botao'
import type { SessaoDaFicha } from './queries'
import { useRegistrarAnotacao } from './queries'

/**
 * Espelha a validacao do `POST /pacientes/{id}/anotacoes`. A mensagem e a
 * MESMA do servidor (spec 6.3): duas redacoes diferentes para a mesma recusa
 * fariam o usuario achar que sao dois problemas.
 */
const esquemaDaAnotacao = z.object({
  anotacao: z.string().min(1, 'Informe o conteúdo da anotação.'),
})

type DadosDaAnotacao = z.infer<typeof esquemaDaAnotacao>

type Props = {
  pacienteId: string
  /**
   * A sessao de que esta anotacao e DAQUELA sessao. `consultaId` nao e prop
   * separada de proposito: nao existe registro de anotacao sem sessao aqui —
   * anotacao avulsa (spec 5.1) nao tem tela nesta fatia.
   */
  sessao: SessaoDaFicha
}

/** A caixa de registrar a anotacao de UMA sessao. So aparece (ver
 *  `LinhaDoTempo.tsx`) para sessao `REALIZADA` e ainda sem anotacao. */
export function RegistroDaSessao({ pacienteId, sessao }: Props) {
  const idDoCampo = useId()
  const idDoErro = `${idDoCampo}-erro`
  const registrar = useRegistrarAnotacao(pacienteId)

  const f = useFormularioDoProduto(
    esquemaDaAnotacao,
    async (valores: DadosDaAnotacao) => {
      await registrar.mutateAsync({ anotacao: valores.anotacao, consultaId: sessao.consultaId })
    },
    { defaultValues: { anotacao: '' } },
  )

  const erroDoCampo = f.formState.errors.anotacao?.message

  // O texto vive SO em memoria (design system, "Rascunho de anotacao"): nem
  // `sessionStorage` nem `localStorage`, porque anotacao clinica e o dado mais
  // sensivel do produto. Essa decisao so e honesta com a guarda junto — sem
  // ela, "em memoria" vira "perde em silencio". O aviso vale enquanto houver
  // texto NAO enviado, e some sozinho quando a caixa esvazia ou o componente
  // desmonta (depois de registrar, `LinhaDoTempo` deixa de renderizar a caixa).
  const rascunho = f.watch('anotacao')
  const temRascunho = rascunho.trim().length > 0
  useEffect(() => {
    if (!temRascunho) return
    return registrarRascunho()
  }, [temRascunho])

  return (
    <div className="registro-novo cartao">
      <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
        <ResumoDeErros erros={f.formState.errors} />

        {/* erro sem campo culpado: 409 de sessao de outro paciente. Sem isto
            o botao morre em silencio — a mesma regra da 6.2 usada nos outros
            formularios (DialogoDePaciente, DialogoDeConsulta). */}
        {f.erroDeEnvio && f.erroDeEnvio.erros.length === 0 && (
          <p className="anuncio" role="alert">
            {f.erroDeEnvio.mensagem}
          </p>
        )}

        <div className="campo">
          {/* O `*` e a MESMA marca que `ui/Campo.tsx` poe: a 6.2 pede que a
              obrigatoriedade seja visivel ANTES do erro, e este rotulo e
              escrito a mao so porque `Campo` desenha `<input>` e aqui o
              controle e `<textarea>`. `aria-hidden` mantem o nome acessivel em
              "Anotação", sem o asterisco lido em voz alta — igual ao Campo. */}
          <label className="campo-rotulo" htmlFor={idDoCampo}>
            Anotação
            <span aria-hidden="true"> *</span>
          </label>
          <textarea
            id={idDoCampo}
            aria-invalid={erroDoCampo ? true : undefined}
            aria-describedby={erroDoCampo ? idDoErro : undefined}
            {...f.register('anotacao')}
          />
          {erroDoCampo && (
            <p className="campo-erro" id={idDoErro}>
              {erroDoCampo}
            </p>
          )}
        </div>

        <Botao type="submit" disabled={f.enviando}>
          Registrar
        </Botao>
      </form>
    </div>
  )
}
