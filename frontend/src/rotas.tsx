import { Route, Routes } from 'react-router-dom'

import { RotaProtegida } from './sessao/RotaProtegida'
import { AgendaPage } from './telas/agenda/AgendaPage'
import { AjustesPage } from './telas/ajustes/AjustesPage'
import { CadastroPage } from './telas/cadastro/CadastroPage'
import { DescadastroPage } from './telas/descadastro/DescadastroPage'
import { FichaPage } from './telas/ficha/FichaPage'
import { FinanceiroPage } from './telas/financeiro/FinanceiroPage'
import { HojePage } from './telas/hoje/HojePage'
import { LoginPage } from './telas/login/LoginPage'
import { PacientesPage } from './telas/pacientes/PacientesPage'
import { TermosPage } from './telas/termos/TermosPage'
import { Layout } from './ui/Layout'

export function Rotas() {
  return (
    <Routes>
      <Route path="/entrar" element={<LoginPage />} />
      <Route path="/cadastro" element={<CadastroPage />} />
      <Route path="/termos" element={<TermosPage documento="termos" />} />
      <Route path="/privacidade" element={<TermosPage documento="privacidade" />} />
      <Route path="/descadastro/:token" element={<DescadastroPage />} />
      <Route
        element={
          <RotaProtegida>
            <Layout />
          </RotaProtegida>
        }
      >
        <Route path="/" element={<HojePage />} />
        <Route path="/agenda" element={<AgendaPage />} />
        <Route path="/pacientes" element={<PacientesPage />} />
        <Route path="/pacientes/:pacienteId" element={<FichaPage />} />
        <Route path="/financeiro" element={<FinanceiroPage />} />
        <Route path="/ajustes" element={<AjustesPage />} />
      </Route>
    </Routes>
  )
}
