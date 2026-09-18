import type { MouseEvent } from 'react'

/**
 * A guarda de saida com rascunho pendente — a mitigacao que o design system
 * (`specs/2026-08-26-frontend-design.md`, "Rascunho de anotacao") exige junto
 * com a decisao de manter o texto SO EM MEMORIA: "faz parte do escopo, nao e
 * polimento". Sem ela, escrever a anotacao e clicar em "← Pacientes" por
 * engano apaga o texto sem nem perguntar.
 *
 * **Por que nao `useBlocker`.** Ele so existe sob um data router
 * (`createBrowserRouter`), e este app monta `<BrowserRouter>` + `<Routes>` em
 * `main.tsx`/`rotas.tsx`. Trocar a raiz por causa de uma caixa de texto seria
 * mexer no roteamento inteiro de quatro telas para resolver o problema de uma.
 * Entao a guarda tem duas metades, cada uma cobrindo uma forma de sair:
 *
 * - `registrarRascunho()` — enquanto ha rascunho, um `beforeunload` cuida de
 *   fechar a aba, recarregar e sair do site
 * - `podeSair()` — os links internos perguntam antes de navegar
 *
 * O estado e um modulo e nao um contexto de proposito: quem escreve o rascunho
 * (`RegistroDaSessao`, no fundo da linha do tempo) e quem sai (a navegacao
 * lateral, irma do `<Outlet>`) nao tem ancestral comum abaixo do app inteiro,
 * e enfiar um provider na raiz para isso espalharia a ficha pelo layout.
 */

/** Quantos rascunhos sujos existem agora. Contador, e nao booleano: a ficha
 *  pode ter mais de uma sessao com caixa de registro aberta ao mesmo tempo. */
let sujos = 0

const AVISO =
  'A anotação escrita ainda não foi registrada e será perdida. Deseja sair mesmo assim?'

function aoDescarregar(evento: BeforeUnloadEvent) {
  evento.preventDefault()
  // navegadores atuais ignoram o texto e mostram o proprio; `returnValue`
  // continua sendo o que liga o dialogo em parte deles
  evento.returnValue = AVISO
}

/**
 * Liga/desliga a guarda de um rascunho. Devolve a funcao de baixa, para o
 * `useEffect` que a chamou nao deixar contador pendurado ao desmontar.
 */
export function registrarRascunho(): () => void {
  sujos += 1
  if (sujos === 1) window.addEventListener('beforeunload', aoDescarregar)

  let baixado = false
  return () => {
    if (baixado) return
    baixado = true
    sujos -= 1
    if (sujos === 0) window.removeEventListener('beforeunload', aoDescarregar)
  }
}

export function haRascunhoPendente(): boolean {
  return sujos > 0
}

/**
 * Pergunta antes de sair. `true` = pode navegar. Sem rascunho pendente nao
 * pergunta nada — a guarda que avisa em formulario limpo vira ruido, e ruido
 * ensina o usuario a clicar em "sair" sem ler.
 */
export function podeSair(): boolean {
  if (!haRascunhoPendente()) return true
  return window.confirm(AVISO)
}

/**
 * Handler pronto para um `<Link>`/`<NavLink>` que LEVA PARA FORA de onde o
 * rascunho vive. Cancelar a navegacao e `preventDefault()` no proprio clique —
 * o router so navega no clique que ele ve passar.
 */
export function aoClicarSaindo(evento: MouseEvent): void {
  if (!podeSair()) evento.preventDefault()
}

/** So para o teste: zera o contador entre casos. */
export function esquecerRascunhos(): void {
  if (sujos > 0) window.removeEventListener('beforeunload', aoDescarregar)
  sujos = 0
}
