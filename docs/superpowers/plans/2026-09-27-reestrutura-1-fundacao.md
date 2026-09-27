# Reestrutura do frontend — Plano 1: Fundação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar a camada de tokens, os cinco primitivos de UI e o shell para a
linguagem Apple aprovada, e elevar o modo privacidade para o shell — sem
reestruturar nenhuma tela ainda.

**Architecture:** A troca acontece de baixo para cima. `tokens.css` é
substituído inteiro; `estilo.css` e `dialogo.css` passam a consumir os tokens
novos. Como todas as telas já desenham através dos primitivos (`Botao`, `Campo`,
`Chip`, `Cartao`, `EstadoVazio`) e do `Layout`, trocar essa base repinta o
produto inteiro sem tocar em nenhuma página. A última tarefa é a única que muda
comportamento: o estado do modo privacidade sai de `HojePage` e passa a viver no
shell.

**Tech Stack:** React 19, TypeScript, Vite, Tailwind v4 (só o preflight), CSS
escrito à mão em `src/estilo/`, Vitest + Testing Library, Playwright para e2e.

**Spec:** `docs/superpowers/specs/2026-09-27-reestrutura-frontend-design.md`
(decisões e invariantes) e `docs/superpowers/specs/2026-09-27-linguagem-apple.md`
(valor exato de cada token, curva e tratamento de controle).

## Global Constraints

- **Sem comentário novo em código.** Instrução permanente do dono. Não remova os
  comentários que já existem nos arquivos — apenas não crie novos. O porquê de
  cada decisão vai na mensagem de commit e no spec, não no fonte.
- **Nada de commitar.** Toda tarefa termina entregando o comando de commit ao
  dono, que executa. Não rode `git commit` nem `git add`.
- **Teste e navegação por rótulo escrito, nunca por `className`.** Se um teste
  precisar de classe para passar, o teste está errado.
- **Copy em português intocada.** Nenhuma string que o usuário lê muda nesta
  reestrutura.
- **Nenhuma informação removida** de nenhuma tela.
- **Tokens nascem em par claro/escuro, mas só o claro é implementado e testado**
  (D4 do spec). O bloco escuro entra declarado e sem consumidor.
- **Movimento fica fora deste plano.** Tem orçamento próprio, já levantado.
- **Baseline que não pode regredir:** `ASDF_NODEJS_VERSION=20.19.1 npm test` →
  18 arquivos, 86 testes, 0 falhas. `ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit`
  → exit 0. `ASDF_NODEJS_VERSION=20.19.1 npm run e2e` → 2 passed (exige backend
  no ar em :8080).
- **Todos os caminhos são relativos a `frontend/`.**

---

## File Structure

| Arquivo | Responsabilidade depois deste plano |
|---|---|
| `src/estilo/tokens.css` | **Substituído.** Bloco de tokens da linguagem nova, claro + escuro declarado. |
| `src/estilo/estilo.css` | **Modificado.** Passa a consumir os tokens novos. Nenhuma tela reestruturada. |
| `src/estilo/dialogo.css` | **Modificado.** Superfície e campo do diálogo na linguagem nova. |
| `src/ui/Botao.tsx` | Inalterado na API; ganha feedback de pressão via CSS. |
| `src/ui/Chip.tsx` | **Modificado.** Ganha glifo por variante — estado deixa de depender só de cor. |
| `src/ui/Campo.tsx` | Inalterado na API. |
| `src/ui/Cartao.tsx` | Inalterado. |
| `src/ui/EstadoVazio.tsx` | Inalterado. |
| `src/ui/Layout.tsx` | **Modificado.** Passa a hospedar o estado do modo privacidade. |
| `src/ui/Navegacao.tsx` | Inalterado na API. |
| `src/privacidade/PrivacidadeProvider.tsx` | **Novo.** Contexto do modo privacidade. |
| `src/privacidade/usePrivacidade.ts` | **Novo.** Hook de leitura e toggle. |
| `src/telas/hoje/HojePage.tsx` | **Modificado.** Deixa de ter estado local de privacidade; passa a ler do hook. |

