import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useSessao } from '../features/auth/useSessao'

export function RotaProtegida() {
  const { autenticado, carregando } = useSessao()
  const local = useLocation()

  if (carregando) {
    return <p className="p-14 text-sm text-tinta-3">Carregando…</p>
  }

  if (autenticado) {
    return <Outlet />
  }

  /**
   * O destino viaja no state para o login devolver o usuario onde ele queria
   * estar. Sem isso, quem abre um link direto para a ficha de um paciente e
   * obrigado a entrar cai na lista e tem de reencontrar o paciente na mao.
   */
  return <Navigate to="/login" replace state={{ de: `${local.pathname}${local.search}` }} />
}
