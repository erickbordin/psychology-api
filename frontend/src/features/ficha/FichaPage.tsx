import { Link, NavLink, Outlet, useParams } from 'react-router-dom'

import { dataCurta } from '../../ui/data'
import { usePaciente } from '../pacientes/queries'

const ABAS = [
  { para: 'anotacoes', rotulo: 'Anotações' },
  { para: 'lembretes', rotulo: 'Lembretes' },
  { para: 'consultas', rotulo: 'Consultas' },
]

/**
 * Casca da ficha. As abas sao rotas, nao estado local: a spec pede
 * `/pacientes/:id/anotacoes`, e assim recarregar a pagina e compartilhar o link
 * caem na mesma aba.
 */
export function FichaPage() {
  const { pacienteId = '' } = useParams()
  const paciente = usePaciente(pacienteId)

  return (
    <div className="flex flex-col gap-10">
      <header className="flex flex-col gap-3">
        <Link
          to="/pacientes"
          className="w-fit text-sm font-medium text-acento underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
        >
          Voltar
        </Link>
        <h1 className="font-serif text-4xl leading-tight">
          {paciente.data?.nome ?? 'Carregando…'}
        </h1>
        {paciente.data ? (
          <p className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-tinta-2">
            {paciente.data.telefone ? <span>{paciente.data.telefone}</span> : null}
            {paciente.data.email ? <span>{paciente.data.email}</span> : null}
            <span>
              <span className="text-tinta-3">nascimento</span>{' '}
              {dataCurta(paciente.data.dataNascimento)}
            </span>
          </p>
        ) : null}

        <nav aria-label="Seções da ficha" className="mt-4 flex gap-6 border-b border-linha">
          {ABAS.map((aba) => (
            <NavLink
              key={aba.para}
              to={aba.para}
              className={({ isActive }) =>
                `-mb-px border-b-2 pb-3 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento ${
                  isActive ? 'border-acento text-tinta' : 'border-transparent text-tinta-2 hover:text-tinta'
                }`
              }
            >
              {aba.rotulo}
            </NavLink>
          ))}
        </nav>
      </header>

      <Outlet />
    </div>
  )
}