---

## Ordem e colisões

Sequencial: 1 → 2 → 3 → 4 → 5 → 6. A Tarefa 1 redefine os tokens que todas as
outras consomem; nenhuma tarefa posterior é segura antes dela. As Tarefas 2 a 4
tocam `estilo.css` em regiões diferentes e ainda assim são sequenciais, porque
cada uma reescreve regras que a anterior deixou meio-migradas.

---

### Task 1: Tokens da linguagem nova

**Files:**
- Modify: `src/estilo/tokens.css` (substituição integral do bloco `:root`)
- Test: `src/estilo/tokens.test.ts` (criar)

**Interfaces:**
- Produces: as custom properties consumidas por todas as tarefas seguintes.
  Nomes exatos em `docs/superpowers/specs/2026-09-27-linguagem-apple.md`, seção
  "0. Bloco de tokens — copie e cole".

- [ ] **Step 1: Escreva o teste que falha**

O risco real desta tarefa não é estética, é **token órfão**: uma regra em
`estilo.css` continuar apontando para um token que deixou de existir, o que o
navegador resolve silenciosamente como valor vazio. Nenhum teste de componente
pega isso. Este pega.

Crie `src/estilo/tokens.test.ts`:

```ts
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const raiz = join(__dirname, '..', '..')
const ler = (p: string) => readFileSync(join(raiz, 'src', 'estilo', p), 'utf-8')

const definidos = (css: string) =>
  new Set(Array.from(css.matchAll(/^\s*(--[\w-]+)\s*:/gm), (m) => m[1]))

const usados = (css: string) =>
  new Set(Array.from(css.matchAll(/var\(\s*(--[\w-]+)/g), (m) => m[1]))

describe('tokens', () => {
  it('nenhuma folha usa token que nao existe', () => {
    const tokens = definidos(ler('tokens.css'))
    const orfaos: string[] = []

    for (const folha of ['estilo.css', 'dialogo.css', 'tokens.css']) {
      const css = ler(folha)
      const locais = definidos(css)
      for (const t of usados(css)) {
        if (!tokens.has(t) && !locais.has(t)) orfaos.push(`${folha}: ${t}`)
      }
    }

    expect(orfaos).toEqual([])
  })

  it('declara o par claro e escuro', () => {
    const css = ler('tokens.css')
    expect(css).toContain('prefers-color-scheme: dark')
  })

  it('mantem o kill-switch de movimento reduzido', () => {
    expect(ler('tokens.css')).toContain('prefers-reduced-motion: reduce')
  })
})
```

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/estilo/tokens.test.ts
```

Esperado: FAIL no segundo caso — `tokens.css` hoje não tem bloco
`prefers-color-scheme: dark`. O primeiro e o terceiro passam desde já; eles
existem para **prender a regressão** quando você trocar o arquivo no passo
seguinte.

- [ ] **Step 3: Substitua o bloco de tokens**

Abra `docs/superpowers/specs/2026-09-27-linguagem-apple.md`, seção "0. Bloco de
tokens — copie e cole". Copie o bloco `:root { … }` **e** o bloco
`@media (prefers-color-scheme: dark) { … }` para `src/estilo/tokens.css`,
substituindo o `:root` atual.

Três coisas do arquivo atual que **ficam**, exatamente como estão:

1. O bloco `body { … }`, adaptado para os tokens novos: `background` passa a
   `var(--fundo-base)` e `color` a `var(--tinta)`.
2. A regra `:where(input, select, textarea, button) { font-family: inherit; }`.
3. O bloco `@media (prefers-reduced-motion: reduce)` inteiro, sem alteração.

Uma coisa **sai**: o `@import url("https://fonts.googleapis.com/…Roboto…")` na
primeira linha. A linguagem nova usa `--fonte-ui`, que é stack de sistema — não
há fonte remota para buscar. Remova o `@import`.

> `frontend/index.html` também carrega a fonte Inter do Google Fonts e nada a
> usa. É peso morto pré-existente e o spec o lista fora de escopo; deixe como
> está.

- [ ] **Step 4: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/estilo/tokens.test.ts
```

