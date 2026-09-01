import type { StatusConsulta } from '../api/tipos'

/**
 * Segue o enum `Status` da API — os quatro valores existem no servidor e a tela
 * nao inventa um quinto. Pastilha, e nao texto solto: numa linha com avatar,
 * nome e hora, o estado precisa de uma forma propria para nao ser lido como
 * mais um pedaco do nome.
 */
const COR: Record<StatusConsulta, string> = {
  AGENDADA: 'bg-acento-suave text-acento-tinta',
  REALIZADA: 'bg-sucesso-suave text-sucesso',
  CANCELADA: 'bg-superficie-2 text-tinta-3',
  FALTOU: 'bg-atencao-suave text-atencao',
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
      className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${COR[status]}`}
    >
      {ROTULO[status]}
    </span>
  )
}

/** Etiqueta neutra, sem enum atras: "série", "concluído". */
export function Etiqueta({ children, tom = 'neutro' }: { children: string; tom?: 'neutro' | 'sucesso' }) {
  return (
    <span
      className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${
        tom === 'sucesso' ? 'bg-sucesso-suave text-sucesso' : 'bg-superficie-2 text-tinta-2'
      }`}
    >
      {children}
    </span>
  )
}
