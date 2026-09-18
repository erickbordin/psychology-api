import { useState } from 'react'
import { Link } from 'react-router-dom'

import { Botao } from '../../ui/Botao'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { DialogoDePaciente } from './DialogoDePaciente'
import { usePacientes } from './queries'

export function PacientesPage() {
  const [busca, setBusca] = useState('')
  const [dialogo, setDialogo] = useState(false)
  const { data, isPending, isError, isPlaceholderData } = usePacientes(busca)
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
              <Botao onClick={() => setDialogo(true)}>Cadastrar o primeiro</Botao>
            ) : undefined
          }
        />
      )}

      {data && data.length > 0 && (
        <ul className="cartao lista-sessoes">
          {data.map((p) => (
            <li className="linha-pendencia" key={p.idPaciente}>
              <Link to={`/pacientes/${p.idPaciente}`}>{p.nome}</Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