Esperado: PASS nos três.

Se o primeiro caso falhar listando órfãos, é **achado esperado**: são as regras
de `estilo.css` que ainda apontam para tokens do Calendar. Anote a lista — as
Tarefas 2 a 5 a consomem — e faça o teste passar temporariamente definindo os
tokens antigos ainda usados como aliases no fim de `tokens.css`, num bloco
marcado para morrer na Tarefa 5.

- [ ] **Step 5: Rode a suíte inteira**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 19 arquivos, 89 testes, 0 falhas. `tsc` exit 0.

- [ ] **Step 6: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/estilo/tokens.css frontend/src/estilo/tokens.test.ts
git commit -m "feat(tokens): a linguagem nova entra pela base, com guarda contra token orfao"
```

---

### Task 2: O chip para de depender de cor

**Files:**
- Modify: `src/ui/Chip.tsx`
- Modify: `src/estilo/estilo.css:129-142` (regras `.chip`)
- Test: `src/ui/Chip.test.tsx` (criar)

**Interfaces:**
- Consumes: tokens `--realizada-fundo`, `--realizada-tinta`, `--faltou-fundo`,
  `--faltou-tinta` e os equivalentes de alerta, da Tarefa 1.
- Produces: `Chip` com a mesma assinatura de hoje —
  `{ children: ReactNode; variante?: 'realizada' | 'faltou' | 'alerta' | 'neutro' }`.
  Nenhuma tela precisa mudar.

**Por que esta tarefa existe.** O spec, §2.5, exige que estado de sessão
sobreviva a daltonismo. Hoje `REALIZADA` e `FALTOU` se distinguem **só** por
matiz de fundo — verde contra âmbar, que é exatamente o par que protanopia e
deuteranopia colapsam. A linguagem nova reduz ainda mais o contraste entre
superfícies, então o problema piora se nada for feito.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/ui/Chip.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Chip } from './Chip'

describe('Chip', () => {
  it('realizada e faltou se distinguem sem depender de cor', () => {
    const { rerender } = render(<Chip variante="realizada">REALIZADA</Chip>)
    const realizada = screen.getByText('REALIZADA').closest('[data-chip]')
    const glifoRealizada = realizada?.querySelector('svg')

    expect(glifoRealizada).not.toBeNull()

    rerender(<Chip variante="faltou">FALTOU</Chip>)
    const faltou = screen.getByText('FALTOU').closest('[data-chip]')
    const glifoFaltou = faltou?.querySelector('svg')

    expect(glifoFaltou).not.toBeNull()
    expect(glifoFaltou?.innerHTML).not.toEqual(glifoRealizada?.innerHTML)
  })

  it('o glifo nao e lido em voz alta, porque a palavra ja esta escrita', () => {
    render(<Chip variante="realizada">REALIZADA</Chip>)
    const svg = screen.getByText('REALIZADA').closest('[data-chip]')?.querySelector('svg')
    expect(svg?.getAttribute('aria-hidden')).toBe('true')
  })

  it('neutro nao ganha glifo', () => {
    render(<Chip>AGENDADA</Chip>)
    const svg = screen.getByText('AGENDADA').closest('[data-chip]')?.querySelector('svg')
    expect(svg).toBeNull()
  })
})
```

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/ui/Chip.test.tsx
```

Esperado: FAIL — `expect(glifoRealizada).not.toBeNull()` recebe `null`, porque o
chip atual não renderiza `svg` nenhum.

- [ ] **Step 3: Implemente o glifo**

Substitua `src/ui/Chip.tsx` por:

```tsx
import type { ReactNode } from 'react'

