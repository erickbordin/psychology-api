import type { ReactNode } from 'react'

/**
 * Tabela de verdade, com `<th scope="col">`: a trilha de auditoria e dado
 * tabular, e leitor de tela precisa do cabecalho para anunciar cada celula.
 */
export function Tabela({ colunas, children }: { colunas: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-linha bg-superficie">
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-linha bg-superficie-2">
            {colunas.map((coluna) => (
              <th
                key={coluna}
                scope="col"
                className="px-5 py-3 text-xs font-semibold uppercase tracking-wide text-tinta-2"
              >
                {coluna}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}
