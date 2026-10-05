import { Link, useLocation, useNavigate } from 'react-router-dom'

import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { useSessao } from '../../sessao/useSessao'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { esquemaDoLogin } from './esquema'

export function LoginPage() {
  const { entrar } = useSessao()
  const navegar = useNavigate()
  const estado = useLocation().state as { expirou?: boolean; de?: { pathname: string } } | null

  const f = useFormularioDoProduto(esquemaDoLogin, async (valores) => {
    await entrar(valores)
    navegar(estado?.de?.pathname ?? '/', { replace: true })
  })

  return (
    <main className="tela tela-login">
      <div className="cartao cartao-login">
        <h1>Entrar</h1>

        {estado?.expirou && (
          <p className="anuncio" role="status">
            Sua sessão expirou. Entre novamente.
          </p>
        )}

        <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
          <ResumoDeErros erros={f.formState.errors} />

          {/* o 401 nao tem campo culpado — a API nao diz qual dos dois errou, de
              proposito, para nao confirmar se um e-mail existe */}
          {f.erroDeEnvio && f.erroDeEnvio.erros.length === 0 && (
            <p className="anuncio" role="alert">
              {f.erroDeEnvio.mensagem}
            </p>
          )}

          <Campo
            rotulo="E-mail"
            type="email"
            autoComplete="username"
            obrigatorio
            erro={f.formState.errors.emailUsuario?.message}
            {...f.register('emailUsuario')}
          />

          <Campo
            rotulo="Senha"
            type="password"
            autoComplete="current-password"
            obrigatorio
            erro={f.formState.errors.senha?.message}
            {...f.register('senha')}
          />

          <Botao type="submit" disabled={f.enviando}>
            {f.enviando ? 'Entrando…' : 'Entrar'}
          </Botao>
        </form>

        <p className="ligacao-auxiliar">
          <Link to="/cadastro">Criar conta</Link>
        </p>
      </div>
    </main>
  )
}
