import { Outlet, useNavigate } from 'react-router-dom'

import { useSessao } from '../sessao/useSessao'
import { Botao } from './Botao'
import { Navegacao } from './Navegacao'

/**
 * Hoje a unica forma de encerrar a sessao e fechar a aba — `sair()` existe em
 * `SessaoProvider` desde a Task 1, testado, e nenhuma tela o chama (Importante
 * 2 da revisao do conjunto). Num produto com prontuario, usado em consultorio
 * com terceiros na sala, isso e o inverso do cuidado que o proprio
 * `SessaoProvider` descreve. `<button>`, nao link: encerrar a sessao e uma
 * ACAO, nao navegacao.
 */
export function Layout() {
  const { sair } = useSessao()
  const navegar = useNavigate()

  async function aoSair() {
    await sair()
    navegar('/entrar', { replace: true })
  }

  return (
    <div className="moldura">
      <aside className="lateral">
        <Navegacao />
        <div className="rodape">
          <Botao variante="secundario" className="mini" onClick={aoSair}>
            Sair
          </Botao>
        </div>
      </aside>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
