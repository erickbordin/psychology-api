import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { deApi } from '../../api/data'
import { usePrivacidade } from '../../privacidade/usePrivacidade'
import { Botao } from '../../ui/Botao'
import { fraseDaTrilha } from './frases'
import { useTrilha, type LinhaDaTrilha } from './trilha'

const FILTROS = [
  { rotulo: 'Tudo', entidade: undefined },
  { rotulo: 'Fichas', entidade: 'FichaDoPaciente' },
  { rotulo: 'Pacientes', entidade: 'Paciente' },
  { rotulo: 'Sessões', entidade: 'Consulta' },
  { rotulo: 'Anotações', entidade: 'Anotacao' },
  { rotulo: 'Ajustes', entidade: 'Ajustes' },
] as const

const doisDigitos = (n: number) => String(n).padStart(2, '0')

function dataDaLinha(texto: string) {
  const d = deApi(texto)
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)} ${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`
}

function NomeDoPaciente({ linha, privacidade }: { linha: LinhaDaTrilha; privacidade: boolean }) {
  const nome = linha.pacienteNome
  if (!nome) return null
  const conteudo =
    linha.pacienteExcluido || !linha.pacienteId ? (
      nome
    ) : (
      <Link to={`/pacientes/${linha.pacienteId}`}>{nome}</Link>
    )
  return (
    <>
      {' · '}
      <span data-borrado={privacidade ? 'true' : undefined}>{conteudo}</span>
    </>
  )
}

export function TrilhaDeAcesso() {
  const [pagina, setPagina] = useState(0)
  const [filtro, setFiltro] = useState(0)
  const entidade = FILTROS[filtro].entidade
  const trilha = useTrilha(pagina, entidade)
  const { privacidade } = usePrivacidade()

  const dados = trilha.data
  const carregandoPagina = trilha.isPlaceholderData
  const primeira = pagina === 0
  const ultima = !dados || carregandoPagina || pagina >= dados.page.totalPages - 1
  const recentes = useRef<HTMLButtonElement>(null)
  const antigos = useRef<HTMLButtonElement>(null)
  const clicado = useRef<'recentes' | 'antigos' | null>(null)

  useEffect(() => {
    if (!clicado.current || !dados || carregandoPagina) return
    const alvo = clicado.current === 'recentes' ? recentes : antigos
    const outro = clicado.current === 'recentes' ? antigos : recentes
    const alvoDesabilitado = clicado.current === 'recentes' ? primeira : ultima
    ;(alvoDesabilitado ? outro : alvo).current?.focus()
    clicado.current = null
  }, [dados, carregandoPagina, primeira, ultima])

  return (
    <section aria-label="Trilha de acesso" className="cartao ajustes-secao">
      <h2>Trilha de acesso</h2>
      <div className="campo">
        <label className="campo-rotulo" htmlFor="trilha-tipo">
          Tipo
        </label>
        <select
          id="trilha-tipo"
          value={filtro}
          onChange={(e) => {
            setFiltro(Number(e.target.value))
            setPagina(0)
          }}
        >
          {FILTROS.map((f, i) => (
            <option key={f.rotulo} value={i}>
              {f.rotulo}
            </option>
          ))}
        </select>
      </div>

      {trilha.isPending && <p className="sub">Carregando a trilha…</p>}
      {trilha.isError && (
        <p className="anuncio" role="alert">
          Não foi possível carregar a trilha. Tente de novo.
        </p>
      )}
      {dados && dados.content.length === 0 && <p className="sub">Nenhum registro ainda.</p>}
      {dados && dados.content.length > 0 && (
        <ul
          aria-label="Trilha de acesso"
          className="trilha"
          aria-busy={trilha.isPlaceholderData}
        >
          {dados.content.map((linha) => (
            <li key={linha.id}>
              {dataDaLinha(linha.createdAt)} · {fraseDaTrilha(linha.acao, linha.entidade)}
              <NomeDoPaciente linha={linha} privacidade={privacidade} />
            </li>
          ))}
        </ul>
      )}

      <div className="trilha-paginacao">
        <Botao
          type="button"
          variante="secundario"
          ref={recentes}
          disabled={primeira || carregandoPagina}
          onClick={() => {
            clicado.current = 'recentes'
            setPagina((p) => Math.max(0, p - 1))
          }}
        >
          Mais recentes
        </Botao>
        <Botao
          type="button"
          variante="secundario"
          ref={antigos}
          disabled={ultima}
          onClick={() => {
            clicado.current = 'antigos'
            setPagina((p) => p + 1)
          }}
        >
          Mais antigos
        </Botao>
      </div>
    </section>
  )
}
