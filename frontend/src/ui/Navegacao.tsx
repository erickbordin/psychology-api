import { NavLink } from 'react-router-dom'

const DESTINOS = [
  { para: '/', rotulo: 'Hoje' },
  { para: '/agenda', rotulo: 'Agenda' },
  { para: '/pacientes', rotulo: 'Pacientes' },
  { para: '/financeiro', rotulo: 'Financeiro' },
  { para: '/ajustes', rotulo: 'Ajustes' },
]

/** Cinco destinos com ROTULO ESCRITO (spec 4). O icon-only anterior saiu por
 *  decisao do dono: quatro icones mudos numa coluna de 64px e adivinhacao.
 *  `.ativo` (estilo/estilo.css) e a pilula suave do item selecionado — acao e
 *  forma, nao cor (ver tokens.css). */
export function Navegacao() {
  return (
    <nav aria-label="Navegacao principal">
      <ul>
        {DESTINOS.map((d) => (
          <li key={d.para}>
            <NavLink
              to={d.para}
              end={d.para === '/'}
              className={({ isActive }) => (isActive ? 'ativo' : undefined)}
            >
              {d.rotulo}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
