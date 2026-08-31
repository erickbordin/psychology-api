import { Link, useParams, useSearchParams } from 'react-router-dom'

import { Chip } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { Paginacao } from '../../ui/Paginacao'
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
            Nenhuma consulta ainda. Agende pela <Link to="/agenda" className="text-acento underline underline-offset-4">agenda</Link>.
          </EstadoVazio>
        )
      ) : (
        <ul className="border-t border-linha">
          {itens.map((consulta) => (
            <li
              key={consulta.id}
              className="flex flex-wrap items-center gap-x-5 gap-y-2 border-b border-linha py-4"
            >
              <span className="flex-1 text-sm tabular-nums">{dataHora(consulta.dtConsulta)}</span>
              {consulta.serieId ? (
                <span className="text-sm text-tinta-3">série semanal</span>
              ) : null}
              <Chip status={consulta.status} />
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
