import type { ComponentPropsWithRef } from 'react'

type Props = ComponentPropsWithRef<'button'> & {
  variante?: 'primario' | 'secundario'
}

/** Acao e FORMA, nao cor (ver o comentario em estilo/tokens.css): pilula =
 *  acao, bloco chapado = evento. Por isso botao e evento podem ser azuis sem
 *  confundir. `data-variante` continua no DOM (nao faz mal manter), mas quem
 *  desenha o botao agora e a classe `.botao`/`.botao.secundario` de
 *  estilo/estilo.css — sem ela o botao era HTML nu (Critico 2 da revisao do
 *  conjunto). `className` do chamador (ex.: "mini") entra junto. */
export function Botao({ variante = 'primario', className, ...resto }: Props) {
  const classes = ['botao', variante === 'secundario' ? 'secundario' : '', className]
    .filter(Boolean)
    .join(' ')
  return <button data-variante={variante} className={classes} {...resto} />
}
