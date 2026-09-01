/**
 * A inicial no lugar da foto: o produto nunca pede retrato de paciente, e uma
 * lista de circulos vazios seria pior que uma lista de letras. `aria-hidden`
 * porque o nome inteiro esta sempre escrito ao lado — leitor de tela nao precisa
 * ouvir "A" antes de "Ana Paula Souza".
 */
const MEDIDA = {
  p: 'size-10 text-sm',
  m: 'size-11 text-base',
  g: 'size-20 text-2xl',
} as const

export function Avatar({ nome, medida = 'p' }: { nome: string; medida?: keyof typeof MEDIDA }) {
  const inicial = nome.trim().charAt(0).toUpperCase() || '?'

  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center rounded-full bg-acento-suave font-semibold text-acento-tinta ${MEDIDA[medida]}`}
    >
      {inicial}
    </span>
  )
}
