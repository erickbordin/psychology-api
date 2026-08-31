import { useState } from 'react'

import { ErroApi } from '../../api/erro'
import type { Paciente } from '../../api/tipos'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { Dialogo } from '../../ui/Dialogo'
import { useAtualizarPaciente, useExcluirPaciente } from './queries'

/**
 * Editar e excluir moram no mesmo dialogo porque sao a mesma decisao — "este
 * cadastro esta errado" — e separar em dois lugares faria o psicologo procurar.
 * A exclusao exige um segundo passo: e o unico caminho do produto que tira um
 * paciente inteiro da lista.
 */
export function EdicaoDePaciente({
  paciente,
  aoFechar,
}: {
  paciente: Paciente
  aoFechar: () => void
}) {
  const atualizar = useAtualizarPaciente()
  const excluir = useExcluirPaciente()

  const [nome, setNome] = useState(paciente.nome)
  const [telefone, setTelefone] = useState(paciente.telefone ?? '')
  const [email, setEmail] = useState(paciente.email ?? '')
  const [nascimento, setNascimento] = useState(paciente.dataNascimento)
  const [confirmandoExclusao, setConfirmandoExclusao] = useState(false)

  const falha =
    atualizar.error instanceof ErroApi
      ? atualizar.error
      : excluir.error instanceof ErroApi
        ? excluir.error
        : null

  async function salvar() {
    try {
      await atualizar.mutateAsync({
        pacienteId: paciente.idPaciente,
        mudanca: {
          nome,
          telefone: telefone || undefined,
          email: email || undefined,
          dataNascimento: nascimento,
        },
      })
      aoFechar()
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  async function apagar() {
    try {
      await excluir.mutateAsync(paciente.idPaciente)
      aoFechar()
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  return (
    <Dialogo aberto aoFechar={aoFechar} titulo={confirmandoExclusao ? 'Excluir paciente?' : 'Editar paciente'}
      descricao={
        confirmandoExclusao
          ? `${paciente.nome} sai da lista junto com as anotações, os lembretes e as consultas. A exclusão fica registrada na trilha.`
          : undefined
      }
    >
      {confirmandoExclusao ? (
        <div className="flex flex-wrap items-center gap-6">
          <Botao onClick={() => void apagar()} disabled={excluir.isPending}>
            Excluir paciente
          </Botao>
          <Botao variante="texto" onClick={() => setConfirmandoExclusao(false)}>
            Manter
          </Botao>
        </div>
      ) : (
        <>
          <Campo rotulo="Nome" valor={nome} aoMudar={setNome} erro={falha?.mensagemDoCampo('nome')} />
          <Campo
            rotulo="Telefone"
            valor={telefone}
            aoMudar={setTelefone}
            erro={falha?.mensagemDoCampo('telefone')}
          />
          <Campo
            rotulo="E-mail"
            valor={email}
            aoMudar={setEmail}
            erro={falha?.mensagemDoCampo('email')}
          />
          <Campo
            rotulo="Data de nascimento"
            valor={nascimento}
            aoMudar={setNascimento}
            exemplo="1991-04-12"
            erro={falha?.mensagemDoCampo('dataNascimento')}
          />
          <div className="flex flex-wrap items-center gap-6">
            <Botao onClick={() => void salvar()} disabled={atualizar.isPending}>
              Salvar
            </Botao>
            <Botao variante="texto" onClick={aoFechar}>
              Cancelar
            </Botao>
            <span className="flex-1" />
            <Botao variante="texto" onClick={() => setConfirmandoExclusao(true)}>
              Excluir
            </Botao>
          </div>
        </>
      )}

      {falha && falha.erros.length === 0 ? (
        <p className="border-l-2 border-perigo bg-superficie px-4 py-3 text-sm">{falha.mensagem}</p>
      ) : null}
    </Dialogo>
  )
}
