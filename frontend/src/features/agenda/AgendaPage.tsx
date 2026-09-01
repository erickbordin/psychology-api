import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import type { Consulta } from '../../api/tipos'
import { Avatar } from '../../ui/Avatar'
import { Botao, BotaoDeIcone } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { Cartao } from '../../ui/Cartao'
import { Chip, Etiqueta } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { IconeLapis, IconeMais } from '../../ui/icones'
import { dataDeSessao, diaEMes, hojeIso, hora } from '../../ui/data'
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

/**
 * O titulo diz o dia, nao o nome da tela: quem esta aqui sabe que abriu a
 * agenda, e o que ele precisa ler e "hoje" ou o intervalo que escolheu.
 */
function titulo(de: string, ate: string): string {
  if (de === ate) {
    return de === hojeIso() ? `Hoje, ${diaEMes(de)}` : diaEMes(de)
  }
  return `${diaEMes(de)} – ${diaEMes(ate)}`
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
  const umDiaSo = dias.length <= 1

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight">{titulo(de, ate)}</h1>
          {agenda.data ? (
            <p className="text-sm text-tinta-2">
              {consultas.length === 1 ? '1 consulta' : `${consultas.length} consultas`}
            </p>
          ) : null}
        </div>
        <Botao icone={<IconeMais tamanho={18} />} onClick={() => setCriando(true)}>
          Nova consulta
        </Botao>
      </header>

      {/* o intervalo e um controle, nao conteudo: fica num cartao raso, acima da
          lista, para nao competir com os nomes dos pacientes */}
      <Cartao className="flex flex-wrap items-end gap-4 p-4">
        <Campo rotulo="De" tipo="date" valor={de} aoMudar={(valor) => mudarIntervalo(valor, ate)} />
        <Campo rotulo="Até" tipo="date" valor={ate} aoMudar={(valor) => mudarIntervalo(de, valor)} />
        <div className="flex flex-wrap items-center gap-4 pb-1.5">
          <Botao variante="texto" onClick={() => mudarIntervalo(hojeIso(), hojeIso())}>
            Hoje
          </Botao>
          <Botao variante="texto" onClick={() => setCriandoSerie(true)}>
            Série semanal
          </Botao>
        </div>
      </Cartao>

      {agenda.isPending ? <p className="text-sm text-tinta-3">Carregando…</p> : null}

      {agenda.data ? (
        consultas.length === 0 ? (
          <EstadoVazio>Nenhuma consulta neste intervalo.</EstadoVazio>
        ) : (
          <div className="flex flex-col gap-7">
            {dias.map(([dia, doDia]) => (
              <section key={dia} className="flex flex-col gap-2.5">
                {/* com um dia so na tela, o titulo acima ja disse a data */}
                {umDiaSo ? null : (
                  <h2 className="text-sm font-semibold text-tinta-2">{dataDeSessao(dia)}</h2>
                )}
                <ul className="flex flex-col gap-2.5">
                  {doDia.map((consulta) => (
                    <li key={consulta.id}>
                      <Cartao className="flex items-center gap-4 p-3.5">
                        <Avatar nome={consulta.pacienteNome} />
                        <div className="flex min-w-0 flex-1 flex-col">
                          <Link
                            to={`/pacientes/${consulta.pacienteId}`}
                            className="w-fit rounded-sm font-semibold underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
                          >
                            {consulta.pacienteNome}
                          </Link>
                          <span className="text-sm tabular-nums text-tinta-2">
                            {hora(consulta.dtConsulta)}
                          </span>
                        </div>
                        {consulta.serieId ? <Etiqueta>série</Etiqueta> : null}
                        <Chip status={consulta.status} />
                        <BotaoDeIcone
                          rotulo="Editar"
                          icone={<IconeLapis tamanho={18} />}
                          onClick={() => setEmEdicao(consulta)}
                        />
                      </Cartao>
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
