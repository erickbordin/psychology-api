import type { StatusConsulta } from '../api/tipos'

/**
 * Segue o enum `Status` da API — os quatro valores existem no servidor e a tela
 * nao inventa um quinto. Sem caixa: o acento e a borda ficam reservados para o
 * que se pode clicar, e um rotulo de estado emoldurado ao lado de um botao de
 * verdade so faz o olho parar no lugar errado.
 */
const COR: Record<StatusConsulta, string> = {
  AGENDADA: 'text-tinta-2',
  REALIZADA: 'text-sucesso',
  CANCELADA: 'text-tinta-3',
  FALTOU: 'text-atencao',
}

const ROTULO: Record<StatusConsulta, string> = {
  AGENDADA: 'agendada',
  REALIZADA: 'realizada',
  CANCELADA: 'cancelada',
  FALTOU: 'faltou',
}

export function Chip({ status }: { status: StatusConsulta }) {
  return (
    <span
      className={`shrink-0 text-sm ${COR[status]}`}
    >
      {ROTULO[status]}
    </span>
  )
}
