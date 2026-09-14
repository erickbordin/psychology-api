import { Route, Routes } from 'react-router-dom'

import { RotaProtegida } from './sessao/RotaProtegida'
import { AgendaPage } from './telas/agenda/AgendaPage'
import { HojePage } from './telas/hoje/HojePage'
import { LoginPage } from './telas/login/LoginPage'
import { Layout } from './ui/Layout'

function AindaNaoNestaFatia({ nome }: { nome: string }) {
  return (
    <p>
      {nome} chega numa fatia proxima. Por enquanto, Hoje e Agenda ja funcionam.
    </p>
  )
}

export function Rotas() {
  return (
    <Routes>
      <Route path="/entrar" element={<LoginPage />} />
      <Route
        element={
          <RotaProtegida>
            <Layout />
          </RotaProtegida>
        }
      >
        <Route path="/" element={<HojePage />} />
        <Route path="/agenda" element={<AgendaPage />} />
        <Route path="/pacientes" element={<AindaNaoNestaFatia nome="Pacientes" />} />
        <Route path="/financeiro" element={<AindaNaoNestaFatia nome="Financeiro" />} />
        <Route path="/ajustes" element={<AindaNaoNestaFatia nome="Ajustes" />} />
      </Route>
    </Routes>
  )
}
