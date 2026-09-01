import { Link, useParams, useSearchParams } from 'react-router-dom'

import { Cartao } from '../../ui/Cartao'
import { Chip, Etiqueta } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { Paginacao } from '../../ui/Paginacao'
import { IconeCalendario } from '../../ui/icones'
import { dataHora } from '../../ui/data'
import { useConsultasDoPaciente } from './queries'

/**
 * So leitura, como a spec define: agendar e remarcar acontecem na agenda, onde o
 * conflito de horario com outros pacientes e visivel.
 */
export function ConsultasTab() {
  const { pacienteId = '' } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const pagina = Number(parametros.get('page') ?? '0')

  const consultas = useConsultasDoPaciente(pacienteId, pagina)

  const total = consultas.data?.page.totalElements ?? 0
  const totalPaginas = consultas.data?.page.totalPages ?? 1
  const itens = consultas.data?.content ?? []

  return (
    <section className="flex flex-col gap-4">
      <p className="text-sm text-tinta-2">
        {total === 1 ? '1 consulta' : `${total} consultas`}
      </p>

      {itens.length === 0 ? (
        consultas.isPending ? null : (
          <EstadoVazio>
            Nenhuma consulta ainda. Agende pela{' '}
            <Link
              to="/agenda"
              className="font-medium text-acento underline underline-offset-4"
            >
              agenda
            </Link>
            .
          </EstadoVazio>
        )
      ) : (
        <ul className="flex flex-col gap-2.5">
          {itens.map((consulta) => (
            <li key={consulta.id}>
              <Cartao className="flex flex-wrap items-center gap-x-4 gap-y-2 p-4">
                <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg bg-acento-suave text-acento">
                  <IconeCalendario tamanho={18} />
                </span>
                <span className="min-w-40 flex-1 text-sm tabular-nums">
                  {dataHora(consulta.dtConsulta)}
                </span>
                {consulta.serieId ? <Etiqueta>série semanal</Etiqueta> : null}
                <Chip status={consulta.status} />
              </Cartao>
            </li>
          ))}
        </ul>
      )}

      <Paginacao
        pagina={pagina}
        totalPaginas={totalPaginas}
        contagem={total === 1 ? '1 consulta' : `${total} consultas`}
        aoMudar={(destino) => setParametros({ page: String(destino) })}
      />
    </section>
  )
}
