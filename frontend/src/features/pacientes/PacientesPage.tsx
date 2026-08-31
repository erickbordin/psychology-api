import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { ErroApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { dataCurta } from '../../ui/data'
import { EdicaoDePaciente } from './EdicaoDePaciente'
import type { Paciente } from '../../api/tipos'
import { useCriarPaciente, usePacientes } from './queries'

export function PacientesPage() {
  /**
   * O filtro vive na URL, nao em useState: recarregar preserva a busca, o link e
   * compartilhavel, e `nome` casa um a um com o parametro da API. `replace` para
   * nao empilhar uma entrada de historico por tecla digitada.
   */
  const [parametros, setParametros] = useSearchParams()
  const nome = parametros.get('nome') ?? ''

  const { data: pacientes, isPending, error } = usePacientes(nome)
  const criar = useCriarPaciente()
  const [criando, setCriando] = useState(false)
  const [emEdicao, setEmEdicao] = useState<Paciente | null>(null)
  const [nomeNovo, setNomeNovo] = useState('')
  const [nascimento, setNascimento] = useState('')
  const [vazios, setVazios] = useState<string[]>([])

  const falha = criar.error instanceof ErroApi ? criar.error : null

  function erroDoCampo(campo: string, valorDoCampo: string): string | undefined {
    if (vazios.includes(campo) && !valorDoCampo.trim()) return 'campo obrigatorio'
    return falha?.mensagemDoCampo(campo)
  }

  async function cadastrar() {
    const faltando = [
      ...(nomeNovo.trim() ? [] : ['nome']),
      ...(nascimento.trim() ? [] : ['dataNascimento']),
    ]
    setVazios(faltando)
    if (faltando.length > 0) return

    try {
      await criar.mutateAsync({ nome: nomeNovo, dataNascimento: nascimento })
      setCriando(false)
      setNomeNovo('')
      setNascimento('')
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  const total = pacientes?.length ?? 0

  return (
    <div className="flex flex-col gap-10">
      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-linha pb-6">
        <div className="flex flex-col gap-2">
          <h1 className="font-serif text-4xl leading-none">Pacientes</h1>
          {pacientes ? (
            <p className="text-sm text-tinta-2">
              {total === 1 ? '1 paciente' : `${total} pacientes`}
            </p>
          ) : null}
        </div>
        <Botao onClick={() => setCriando((antes) => !antes)}>Novo paciente</Botao>
      </header>

      <div className="max-w-sm">
        <Campo
          rotulo="Buscar por nome"
          valor={nome}
          aoMudar={(valor) =>
            setParametros(valor ? { nome: valor } : {}, { replace: true })
          }
          exemplo="parte do nome"
        />
      </div>

      {criando ? (
        <section className="flex max-w-md flex-col gap-6 border border-linha p-6">
          <Campo
            rotulo="Nome"
            valor={nomeNovo}
            aoMudar={setNomeNovo}
            erro={erroDoCampo('nome', nomeNovo)}
          />
          <Campo
            rotulo="Data de nascimento"
            valor={nascimento}
            aoMudar={setNascimento}
            exemplo="1991-04-12"
            erro={erroDoCampo('dataNascimento', nascimento)}
          />
          <div>
            <Botao onClick={() => void cadastrar()} disabled={criar.isPending}>
              Cadastrar
            </Botao>
          </div>
        </section>
      ) : null}

      {isPending ? <p className="text-sm text-tinta-3">Carregando…</p> : null}
      {error ? (
        <p className="border-l-2 border-perigo bg-superficie px-4 py-3 text-sm">
          {(error as Error).message}
        </p>
      ) : null}

      {pacientes ? (
        total === 0 ? (
          nome ? (
            <EstadoVazio>Nenhum paciente com “{nome}” no nome.</EstadoVazio>
          ) : (
            <p className="py-16 text-center text-sm text-tinta-2">
              Nenhum paciente cadastrado ainda.
            </p>
          )
        ) : (
          <ul>
            {pacientes.map((paciente) => (
              <li
                key={paciente.idPaciente}
                className="flex items-baseline gap-5 border-b border-linha"
              >
                <Link
                  to={`/pacientes/${paciente.idPaciente}`}
                  className="-ml-3 flex flex-1 items-baseline justify-between gap-6 px-3 py-4 transition-colors hover:bg-superficie focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
                >
                  <span className="font-serif text-lg">{paciente.nome}</span>
                  <span className="shrink-0 text-sm tabular-nums text-tinta-2">
                    {dataCurta(paciente.dataNascimento)}
                  </span>
                </Link>
                <Botao variante="texto" onClick={() => setEmEdicao(paciente)}>
                  Editar
                </Botao>
              </li>
            ))}
          </ul>
        )
      ) : null}

      {emEdicao ? (
        <EdicaoDePaciente paciente={emEdicao} aoFechar={() => setEmEdicao(null)} />
      ) : null}
    </div>
  )
}