type Variante = 'realizada' | 'faltou' | 'alerta' | 'neutro'

const GLIFOS: Record<Exclude<Variante, 'neutro'>, ReactNode> = {
  realizada: <path d="M3 7.2 5.6 10 11 3.6" />,
  faltou: <path d="M3.6 3.6 10.4 10.4M10.4 3.6 3.6 10.4" />,
  alerta: <path d="M7 3.2v4.4M7 10.3v.5" />,
}

export function Chip({
  children,
  variante = 'neutro',
}: {
  children: ReactNode
  variante?: Variante
}) {
  const classes = variante === 'neutro' ? 'chip' : `chip ${variante}`

  return (
    <span data-chip="" className={classes}>
      {variante !== 'neutro' && (
        <svg
          aria-hidden="true"
          focusable="false"
          className="chip-glifo"
          viewBox="0 0 14 14"
          width="12"
          height="12"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          {GLIFOS[variante]}
        </svg>
      )}
      {children}
    </span>
  )
}
```

- [ ] **Step 4: Migre as regras de chip**

Em `src/estilo/estilo.css`, substitua o bloco `.chip` / `.chip.realizada` /
`.chip.faltou` / `.chip.alerta` (hoje nas linhas 129-142) por:

```css
.chip {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 4px 9px;
  border-radius: var(--raio-sm);
  font-size: var(--t-legenda-tam); line-height: var(--t-legenda-alt);
  letter-spacing: var(--t-legenda-esp); font-weight: var(--peso-medio);
  background: var(--superficie-3); color: var(--tinta-2);
  box-shadow: inset 0 0 0 1px var(--separador);
}
.chip-glifo { flex: 0 0 auto; }
.chip.realizada { background: var(--realizada-fundo); color: var(--realizada-tinta);
                  box-shadow: inset 0 0 0 1px var(--realizada-contorno); }
.chip.faltou    { background: var(--faltou-fundo);    color: var(--faltou-tinta);
                  box-shadow: inset 0 0 0 1px var(--faltou-contorno); }
.chip.alerta    { background: var(--alerta-fundo);    color: var(--alerta-tinta);
                  box-shadow: inset 0 0 0 1px var(--alerta-contorno); }
```

Os nomes acima foram conferidos contra a seção 0 do spec de linguagem: todos
existem. O spec é a autoridade sobre token — se algo divergir, ele vence.

- [ ] **Step 5: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/ui/Chip.test.tsx && ASDF_NODEJS_VERSION=20.19.1 npm test
```

Esperado: os três casos novos passam; a suíte inteira continua verde.

Os testes de `HojePage` e `FichaPage` que procuram o texto `REALIZADA` continuam
passando — eles buscam por texto escrito, e o texto não mudou. Se algum quebrar,
é sinal de que buscava por classe, e **isso é achado**: corrija o teste, não o
componente.

- [ ] **Step 6: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/ui/Chip.tsx frontend/src/ui/Chip.test.tsx frontend/src/estilo/estilo.css
git commit -m "feat(chip): estado de sessao para de depender so de cor"
```

---

### Task 3: Botão, campo e cartão na linguagem nova

**Files:**
- Modify: `src/estilo/estilo.css` (blocos `.botao`, `.cartao`, `.busca`)
- Modify: `src/estilo/dialogo.css` (blocos `.campo …`)
- Test: `src/ui/Botao.test.tsx` (criar)

**Interfaces:**
- Consumes: tokens da Tarefa 1.
- Produces: nenhuma mudança de API. `Botao`, `Campo` e `Cartao` mantêm as
  assinaturas atuais.

Esta tarefa é quase inteiramente CSS. O único teste novo prende a regra que o
spec chama de invariante e que é fácil de perder numa migração de folha: o botão
desabilitado continua anunciado como desabilitado, e o foco continua visível.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/ui/Botao.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Botao } from './Botao'

describe('Botao', () => {
  it('desabilitado e anunciado como desabilitado', () => {
    render(<Botao disabled>Salvar</Botao>)
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeDisabled()
  })

  it('o estado ligado e exposto por aria-pressed, nao so por cor', () => {
    render(<Botao aria-pressed="true">Modo privacidade</Botao>)
    const b = screen.getByRole('button', { name: 'Modo privacidade' })
    expect(b).toHaveAttribute('aria-pressed', 'true')
  })

  it('secundario continua sendo o mesmo botao para quem le a tela', () => {
    render(<Botao variante="secundario">Faltou</Botao>)
    expect(screen.getByRole('button', { name: 'Faltou' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Rode e veja falhar ou passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/ui/Botao.test.tsx
```

