import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import type { Consulta } from '../../api/tipos'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { Chip } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { dataDeSessao, hojeIso, hora } from '../../ui/data'
import { ConsultaDialogo } from './ConsultaDialogo'
import { NovaConsultaDialogo } from './NovaConsultaDialogo'
import { SerieDialogo } from './SerieDialogo'
import { useAgenda } from './queries'

/** Agrupa por dia preservando a ordem que a API devolveu. */
function porDia(consultas: Consulta[]): [string, Consulta[]][] {
  const dias = new Map<string, Consulta[]>()
  for (const consulta of consultas) {
    const dia = consulta.dtConsulta.split('T')[0]
    dias.set(dia, [...(dias.get(dia) ?? []), consulta])
  }
  return [...dias.entries()].sort(([a], [b]) => a.localeCompare(b))
}

export function AgendaPage() {
  /** Sem parametros, hoje — e o intervalo vive na URL, como o filtro de pacientes. */
  const [parametros, setParametros] = useSearchParams()
  const de = parametros.get('de') ?? hojeIso()
  const ate = parametros.get('ate') ?? de

  const agenda = useAgenda(de, ate)
  const [criando, setCriando] = useState(false)
  const [criandoSerie, setCriandoSerie] = useState(false)
  const [emEdicao, setEmEdicao] = useState<Consulta | null>(null)

  function mudarIntervalo(novoDe: string, novoAte: string) {
    setParametros({ de: novoDe, ate: novoAte }, { replace: true })
  }

  const consultas = agenda.data ?? []
  const dias = porDia(consultas)

  return (
    <div className="flex flex-col gap-10">
      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-linha pb-6">
        <div className="flex flex-col gap-2">
          <h1 className="font-serif text-4xl leading-none">Agenda</h1>
          {agenda.data ? (
            <p className="text-sm text-tinta-2">
              {consultas.length === 1 ? '1 consulta' : `${consultas.length} consultas`}
            </p>
          ) : null}
        </div>
        <div className="flex flex-wrap items-center gap-4">
          <Botao onClick={() => setCriandoSerie(true)} variante="texto">
            Série semanal
          </Botao>
          <Botao onClick={() => setCriando(true)}>Nova consulta</Botao>
        </div>
      </header>

      <div className="flex flex-wrap items-end gap-6">
        <Campo rotulo="De" tipo="date" valor={de} aoMudar={(valor) => mudarIntervalo(valor, ate)} />
        <Campo rotulo="Até" tipo="date" valor={ate} aoMudar={(valor) => mudarIntervalo(de, valor)} />
        <Botao variante="texto" onClick={() => mudarIntervalo(hojeIso(), hojeIso())}>
          Hoje
        </Botao>
      </div>

      {agenda.isPending ? <p className="text-sm text-tinta-3">Carregando…</p> : null}

      {agenda.data ? (
        consultas.length === 0 ? (
          <EstadoVazio>Nenhuma consulta neste intervalo.</EstadoVazio>
        ) : (
          <div className="flex flex-col gap-10">
            {dias.map(([dia, doDia]) => (
              <section key={dia} className="flex flex-col gap-3">
                <h2 className="text-sm font-medium text-tinta-2">{dataDeSessao(dia)}</h2>
                <ul className="border-t border-linha">
                  {doDia.map((consulta) => (
                    <li
                      key={consulta.id}
                      className="flex flex-wrap items-center gap-x-5 gap-y-2 border-b border-linha py-4"
                    >
                      <span className="w-14 shrink-0 text-sm tabular-nums">
                        {hora(consulta.dtConsulta)}
                      </span>
                      <Link
                        to={`/pacientes/${consulta.pacienteId}`}
                        className="flex-1 font-serif text-lg underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
                      >
                        {consulta.pacienteNome}
                      </Link>
                      {consulta.serieId ? (
                        <span className="text-sm text-tinta-3">série</span>
                      ) : null}
                      <Chip status={consulta.status} />
                      <Botao variante="texto" onClick={() => setEmEdicao(consulta)}>
                        Editar
                      </Botao>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )
      ) : null}

      {criando ? <NovaConsultaDialogo aoFechar={() => setCriando(false)} /> : null}
      {criandoSerie ? <SerieDialogo aoFechar={() => setCriandoSerie(false)} /> : null}
      {emEdicao ? (
        <ConsultaDialogo consulta={emEdicao} aoFechar={() => setEmEdicao(null)} />
      ) : null}
    </div>
  )
}
