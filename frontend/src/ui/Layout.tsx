import { NavLink, Outlet } from 'react-router-dom'

import { useSessao } from '../features/auth/useSessao'
import { BotaoDeIcone } from './Botao'
import { IconeCalendario, IconeCasa, IconePessoas, IconePrancheta, IconeSair } from './icones'

const ITENS = [
  { para: '/', rotulo: 'Início', icone: IconeCasa, exato: true },
  { para: '/agenda', rotulo: 'Agenda', icone: IconeCalendario, exato: false },
  { para: '/pacientes', rotulo: 'Pacientes', icone: IconePessoas, exato: false },
  { para: '/auditoria', rotulo: 'Trilha', icone: IconePrancheta, exato: false },
]

/**
 * A moldura e constante: mesma coluna de icones a esquerda, mesma medida de
 * conteudo em toda tela. Barra de icones em vez de barra de rotulos porque sao
 * quatro destinos fixos — o produto inteiro cabe na largura de um dedo, e a
 * tela sobra para o que importa, que e o paciente.
 *
 * Icone sem texto exige nome acessivel: cada link leva `aria-label`, e o mesmo
 * texto vai no `title` para quem passa o mouse.
 */
export function Layout() {
  const { sair } = useSessao()

  return (
    <div className="flex min-h-screen">
      <nav
        aria-label="Navegação principal"
        className="sticky top-0 flex h-screen w-16 shrink-0 flex-col items-center gap-1.5 border-r border-linha bg-superficie py-4"
      >
        <ul className="flex flex-col items-center gap-1.5">
          {ITENS.map((item) => (
            <li key={item.para}>
              <NavLink
                to={item.para}
                end={item.exato}
                aria-label={item.rotulo}
                title={item.rotulo}
                className={({ isActive }) =>
                  `inline-flex size-11 items-center justify-center rounded-xl transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento ${
                    isActive
                      ? 'bg-acento text-white shadow-[0_1px_2px_rgba(37,99,235,0.35)]'
                      : 'text-tinta-3 hover:bg-superficie-2 hover:text-tinta'
                  }`
                }
              >
                <item.icone tamanho={21} />
              </NavLink>
            </li>
          ))}
        </ul>

        <span className="flex-1" />

        <BotaoDeIcone
          rotulo="Sair"
          icone={<IconeSair tamanho={21} />}
          onClick={() => void sair()}
        />
      </nav>

      <main className="min-w-0 flex-1 px-5 py-7 sm:px-8 sm:py-9">
        <div className="mx-auto w-full max-w-4xl">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