Esperado: **PASS nos três, desde já.** Este é um teste de regressão, não de
funcionalidade nova — ele existe para falhar se a migração de CSS da etapa
seguinte quebrar a semântica do botão. Registre que passou antes de seguir.

- [ ] **Step 3: Migre o botão**

Em `src/estilo/estilo.css`, substitua o bloco `.botao` e derivados (hoje nas
linhas 68-85) pelo tratamento da seção "6.1 Botão" do spec de linguagem. Copie
os valores de lá, não improvise.

Duas coisas que a seção 6.1 especifica e que a folha atual não tem:

- `:active { transform: scale(0.97) }` com `transition: transform 120ms var(--curva-saida)`.
  É a **única ausência real** que a varredura de movimento de 18/09 encontrou —
  marcar presença hoje não dá sinal nenhum até a rede responder.
- `:focus-visible` com anel em `var(--foco-anel)`. A folha atual não declara
  foco em botão nenhum.

Mantenha `.botao[aria-pressed="true"]` com tratamento distinto — é o que o teste
do passo 1 protege, e o spec §7 lista como risco a verificar: o estado ligado
precisa continuar legível **sem depender só de tom**. Use o par
fundo + `box-shadow: inset` do spec, não só troca de cor de fundo.

- [ ] **Step 4: Migre cartão, busca e campo**

- `.cartao` (`estilo.css:50-53`): superfície e elevação da seção "3. Profundidade
  e elevação" do spec.
- `.busca` (`estilo.css:206-207`): mesmo tratamento de campo do diálogo.
- Em `dialogo.css`, os blocos `.campo input`, `.campo select`, `.campo textarea`
  e seus `:focus` / `[aria-invalid='true']`: tratamento da seção "6.2 Campo de
  texto e textarea". O spec substitui a borda de 1px por anel interno
  (`box-shadow: inset`) — é a única hairline que ele preserva junto com a da
  grade da Agenda.

- [ ] **Step 5: Rode a suíte inteira**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: tudo verde, contagem igual à do fim da Tarefa 2 mais 3.

- [ ] **Step 6: Confira com os olhos**

CSS não tem teste que substitua olhar. Suba o app e compare com o espécime
aprovado:

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm run dev
```

Abra `/entrar` e compare o cartão de login, o campo e o botão com a aba **Login**
do artifact de espécimes. Divergência de forma, peso ou espaçamento aqui é
defeito — o artifact é a régua (spec §8).

- [ ] **Step 7: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/estilo/estilo.css frontend/src/estilo/dialogo.css frontend/src/ui/Botao.test.tsx
git commit -m "feat(controles): botao, campo e cartao na linguagem nova, com foco e pressao visiveis"
```

---

### Task 4: O shell

**Files:**
- Modify: `src/estilo/estilo.css` (blocos `.moldura`, `.lateral`, `.tela`, `main`)
- Test: `src/ui/Navegacao.test.tsx` (criar)

