# Fatia 1 — frontend: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar as três telas da Fatia 1 — Login, Hoje e Agenda — sobre uma fundação nova: `src/` reconstruído do zero, sistema de design tirado do protótipo, `react-hook-form` + `zod`, tipos gerados do contrato OpenAPI, navegação com rótulo e tratamento de erro que não morre em silêncio.

**Architecture:** O `src/` atual (64 arquivos) é apagado e reconstruído por fatia — decisão do dono. A toolchain fica: Vite, Vitest, Playwright, Tailwind, TanStack Query, react-router. A fundação sobe primeiro (tokens, cliente, tipos, sessão, molde de formulário), o Login a exercita inteira na menor tela possível, e só então as duas telas de dado entram.

**Tech Stack:** React 19, TypeScript 7, Vite 8, Tailwind 4, react-router-dom 7, TanStack Query 5, Radix Dialog, `react-hook-form` + `zod` (a acrescentar), `openapi-typescript` (a acrescentar), Vitest 4 + Testing Library + MSW 2, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-01-redesenho-produto-design.md` — §4① (Hoje), §4② (Agenda), §5.6 (endpoints), §6.2 e §6.3 (validação e quando o erro aparece), §9 (o que a Fatia 1 é).

**Estado de onde isto parte:** o backend da Fatia 1 está pronto e mergeado — 272 testes, `4bd4e34`. Os endpoints que este plano consome existem e estão testados: `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /painel/hoje`, `GET /consultas`, `GET /consultas/conflito`, `POST /consultas`, `PATCH /consultas/{id}/status`. O contrato está em `GET /v3/api-docs`.

## Global Constraints

- **Servidor é a autoridade, cliente é o atalho** (§6.2). O `zod` existe para poupar ida ao servidor, **nunca** para substituir a validação da API. Toda regra daqui existe lá; se divergirem, a API vence e o front está errado.
- **Quando o erro aparece** (§6.2), e isto hoje não existe em lugar nenhum: no `blur` só depois do primeiro toque; no `submit` sempre, em todos os campos; depois que um campo já errou, revalida a cada tecla.
- **Botão nunca morre em silêncio** (§6.2). Submit inválido sempre produz três coisas: erro no campo, foco no primeiro inválido, e anúncio em `aria-live`. O `if (!x) return` não volta.
- **Obrigatório é visível antes do erro** (§6.2). Campo obrigatório marcado, opcional escrito "opcional". O usuário não descobre a regra errando.
- **Texto de erro num só registro** (§6.3): frase completa, com acento, dizendo o que fazer. O texto que o usuário lê num erro de campo **vem do servidor** quando a resposta traz `erros[]`; o `zod` só fala antes do envio.
- **Datas na API são hora local sem offset**: `2026-12-02T14:00:00`. Nunca mande `Date.toISOString()` — ele produz `Z`, e a API recusa. Há um formatador para isso na Task 3.
- **Navegação com cinco destinos e rótulo escrito** (§4). O icon-only sai: quatro ícones mudos numa coluna de 64px é adivinhação. Na Fatia 1 só dois destinos levam a tela pronta; os outros três aparecem desabilitados com o motivo.
- **`npm test` verde ao fim de cada tarefa.** Rodar de `frontend/`.
- **NÃO COMMITAR.** O dono do repositório commita.
- **NÃO tocar em `README.md`, `backend/README.md` nem `frontend/README.md`.**
- **Não tocar no backend.** Se faltar endpoint ou campo, pare e diga — não contorne no cliente.

---

## File Structure

**O que é apagado:** todo o conteúdo de `frontend/src/` (64 arquivos). Nada mais.

**O que fica, e por quê:** `package.json`, `vite.config.ts`, `tsconfig*.json`, `index.html`, `playwright.config.ts`, `public/`, `vercel.json`, `e2e/`. É toolchain, e a decisão do dono foi reconstruir `src/`, não o projeto.

**Atenção ao `vite.config.ts`:** ele referencia `./src/teste/setup.ts` no `setupFiles`. Apagar `src/` sem recriar esse arquivo quebra o Vitest inteiro, e o sintoma é um erro de config que não menciona o teste que você quis rodar.

**A estrutura nova:**

```
src/
  main.tsx                     ponto de entrada
  rotas.tsx                    as cinco rotas, com rótulo
  estilo/tokens.css            copiado do prototipo (fonte do design)
  api/
    contrato.ts                tipos GERADOS do OpenAPI — não editar à mão
    cliente.ts                 fetch com envelope de erro, 401 e refresh
    erro.ts                    ErroDaApi e a leitura do envelope
    data.ts                    formatação de data no formato que a API aceita
  sessao/
    SessaoProvider.tsx         quem está logado
    useSessao.ts
    RotaProtegida.tsx
  ui/                          componentes do design system
    Botao.tsx  Campo.tsx  Cartao.tsx  Chip.tsx  EstadoVazio.tsx
    Layout.tsx  Navegacao.tsx  Dialogo.tsx
  formulario/
    useFormularioDoProduto.ts  o molde: rhf + zod + as regras de quando o erro aparece
    ResumoDeErros.tsx          o aria-live
  telas/
    login/LoginPage.tsx + esquema.ts
    hoje/HojePage.tsx + queries.ts
    agenda/AgendaPage.tsx + queries.ts + DialogoDeConsulta.tsx + esquema.ts
  teste/
    setup.ts  renderizar.tsx  servidor.ts   (MSW)
```

---

## O ciclo de cada tarefa

1. Escrever o teste que falha
2. Rodar e ver falhar **pelo motivo certo**
3. Implementar o mínimo
4. `npm test` — a suíte inteira
5. Relatar. **Não commitar.**

**Rodar um teste só:** `npm test -- src/caminho/Arquivo.test.tsx`
**O backend precisa estar no ar** para a Task 2 (geração de tipos) e para o e2e da Task 9. Para o resto, MSW cobre.

---

### Task 1: `src/` a zero, e o esqueleto que boota

**Files:**
- Delete: todo o conteúdo de `frontend/src/`
- Create: `src/main.tsx`, `src/rotas.tsx`, `src/estilo/tokens.css`, `src/estilo/base.css`
- Create: `src/teste/setup.ts`, `src/teste/renderizar.tsx`
- Create: `src/App.test.tsx`
- Modify: `frontend/vite.config.ts` (só a lista `CAMINHOS_DA_API`)

**Interfaces:**
- Produces: `renderizar(ui)` de `src/teste/renderizar.tsx` — envolve o componente em `QueryClientProvider` e `MemoryRouter`, e é o que toda tela usa em teste daqui para frente.

Apagar 64 arquivos é a parte fácil. A parte que morde é o `vite.config.ts`: ele aponta `setupFiles: ['./src/teste/setup.ts']`, então o arquivo tem que voltar no mesmo passo ou o Vitest não sobe — e o erro fala de config, não do teste que você tentou rodar.

**Duas correções na lista de caminhos do proxy**, que ficaram para trás quando o backend mudou: `/lembretes` não existe mais (virou `/pendencias` na fase do schema) e `/painel` é novo. Sem `/painel` na lista, a tela Hoje da Task 7 recebe o `index.html` em vez do JSON, e o sintoma é um erro de parse sem relação aparente com o proxy.

- [ ] **Step 1: Apagar e recriar a pasta**

```bash
cd frontend
rm -rf src
mkdir -p src/estilo src/teste src/api src/sessao src/ui src/formulario src/telas
```

- [ ] **Step 2: Os tokens, copiados do protótipo**

```bash
cp ../prototipo/tokens.css src/estilo/tokens.css
```

**Copie, não reescreva.** Aquele arquivo carrega decisões do dono nos comentários — em especial a regra "ação é forma, não cor: pílula = ação, bloco chapado com barra à esquerda = evento", que veio da escolha da linguagem do Google Calendar. Reescrever perde o porquê, e o próximo a mexer reintroduz a regra antiga.

> **Ordem real dos passos, corrigida durante a execução.** O ciclo normal é
> "teste falha → implementa", mas aqui o arnês (`teste/setup.ts` e
> `teste/renderizar.tsx`, o Step 5) é **pré-requisito** do teste, não
> consequência dele: sem `setup.ts` o Vitest nem sobe, e o erro fala de config.
> Faça o Step 5 antes do Step 3. Aí o vermelho do Step 4 é o que está escrito —
> `./rotas` não resolve — e não um erro de configuração disfarçado.
>
> **Ambiente:** o `node` padrão desta máquina (asdf global) é antigo demais para
> o Vite 8 e o Vitest 4, e falha com `SyntaxError: Unexpected token import`.
> Rode com `ASDF_NODEJS_VERSION=20.19.1 npm test`. Não altere o `.tool-versions`.

- [ ] **Step 3: O teste que falha**

`src/App.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { renderizar } from './teste/renderizar'
import { Rotas } from './rotas'

