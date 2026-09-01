import type { RouteObject } from 'react-router-dom'

import { AgendaPage } from './features/agenda/AgendaPage'
import { AuditoriaPage } from './features/auditoria/AuditoriaPage'
import { LoginPage } from './features/auth/LoginPage'
import { AnotacoesTab } from './features/ficha/AnotacoesTab'
import { ConsultasTab } from './features/ficha/ConsultasTab'
import { FichaPage } from './features/ficha/FichaPage'
import { LembretesTab } from './features/ficha/LembretesTab'
import { VisaoGeral } from './features/ficha/VisaoGeral'
import { InicioPage } from './features/inicio/InicioPage'
import { PacientesPage } from './features/pacientes/PacientesPage'
import { Layout } from './ui/Layout'
import { RotaProtegida } from './ui/RotaProtegida'

export const definicaoDeRotas: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <RotaProtegida />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <InicioPage /> },
          { path: 'agenda', element: <AgendaPage /> },
          { path: 'pacientes', element: <PacientesPage /> },
          {
            path: 'pacientes/:pacienteId',
            element: <FichaPage />,
            children: [
              { index: true, element: <VisaoGeral /> },
              { path: 'anotacoes', element: <AnotacoesTab /> },
              { path: 'lembretes', element: <LembretesTab /> },
              { path: 'consultas', element: <ConsultasTab /> },
            ],
          },
          { path: 'auditoria', element: <AuditoriaPage /> },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <p className="p-14 text-sm text-tinta-2">Página não encontrada.</p>,
  },
]
