import type { ReactNode } from 'react'

/**
 * Tabela de verdade, com `<th scope="col">`: a trilha de auditoria e dado
 * tabular, e leitor de tela precisa do cabecalho para anunciar cada celula.
 */
export function Tabela({ colunas, children }: { colunas: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-y border-linha">
            {colunas.map((coluna) => (
              <th
                key={coluna}
                scope="col"
                className="py-3 pr-6 text-sm font-medium text-tinta-2 last:pr-0"
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