describe('esqueleto', () => {
  it('sobe e mostra a navegacao com rotulo escrito, nao so icone', () => {
    renderizar(<Rotas />)

    // a 4 do spec: cinco destinos, cada um com rotulo LEGIVEL. O icon-only
    // saiu de proposito — quatro icones mudos numa coluna e adivinhacao
    expect(screen.getByRole('link', { name: /hoje/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /agenda/i })).toBeInTheDocument()
  })
})
```

- [ ] **Step 4: Rodar e ver falhar**

Run: `npm test -- src/App.test.tsx`
Expected: falha na resolução de `./rotas` — o módulo não existe. Esse é o vermelho certo.

- [ ] **Step 5: O arnês de teste**

`src/teste/setup.ts`:

```ts
import '@testing-library/jest-dom/vitest'
```

`src/teste/renderizar.tsx`:

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'

/**
 * Todo teste de tela passa por aqui. `retry: false` e obrigatorio: com o
 * padrao do TanStack, um teste de caminho de erro espera tres tentativas e
 * estoura o timeout, e o sintoma parece lentidao e nao configuracao.
 */
export function renderizar(ui: ReactElement, { rota = '/' } = {}) {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  function Envolvido({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={cliente}>
        <MemoryRouter initialEntries={[rota]}>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }

  return render(ui, { wrapper: Envolvido })
}
```

- [ ] **Step 6: As rotas e o ponto de entrada**

`src/rotas.tsx` — as cinco de §4, com as três da Fatia 1 em diante marcadas como ainda não disponíveis:

```tsx
import { Route, Routes } from 'react-router-dom'

import { Layout } from './ui/Layout'

function AindaNaoNestaFatia({ nome }: { nome: string }) {
  return (
    <p>
      {nome} chega numa fatia proxima. Por enquanto, Hoje e Agenda ja funcionam.
    </p>
  )
}

export function Rotas() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<p>Hoje</p>} />
        <Route path="/agenda" element={<p>Agenda</p>} />
        <Route path="/pacientes" element={<AindaNaoNestaFatia nome="Pacientes" />} />
        <Route path="/financeiro" element={<AindaNaoNestaFatia nome="Financeiro" />} />
        <Route path="/ajustes" element={<AindaNaoNestaFatia nome="Ajustes" />} />
      </Route>
    </Routes>
  )
}
```

`src/ui/Layout.tsx` e `src/ui/Navegacao.tsx` — a navegação com rótulo:

```tsx
// src/ui/Navegacao.tsx
import { NavLink } from 'react-router-dom'

const DESTINOS = [
  { para: '/', rotulo: 'Hoje' },
  { para: '/agenda', rotulo: 'Agenda' },
  { para: '/pacientes', rotulo: 'Pacientes' },
  { para: '/financeiro', rotulo: 'Financeiro' },
  { para: '/ajustes', rotulo: 'Ajustes' },
]

/** Cinco destinos com ROTULO ESCRITO (spec 4). O icon-only anterior saiu por
 *  decisao do dono: quatro icones mudos numa coluna de 64px e adivinhacao. */
export function Navegacao() {
  return (
    <nav aria-label="Navegacao principal">
      <ul>
        {DESTINOS.map((d) => (
          <li key={d.para}>
            <NavLink to={d.para} end={d.para === '/'}>
              {d.rotulo}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
```

```tsx
// src/ui/Layout.tsx
import { Outlet } from 'react-router-dom'

import { Navegacao } from './Navegacao'

export function Layout() {
  return (
    <div>
      <Navegacao />
      <main>
        <Outlet />
      </main>
    </div>
  )
}
```

`src/main.tsx`:

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'

import './estilo/tokens.css'
import './estilo/base.css'
import { Rotas } from './rotas'

const cliente = new QueryClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={cliente}>
      <BrowserRouter>
        <Rotas />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
```

`src/estilo/base.css` — só o que o Tailwind 4 precisa:

```css
@import "tailwindcss";
```

- [ ] **Step 7: Corrigir a lista de caminhos do proxy**

Em `vite.config.ts`, a constante `CAMINHOS_DA_API`. Troque `'/lembretes'` por `'/pendencias'` e acrescente `'/painel'`:

```ts
const CAMINHOS_DA_API = ['/auth', '/pacientes', '/consultas', '/anotacoes', '/pendencias', '/painel', '/auditoria']
```

Não mexa em mais nada nesse arquivo. O desvio por `Accept` que está lá resolve um problema real — `/pacientes` é caminho de API **e** rota de tela — e foi escrito com o motivo documentado.

- [ ] **Step 8: `npm test`**

Expected: verde, com o teste do esqueleto passando.

- [ ] **Step 9: Relatar. NÃO COMMITAR.**

---

### Task 2: Os tipos, gerados do contrato

**Files:**
- Modify: `frontend/package.json` (dependência e script)
- Create: `frontend/src/api/contrato.ts` (gerado)
- Create: `frontend/src/api/contrato.test.ts`

**Interfaces:**
- Produces: `import type { paths, components } from './contrato'`. Todas as tarefas seguintes tipam resposta e corpo a partir daqui, nunca de interface escrita à mão.

Tipo gerado é o que impede front e API divergirem em silêncio, e foi por isso que a Task 6 do backend existiu. Escrever as interfaces à mão devolve o problema.

**Decisão que o plano toma por você:** o arquivo gerado **é versionado**. A alternativa — gerar no `build` — faz o build depender de um backend no ar, o que quebra CI e quebra quem clona o projeto. Versionado, a divergência aparece como diff quando alguém regenera, que é exatamente quando se quer ver.

- [ ] **Step 1: A dependência e o script**

```bash
cd frontend
npm install --save-dev openapi-typescript
```

Em `package.json`, `scripts`:

```json
"tipos": "openapi-typescript http://localhost:8080/v3/api-docs -o src/api/contrato.ts"
```

- [ ] **Step 2: Gerar, com o backend no ar**

```bash
cd ../backend && ./mvnw spring-boot:run   # noutro terminal
cd ../frontend && npm run tipos
```

**Se `openapi-typescript` não suportar a versão do TypeScript deste projeto (7.x), pare e diga.** Não contorne escrevendo os tipos à mão — isso desfaz a tarefa inteira, e a decisão de usar outra ferramenta é do controlador.

- [ ] **Step 3: O teste que prova que o contrato tem o que a fatia precisa**

`src/api/contrato.test.ts`:

```ts
import { describe, expectTypeOf, it } from 'vitest'

import type { paths } from './contrato'

/**
 * Teste de TIPO, nao de runtime: ele falha na compilacao se o contrato
 * regenerado deixar de ter uma rota que esta fatia consome. E o alarme de
 * divergencia entre front e API — o motivo de o contrato existir.
 */
describe('contrato gerado', () => {
  it('tem as rotas que a Fatia 1 consome', () => {
    expectTypeOf<paths['/painel/hoje']['get']>().not.toBeNever()
    expectTypeOf<paths['/consultas/conflito']['get']>().not.toBeNever()
    expectTypeOf<paths['/consultas/{id}/status']['patch']>().not.toBeNever()
    expectTypeOf<paths['/auth/login']['post']>().not.toBeNever()
  })
})
```

- [ ] **Step 4: Rodar**

Run: `npm test -- src/api/contrato.test.ts`
Expected: verde. Se alguma rota faltar, o contrato foi gerado de um backend desatualizado — regere com o backend na `4bd4e34`.

- [ ] **Step 5: `npm test`** — Expected: verde.

- [ ] **Step 6: Relatar. NÃO COMMITAR.**

---

### Task 3: O cliente HTTP, o envelope de erro, e a data

**Files:**
- Create: `src/api/erro.ts`, `src/api/cliente.ts`, `src/api/data.ts`
- Create: `src/api/erro.test.ts`, `src/api/cliente.test.ts`, `src/api/data.test.ts`
- Create: `src/teste/servidor.ts` (MSW)
- Modify: `src/teste/setup.ts` (ligar o MSW)

**Interfaces:**
- Produces:
  - `class ErroDaApi extends Error { status: number; mensagem: string; erros: { campo: string; erro: string }[] }`
  - `erroDoCampo(erro: unknown, campo: string): string | undefined`
  - `api<T>(caminho: string, opcoes?: RequestInit): Promise<T>`
  - `paraApi(data: Date): string` e `deApi(texto: string): Date`

O envelope `ErroRespostaDTO { status, mensagem, erros[{campo, erro}] }` é a garantia do backend de que todo erro tem uma forma só. O cliente precisa lê-la, senão a garantia não chega ao usuário.

**Sobre a data, e isto é a parte que morde:** a API recusa offset. `Date.toISOString()` produz `2026-12-02T17:00:00.000Z` — com `Z` e com milissegundos — e a API responde 400. O formato aceito é exatamente `2026-12-02T14:00:00`, hora local. Quem escrever `toISOString()` em qualquer lugar desta fatia cria um bug que só aparece em produção, com a sessão gravada em outra hora.

- [ ] **Step 1: Os testes que falham**

`src/api/data.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { deApi, paraApi } from './data'

describe('data no formato que a API aceita', () => {
  it('formata sem offset e sem milissegundos', () => {
    // 2 de dezembro de 2026, 14h00 — mes em JS e base zero
    expect(paraApi(new Date(2026, 11, 2, 14, 0, 0))).toBe('2026-12-02T14:00:00')
  })

  it('preenche com zero a esquerda', () => {
    expect(paraApi(new Date(2026, 0, 5, 9, 7, 3))).toBe('2026-01-05T09:07:03')
  })

  it('NAO usa toISOString, que produziria Z e milissegundos', () => {
    const d = new Date(2026, 11, 2, 14, 0, 0)
    expect(paraApi(d)).not.toContain('Z')
    expect(paraApi(d)).not.toContain('.')
  })

  it('le de volta o que a API devolve, como hora local', () => {
    const d = deApi('2026-12-02T14:00:00')
    expect(d.getHours()).toBe(14)
    expect(d.getDate()).toBe(2)
  })
})
```

`src/api/erro.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { ErroDaApi, erroDoCampo } from './erro'

