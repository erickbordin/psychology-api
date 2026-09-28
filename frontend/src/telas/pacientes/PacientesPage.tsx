import { useState } from 'react'
import { Link } from 'react-router-dom'

import { usePrivacidade } from '../../privacidade/usePrivacidade'
import { Botao } from '../../ui/Botao'
import { Chip } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { DialogoDePaciente } from './DialogoDePaciente'
import { usePacientes } from './queries'
import type { Paciente } from './queries'

function idadeEmAnos(nascimento: string | null): number | null {
  if (!nascimento) return null
  const nasc = new Date(nascimento)
  if (Number.isNaN(nasc.getTime())) return null
  const hoje = new Date()
  let anos = hoje.getFullYear() - nasc.getFullYear()
  const mes = hoje.getMonth() - nasc.getMonth()
  if (mes < 0 || (mes === 0 && hoje.getDate() < nasc.getDate())) anos -= 1
  return anos >= 0 ? anos : null
}

function linhaDeMeta(p: Paciente): string {
  const idade = idadeEmAnos(p.dataNascimento)
  const partes = [
    idade === null ? null : `${idade} anos`,
    p.telefone ?? p.email,
    p.valorSessao === null
      ? null
      : p.valorSessao.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }),
  ]
  return partes.filter(Boolean).join(' · ')
}

export function PacientesPage() {
  const [busca, setBusca] = useState('')
  const [dialogo, setDialogo] = useState(false)
  const { data, isPending, isError, isPlaceholderData } = usePacientes(busca)
  const { privacidade } = usePrivacidade()
  const buscando = busca.trim().length > 0

  return (
    <div className="tela">
      <h1>Pacientes</h1>

      <div className="barra-agenda">
        <input
          className="busca"
          type="search"
          placeholder="Buscar por nome"
          aria-label="Buscar paciente"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
        <Botao onClick={() => setDialogo(true)}>+ Novo paciente</Botao>
      </div>

      {/* MONTAGEM CONDICIONAL, o mesmo que `AgendaPage.tsx` faz com o dialogo
          de consulta — e nao e estilo, e correcao de defeito. O Radix desmonta
          so o CONTEUDO quando fecha; o componente (e com ele o `useForm` de
          `useFormularioDoProduto`) seguia montado, e o RHF guarda
          `_formValues` (`shouldUnregister: false` e o padrao). Reabrir trazia
          nome, telefone, e-mail, valor e canal do paciente ANTERIOR, mais os
          erros em vermelho do envio passado. Num cadastro clinico isso escreve
          o telefone de uma pessoa na ficha de outra, em silencio. Montar so
          quando aberto faz cada abertura ser uma montagem limpa. */}
      {dialogo && (
        <DialogoDePaciente aberto aoFechar={() => setDialogo(false)} />
      )}

      {/* A regiao viva existe SEMPRE, ainda que vazia: leitor de tela so
          anuncia conteudo que entra numa regiao que ja estava no DOM. A mesma
          razao que faz o ResumoDeErros manter a dele. */}
      <p className="sub" aria-live="polite">
        {isPending ? 'Carregando…' : `${data?.length ?? 0} na lista`}
      </p>

      {isError && (
        <p className="anuncio" role="alert">
          Nao foi possivel carregar os pacientes. Tente de novo.
        </p>
      )}

      {/* `!isPlaceholderData` guarda os DOIS estados vazios (M2 da revisao):
          `buscando` fala da caixa de busca, mas `data` pode ser o
          `placeholderData` do termo ANTERIOR — e ao LIMPAR a busca os dois
          discordam por um quadro. Quem tem pacientes lia, nesse quadro,
          "Nenhum paciente cadastrado ainda." com o botao "Cadastrar o
          primeiro": exatamente a mentira que ter dois estados vazios existe
          para evitar. Enquanto o dado exibido nao pertence ao termo atual,
          nenhum dos dois e afirmavel. */}
      {data && data.length === 0 && !isPlaceholderData && (
        <EstadoVazio
          titulo={
            buscando
              ? 'Nenhum paciente com esse nome.'
              : 'Nenhum paciente cadastrado ainda.'
          }
          acao={
            !buscando ? (
              <Botao variante="secundario" onClick={() => setDialogo(true)}>Cadastrar o primeiro</Botao>
            ) : undefined
          }
        />
      )}

      {data && data.length > 0 && (
        <ul className="cartao lista-sessoes">
          {data.map((p) => {
            const meta = linhaDeMeta(p)
            return (
              <li className="linha-pendencia linha-paciente" key={p.idPaciente}>
                <Link to={`/pacientes/${p.idPaciente}`}>
                  <span className="linha-paciente-nome" data-borrado={privacidade}>{p.nome}</span>
                  {meta && (
                    <span className="linha-paciente-meta" data-borrado={privacidade}>{meta}</span>
                  )}
                </Link>
                <Chip variante={p.consentimentoAvisoEm ? 'neutro' : 'alerta'}>
                  {p.consentimentoAvisoEm ? 'Aviso autorizado' : 'Aviso nao autorizado'}
                </Chip>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
