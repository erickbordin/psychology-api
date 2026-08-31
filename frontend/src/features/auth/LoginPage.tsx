import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { ErroApi } from '../../api/erro'
import { registrar } from '../../api/recursos/auth'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { useSessao } from './useSessao'

type Modo = 'login' | 'registro'

const CAMPOS_DO_FORMULARIO = ['nome', 'email', 'emailUsuario', 'senha']

export function LoginPage() {
  const { autenticado, entrar } = useSessao()
  const local = useLocation()
  const [modo, setModo] = useState<Modo>('login')
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<ErroApi | null>(null)
  const [faltando, setFaltando] = useState<string[]>([])
  const [enviando, setEnviando] = useState(false)

  /**
   * Quem ja tem sessao nao fica no /login, e volta para onde tentava chegar — a
   * RotaProtegida deixa o destino no state. O placeholder que ficava aqui era um
   * marcador da Task 5, de quando nao existia rota interna nenhuma para onde ir;
   * mantido depois da Task 7 ele deixava o usuario preso numa tela morta logo
   * apos entrar — foi o E2E de fumaca que pegou isso.
   */
  if (autenticado) {
    const destino = (local.state as { de?: string } | null)?.de
    return <Navigate to={destino ?? '/'} replace />
  }

  const ehLogin = modo === 'login'

  /**
   * O cliente so checa campo vazio — poupa uma ida ao servidor. O resto da
   * validacao e do envelope da API, que nomeia o campo culpado.
   */
  function camposVazios(): string[] {
    const vazios: string[] = []
    if (!ehLogin && !nome.trim()) vazios.push('nome')
    if (!email.trim()) vazios.push('email')
    if (!senha.trim()) vazios.push('senha')
    return vazios
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    if (enviando) return

    const vazios = camposVazios()
    setFaltando(vazios)
    setErro(null)
    if (vazios.length > 0) return

    setEnviando(true)
    try {
      if (ehLogin) {
        await entrar(email, senha)
      } else {
        await registrar(nome, email, senha)
        await entrar(email, senha)
      }
    } catch (falha) {
      if (falha instanceof ErroApi) {
        setErro(falha)
        return
      }
      throw falha
    } finally {
      setEnviando(false)
    }
  }

  /**
   * A marca de campo obrigatorio olha o valor de agora, nao so o retrato do
   * ultimo envio: sem isso ela continuava embaixo do campo depois de o usuario
   * te-lo preenchido, ate ele tentar enviar outra vez.
   */
  function erroDoCampo(campo: string, valorDoCampo: string): string | undefined {
    if (faltando.includes(campo) && !valorDoCampo.trim()) return 'campo obrigatorio'
    if (campo === 'email') {
      return erro?.mensagemDoCampo('email') ?? erro?.mensagemDoCampo('emailUsuario')
    }
    return erro?.mensagemDoCampo(campo)
  }

  /**
   * `status === 0` e o marcador do client (Task 2) para "a requisicao nunca
   * chegou no servidor" — DNS, conexao recusada, API fora do ar. Sem esse
   * desvio, um 401 de credencial invalida e uma queda de rede cairiam no
   * mesmo texto generico, e o psicologo ficaria retentando a mesma senha
   * certa contra um servidor que nao esta no ar.
   */
  function mensagemGeral(falha: ErroApi): string {
    if (falha.status === 0) {
      return 'Nao foi possivel conectar ao servidor. Tente novamente em instantes.'
    }
    const orfaos = falha.errosForaDe(CAMPOS_DO_FORMULARIO)
    if (orfaos.length > 0) {
      return orfaos.map((item) => item.erro).join(' ')
    }
    return falha.mensagem
  }

  const avisoGeral =
    erro && (erro.erros.length === 0 || erro.errosForaDe(CAMPOS_DO_FORMULARIO).length > 0)
      ? mensagemGeral(erro)
      : null

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-sm flex-col justify-center gap-10 px-6 py-16">
      <div className="flex flex-col gap-4">
        <span className="font-serif text-lg leading-none">psychology</span>
        <h1 className="font-serif text-4xl leading-tight">{ehLogin ? 'Entrar' : 'Criar conta'}</h1>
        <p className="text-sm text-tinta-2">
          {ehLogin
            ? 'Seus pacientes e as anotações de sessão ficam aqui.'
            : 'Uma conta por profissional. Ninguém mais enxerga seus pacientes.'}
        </p>
      </div>

      {/* form de verdade, e nao um punhado de inputs: e o que faz o Enter no
          ultimo campo enviar, do jeito que qualquer tela de login se comporta. */}
      <form onSubmit={(evento) => void enviar(evento)} className="flex flex-col gap-8">
        <div className="flex flex-col gap-6">
          {ehLogin ? null : (
            <Campo
              rotulo="Nome"
              valor={nome}
              aoMudar={setNome}
              autoPreenchimento="name"
              erro={erroDoCampo('nome', nome)}
            />
          )}
          <Campo
            rotulo="E-mail"
            valor={email}
            aoMudar={setEmail}
            autoPreenchimento="username"
            erro={erroDoCampo('email', email)}
          />
          <Campo
            rotulo="Senha"
            tipo="password"
            valor={senha}
            aoMudar={setSenha}
            autoPreenchimento={ehLogin ? 'current-password' : 'new-password'}
            erro={erroDoCampo('senha', senha)}
          />
        </div>

        {avisoGeral ? (
          <p className="border-l-2 border-perigo bg-superficie px-4 py-3 text-sm">{avisoGeral}</p>
        ) : null}

        <div className="flex flex-col items-start gap-5">
          <Botao type="submit" disabled={enviando}>
            {ehLogin ? 'Entrar' : 'Cadastrar'}
          </Botao>
          <Botao
            variante="texto"
            onClick={() => {
              setModo(ehLogin ? 'registro' : 'login')
              setErro(null)
              setFaltando([])
            }}
          >
            {ehLogin ? 'Criar conta' : 'Já tenho conta'}
          </Botao>
        </div>
      </form>
    </main>
  )
}
