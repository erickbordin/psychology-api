import { Outlet, useNavigate } from 'react-router-dom'

import { PrivacidadeProvider } from '../privacidade/PrivacidadeProvider'
import { usePrivacidade } from '../privacidade/usePrivacidade'
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
    <PrivacidadeProvider>
      <div className="moldura">
        <aside className="lateral">
          <Navegacao />
          <ControlePrivacidade />
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
    </PrivacidadeProvider>
  )
}

function ControlePrivacidade() {
  const { privacidade, alternar } = usePrivacidade()

  return (
    <div className="acoes-cabecalho-dia">
      <Botao variante="secundario" aria-pressed={privacidade} onClick={alternar}>
        Modo privacidade
      </Botao>
    </div>
  )
}
