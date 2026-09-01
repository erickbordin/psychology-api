import { Link, NavLink, Outlet, useLocation, useParams } from 'react-router-dom'

import { Avatar } from '../../ui/Avatar'
import { IconeCalendario, IconeEnvelope, IconeTelefone, IconeVoltar } from '../../ui/icones'
import { dataCurta } from '../../ui/data'
import { usePaciente } from '../pacientes/queries'

const ABAS = [
  { para: 'anotacoes', rotulo: 'Anotações' },
  { para: 'lembretes', rotulo: 'Lembretes' },
  { para: 'consultas', rotulo: 'Consultas' },
]

/** As tres abas sao rotas filhas; qualquer outra coisa e a visao geral. */
const EM_ABA = /\/(anotacoes|lembretes|consultas)$/

/**
 * Casca da ficha. As abas sao rotas, nao estado local: a spec pede
 * `/pacientes/:id/anotacoes`, e assim recarregar a pagina e compartilhar o link
 * caem na mesma aba.
 *
 * O cabecalho e o mesmo nas quatro rotas — identidade do paciente fixa no alto,
 * conteudo trocando embaixo. A faixa de abas so aparece dentro de uma aba: na
 * visao geral quem navega sao os cartoes, e duas navegacoes para os mesmos tres
 * destinos, na mesma tela, e uma a mais.
 */
export function FichaPage() {
  const { pacienteId = '' } = useParams()
  const { pathname } = useLocation()
  const paciente = usePaciente(pacienteId)
  const emAba = EM_ABA.test(pathname)

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-4">
        <Link
          to="/pacientes"
          className="flex w-fit items-center gap-1.5 rounded-sm text-sm font-medium text-acento underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
        >
          <IconeVoltar tamanho={16} />
          Voltar
        </Link>

        <div className="flex flex-wrap items-center gap-5">
          <Avatar nome={paciente.data?.nome ?? '?'} medida="g" />
          <div className="flex min-w-0 flex-col gap-2">
            <h1 className="text-2xl font-bold tracking-tight">
              {paciente.data?.nome ?? 'Carregando…'}
            </h1>
            {paciente.data ? (
              <div className="flex flex-wrap gap-x-6 gap-y-1.5 text-sm text-tinta-2">
                {paciente.data.telefone ? (
                  <span className="flex items-center gap-2">
                    <IconeTelefone tamanho={16} className="text-tinta-3" />
                    <span className="tabular-nums">{paciente.data.telefone}</span>
                  </span>
                ) : null}
                <span className="flex items-center gap-2">
                  <IconeCalendario tamanho={16} className="text-tinta-3" />
                  <span className="tabular-nums">{dataCurta(paciente.data.dataNascimento)}</span>
                </span>
                {paciente.data.email ? (
                  <span className="flex items-center gap-2">
                    <IconeEnvelope tamanho={16} className="text-tinta-3" />
                    {paciente.data.email}
                  </span>
                ) : null}
              </div>
            ) : null}
          </div>
        </div>

        {emAba ? (
          <nav aria-label="Seções da ficha" className="flex gap-6 border-b border-linha">
            {ABAS.map((aba) => (
              <NavLink
                key={aba.para}
                to={aba.para}
                className={({ isActive }) =>
                  `-mb-px border-b-2 pb-3 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento ${
                    isActive
                      ? 'border-acento text-acento'
                      : 'border-transparent text-tinta-2 hover:text-tinta'
                  }`
                }
              >
                {aba.rotulo}
              </NavLink>
            ))}
          </nav>
        ) : null}
      </header>

      <Outlet />
    </div>
  )
}
