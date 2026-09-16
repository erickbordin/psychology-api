import { useState } from 'react'
import { Link } from 'react-router-dom'

import { Botao } from '../../ui/Botao'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { DialogoDePaciente } from './DialogoDePaciente'
import { usePacientes } from './queries'

export function PacientesPage() {
  const [busca, setBusca] = useState('')
  const [dialogo, setDialogo] = useState(false)
  const { data, isPending, isError } = usePacientes(busca)
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

      <DialogoDePaciente aberto={dialogo} aoFechar={() => setDialogo(false)} />

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

      {data && data.length === 0 && (
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