**Interfaces:**
- Consumes: tokens da Tarefa 1; `--superficie-vidro` e o fallback da seção 3.
- Produces: nenhuma mudança de API.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/ui/Navegacao.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { Navegacao } from './Navegacao'

describe('Navegacao', () => {
  it('os cinco destinos continuam com rotulo escrito', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Navegacao />
      </MemoryRouter>,
    )

    for (const rotulo of ['Hoje', 'Agenda', 'Pacientes', 'Financeiro', 'Ajustes']) {
      expect(screen.getByRole('link', { name: rotulo })).toBeInTheDocument()
    }
  })

  it('o item ativo e identificavel sem depender de cor', () => {
    render(
      <MemoryRouter initialEntries={['/agenda']}>
        <Navegacao />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Agenda' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })
})
```

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/ui/Navegacao.test.tsx
```

Esperado: o primeiro caso PASSA; o segundo **FALHA**.

O `NavLink` do react-router emite `aria-current="page"` por padrão, mas só no
elemento ativo — e o teste falha se a rota `/agenda` não casar. Se falhar por
outro motivo, leia a mensagem antes de mexer: pode ser que o `end` do destino
`/` esteja capturando.

- [ ] **Step 3: Garanta o `aria-current`**

Se o segundo caso falhou, o `NavLink` de `Navegacao.tsx` já entrega
`aria-current` — confirme que nada no `className` callback está removendo. Não
adicione `aria-current` à mão; o router já o emite.

Se o teste passar sem alteração, registre isso: a tarefa segue para o CSS.

- [ ] **Step 4: Migre o shell**

Em `src/estilo/estilo.css`:

- `.moldura` e `main`: fundo em `var(--fundo-base)`.
- `.lateral`: material da seção "2.4 O que substitui `--linha`" do spec — a
  lateral vira superfície translúcida com `backdrop-filter`, e a
  `border-right: 1px solid var(--linha)` **sai**.
- `.lateral a`, `.lateral a:hover`, `.lateral a.ativo`: tratamento da seção
  "6. Os controles", item de navegação. O item ativo deixa de ser pílula azul e
  vira laje elevada sobre o vidro.
- `.tela`: espaçamento da seção "4.2 Espaço".

**O fallback do `backdrop-filter` é obrigatório**, não opcional. A seção 3 do
spec traz o `@supports not (backdrop-filter: blur(1px))` com a superfície opaca
equivalente. Sem ele, num navegador sem suporte a lateral fica translúcida sobre
nada e o texto da navegação pousa em cima do conteúdo.

- [ ] **Step 5: Rode e confira**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Depois suba o app e navegue entre Hoje, Agenda e Pacientes conferindo o item
ativo contra o espécime. A lateral aparece em todas as abas do artifact.

- [ ] **Step 6: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/estilo/estilo.css frontend/src/ui/Navegacao.test.tsx
git commit -m "feat(shell): lateral e navegacao na linguagem nova, com fallback de vidro"
```

---

### Task 5: Limpeza dos tokens do Calendar

**Files:**
- Modify: `src/estilo/tokens.css` (remover o bloco de aliases temporário)
- Modify: `src/estilo/estilo.css` (últimos consumidores)

**Interfaces:**
- Consumes: a lista de órfãos anotada na Tarefa 1, Step 4.

- [ ] **Step 1: Ache o que sobrou**

```bash
cd frontend && grep -nE "var\(--(linha|acento-suave|acento-escuro|acento-linha|superficie-2|tinta-3|sombra-1|sombra-2|agora|sucesso|atencao|perigo)" src/estilo/*.css
```

Cada ocorrência é uma regra que ainda fala a língua antiga.

- [ ] **Step 2: Migre cada uma para o token equivalente**

Use a tabela de equivalência da seção "2.4 O que substitui `--linha`" do spec.
Onde não houver equivalente — é o caso de `--agora`, a linha vermelha do horário
atual — mantenha o valor, movendo a declaração para o bloco de tokens novo com o
nome que o spec der.

- [ ] **Step 3: Remova o bloco de aliases**

Apague o bloco temporário que a Tarefa 1 criou no fim de `tokens.css`.

- [ ] **Step 4: Rode o teste de token órfão**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/estilo/tokens.test.ts && ASDF_NODEJS_VERSION=20.19.1 npm test
```

Esperado: o primeiro caso passa com a lista vazia — e agora ele significa algo,
porque não há mais aliases escondendo órfão.

- [ ] **Step 5: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/estilo/tokens.css frontend/src/estilo/estilo.css
git commit -m "refactor(estilo): os ultimos tokens do Calendar saem da folha"
```

---

### Task 6: O modo privacidade sobe para o shell

**Files:**
- Create: `src/privacidade/PrivacidadeProvider.tsx`
- Create: `src/privacidade/usePrivacidade.ts`
- Create: `src/privacidade/PrivacidadeProvider.test.tsx`
- Modify: `src/ui/Layout.tsx`
- Modify: `src/telas/hoje/HojePage.tsx:63-70` (o `Botao` de privacidade) e o
  estado local
- Modify: `src/teste/renderizar.tsx` (o provider entra na árvore de teste)

**Interfaces:**
- Produces:
  - `PrivacidadeProvider({ children }: { children: ReactNode }): JSX.Element`
  - `usePrivacidade(): { privacidade: boolean; alternar: () => void }`

**Esta é a única tarefa do plano que muda comportamento.** Hoje o estado vive em
`HojePage` e morre ao navegar. Depois dela, ele sobrevive — atravessar para
Pacientes mantém borrado. É o comportamento certo com alguém na sala (spec §4.1),
e é por isso que tem teste próprio.

- [ ] **Step 1: Escreva o teste que falha**

Crie `src/privacidade/PrivacidadeProvider.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { PrivacidadeProvider } from './PrivacidadeProvider'
import { usePrivacidade } from './usePrivacidade'

function Sonda({ nome }: { nome: string }) {
  const { privacidade, alternar } = usePrivacidade()
  return (
    <div>
      <span data-borrado={privacidade ? 'true' : undefined}>{nome}</span>
      <button type="button" aria-pressed={privacidade} onClick={alternar}>
        Modo privacidade
      </button>
    </div>
  )
}

describe('PrivacidadeProvider', () => {
  it('comeca desligado', () => {
    render(
      <PrivacidadeProvider>
        <Sonda nome="Paciente Um" />
      </PrivacidadeProvider>,
    )
    expect(screen.getByRole('button', { name: 'Modo privacidade' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })

  it('o estado e o mesmo para duas telas irmas', async () => {
    const usuario = userEvent.setup()
    render(
      <PrivacidadeProvider>
        <Sonda nome="Paciente Um" />
        <Sonda nome="Paciente Dois" />
      </PrivacidadeProvider>,
    )

    await usuario.click(screen.getAllByRole('button', { name: 'Modo privacidade' })[0])

    for (const b of screen.getAllByRole('button', { name: 'Modo privacidade' })) {
      expect(b).toHaveAttribute('aria-pressed', 'true')
    }
    expect(screen.getByText('Paciente Um')).toHaveAttribute('data-borrado', 'true')
    expect(screen.getByText('Paciente Dois')).toHaveAttribute('data-borrado', 'true')
  })

  it('usar o hook fora do provider e erro, nao silencio', () => {
    expect(() => render(<Sonda nome="Paciente Um" />)).toThrow()
  })
})
```

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/privacidade/
```

Esperado: FAIL — os módulos não existem.

- [ ] **Step 3: Implemente o contexto**

Crie `src/privacidade/PrivacidadeProvider.tsx`:

```tsx
import { createContext, useCallback, useMemo, useState, type ReactNode } from 'react'

export type Privacidade = { privacidade: boolean; alternar: () => void }

export const ContextoDePrivacidade = createContext<Privacidade | null>(null)

export function PrivacidadeProvider({ children }: { children: ReactNode }) {
  const [privacidade, setPrivacidade] = useState(false)
  const alternar = useCallback(() => setPrivacidade((p) => !p), [])
  const valor = useMemo(() => ({ privacidade, alternar }), [privacidade, alternar])

  return (
    <ContextoDePrivacidade.Provider value={valor}>{children}</ContextoDePrivacidade.Provider>
  )
}
```

Crie `src/privacidade/usePrivacidade.ts`:

```ts
import { useContext } from 'react'

import { ContextoDePrivacidade, type Privacidade } from './PrivacidadeProvider'

export function usePrivacidade(): Privacidade {
  const valor = useContext(ContextoDePrivacidade)
  if (!valor) throw new Error('usePrivacidade precisa estar dentro de PrivacidadeProvider.')
  return valor
}
```

- [ ] **Step 4: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/privacidade/
```

Esperado: os três casos passam.

- [ ] **Step 5: Ligue no shell**

Em `src/ui/Layout.tsx`, envolva a `<div className="moldura">` com
`<PrivacidadeProvider>`. O provider fica **dentro** do `Layout`, não acima das
rotas — Login não precisa dele, e envolvê-lo daria acesso a um estado que a tela
não usa.

- [ ] **Step 6: Faça a HojePage ler do hook**

Em `src/telas/hoje/HojePage.tsx`, remova o `useState` local de privacidade e
passe a usar `usePrivacidade()`. O `Botao` com `aria-pressed` e o rótulo
`Modo privacidade` ficam exatamente como estão — a copy não muda.

- [ ] **Step 7: Ajuste o utilitário de render dos testes**

Em `src/teste/renderizar.tsx`, acrescente `PrivacidadeProvider` à árvore, de modo
que os testes de tela que montam `HojePage` continuem funcionando sem cada um
montar o provider à mão.

- [ ] **Step 8: Rode a suíte inteira e o e2e**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Com o backend no ar em :8080:

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm run e2e
```

Esperado: 2 passed. **Se o e2e falhar, é achado, não obstáculo** — foi um e2e
contra a API real que descobriu, na Fatia 1, um diálogo sem mensagem de erro com
41 testes de mock verdes. Reporte a saída antes de mexer em qualquer tela.

- [ ] **Step 9: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/privacidade frontend/src/ui/Layout.tsx frontend/src/telas/hoje/HojePage.tsx frontend/src/teste/renderizar.tsx
git commit -m "feat(privacidade): o modo sobe para o shell e sobrevive a navegacao"
```

---

## Fora do escopo deste plano, de propósito

- **Reestruturar qualquer tela.** Hoje, Ficha, Pacientes, Agenda e Login mantêm
  a estrutura atual; só mudam de aparência, pela base. A reestrutura é o Plano 2.
- **A fila única "Precisa de você"** e a reconciliação de dois modelos de dado
  (D6) — Plano 3, o mais caro.
- **Estender o borrado à tela Pacientes.** A Tarefa 6 entrega o estado
  compartilhado; **consumi-lo** em Pacientes é Plano 2, junto com a linha que
  passa a exibir idade, contato e valor.
- **Movimento**, além do `:active` do botão que a Tarefa 3 traz — orçamento
  próprio, já levantado.
- **Implementar e testar o tema escuro** (D4).
- **Colapso de hora vazia na Agenda** (D7).

## Verificação no fim do plano

1. `npm test` verde, com contagem ≥ 86 mais os testes novos deste plano.
2. `npx tsc --noEmit` exit 0.
3. `npm run e2e` 2 passed contra a API real.
4. O teste de token órfão passando com lista vazia e sem aliases.
5. As cinco telas subidas no navegador e conferidas contra o artifact de
   espécimes — ainda com a estrutura antiga, mas na linguagem nova.
