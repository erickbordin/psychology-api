import { useQueryClient } from '@tanstack/react-query'
import { useId } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { api } from '../../api/cliente'
import { ErroDaApi } from '../../api/erro'
import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { useSessao } from '../../sessao/useSessao'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { chaveDosTermos, useTermosAtuais } from '../termos/queries'
import { esquemaDoCadastro } from './esquema'

const TERMOS_MUDARAM = 'Os termos mudaram. Leia a versão atual.'
const INDISPONIVEL = 'Cadastro ainda não está disponível.'

export function CadastroPage() {
  const { entrar } = useSessao()
  const navegar = useNavigate()
  const cliente = useQueryClient()
  const termos = useTermosAtuais()
  const idDoAceite = useId()
  const idDoErroDoAceite = `${idDoAceite}-erro`

  const f = useFormularioDoProduto(
    esquemaDoCadastro,
    async (valores) => {
      try {
        await api('/auth/registrar', {
          method: 'POST',
          body: JSON.stringify({ ...valores, versaoTermos: termos.data?.versao }),
        })
      } catch (erro) {
        if (erro instanceof ErroDaApi && erro.status === 409 && erro.mensagem === TERMOS_MUDARAM) {
          await cliente.invalidateQueries({ queryKey: chaveDosTermos })
          f.setValue('aceiteTermos', false)
        }
        throw erro
      }
      await entrar({ emailUsuario: valores.email, senha: valores.senha })
      navegar('/', { replace: true })
    },
    { defaultValues: { codigoConvite: '', nome: '', email: '', senha: '', aceiteTermos: false } },
  )

  const indisponivel = termos.error instanceof ErroDaApi && termos.error.status === 404
  const erroDoAceite = f.formState.errors.aceiteTermos?.message

  return (
    <main className="tela tela-login">
      <div className="cartao cartao-login">
        <h1>Criar conta</h1>

        {termos.isPending && <p role="status">Carregando…</p>}

        {indisponivel && <p role="status">{INDISPONIVEL}</p>}

        {termos.isError && !indisponivel && (
          <p className="anuncio" role="alert">
            Não foi possível carregar. Tente de novo em instantes.
          </p>
        )}

        {termos.data && (
          <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
            <ResumoDeErros erros={f.formState.errors} />

            {f.erroDeEnvio && f.erroDeEnvio.erros.length === 0 && (
              <p className="anuncio" role="alert">
                {f.erroDeEnvio.mensagem}
              </p>
            )}

            <Campo
              rotulo="Código de convite"
              autoComplete="off"
              obrigatorio
              erro={f.formState.errors.codigoConvite?.message}
              {...f.register('codigoConvite')}
            />

            <Campo
              rotulo="Nome"
              autoComplete="name"
              obrigatorio
              erro={f.formState.errors.nome?.message}
              {...f.register('nome')}
            />

            <Campo
              rotulo="E-mail"
              type="email"
              autoComplete="email"
              obrigatorio
              erro={f.formState.errors.email?.message}
              {...f.register('email')}
            />

            <Campo
              rotulo="Senha"
              type="password"
              autoComplete="new-password"
              obrigatorio
              erro={f.formState.errors.senha?.message}
              {...f.register('senha')}
            />

            <div className="campo">
              <label className="campo-aceite">
                <input
                  type="checkbox"
                  aria-invalid={erroDoAceite ? true : undefined}
                  aria-describedby={erroDoAceite ? idDoErroDoAceite : undefined}
                  {...f.register('aceiteTermos')}
                />
                <span>
                  Li e aceito os <Link to="/termos">Termos de Uso</Link> e a{' '}
                  <Link to="/privacidade">Política de Privacidade</Link>
                </span>
              </label>
              {erroDoAceite && (
                <p className="campo-erro" id={idDoErroDoAceite}>
                  {erroDoAceite}
                </p>
              )}
            </div>

            <Botao type="submit" disabled={f.enviando}>
              {f.enviando ? 'Criando…' : 'Criar conta'}
            </Botao>
          </form>
        )}

        <p className="ligacao-auxiliar">
          <Link to="/entrar">Já tenho conta</Link>
        </p>
      </div>
    </main>
  )
}
