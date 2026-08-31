import { NavLink, Outlet } from 'react-router-dom'

import { useSessao } from '../features/auth/useSessao'
import { Botao } from './Botao'

const ITENS = [
  { para: '/agenda', rotulo: 'Agenda' },
  { para: '/pacientes', rotulo: 'Pacientes' },
  { para: '/auditoria', rotulo: 'Trilha' },
]

/**
 * A moldura e constante: mesma barra, mesma coluna, mesma medida em toda tela.
 * O que muda e o conteudo dentro dela. Uma coluna medida em vez da barra lateral
 * de 240px que segurava um unico link — este produto tem duas telas, nao um
 * painel.
 */
export function Layout() {
  const { sair } = useSessao()

  return (
    <div className="min-h-screen">
      <nav
        aria-label="Navegação principal"
        className="sticky top-0 z-10 border-b border-linha bg-fundo/90 backdrop-blur"
      >
        <div className="mx-auto flex h-14 w-full max-w-3xl items-center gap-8 px-6">
          <span className="font-serif text-lg leading-none">psychology</span>

          <ul className="flex flex-1 items-center gap-6">
            {ITENS.map((item) => (
              <li key={item.para}>
                <NavLink
                  to={item.para}
                  className={({ isActive }) =>
                    `text-sm font-medium underline-offset-8 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento ${
                      isActive ? 'text-tinta underline decoration-acento decoration-2' : 'text-tinta-2 hover:text-tinta'
                    }`
                  }
                >
                  {item.rotulo}
                </NavLink>
              </li>
            ))}
          </ul>

          <Botao variante="texto" onClick={() => void sair()}>
            Sair
          </Botao>
        </div>
      </nav>

      <main className="mx-auto w-full max-w-3xl px-6 pt-12 pb-24">
        <Outlet />
      </main>
    </div>
  )
}
