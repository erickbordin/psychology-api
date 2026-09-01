import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { Avatar } from '../../ui/Avatar'
import { Botao, BotaoDeIcone } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { Cartao } from '../../ui/Cartao'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { IconeLapis, IconeLupa, IconeMais, IconeSeta } from '../../ui/icones'
import { dataCurta } from '../../ui/data'
import { EdicaoDePaciente } from './EdicaoDePaciente'
import { NovoPaciente } from './NovoPaciente'
import type { Paciente } from '../../api/tipos'
import { usePacientes } from './queries'

export function PacientesPage() {
  /**
   * O filtro vive na URL, nao em useState: recarregar preserva a busca, o link e
   * compartilhavel, e `nome` casa um a um com o parametro da API. `replace` para
   * nao empilhar uma entrada de historico por tecla digitada.
   */
  const [parametros, setParametros] = useSearchParams()
  const nome = parametros.get('nome') ?? ''

  const { data: pacientes, isPending, error } = usePacientes(nome)
  const [criando, setCriando] = useState(false)
  const [emEdicao, setEmEdicao] = useState<Paciente | null>(null)

  const total = pacientes?.length ?? 0

  if (criando) {
    return <NovoPaciente aoFechar={() => setCriando(false)} />
  }

  return (
    <div className="flex flex-col gap-5">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight">Pacientes</h1>
        {pacientes ? (
          <p className="text-sm text-tinta-2">
            {total === 1 ? '1 paciente' : `${total} pacientes`}
          </p>
        ) : null}
      </header>

      {/* busca e cadastro na mesma linha: sao as duas unicas coisas que se faz
          nesta tela antes de escolher um paciente */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-56 flex-1">
          <Campo
            rotulo="Buscar por nome"
            rotuloOculto
            valor={nome}
            adorno={<IconeLupa tamanho={18} />}
            exemplo="Pesquisar paciente"
            aoMudar={(valor) => setParametros(valor ? { nome: valor } : {}, { replace: true })}
          />
        </div>
        <Botao icone={<IconeMais tamanho={18} />} onClick={() => setCriando(true)}>
          Novo paciente
        </Botao>
      </div>

      {isPending ? <p className="text-sm text-tinta-3">Carregando…</p> : null}
      {error ? (
        <p className="rounded-xl border border-perigo/30 bg-perigo-suave px-4 py-3 text-sm text-perigo">
          {(error as Error).message}
        </p>
      ) : null}

      {pacientes ? (
        total === 0 ? (
          nome ? (
            <EstadoVazio>Nenhum paciente com “{nome}” no nome.</EstadoVazio>
          ) : (
            <EstadoVazio>Nenhum paciente cadastrado ainda.</EstadoVazio>
          )
        ) : (
          <ul className="flex flex-col gap-2.5">
            {pacientes.map((paciente) => (
              <li key={paciente.idPaciente}>
                <Cartao className="flex items-center gap-2 pr-3.5">
                  {/*
                    O cartao inteiro e o link — area de clique de uma linha de
                    lista, e nao um nome de 12 pixels de altura. O botao de
                    editar fica FORA da ancora: botao dentro de link e HTML
                    invalido, e o clique viraria navegacao.
                  */}
                  <Link
                    to={`/pacientes/${paciente.idPaciente}`}
                    className="flex min-w-0 flex-1 items-center gap-4 rounded-2xl p-3.5 transition-colors hover:bg-superficie-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
                  >
                    <Avatar nome={paciente.nome} />
                    <span className="flex min-w-0 flex-col">
                      <span className="truncate font-semibold">{paciente.nome}</span>
                      <span className="truncate text-sm tabular-nums text-tinta-2">
                        {paciente.telefone ?? dataCurta(paciente.dataNascimento)}
                      </span>
                    </span>
                    <span className="flex-1" />
                    <IconeSeta tamanho={20} className="shrink-0 text-tinta-3" />
                  </Link>
                  <BotaoDeIcone
                    rotulo="Editar"
                    icone={<IconeLapis tamanho={18} />}
                    onClick={() => setEmEdicao(paciente)}
                  />
                </Cartao>
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