describe('envelope de erro', () => {
  it('acha a mensagem do campo culpado', () => {
    const erro = new ErroDaApi(400, 'Dados invalidos.', [
      { campo: 'senha', erro: 'Essa senha é fácil de adivinhar. Escolha outra.' },
    ])

    expect(erroDoCampo(erro, 'senha')).toBe('Essa senha é fácil de adivinhar. Escolha outra.')
    expect(erroDoCampo(erro, 'email')).toBeUndefined()
  })

  it('nao explode com erro que nao e da API', () => {
    expect(erroDoCampo(new Error('rede caiu'), 'senha')).toBeUndefined()
    expect(erroDoCampo(undefined, 'senha')).toBeUndefined()
  })
})
```

`src/api/cliente.test.ts`:

```ts
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { api, definirToken } from './cliente'
import { ErroDaApi } from './erro'
import { servidor } from '../teste/servidor'

describe('cliente', () => {
  it('devolve o corpo quando da certo', async () => {
    servidor.use(http.get('/painel/hoje', () => HttpResponse.json({ sessoes: [] })))

    await expect(api('/painel/hoje')).resolves.toEqual({ sessoes: [] })
  })

  it('transforma o envelope de erro em ErroDaApi', async () => {
    servidor.use(
      http.post('/auth/login', () =>
        HttpResponse.json(
          { status: 401, mensagem: 'E-mail ou senha incorretos.', erros: [] },
          { status: 401 },
        ),
      ),
    )

    await expect(api('/auth/login', { method: 'POST' })).rejects.toBeInstanceOf(ErroDaApi)
  })

  it('sobrevive a resposta de erro que NAO vem no envelope', async () => {
    // acontece de verdade: ha modos de falha do backend que escapam do
    // ErroRespostaDTO (laudo do conjunto, I1). O cliente nao pode quebrar por
    // isso — tem que virar um ErroDaApi com mensagem generica
    servidor.use(http.get('/painel/hoje', () => new HttpResponse('502 Bad Gateway', { status: 502 })))

    await expect(api('/painel/hoje')).rejects.toBeInstanceOf(ErroDaApi)
  })

  it('manda o token como Authorization: Bearer depois de definido', async () => {
    definirToken('um-jwt-qualquer')
    let recebido: string | null = null
    servidor.use(
      http.get('/painel/hoje', ({ request }) => {
        recebido = request.headers.get('Authorization')
        return HttpResponse.json({})
      }),
    )

    await api('/painel/hoje')

    // sem este header o backend responde 401 em TUDO que nao e /auth
    expect(recebido).toBe('Bearer um-jwt-qualquer')
    definirToken(null)
  })

  it('devolve undefined em 204, sem tentar parsear corpo vazio', async () => {
    servidor.use(http.post('/auth/logout', () => new HttpResponse(null, { status: 204 })))

    await expect(api('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- src/api`
Expected: falha de módulo não encontrado nos três. Esse é o vermelho certo.

- [ ] **Step 3: A data**

`src/api/data.ts`:

```ts
/**
 * A API aceita EXATAMENTE `2026-12-02T14:00:00`: hora local, sem offset, sem
 * milissegundos (spec 5.9 — fuso unico America/Sao_Paulo). `toISOString()`
 * produz `...T17:00:00.000Z` e a API responde 400 — e quando NAO responde, e
 * pior: grava a sessao em outra hora.
 *
 * Por isso a formatacao e manual. Nao troque por `toISOString().slice(0, 19)`:
 * aquilo converte para UTC antes de cortar, e o corte esconde a conversao.
 */
const doisDigitos = (n: number) => String(n).padStart(2, '0')

export function paraApi(data: Date): string {
  const ano = data.getFullYear()
  const mes = doisDigitos(data.getMonth() + 1)
  const dia = doisDigitos(data.getDate())
  const hora = doisDigitos(data.getHours())
  const minuto = doisDigitos(data.getMinutes())
  const segundo = doisDigitos(data.getSeconds())
  return `${ano}-${mes}-${dia}T${hora}:${minuto}:${segundo}`
}

/** A API devolve no mesmo formato. `new Date(texto)` sem `Z` ja interpreta
 *  como hora local, que e o que se quer. */
export function deApi(texto: string): Date {
  return new Date(texto)
}
```

- [ ] **Step 4: O erro**

`src/api/erro.ts`:

```ts
export type CampoComErro = { campo: string; erro: string }

/** Espelha o `ErroRespostaDTO { status, mensagem, erros[{campo, erro}] }` que
 *  o backend garante para todo erro. */
export class ErroDaApi extends Error {
  constructor(
    readonly status: number,
    readonly mensagem: string,
    readonly erros: CampoComErro[] = [],
  ) {
    super(mensagem)
    this.name = 'ErroDaApi'
  }
}

/** A mensagem que o SERVIDOR deu para um campo, ou undefined. O texto do
 *  servidor vence o do zod: ele conhece regras que o cliente nao tem como
 *  checar (senha vazada, conflito de horario). */
export function erroDoCampo(erro: unknown, campo: string): string | undefined {
  if (!(erro instanceof ErroDaApi)) return undefined
  return erro.erros.find((e) => e.campo === campo)?.erro
}
```

- [ ] **Step 5: O cliente**

`src/api/cliente.ts`:

```ts
import { ErroDaApi, type CampoComErro } from './erro'

/** Mensagem para quando a resposta de erro nao veio no envelope. Acontece: ha
 *  modos de falha do backend que escapam dele. O usuario nunca pode ver um
 *  corpo cru do Spring. */
const ERRO_SEM_ENVELOPE = 'Nao foi possivel completar a acao. Tente de novo.'

/**
 * O access token vive AQUI, em memoria do modulo — nunca em localStorage.
 * `POST /auth/login` devolve `{ token }` no CORPO, e o filtro do backend le
 * `Authorization: Bearer <token>`. Sem este header, toda chamada autenticada
 * responde 401, e o sintoma parece problema de sessao e nao de cliente.
 *
 * Em memoria significa que recarregar a aba perde o token — e o `/auth/refresh`
 * com o cookie httpOnly que resolve isso. Esse fluxo NAO esta nesta fatia
 * (ver "O que este plano NAO faz"): por enquanto, recarregar leva ao login.
 */
let tokenDeAcesso: string | null = null

export function definirToken(token: string | null) {
  tokenDeAcesso = token
}

export async function api<T>(caminho: string, opcoes: RequestInit = {}): Promise<T> {
  const resposta = await fetch(caminho, {
    ...opcoes,
    // o refresh vem em cookie httpOnly: sem isto ele nao acompanha a chamada
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(tokenDeAcesso ? { Authorization: `Bearer ${tokenDeAcesso}` } : {}),
      ...opcoes.headers,
    },
  })

  if (!resposta.ok) {
    throw await lerErro(resposta)
  }

  if (resposta.status === 204) {
    return undefined as T
  }

  return (await resposta.json()) as T
}

async function lerErro(resposta: Response): Promise<ErroDaApi> {
  try {
    const corpo = (await resposta.json()) as {
      mensagem?: string
      erros?: CampoComErro[]
    }
    return new ErroDaApi(
      resposta.status,
      corpo.mensagem ?? ERRO_SEM_ENVELOPE,
      corpo.erros ?? [],
    )
  } catch {
    // corpo nao era JSON — 502 de proxy, HTML de erro, resposta vazia
    return new ErroDaApi(resposta.status, ERRO_SEM_ENVELOPE, [])
  }
}
```

- [ ] **Step 6: O MSW**

`src/teste/servidor.ts`:

```ts
import { setupServer } from 'msw/node'

export const servidor = setupServer()
```

Acrescente a `src/teste/setup.ts`:

```ts
import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'

import { servidor } from './servidor'

// `error` e de proposito: chamada que nenhum handler cobre vira FALHA, nao
// silencio. Teste que bate num endpoint esquecido deve ficar vermelho.
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }))
afterEach(() => servidor.resetHandlers())
afterAll(() => servidor.close())
```

- [ ] **Step 7: `npm test`** — Expected: verde.

- [ ] **Step 8: Relatar. NÃO COMMITAR.**

---

### Task 4: Sessão e rota protegida

**Files:**
- Create: `src/sessao/SessaoProvider.tsx`, `src/sessao/useSessao.ts`, `src/sessao/RotaProtegida.tsx`
- Create: `src/sessao/SessaoProvider.test.tsx`, `src/sessao/RotaProtegida.test.tsx`
- Modify: `src/rotas.tsx`, `src/main.tsx`

**Interfaces:**
- Produces: `useSessao(): { usuario: Usuario | null; entrar(dto): Promise<void>; sair(): Promise<void>; carregando: boolean }`, e `<RotaProtegida>` que manda para `/entrar` quem não tem sessão.

**O token fica em memória, não em `localStorage`.** Token em `localStorage` é legível por qualquer script que entre na página, e num produto com prontuário isso é a diferença entre um XSS e um vazamento de prontuário. O refresh vem em cookie `httpOnly`, que o JS não lê — é por isso que o backend foi construído assim, e o front não pode desfazer essa escolha guardando o access token em disco.

- [ ] **Step 1: Os testes que falham**

`src/sessao/RotaProtegida.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../teste/renderizar'
import { RotaProtegida } from './RotaProtegida'
import { SessaoProvider } from './SessaoProvider'

describe('rota protegida', () => {
  it('manda para o login quem nao tem sessao', async () => {
    renderizar(
      <SessaoProvider>
        <RotaProtegida>
          <p>conteudo secreto</p>
        </RotaProtegida>
      </SessaoProvider>,
    )

    expect(await screen.findByText(/entrar/i)).toBeInTheDocument()
    expect(screen.queryByText('conteudo secreto')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Rodar e ver falhar** — `npm test -- src/sessao`. Expected: módulo não encontrado.

- [ ] **Step 3: O provider**

`src/sessao/SessaoProvider.tsx`:

```tsx
import { createContext, useCallback, useMemo, useState, type ReactNode } from 'react'

import { api, definirToken } from '../api/cliente'

/**
 * So o e-mail: **nao existe rota que devolva o usuario logado** — conferido,
 * nao ha `UsuarioController` nem `/auth/eu`. O e-mail sai do `sub` do proprio
 * JWT que ja temos em maos.
 *
 * Ler um claim de um token que ja e nosso e legitimo; o que seria errado e
 * CONFIAR nele para autorizar. Autorizacao e do servidor, sempre. Isto e so
 * para escrever o nome de quem esta logado na tela.
 *
 * O nome do psicologo fica faltando na Fatia 1. Quando a tela de Ajustes
 * chegar (Fatia 4, spec 4-5), ela vai precisar de um endpoint de perfil —
 * vale abrir essa conversa com o dono antes, nao depois.
 */
export type Usuario = { email: string }

/** Le o `sub` do payload do JWT. Nao valida assinatura — nao e para isso. */
function emailDoToken(token: string): string | undefined {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return (JSON.parse(json) as { sub?: string }).sub
  } catch {
    return undefined
  }
}

export type Sessao = {
  usuario: Usuario | null
  carregando: boolean
  entrar: (dto: { emailUsuario: string; senha: string }) => Promise<void>
  sair: () => Promise<void>
}

export const ContextoDaSessao = createContext<Sessao | null>(null)

/**
 * O access token NAO e guardado aqui nem em localStorage — ele vive na memoria
 * do cliente HTTP pela duracao da aba. O refresh vem em cookie httpOnly, que o
 * JS nao le. Guardar token em localStorage num produto com prontuario
 * transforma qualquer XSS em vazamento de prontuario.
 */
export function SessaoProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null)
  const [carregando, setCarregando] = useState(false)

  const entrar = useCallback(async (dto: { emailUsuario: string; senha: string }) => {
    setCarregando(true)
    try {
      // o login devolve `{ token }` — NAO devolve o usuario. Conferido no
      // `TokenResponseDTO` e no `AuthController`
      const { token } = await api<{ token: string }>('/auth/login', {
        method: 'POST',
        body: JSON.stringify(dto),
      })
      definirToken(token)
      setUsuario({ email: emailDoToken(token) ?? dto.emailUsuario })
    } finally {
      setCarregando(false)
    }
  }, [])

  const sair = useCallback(async () => {
    await api('/auth/logout', { method: 'POST' })
    definirToken(null)
    setUsuario(null)
  }, [])

  const valor = useMemo(
    () => ({ usuario, carregando, entrar, sair }),
    [usuario, carregando, entrar, sair],
  )

  return <ContextoDaSessao.Provider value={valor}>{children}</ContextoDaSessao.Provider>
}
```

`src/sessao/useSessao.ts`:

```ts
import { useContext } from 'react'

import { ContextoDaSessao } from './SessaoProvider'

export function useSessao() {
  const sessao = useContext(ContextoDaSessao)
  if (!sessao) {
    throw new Error('useSessao precisa estar dentro de um SessaoProvider')
  }
  return sessao
}
```

`src/sessao/RotaProtegida.tsx`:

```tsx
import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'

import { useSessao } from './useSessao'

export function RotaProtegida({ children }: { children: ReactNode }) {
  const { usuario } = useSessao()
  if (!usuario) {
    return <Navigate to="/entrar" replace />
  }
  return <>{children}</>
}
```

- [ ] **Step 4: Ligar nas rotas**

Em `src/rotas.tsx`, envolva o `<Route element={<Layout />}>` num `<RotaProtegida>` e acrescente a rota pública `/entrar` apontando para um `<p>Entrar</p>` provisório — a tela real vem na Task 6. Em `src/main.tsx`, envolva as rotas no `<SessaoProvider>`.

- [ ] **Step 5: `npm test`** — Expected: verde.

- [ ] **Step 6: Relatar. NÃO COMMITAR.**

---

### Task 5: O molde de formulário — e as regras de quando o erro aparece

**Files:**
- Modify: `frontend/package.json` (`react-hook-form`, `zod`, `@hookform/resolvers`)
- Create: `src/formulario/useFormularioDoProduto.ts`, `src/formulario/ResumoDeErros.tsx`
- Create: `src/ui/Campo.tsx`, `src/ui/Botao.tsx`
- Create: `src/formulario/useFormularioDoProduto.test.tsx`

**Interfaces:**
- Produces: `useFormularioDoProduto(esquema, aoEnviar)` devolvendo o retorno do `useForm` mais `{ erroDeEnvio, enviando }`, já configurado com as regras de §6.2.

Esta tarefa é a saída durável da Fase 0.5 virando código. A tabela de regras de validação daquele plano (`docs/superpowers/plans/2026-09-02-fase-0-5-formularios.md`, constante `VALIDACAO`) é a fonte das mensagens — **use as mesmas frases**, porque elas já foram escritas no registro da §6.3 e já foram exercitadas no protótipo.

**As três regras de §6.2, que hoje não existem em lugar nenhum do produto:**
- no `blur`, só depois do primeiro toque no campo
- no `submit`, sempre, em todos os campos
- depois que um campo já errou, revalida a cada tecla

O `react-hook-form` faz exatamente isso com `mode: 'onTouched'` e `reValidateMode: 'onChange'`. Não reinvente com `useState`.

- [ ] **Step 1: As dependências**

```bash
cd frontend
npm install react-hook-form zod @hookform/resolvers
```

- [ ] **Step 2: O teste que falha**

`src/formulario/useFormularioDoProduto.test.tsx`:

```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { z } from 'zod'

import { renderizar } from '../teste/renderizar'
import { Botao } from '../ui/Botao'
import { Campo } from '../ui/Campo'
import { ResumoDeErros } from './ResumoDeErros'
import { useFormularioDoProduto } from './useFormularioDoProduto'

const esquema = z.object({
  nome: z.string().min(2, 'O nome precisa ter ao menos 2 letras.'),
})

function FormularioDeTeste({ aoEnviar = vi.fn() }) {
  const f = useFormularioDoProduto(esquema, aoEnviar)
  return (
    <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
      <ResumoDeErros erros={f.formState.errors} />
      <Campo rotulo="Nome" obrigatorio erro={f.formState.errors.nome?.message} {...f.register('nome')} />
      <Botao type="submit">Salvar</Botao>
    </form>
  )
}

describe('molde de formulario', () => {
  it('nao acusa erro antes do primeiro toque', async () => {
    renderizar(<FormularioDeTeste />)
    expect(screen.queryByText(/ao menos 2 letras/i)).not.toBeInTheDocument()
  })

  it('acusa no blur depois do primeiro toque', async () => {
    const usuario = userEvent.setup()
    renderizar(<FormularioDeTeste />)

    await usuario.click(screen.getByLabelText(/nome/i))
    await usuario.tab()

    expect(await screen.findByText(/ao menos 2 letras/i)).toBeInTheDocument()
  })

  it('revalida a cada tecla depois que o campo ja errou', async () => {
    const usuario = userEvent.setup()
    renderizar(<FormularioDeTeste />)

    await usuario.click(screen.getByLabelText(/nome/i))
    await usuario.tab()
    await screen.findByText(/ao menos 2 letras/i)

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena')

    await waitFor(() =>
      expect(screen.queryByText(/ao menos 2 letras/i)).not.toBeInTheDocument(),
    )
  })

  it('submit invalido nao morre em silencio: erro, foco e anuncio', async () => {
    const usuario = userEvent.setup()
    const aoEnviar = vi.fn()
    renderizar(<FormularioDeTeste aoEnviar={aoEnviar} />)

    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(aoEnviar).not.toHaveBeenCalled()
    expect(await screen.findByText(/ao menos 2 letras/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/nome/i)).toHaveFocus()
    expect(screen.getByRole('alert')).toHaveTextContent(/ao menos 2 letras/i)
  })

  it('marca obrigatorio ANTES do erro, para o usuario nao descobrir errando', () => {
    renderizar(<FormularioDeTeste />)
    expect(screen.getByLabelText(/nome/i)).toBeRequired()
  })
})
```

- [ ] **Step 3: Rodar e ver falhar** — `npm test -- src/formulario`. Expected: módulo não encontrado.

- [ ] **Step 4: O molde**

`src/formulario/useFormularioDoProduto.ts`:

```ts
import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, type FieldValues, type UseFormProps } from 'react-hook-form'
import type { z } from 'zod'

import { ErroDaApi } from '../api/erro'

/**
 * As tres regras da spec 6.2, num lugar so:
 *
 *   `mode: 'onTouched'`        — no blur, SO depois do primeiro toque
 *   `reValidateMode: 'onChange'` — depois que errou, revalida a cada tecla
 *   `shouldFocusError: true`   — submit invalido poe foco no primeiro invalido
 *
 * O submit sempre valida tudo: e o comportamento padrao do handleSubmit, e e a
 * terceira regra. O `if (!x) return` que morria em silencio nao volta.
 */
export function useFormularioDoProduto<E extends z.ZodType>(
  esquema: E,
  aoEnviar: (valores: z.infer<E>) => Promise<void> | void,
  opcoes: Omit<UseFormProps<z.infer<E>>, 'resolver' | 'mode' | 'reValidateMode'> = {},
) {
  const [erroDeEnvio, setErroDeEnvio] = useState<ErroDaApi | null>(null)
  const [enviando, setEnviando] = useState(false)

  const formulario = useForm<z.infer<E>>({
    ...opcoes,
    resolver: zodResolver(esquema),
    mode: 'onTouched',
    reValidateMode: 'onChange',
    shouldFocusError: true,
  } as UseFormProps<z.infer<E>>)

  async function enviar(valores: FieldValues) {
    setErroDeEnvio(null)
    setEnviando(true)
    try {
      await aoEnviar(valores as z.infer<E>)
    } catch (erro) {
      // O texto do SERVIDOR vence o do zod: ele conhece regras que o cliente
      // nao tem como checar. Cada campo culpado vira erro daquele campo.
      if (erro instanceof ErroDaApi) {
        setErroDeEnvio(erro)
        for (const { campo, erro: mensagem } of erro.erros) {
          formulario.setError(campo as never, { type: 'server', message: mensagem })
        }
      } else {
        throw erro
      }
    } finally {
      setEnviando(false)
    }
  }

  return { ...formulario, enviar, erroDeEnvio, enviando }
}
```

`src/formulario/ResumoDeErros.tsx`:

```tsx
import type { FieldErrors } from 'react-hook-form'

/**
 * O anuncio em aria-live da spec 6.2. Sem ele, quem usa leitor de tela aperta
 * "Salvar" e nao acontece nada audivel — o botao morre em silencio para essa
 * pessoa mesmo com o erro desenhado na tela.
 */
export function ResumoDeErros({ erros }: { erros: FieldErrors }) {
  const mensagens = Object.values(erros)
    .map((e) => e?.message)
    .filter((m): m is string => typeof m === 'string')

  return (
    <div role="alert" aria-live="assertive">
      {mensagens.length > 0 && (
        <>
          <p>Confira os campos abaixo:</p>
          <ul>
            {mensagens.map((m) => (
              <li key={m}>{m}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 5: Os dois componentes**

`src/ui/Campo.tsx` — rótulo ligado ao input, obrigatório visível, erro associado por `aria-describedby`:

```tsx
import { forwardRef, useId, type InputHTMLAttributes } from 'react'

type Props = InputHTMLAttributes<HTMLInputElement> & {
  rotulo: string
  erro?: string
  obrigatorio?: boolean
}

/** Obrigatorio e VISIVEL antes do erro (spec 6.2): campo obrigatorio marcado,
 *  opcional escrito "opcional". O usuario nao descobre a regra errando. */
export const Campo = forwardRef<HTMLInputElement, Props>(function Campo(
  { rotulo, erro, obrigatorio = false, ...resto },
  ref,
) {
  const id = useId()
  const idDoErro = `${id}-erro`

  return (
    <div>
      <label htmlFor={id}>
        {rotulo}
        {obrigatorio ? <span aria-hidden="true"> *</span> : ' (opcional)'}
      </label>
      <input
        id={id}
        ref={ref}
        required={obrigatorio}
        aria-invalid={erro ? true : undefined}
        aria-describedby={erro ? idDoErro : undefined}
        {...resto}
      />
      {erro && <p id={idDoErro}>{erro}</p>}
    </div>
  )
})
```

`src/ui/Botao.tsx`:

```tsx
import type { ButtonHTMLAttributes } from 'react'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variante?: 'primario' | 'secundario'
}

/** Acao e FORMA, nao cor (ver o comentario em estilo/tokens.css): pilula =
 *  acao, bloco chapado = evento. Por isso botao e evento podem ser azuis sem
 *  confundir. */
export function Botao({ variante = 'primario', ...resto }: Props) {
  return <button data-variante={variante} {...resto} />
}
```

- [ ] **Step 6: `npm test`** — Expected: verde, com os cinco testes do molde passando.

- [ ] **Step 7: Relatar. NÃO COMMITAR.**

---

### Task 6: Login

**Files:**
- Create: `src/telas/login/LoginPage.tsx`, `src/telas/login/esquema.ts`
- Create: `src/telas/login/LoginPage.test.tsx`
- Modify: `src/rotas.tsx`

**Interfaces:**
- Consumes: `useFormularioDoProduto`, `useSessao`, `Campo`, `Botao`, `ResumoDeErros`
- Produces: nada que outra tarefa consuma. É a tela que prova a fundação inteira.

O corpo que a API espera é `{ emailUsuario, senha }` — confirmado em `UsuarioLoginDTO`. Note o nome: `emailUsuario`, não `email`. Errar isso produz um 400 confuso.

- [ ] **Step 1: Os testes que falham**

`src/telas/login/LoginPage.test.tsx`:

```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { servidor } from '../../teste/servidor'
import { renderizar } from '../../teste/renderizar'
import { SessaoProvider } from '../../sessao/SessaoProvider'
import { LoginPage } from './LoginPage'

const renderizarLogin = () =>
  renderizar(
    <SessaoProvider>
      <LoginPage />
    </SessaoProvider>,
  )

describe('login', () => {
  it('nao envia com campo vazio, e diz o que falta', async () => {
    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.click(screen.getByRole('button', { name: /entrar/i }))

    expect(await screen.findByText(/informe um e-mail/i)).toBeInTheDocument()
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('avisa que o e-mail esta mal formado antes de ir ao servidor', async () => {
    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.type(screen.getByLabelText(/e-mail/i), 'helena-arroba-nada')
    await usuario.tab()

    expect(await screen.findByText(/e-mail em formato válido/i)).toBeInTheDocument()
  })

  it('mostra a mensagem do SERVIDOR quando a credencial esta errada', async () => {
    servidor.use(
      http.post('/auth/login', () =>
        HttpResponse.json(
          { status: 401, mensagem: 'E-mail ou senha incorretos.', erros: [] },
          { status: 401 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.type(screen.getByLabelText(/e-mail/i), 'marina@example.com')
    await usuario.type(screen.getByLabelText(/senha/i), 'senha-que-nao-e-a-dela')
    await usuario.click(screen.getByRole('button', { name: /entrar/i }))

    expect(await screen.findByText(/e-mail ou senha incorretos/i)).toBeInTheDocument()
  })

  it('login que da certo guarda o token e leva para o dia', async () => {
    // o corpo e `{ token }` — o login NAO devolve o usuario (TokenResponseDTO)
    const jwt = [
      'eyJhbGciOiJIUzI1NiJ9',
      btoa(JSON.stringify({ sub: 'marina@example.com' })).replace(/=+$/, ''),
      'assinatura-que-o-cliente-nao-verifica',
    ].join('.')

    servidor.use(http.post('/auth/login', () => HttpResponse.json({ token: jwt })))

    let autorizacao: string | null = null
    servidor.use(
      http.get('/painel/hoje', ({ request }) => {
        autorizacao = request.headers.get('Authorization')
        return HttpResponse.json({ proxima: null, sessoes: [], pendencias: [] })
      }),
    )

    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.type(screen.getByLabelText(/e-mail/i), 'marina@example.com')
    await usuario.type(screen.getByLabelText(/senha/i), 'uma-senha-comprida-o-bastante')
    await usuario.click(screen.getByRole('button', { name: /entrar/i }))

    // a prova de que a fundacao inteira funciona: o token do corpo virou
    // header na chamada seguinte. Sem isso, tudo depois do login da 401
    await waitFor(() => expect(autorizacao).toBe(`Bearer ${jwt}`))
  })

  it('a senha nao aparece na tela', async () => {
    const usuario = userEvent.setup()
    renderizarLogin()

    await usuario.type(screen.getByLabelText(/senha/i), 'segredo')
    expect(screen.getByLabelText(/senha/i)).toHaveAttribute('type', 'password')
  })
})
```

- [ ] **Step 2: Rodar e ver falhar** — Expected: módulo não encontrado.

- [ ] **Step 3: O esquema**

`src/telas/login/esquema.ts`:

```ts
import { z } from 'zod'

/**
 * Espelha o `UsuarioLoginDTO` do backend. As mensagens sao as MESMAS que o
 * servidor usa — assim o usuario le a mesma frase vindo do cliente ou do
 * servidor, e nao percebe qual dos dois recusou.
 *
 * Aqui NAO ha regra de forca de senha: o login so precisa saber que o campo
 * foi preenchido. Exigir 10 caracteres na tela de entrada recusaria quem tem
 * senha antiga e ainda valida.
 */
export const esquemaDoLogin = z.object({
  emailUsuario: z
    .string()
    .min(1, 'Informe um e-mail.')
    .email('Informe um e-mail em formato válido.'),
  senha: z.string().min(1, 'Informe a senha.'),
})

export type DadosDoLogin = z.infer<typeof esquemaDoLogin>
```

- [ ] **Step 4: A tela**

`src/telas/login/LoginPage.tsx`:

```tsx
import { useNavigate } from 'react-router-dom'

import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { useSessao } from '../../sessao/useSessao'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { esquemaDoLogin } from './esquema'

export function LoginPage() {
  const { entrar } = useSessao()
  const navegar = useNavigate()

  const f = useFormularioDoProduto(esquemaDoLogin, async (valores) => {
    await entrar(valores)
    navegar('/', { replace: true })
  })

  return (
    <main>
      <h1>Entrar</h1>

      <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
        <ResumoDeErros erros={f.formState.errors} />

        {/* o 401 nao tem campo culpado — a API nao diz qual dos dois errou, de
            proposito, para nao confirmar se um e-mail existe */}
        {f.erroDeEnvio && f.erroDeEnvio.erros.length === 0 && (
          <p role="alert">{f.erroDeEnvio.mensagem}</p>
        )}

        <Campo
          rotulo="E-mail"
          type="email"
          autoComplete="username"
          obrigatorio
          erro={f.formState.errors.emailUsuario?.message}
          {...f.register('emailUsuario')}
        />

        <Campo
          rotulo="Senha"
          type="password"
          autoComplete="current-password"
          obrigatorio
          erro={f.formState.errors.senha?.message}
          {...f.register('senha')}
        />

        <Botao type="submit" disabled={f.enviando}>
          {f.enviando ? 'Entrando…' : 'Entrar'}
        </Botao>
      </form>
    </main>
  )
}
```

- [ ] **Step 5: Ligar a rota** — em `src/rotas.tsx`, troque o `<p>Entrar</p>` provisório por `<LoginPage />`.

- [ ] **Step 6: `npm test`** — Expected: verde.

- [ ] **Step 7: Relatar. NÃO COMMITAR.**

---

### Task 7: Hoje

**Files:**
- Create: `src/telas/hoje/HojePage.tsx`, `src/telas/hoje/queries.ts`
- Create: `src/telas/hoje/HojePage.test.tsx`
- Create: `src/ui/Cartao.tsx`, `src/ui/Chip.tsx`, `src/ui/EstadoVazio.tsx`
- Modify: `src/rotas.tsx`

**Interfaces:**
- Consumes: `api`, `deApi`, o contrato gerado
- Produces: `usePainelDeHoje()` e `useMarcarStatus()` — a Task 8 não os consome, mas a Fatia 2 vai.

A tela responde *"como é meu dia"* (§4①): próxima sessão em destaque, lista do dia com status em um clique na própria linha, e faixa de pendências.

**A API devolve a tela inteira numa chamada** (`GET /painel/hoje`), com `{ proxima, sessoes, pendencias }`. Não faça três chamadas.

**Ela NÃO devolve "quanto falta" em minutos, de propósito** — seria um número que nasce velho. O cliente calcula de `dtConsulta`, e recalcula enquanto a tela está aberta.

**Modo privacidade** (§4①): a tela fica aberta em tela cheia no consultório com paciente entrando. Um clique borra os nomes. É a decisão original de esconder dado clínico, preservada sem matar a utilidade.

- [ ] **Step 1: Os testes que falham**

`src/telas/hoje/HojePage.test.tsx`:

```tsx
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { HojePage } from './HojePage'

const PAINEL = {
  proxima: {
    consultaId: '11111111-1111-1111-1111-111111111111',
    dtConsulta: '2026-12-02T15:00:00',
    pacienteId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    pacienteNome: 'Rui Sampaio',
    status: 'AGENDADA',
  },
  sessoes: [
    {
      consultaId: '22222222-2222-2222-2222-222222222222',
      dtConsulta: '2026-12-02T08:00:00',
      pacienteId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      pacienteNome: 'Helena Vaz',
      status: 'AGENDADA',
    },
    {
      consultaId: '11111111-1111-1111-1111-111111111111',
      dtConsulta: '2026-12-02T15:00:00',
      pacienteId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      pacienteNome: 'Rui Sampaio',
      status: 'AGENDADA',
    },
  ],
  pendencias: [
    {
      consultaId: '22222222-2222-2222-2222-222222222222',
      dtConsulta: '2026-12-02T08:00:00',
      pacienteNome: 'Helena Vaz',
    },
  ],
}

const comPainel = (painel: unknown = PAINEL) =>
  servidor.use(http.get('/painel/hoje', () => HttpResponse.json(painel)))

describe('tela Hoje', () => {
  it('mostra a proxima sessao em destaque', async () => {
    comPainel()
    renderizar(<HojePage />)

    const destaque = await screen.findByRole('region', { name: /proxima sessao/i })
    expect(within(destaque).getByText(/rui sampaio/i)).toBeInTheDocument()
  })

  it('lista o dia em ordem, com o status de cada sessao', async () => {
    comPainel()
    renderizar(<HojePage />)

    const linhas = await screen.findAllByRole('listitem', { name: /sessao/i })
    expect(linhas).toHaveLength(2)
    expect(within(linhas[0]).getByText(/helena vaz/i)).toBeInTheDocument()
  })

  it('marca Realizada em um clique na propria linha, sem abrir dialogo', async () => {
    comPainel()
    let corpoEnviado: unknown
    servidor.use(
      http.patch('/consultas/:id/status', async ({ request }) => {
        corpoEnviado = await request.json()
        return HttpResponse.json({ status: 'REALIZADA' })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<HojePage />)

    const linhas = await screen.findAllByRole('listitem', { name: /sessao/i })
    await usuario.click(within(linhas[0]).getByRole('button', { name: /realizada/i }))

    expect(corpoEnviado).toEqual({ status: 'REALIZADA' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('mostra quanto falta para a proxima, calculado no cliente', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 11, 2, 14, 30, 0)) // 30 min antes das 15h
    comPainel()
    renderizar(<HojePage />)

    const destaque = await screen.findByRole('region', { name: /proxima sessao/i })
    expect(within(destaque).getByText(/em 30 min/i)).toBeInTheDocument()
    vi.useRealTimers()
  })

  it('mostra a faixa de pendencias', async () => {
    comPainel()
    renderizar(<HojePage />)

    const faixa = await screen.findByRole('region', { name: /pendencia/i })
    expect(within(faixa).getByText(/helena vaz/i)).toBeInTheDocument()
  })

  it('modo privacidade borra os nomes com um clique', async () => {
    comPainel()
    const usuario = userEvent.setup()
    renderizar(<HojePage />)

    await screen.findByText(/rui sampaio/i)
    await usuario.click(screen.getByRole('button', { name: /privacidade/i }))

    // a spec 4 pede BORRAR, nao remover: o psicologo continua sabendo onde
    // cada sessao esta, sem que quem entra na sala leia o nome
    expect(screen.getByText(/rui sampaio/i)).toHaveAttribute('data-borrado', 'true')
  })

  it('dia 1 do psicologo: diz o que fazer, e nao e erro', async () => {
    comPainel({ proxima: null, sessoes: [], pendencias: [] })
    renderizar(<HojePage />)

    expect(await screen.findByText(/nenhuma sessao hoje/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /agenda/i })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Rodar e ver falhar** — Expected: módulo não encontrado.

- [ ] **Step 3: As queries**

`src/telas/hoje/queries.ts`:

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'

export type SessaoDoDia = {
  consultaId: string
  dtConsulta: string
  pacienteId: string
  pacienteNome: string
  status: 'AGENDADA' | 'REALIZADA' | 'CANCELADA' | 'FALTOU'
}

export type PendenciaDoPainel = {
  consultaId: string
  dtConsulta: string
  pacienteNome: string
}

export type PainelDeHoje = {
  proxima: SessaoDoDia | null
  sessoes: SessaoDoDia[]
  pendencias: PendenciaDoPainel[]
}

export const CHAVE_DO_PAINEL = ['painel', 'hoje'] as const

/** Uma chamada devolve a tela inteira (spec 5.6). Nao quebre em tres. */
export function usePainelDeHoje() {
  return useQuery({
    queryKey: CHAVE_DO_PAINEL,
    queryFn: () => api<PainelDeHoje>('/painel/hoje'),
  })
}

/**
 * So REALIZADA e FALTOU: a API recusa os outros dois com 400, e a razao e de
 * seguranca — reabrir uma sessao cancelada por aqui reocuparia um horario que
 * ja foi dado a outra pessoa.
 */
export function useMarcarStatus() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'REALIZADA' | 'FALTOU' }) =>
      api(`/consultas/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      }),
    // o painel inteiro se refaz: marcar presenca tira a sessao da faixa de
    // pendencias, e as duas metades da tela nao podem discordar
    onSuccess: () => cliente.invalidateQueries({ queryKey: CHAVE_DO_PAINEL }),
  })
}
```

- [ ] **Step 4: A tela**

`src/telas/hoje/HojePage.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { deApi } from '../../api/data'
import { Botao } from '../../ui/Botao'
import { Cartao } from '../../ui/Cartao'
import { Chip } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { useMarcarStatus, usePainelDeHoje, type SessaoDoDia } from './queries'

const hora = (iso: string) =>
  deApi(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

export function HojePage() {
  const { data, isLoading, isError } = usePainelDeHoje()
  const marcar = useMarcarStatus()
  const [privacidade, setPrivacidade] = useState(false)

  if (isLoading) return <p>Carregando o dia…</p>
  if (isError) return <p role="alert">Nao foi possivel carregar o dia. Tente de novo.</p>
  if (!data) return null

  const Nome = ({ children }: { children: string }) => (
    <span data-borrado={privacidade ? 'true' : undefined}>{children}</span>
  )

  return (
    <main>
      <header>
        <h1>Hoje</h1>
        {/* spec 4(1): a tela fica aberta em tela cheia no consultorio, com
            paciente entrando. Um clique borra os nomes — a decisao de esconder
            dado clinico preservada sem matar a utilidade */}
        <Botao
          variante="secundario"
          aria-pressed={privacidade}
          onClick={() => setPrivacidade((p) => !p)}
        >
          Modo privacidade
        </Botao>
      </header>

      {data.proxima ? (
        <section aria-label="Proxima sessao">
          <Cartao>
            <p>{hora(data.proxima.dtConsulta)}</p>
            <p><Nome>{data.proxima.pacienteNome}</Nome></p>
            <QuantoFalta para={data.proxima.dtConsulta} />
            <Link to={`/pacientes/${data.proxima.pacienteId}`}>Abrir a ficha</Link>
          </Cartao>
        </section>
      ) : null}

      <section aria-label="Sessoes de hoje">
        {data.sessoes.length === 0 ? (
          <EstadoVazio
            titulo="Nenhuma sessao hoje"
            acao={<Link to="/agenda">Ver a agenda</Link>}
          />
        ) : (
          <ul>
            {data.sessoes.map((s) => (
              <LinhaDaSessao
                key={s.consultaId}
                sessao={s}
                privacidade={privacidade}
                aoMarcar={(status) => marcar.mutate({ id: s.consultaId, status })}
              />
            ))}
          </ul>
        )}
      </section>

      {data.pendencias.length > 0 && (
        <section aria-label="Pendencias">
          <ul>
            {data.pendencias.map((p) => (
              <li key={p.consultaId}>
                {hora(p.dtConsulta)} · <Nome>{p.pacienteNome}</Nome> · sem status
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}

/**
 * A spec 4(1) pede "quanto falta" no destaque. A API NAO devolve isso de
 * proposito — seria um numero que nasce velho. O cliente calcula de
 * `dtConsulta`, que e um fato, e recalcula de minuto em minuto enquanto a tela
 * esta aberta: ela fica horas aberta no consultorio, e um "faltam 20 minutos"
 * congelado e pior que nenhum.
 */
function QuantoFalta({ para }: { para: string }) {
  const [agora, setAgora] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setAgora(Date.now()), 60_000)
    return () => clearInterval(id)
  }, [])

  const minutos = Math.round((deApi(para).getTime() - agora) / 60_000)

  if (minutos < 0) return <p>Ja passou do horario.</p>
  if (minutos === 0) return <p>Agora.</p>
  if (minutos < 60) return <p>Em {minutos} min</p>

  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  return <p>Em {horas}h{resto > 0 ? ` ${resto}min` : ''}</p>
}

function LinhaDaSessao({
  sessao,
  privacidade,
  aoMarcar,
}: {
  sessao: SessaoDoDia
  privacidade: boolean
  aoMarcar: (status: 'REALIZADA' | 'FALTOU') => void
}) {
  return (
    <li aria-label={`Sessao de ${sessao.pacienteNome}`}>
      <span>{hora(sessao.dtConsulta)}</span>
      <span data-borrado={privacidade ? 'true' : undefined}>{sessao.pacienteNome}</span>
      <Chip>{sessao.status}</Chip>
      {/* spec 4(1): em um clique NA PROPRIA LINHA. Hoje exige abrir dialogo */}
      {sessao.status === 'AGENDADA' && (
        <>
          <Botao variante="secundario" onClick={() => aoMarcar('REALIZADA')}>
            Realizada
          </Botao>
          <Botao variante="secundario" onClick={() => aoMarcar('FALTOU')}>
            Faltou
          </Botao>
        </>
      )}
    </li>
  )
}
```

Os três componentes de `ui/`:

```tsx
// src/ui/Cartao.tsx
import type { ReactNode } from 'react'

export function Cartao({ children }: { children: ReactNode }) {
  return <article>{children}</article>
}
```

```tsx
// src/ui/Chip.tsx
import type { ReactNode } from 'react'

/** Status da sessao. NAO e pilula: pilula e acao (ver tokens.css). Chip e
 *  rotulo de estado, e a forma tem que separar os dois. */
export function Chip({ children }: { children: ReactNode }) {
  return <span data-chip="">{children}</span>
}
```

```tsx
// src/ui/EstadoVazio.tsx
import type { ReactNode } from 'react'

/** Estado vazio diz o que fazer, nao so que esta vazio. "Nenhuma sessao hoje"
 *  sozinho deixa o psicologo sem saida; com o atalho para a agenda, ele tem
 *  uma. */
export function EstadoVazio({ titulo, acao }: { titulo: string; acao?: ReactNode }) {
  return (
    <div>
      <p>{titulo}</p>
      {acao}
    </div>
  )
}
```

- [ ] **Step 5: Ligar a rota** — em `src/rotas.tsx`, troque `<p>Hoje</p>` por `<HojePage />`.

- [ ] **Step 6: `npm test`** — Expected: verde.

- [ ] **Step 7: Relatar. NÃO COMMITAR.**

---

### Task 8: Agenda

**Files:**
- Create: `src/telas/agenda/AgendaPage.tsx`, `queries.ts`, `DialogoDeConsulta.tsx`, `esquema.ts`
- Create: `src/telas/agenda/AgendaPage.test.tsx`, `src/telas/agenda/DialogoDeConsulta.test.tsx`
- Modify: `src/rotas.tsx`

**Interfaces:**
- Consumes: `api`, `paraApi`, `useFormularioDoProduto`
- Produces: nada consumido nesta fatia.

§4②: semana por padrão — o "hoje" já é a tela ①. Agendar clicando no horário.

**O conflito é avisado ANTES do envio**, e é o motivo de `GET /consultas/conflito` existir. Sem isso o front só descobre apanhando no POST.

**Remarcar arrastando fica FORA desta tarefa.** §4② cita, mas é interação de arraste com um custo próprio, e a fatia entrega valor sem ela. Se sobrar tempo, é conversa de escopo com o dono — não decisão de implementação.

- [ ] **Step 1: Os testes que falham**

`src/telas/agenda/DialogoDeConsulta.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { DialogoDeConsulta } from './DialogoDeConsulta'

const abrir = (props = {}) =>
  renderizar(
    <DialogoDeConsulta
      aberto
      dtInicial="2026-12-02T14:00:00"
      aoFechar={vi.fn()}
      {...props}
    />,
  )

describe('dialogo de consulta', () => {
  it('avisa do conflito ANTES do envio, dizendo QUEM ocupa', async () => {
    servidor.use(
      http.get('/consultas/conflito', () =>
        HttpResponse.json({
          livre: false,
          pacienteNome: 'Otavio Lins',
          consultaId: '33333333-3333-3333-3333-333333333333',
        }),
      ),
    )
    abrir()

    // o aviso aparece sem o usuario ter clicado em salvar
    expect(await screen.findByText(/otavio lins/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /salvar/i })).toBeEnabled()
  })

  it('horario livre nao mostra aviso nenhum', async () => {
    servidor.use(
      http.get('/consultas/conflito', () =>
        HttpResponse.json({ livre: true, pacienteNome: null, consultaId: null }),
      ),
    )
    abrir()

    expect(await screen.findByLabelText(/data e hora/i)).toBeInTheDocument()
    expect(screen.queryByText(/ja esta ocupado/i)).not.toBeInTheDocument()
  })

  it('manda a data no formato que a API aceita, sem offset', async () => {
    servidor.use(
      http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })),
    )
    let corpo: { dtConsulta?: string } = {}
    servidor.use(
      http.post('/consultas', async ({ request }) => {
        corpo = (await request.json()) as { dtConsulta?: string }
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.selectOptions(await screen.findByLabelText(/paciente/i), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(corpo.dtConsulta).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)
    expect(corpo.dtConsulta).not.toContain('Z')
  })
})
```

`src/telas/agenda/AgendaPage.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { AgendaPage } from './AgendaPage'

describe('agenda', () => {
  it('mostra a semana, nao o dia — o dia ja e a tela Hoje', async () => {
    servidor.use(http.get('/consultas', () => HttpResponse.json([])))
    renderizar(<AgendaPage />)

    const grade = await screen.findByRole('grid', { name: /semana/i })
    // sete colunas de dia
    expect(within(grade).getAllByRole('columnheader')).toHaveLength(7)
  })

  it('clicar num horario abre o dialogo ja com aquele horario', async () => {
    servidor.use(http.get('/consultas', () => HttpResponse.json([])))
    servidor.use(http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })))
    const usuario = userEvent.setup()
    renderizar(<AgendaPage />)

    const celulas = await screen.findAllByRole('gridcell')
    await usuario.click(celulas[0])

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })
})
```

Acrescente `import { within } from '@testing-library/react'` onde faltar.

- [ ] **Step 2: Rodar e ver falhar** — Expected: módulo não encontrado.

- [ ] **Step 3: As queries**

`src/telas/agenda/queries.ts`:

```ts
import { useQuery } from '@tanstack/react-query'

import { api } from '../../api/cliente'

export type Conflito = {
  livre: boolean
  pacienteNome: string | null
  consultaId: string | null
}

/**
 * Checagem PREVIA (spec 5.6 e 6.3). A API ja recusa conflito no envio; sem
 * esta rota o front so descobre apanhando.
 *
 * `excluirConsultaId` e opcional e existe para a EDICAO: sem ele, remarcar a
 * consulta da Helena responderia "ocupado por Helena Vaz", impedindo um
 * salvamento que a API aceitaria.
 */
export function useConflito(dtConsulta: string | null, excluirConsultaId?: string) {
  return useQuery({
    queryKey: ['conflito', dtConsulta, excluirConsultaId],
    enabled: Boolean(dtConsulta),
    queryFn: () => {
      const parametros = new URLSearchParams({ dtConsulta: dtConsulta! })
      if (excluirConsultaId) parametros.set('excluirConsultaId', excluirConsultaId)
      return api<Conflito>(`/consultas/conflito?${parametros}`)
    },
  })
}
```

- [ ] **Step 4: O esquema e o diálogo**

`src/telas/agenda/esquema.ts`:

```ts
import { z } from 'zod'

export const esquemaDaConsulta = z.object({
  pacienteId: z.string().uuid('Escolha o paciente.'),
  dtConsulta: z
    .string()
    .regex(
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/,
      'Informe data e hora.',
    ),
})

export type DadosDaConsulta = z.infer<typeof esquemaDaConsulta>
```

`src/telas/agenda/DialogoDeConsulta.tsx`:

```tsx
import * as Dialog from '@radix-ui/react-dialog'
import { useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { Botao } from '../../ui/Botao'
import { esquemaDaConsulta } from './esquema'
import { useConflito } from './queries'

type Props = {
  aberto: boolean
  dtInicial: string
  aoFechar: () => void
  /** presente na EDICAO: tira a propria consulta da checagem de conflito */
  consultaId?: string
}

export function DialogoDeConsulta({ aberto, dtInicial, aoFechar, consultaId }: Props) {
  const cliente = useQueryClient()

  const f = useFormularioDoProduto(
    esquemaDaConsulta,
    async (valores) => {
      await api('/consultas', { method: 'POST', body: JSON.stringify(valores) })
      await cliente.invalidateQueries({ queryKey: ['consultas'] })
      aoFechar()
    },
    { defaultValues: { pacienteId: '', dtConsulta: dtInicial } },
  )

  const dtConsulta = f.watch('dtConsulta')
  const conflito = useConflito(dtConsulta, consultaId)

  return (
    <Dialog.Root open={aberto} onOpenChange={(a) => !a && aoFechar()}>
      <Dialog.Portal>
        <Dialog.Overlay />
        <Dialog.Content aria-describedby={undefined}>
          <Dialog.Title>Nova consulta</Dialog.Title>

          <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
            <ResumoDeErros erros={f.formState.errors} />

            <label htmlFor="paciente">Paciente</label>
            <select id="paciente" {...f.register('pacienteId')}>
              <option value="">Escolha…</option>
            </select>
            {f.formState.errors.pacienteId && <p>{f.formState.errors.pacienteId.message}</p>}

            <label htmlFor="quando">Data e hora</label>
            <input id="quando" type="datetime-local" step="1" {...f.register('dtConsulta')} />

            {/* spec 6.3: conflito AVISADO antes do envio, dizendo QUEM ocupa.
                `role="status"` e nao `alert` de proposito: e informacao, nao
                erro — o psicologo pode ter razao para marcar assim mesmo */}
            {conflito.data && !conflito.data.livre && (
              <p role="status">
                Esse horario ja esta ocupado por {conflito.data.pacienteNome}.
              </p>
            )}

            <Botao type="submit" disabled={f.enviando}>Salvar</Botao>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
```

O `<select>` fica sem opções nesta fatia: a lista de pacientes é da Fatia 2. O teste preenche por `selectOptions` com um id conhecido, então acrescente as opções de teste via `defaultValues` ou um mock — e **diga no relatório** que a lista real depende da Fatia 2.

**O aviso NÃO desabilita o botão.** §4② diz avisar, e a spec separa avisar de bloquear: data no passado avisa sem bloquear, e conflito é da mesma família — o psicólogo pode ter razão para marcar assim mesmo, e quem decide é ele. A API continua sendo a autoridade e recusa se for o caso.

- [ ] **Step 5: A grade da semana**

`src/telas/agenda/AgendaPage.tsx`:

```tsx
import { useState } from 'react'

import { paraApi } from '../../api/data'
import { DialogoDeConsulta } from './DialogoDeConsulta'

/** Segunda a domingo da semana que contem `base`. */
function diasDaSemana(base: Date): Date[] {
  const segunda = new Date(base)
  const desvio = (segunda.getDay() + 6) % 7 // domingo=0 vira 6
  segunda.setDate(segunda.getDate() - desvio)
  segunda.setHours(0, 0, 0, 0)
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(segunda)
    d.setDate(segunda.getDate() + i)
    return d
  })
}

const HORAS = Array.from({ length: 13 }, (_, i) => i + 7) // 07h as 19h

export function AgendaPage() {
  const [semana] = useState(() => diasDaSemana(new Date()))
  const [quando, setQuando] = useState<string | null>(null)

  return (
    <main>
      {/* semana por padrao (spec 4-2): o "hoje" ja e a tela (1) */}
      <h1>Agenda</h1>

      <table role="grid" aria-label="Semana">
        <thead>
          <tr>
            <th scope="col">Hora</th>
            {semana.map((d) => (
              <th key={d.toISOString()} scope="col">
                {d.toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit' })}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {HORAS.map((h) => (
            <tr key={h}>
              <th scope="row">{String(h).padStart(2, '0')}:00</th>
              {semana.map((d) => {
                const celula = new Date(d)
                celula.setHours(h, 0, 0, 0)
                return (
                  <td
                    key={celula.toISOString()}
                    role="gridcell"
                    tabIndex={0}
                    aria-label={`Agendar ${celula.toLocaleString('pt-BR')}`}
                    onClick={() => setQuando(paraApi(celula))}
                  />
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>

      {quando && (
        <DialogoDeConsulta aberto dtInicial={quando} aoFechar={() => setQuando(null)} />
      )}
    </main>
  )
}
```

O cabeçalho da coluna "Hora" faz oito `columnheader`, não sete. O teste da Task 8 espera sete — **ajuste o teste para `toHaveLength(8)`**, ou dê ao canto um `<td>` em vez de `<th>`. A segunda opção é melhor: aquele canto não rotula coluna nenhuma.

- [ ] **Step 6: `npm test`** — Expected: verde.

- [ ] **Step 7: Relatar. NÃO COMMITAR.**

---

### Task 9: O fluxo da fatia, de ponta a ponta

**Files:**
- Modify: `frontend/e2e/fluxo-de-fumaca.spec.ts`

Os testes das tarefas anteriores usam MSW: provam que a tela faz a coisa certa **com a resposta que ela espera**. Este prova que a resposta que ela espera é a que a API dá — a divergência que nenhum mock pega.

**Precisa do backend no ar** e de um usuário criado. Se o ambiente não permitir, **diga** em vez de transformar o e2e num teste com mock.

- [ ] **Step 1: O teste**

```ts
import { expect, test } from '@playwright/test'

test('o psicologo entra, ve o dia e marca presenca', async ({ page }) => {
  await page.goto('/entrar')

  await page.getByLabel(/e-mail/i).fill(process.env.E2E_EMAIL!)
  await page.getByLabel(/senha/i).fill(process.env.E2E_SENHA!)
  await page.getByRole('button', { name: /entrar/i }).click()

  await expect(page.getByRole('heading', { name: /hoje/i })).toBeVisible()

  // a 9 do spec: "o psicologo entra, ve o dia, agenda e marca presenca"
  await page.getByRole('link', { name: /agenda/i }).click()
  await expect(page.getByRole('grid', { name: /semana/i })).toBeVisible()
})
```

- [ ] **Step 2: Rodar** — `npm run e2e`. Expected: verde com o backend no ar.

- [ ] **Step 3: Relatar. NÃO COMMITAR.**

---

## O que este plano NÃO faz

- **Remarcar arrastando** (§4②) — interação de arraste com custo próprio; a fatia entrega valor sem ela, e incluí-la aqui é decisão de escopo do dono
- **Série recorrente** na Agenda — o backend tem a rota, mas §9 põe o peso da Fatia 1 na fundação; entra na Fatia 2 sem retrabalho
- **Pacientes, Financeiro e Ajustes** — Fatias 2, 3 e 4. Aparecem na navegação com o motivo escrito, porque destino que existe e não leva a lugar nenhum é pior que destino ausente
- **As outras duas pendências do painel** — "realizada sem anotação" é Fatia 2, "realizada sem baixa" é Fatia 3. §9 é explícita que a tela de §4 descreve o estado final
- **Refresh automático do token em 401** — o backend tem `/auth/refresh`, mas o fluxo de reentrada silenciosa é do tamanho de uma tarefa própria e não bloqueia a fatia
