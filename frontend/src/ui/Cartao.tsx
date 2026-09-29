import type { ReactNode } from 'react'

/** `.cartao` (estilo/estilo.css): fundo solido + raio, sem borda — a
 *  separacao vem do espaco e do fundo da pagina, nao de moldura por bloco.
 *  `destaque` e o cartao da proxima sessao: fundo de superficie + barra a esquerda como anel interno. */
export function Cartao({ children, destaque = false }: { children: ReactNode; destaque?: boolean }) {
  return <article className={destaque ? 'cartao destaque' : 'cartao'}>{children}</article>
}
