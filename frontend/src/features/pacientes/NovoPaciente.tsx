import { useState } from 'react'

import { ErroApi } from '../../api/erro'
import { Botao, BotaoDeIcone } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { Cartao } from '../../ui/Cartao'
import { IconeVoltar } from '../../ui/icones'
import { useCriarPaciente } from './queries'

/**
 * O cadastro e uma tela inteira, nao um dialogo: sao quatro campos e o
 * psicologo costuma preenche-los com o paciente na frente, lendo o documento —
 * um modal por cima da lista, com a lista piscando atras, e o pior lugar
 * possivel para isso.
 *
 * Nome e data de nascimento sao obrigatorios porque a API exige; telefone e
 * e-mail vao como `undefined` quando vazios, e nao como string vazia, que o
 * validador do servidor recusaria.
 */
export function NovoPaciente({ aoFechar }: { aoFechar: () => void }) {
  const criar = useCriarPaciente()

  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [email, setEmail] = useState('')
  const [nascimento, setNascimento] = useState('')
  const [vazios, setVazios] = useState<string[]>([])

  const falha = criar.error instanceof ErroApi ? criar.error : null

  function erroDoCampo(campo: string, valorDoCampo: string): string | undefined {
    if (vazios.includes(campo) && !valorDoCampo.trim()) return 'campo obrigatorio'
    return falha?.mensagemDoCampo(campo)
  }

  async function cadastrar() {
    const faltando = [
      ...(nome.trim() ? [] : ['nome']),
      ...(nascimento.trim() ? [] : ['dataNascimento']),
    ]
    setVazios(faltando)
    if (faltando.length > 0) return

    try {
      await criar.mutateAsync({
        nome,
        dataNascimento: nascimento,
        telefone: telefone.trim() || undefined,
        email: email.trim() || undefined,
      })
      aoFechar()
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  return (
    <Cartao className="flex flex-col gap-6 p-6">
      <div className="flex items-center gap-2">
        <BotaoDeIcone rotulo="Voltar" icone={<IconeVoltar tamanho={20} />} onClick={aoFechar} />
        <h2 className="text-xl font-semibold">Novo paciente</h2>
      </div>

      <div className="flex flex-col gap-5">
        <Campo
          rotulo="Nome"
          valor={nome}
          aoMudar={setNome}
          exemplo="Nome completo"
          erro={erroDoCampo('nome', nome)}
        />

        <div className="grid gap-5 sm:grid-cols-2">
          <Campo
            rotulo="Telefone"
            tipo="tel"
            valor={telefone}
            aoMudar={setTelefone}
            exemplo="(51) 00000-0000"
            erro={erroDoCampo('telefone', telefone)}
          />
          <Campo
            rotulo="Data de nascimento"
            valor={nascimento}
            aoMudar={setNascimento}
            exemplo="1991-04-12"
            erro={erroDoCampo('dataNascimento', nascimento)}
          />
        </div>

        <Campo
          rotulo="E-mail"
          tipo="email"
          valor={email}
          aoMudar={setEmail}
          exemplo="email@exemplo.com"
          erro={erroDoCampo('email', email)}
        />
      </div>

      {falha && falha.erros.length === 0 ? (
        <p className="rounded-xl border border-perigo/30 bg-perigo-suave px-4 py-3 text-sm text-perigo">
          {falha.mensagem}
        </p>
      ) : null}

      <div className="flex justify-end gap-3 border-t border-linha pt-5">
        <Botao variante="secundario" onClick={aoFechar}>
          Cancelar
        </Botao>
        <Botao onClick={() => void cadastrar()} disabled={criar.isPending}>
          Salvar
        </Botao>
      </div>
    </Cartao>
  )
}
