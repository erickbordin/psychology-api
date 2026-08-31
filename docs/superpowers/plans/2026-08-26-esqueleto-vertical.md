# Esqueleto vertical do frontend — plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use `superpowers:subagent-driven-development`
> (recomendado) ou `superpowers:executing-plans` para executar tarefa a tarefa. Os
> passos usam checkbox (`- [ ]`) para acompanhamento.

**Objetivo:** levar o `psychology-web` de repositório vazio a uma vertical que
atravessa o produto inteiro — entrar, cadastrar paciente, registrar anotação de
sessão — com o E2E de fumaça passando contra a API real.

**Arquitetura:** SPA React com três camadas de fronteira dura (`api/recursos`
falam HTTP, `features/*/queries.ts` embrulham em TanStack Query, componentes
consomem hooks). Access token só em memória; o cookie `HttpOnly` de refresh
sustenta o recarregamento via um `POST /auth/refresh` no boot. Sem estilo final:
tokens e layout cru, o visual vem numa etapa posterior.

**Stack:** Vite, React, TypeScript, React Router, TanStack Query, Tailwind v4
(CSS-first), Vitest, Testing Library, MSW v2, Playwright.

**Spec:** `docs/superpowers/specs/2026-08-26-frontend-design.md`

**Depende de:** `psychology-api` com a fatia 1 aplicada — CORS, `POST /auth/refresh`,
`POST /auth/logout` e `GET /pacientes/{id}/consultas`. Já está na `main` daquele repo.

## Restrições globais

- **Node 20 obrigatório em todo comando.** O `asdf` desta máquina fixa
  `nodejs 9.11.2` via `~/.tool-versions`, e o `npm` do nvm quebra com
  `Cannot find module 'node:path'` se o `node` do PATH for o v9. Em **cada** shell
  novo, antes de qualquer `npm`/`npx`:
  ```bash
  export PATH=/home/syonet/.nvm/versions/node/v20.20.2/bin:$PATH
  ```
- Access token **só em memória**. Nunca armazenamento do navegador — nem para o
  token, nem para rascunho de anotação.
- Rascunho de anotação só em memória, com guarda de navegação (`useBlocker` mais
  `beforeunload`). Sem a guarda, "em memória" vira "perde em silêncio".
- Nenhum componente monta URL ou toca em `Response`. Isso é de `api/recursos/*`.
- Filtro e paginação vivem na URL (`useSearchParams`), nunca em `useState`.
- Erro tem um caminho só: `ErroApi` a partir do envelope `{status, mensagem, erros[]}`.
- Handlers MSW respondem o contrato **real** da API, incluindo `PagedModel` com
  `page.totalElements`.
- O proxy do Vite **espelha os caminhos da API** (`/auth`, `/pacientes`, …), sem
  prefixo `/api`: o cookie de refresh é `Path=/auth`, e sob um prefixo o navegador
  não o enviaria.
- Validação no cliente só para campo vazio; o resto vem do envelope da API.
- Rodar os testes: `npm test`. Rodar o E2E: `npm run e2e` (exige a API no ar).

---

### Task 1: scaffold, tokens e harness de teste

**Arquivos:**
- Criar: `package.json`, `tsconfig.json`, `tsconfig.node.json`, `vite.config.ts`,
  `index.html`, `src/main.tsx`, `src/App.tsx`, `src/estilo/tokens.css`,
  `src/teste/setup.ts`, `src/App.test.tsx`

**Interfaces:**
- Consome: nada.
- Produz: projeto que roda (`npm run dev`), compila (`npm run build`) e testa
  (`npm test`); tokens Tailwind disponíveis como utilitários (`bg-superficie`,
  `text-tinta`, `font-serif`, `font-mono`).

- [ ] **Passo 1: criar o `package.json`**

Scaffold manual em vez de `npm create vite`: o gerador é interativo quando o
diretório não está vazio (aqui já há `docs/` e `.gitignore`), e um plano não pode
depender de alguém responder um prompt.

```json
{
  "name": "psychology-web",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "e2e": "playwright test"
  }
}
```

- [ ] **Passo 2: instalar as dependências**

```bash
export PATH=/home/syonet/.nvm/versions/node/v20.20.2/bin:$PATH
npm i react@^19 react-dom@^19 react-router-dom@^7 @tanstack/react-query@^5
npm i -D vite @vitejs/plugin-react typescript @types/react @types/react-dom \
         tailwindcss@^4 @tailwindcss/vite@^4 \
         vitest jsdom \
         @testing-library/react @testing-library/dom @testing-library/jest-dom \
         @testing-library/user-event msw@^2 @playwright/test
```

Os majors pinados são os que têm API incompatível entre versões: Tailwind 4 é
CSS-first e não usa `tailwind.config.js`; MSW 2 trocou `rest` por `http`; React
Router 7 e Query 5 mudaram assinaturas. Os demais seguem o latest.

- [ ] **Passo 3: criar os arquivos de configuração**

`tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noEmit": true,
    "skipLibCheck": true,
    "isolatedModules": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src", "e2e"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

`tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "noEmit": true,
    "skipLibCheck": true,
    "composite": true
  },
  "include": ["vite.config.ts", "playwright.config.ts"]
}
```

`vite.config.ts` — o proxy espelha os caminhos da API, sem prefixo:

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const API = 'http://localhost:8080'
const CAMINHOS_DA_API = ['/auth', '/pacientes', '/consultas', '/anotacoes', '/lembretes', '/auditoria']

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: Object.fromEntries(
      CAMINHOS_DA_API.map((caminho) => [caminho, { target: API, changeOrigin: true }]),
    ),
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/teste/setup.ts'],
    css: true,
  },
})
```

`index.html`:

```html
<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>psychology</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Passo 4: criar os tokens**

`src/estilo/tokens.css` — Tailwind 4 declara tokens em CSS, não em arquivo de
config. Os valores vêm da direção visual fixada na spec:

```css
@import "tailwindcss";

@theme {
  --color-fundo: #f4f6f8;
  --color-superficie: #ffffff;
  --color-tinta: #232a33;
  --color-tinta-2: #59636f;
  --color-tinta-3: #8a939e;
  --color-linha: #e0e5ea;
  --color-acento: #3d6b9e;
  --color-sucesso: #3f7f6d;
  --color-atencao: #9a6b3d;
  --color-perigo: #9e4a4a;

  --font-serif: "Newsreader", Georgia, serif;
  --font-sans: "IBM Plex Sans", ui-sans-serif, system-ui, sans-serif;
  --font-mono: "IBM Plex Mono", ui-monospace, monospace;
}

body {
  margin: 0;
  background: var(--color-fundo);
  color: var(--color-tinta);
  font-family: var(--font-sans);
  -webkit-font-smoothing: antialiased;
}
```

- [ ] **Passo 5: criar o setup de teste e a raiz da aplicação**

`src/teste/setup.ts`:

```ts
import '@testing-library/jest-dom/vitest'
```

`src/App.tsx`:

```tsx
export function App() {
  return <div>psychology</div>
}
```

`src/main.tsx`:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { App } from './App'
import './estilo/tokens.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

- [ ] **Passo 6: escrever o teste que prova o harness**

`src/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { App } from './App'

describe('harness de teste', () => {
  it('renderiza a aplicacao', () => {
    render(<App />)
    expect(screen.getByText('psychology')).toBeInTheDocument()
  })
})
```

- [ ] **Passo 7: rodar o teste e o build**

```bash
export PATH=/home/syonet/.nvm/versions/node/v20.20.2/bin:$PATH
npm test
npm run build
```

Esperado: 1 teste passando e build sem erro. Se o `npm test` acusar ambiente
`jsdom` indisponível, falta o `jsdom` do passo 2.

- [ ] **Passo 8: commit**

```bash
git add package.json package-lock.json tsconfig.json tsconfig.node.json \
        vite.config.ts index.html src/
git commit -m "feat(web): scaffold do projeto com tokens e harness de teste"
```

---

### Task 2: cliente HTTP e envelope de erro

**Arquivos:**
- Criar: `src/api/tipos.ts`, `src/api/erro.ts`, `src/api/client.ts`,
  `src/api/client.test.ts`, `src/teste/servidor.ts`
- Modificar: `src/teste/setup.ts`

**Interfaces:**
- Consome: nada das tarefas anteriores.
- Produz: `class ErroApi { status: number; mensagem: string; erros: ErroCampo[]; mensagemDoCampo(campo): string | undefined }`;
  `pedir<T>(caminho: string, opcoes?: RequestInit): Promise<T>`;
  `servidorDeTeste` (MSW) exportado de `src/teste/servidor.ts`.

- [ ] **Passo 1: escrever os tipos do contrato**

`src/api/tipos.ts` — espelho dos DTOs da API:

```ts
export type ErroCampo = { campo: string; mensagem: string }

export type EnvelopeDeErro = {
  status: number
  mensagem: string
  erros: ErroCampo[]
}

export type Pagina<T> = {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

export type TokenResposta = { token: string }

export type Paciente = {
  idPaciente: string
  idUsuario: string
  nome: string
  email: string | null
  telefone: string | null
  dataNascimento: string
  createdAt: string
}

export type NovoPaciente = {
  nome: string
  telefone?: string
  email?: string
  dataNascimento: string
}

export type Anotacao = {
  id: string
  conteudo: string
  createdAt: string
  pacienteId: string
}
```

- [ ] **Passo 2: escrever o servidor MSW e ligá-lo ao setup**

`src/teste/servidor.ts`:

```ts
import { setupServer } from 'msw/node'

export const servidorDeTeste = setupServer()
```

`src/teste/setup.ts` passa a ser:

```ts
import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'

import { servidorDeTeste } from './servidor'

beforeAll(() => servidorDeTeste.listen({ onUnhandledRequest: 'error' }))
afterEach(() => servidorDeTeste.resetHandlers())
afterAll(() => servidorDeTeste.close())
```

`onUnhandledRequest: 'error'` é deliberado: chamada que nenhum handler cobre
falha o teste em vez de escapar para a rede.

- [ ] **Passo 3: escrever o teste que falha**

`src/api/client.test.ts`:

```ts
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'

import { servidorDeTeste } from '../teste/servidor'
import { ErroApi } from './erro'
import { pedir } from './client'

describe('cliente HTTP', () => {
  it('devolve o corpo json em caso de sucesso', async () => {
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.json([{ nome: 'Ana' }])))

    await expect(pedir<{ nome: string }[]>('/pacientes')).resolves.toEqual([{ nome: 'Ana' }])
  })

  it('transforma o envelope de 400 em ErroApi com os campos', async () => {
    servidorDeTeste.use(
      http.post('/pacientes', () =>
        HttpResponse.json(
          {
            status: 400,
            mensagem: 'Erro de validação',
            erros: [{ campo: 'nome', mensagem: 'campo obrigatorio' }],
          },
          { status: 400 },
        ),
      ),
    )

    const erro = await pedir('/pacientes', { method: 'POST' }).catch((e) => e)

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(400)
    expect(erro.erros).toEqual([{ campo: 'nome', mensagem: 'campo obrigatorio' }])
    expect(erro.mensagemDoCampo('nome')).toBe('campo obrigatorio')
  })

  it('transforma o 404 em ErroApi com lista de erros vazia', async () => {
    servidorDeTeste.use(
      http.get('/pacientes/xyz', () =>
        HttpResponse.json(
          { status: 404, mensagem: 'Paciente nao encontrado', erros: [] },
          { status: 404 },
        ),
      ),
    )

    const erro = await pedir('/pacientes/xyz').catch((e) => e)

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(404)
    expect(erro.erros).toEqual([])
  })

  it('nao estoura quando o corpo de erro nao e json', async () => {
    servidorDeTeste.use(
      http.get('/pacientes', () => new HttpResponse('falha no gateway', { status: 502 })),
    )

    const erro = await pedir('/pacientes').catch((e) => e)

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(502)
    expect(erro.mensagem).not.toBe('')
  })

  it('devolve undefined em resposta 204 sem corpo', async () => {
    servidorDeTeste.use(http.post('/auth/logout', () => new HttpResponse(null, { status: 204 })))

    await expect(pedir<void>('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })
})
```

- [ ] **Passo 4: rodar e ver falhar**

```bash
export PATH=/home/syonet/.nvm/versions/node/v20.20.2/bin:$PATH
npm test
```

Esperado: FALHA — `./erro` e `./client` não existem.

- [ ] **Passo 5: escrever o `ErroApi`**

`src/api/erro.ts`:

```ts
import type { EnvelopeDeErro, ErroCampo } from './tipos'

/**
 * Toda falha da API chega aqui. O envelope e unico desde a padronizacao do
 * backend, entao existe um caminho de parsing so — e a lista de erros e sempre
 * um array, mesmo quando vazia.
 */
export class ErroApi extends Error {
  readonly status: number
  readonly erros: ErroCampo[]

  constructor(envelope: EnvelopeDeErro) {
    super(envelope.mensagem)
    this.name = 'ErroApi'
    this.status = envelope.status
    this.erros = envelope.erros ?? []
  }

  /** Mensagem do campo, para o formulario marcar o input certo. */
  mensagemDoCampo(campo: string): string | undefined {
    return this.erros.find((erro) => erro.campo === campo)?.mensagem
  }
}
```

- [ ] **Passo 6: escrever o cliente**

`src/api/client.ts`:

```ts
import { ErroApi } from './erro'
import type { EnvelopeDeErro } from './tipos'

/**
 * Em desenvolvimento a base e vazia: o proxy do Vite espelha os caminhos da API,
 * o que torna tudo same-origin e faz o cookie Path=/auth continuar valendo.
 * Em producao a variavel VITE_API_URL aponta para a origem da API.
 */
const BASE = import.meta.env.VITE_API_URL ?? ''

async function envelopeDe(resposta: Response): Promise<EnvelopeDeErro> {
  try {
    const corpo = await resposta.json()
    if (corpo && typeof corpo.status === 'number' && typeof corpo.mensagem === 'string') {
      return corpo as EnvelopeDeErro
    }
  } catch {
    // corpo vazio ou nao-json: cai no envelope sintetico abaixo
  }
  return { status: resposta.status, mensagem: `Falha na requisição (${resposta.status})`, erros: [] }
}

export async function pedir<T>(caminho: string, opcoes: RequestInit = {}): Promise<T> {
  const resposta = await fetch(`${BASE}${caminho}`, {
    ...opcoes,
    credentials: 'include',
    headers: {
      ...(opcoes.body ? { 'Content-Type': 'application/json' } : {}),
      ...opcoes.headers,
    },
  })

  if (!resposta.ok) {
    throw new ErroApi(await envelopeDe(resposta))
  }

  if (resposta.status === 204) {
    return undefined as T
  }

  return (await resposta.json()) as T
}
```

`credentials: 'include'` é o que faz o cookie de refresh viajar — sem ele a
renovação nunca funciona, e o sintoma é um 401 no boot que parece bug de token.

- [ ] **Passo 7: rodar e ver passar**

Run: `npm test`
Esperado: 6 testes passando (1 do harness + 5 do cliente).

- [ ] **Passo 8: commit**

```bash
git add src/api/ src/teste/
git commit -m "feat(web): cliente http com o envelope de erro unico da API"
```

---

### Task 3: renovação de sessão no cliente

**Arquivos:**
- Criar: `src/api/sessaoEmMemoria.ts`, `src/api/sessaoEmMemoria.test.ts`
- Modificar: `src/api/client.ts`, `src/api/client.test.ts`

**Interfaces:**
- Consome: `pedir` e `ErroApi` (Task 2).
- Produz: `lerAcesso(): string | null`, `gravarAcesso(token: string | null): void`,
  `registrarPerdaDeSessao(cb: () => void): void`, `reiniciarSessaoEmMemoria(): void`;
  `pedir` passa a mandar `Authorization` e a renovar em 401;
  `renovarNoBoot(): Promise<boolean>`.

- [ ] **Passo 1: escrever o teste da sessão em memória**

`src/api/sessaoEmMemoria.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  gravarAcesso,
  lerAcesso,
  registrarPerdaDeSessao,
  reiniciarSessaoEmMemoria,
} from './sessaoEmMemoria'

describe('sessao em memoria', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())

  it('comeca sem token', () => {
    expect(lerAcesso()).toBeNull()
  })

  it('guarda e devolve o token', () => {
    gravarAcesso('abc')
    expect(lerAcesso()).toBe('abc')
  })

  it('avisa quem registrou interesse quando a sessao e perdida', () => {
    const aviso = vi.fn()
    registrarPerdaDeSessao(aviso)

    gravarAcesso('abc')
    gravarAcesso(null)

    expect(aviso).toHaveBeenCalledTimes(1)
  })

  it('nao avisa ao gravar null sobre uma sessao que ja estava vazia', () => {
    const aviso = vi.fn()
    registrarPerdaDeSessao(aviso)

    gravarAcesso(null)

    expect(aviso).not.toHaveBeenCalled()
  })
})
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `npm test`
Esperado: FALHA — `./sessaoEmMemoria` não existe.

- [ ] **Passo 3: implementar a sessão em memória**

`src/api/sessaoEmMemoria.ts`:

```ts
/**
 * O access token vive so aqui. Quem aguenta o recarregamento e o cookie HttpOnly
 * de refresh, que o JavaScript nao alcanca.
 *
 * Modulo, e nao contexto React, porque o client.ts precisa do token e nao pode
 * usar hook.
 */
let acesso: string | null = null
let aoPerder: (() => void) | null = null

export function lerAcesso(): string | null {
  return acesso
}

export function gravarAcesso(token: string | null): void {
  const perdeu = acesso !== null && token === null
  acesso = token
  if (perdeu) {
    aoPerder?.()
  }
}

export function registrarPerdaDeSessao(callback: () => void): void {
  aoPerder = callback
}

/** Só para teste: zera o estado do módulo entre casos. */
export function reiniciarSessaoEmMemoria(): void {
  acesso = null
  aoPerder = null
}
```

- [ ] **Passo 4: escrever o teste da renovação**

Trocar o cabeçalho de imports de `src/api/client.test.ts` por:

```ts
import { HttpResponse, http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { servidorDeTeste } from '../teste/servidor'
import { ErroApi } from './erro'
import { pedir } from './client'
import { gravarAcesso, lerAcesso, reiniciarSessaoEmMemoria } from './sessaoEmMemoria'
```

e acrescentar ao fim do arquivo um segundo bloco:

```ts
describe('renovacao de sessao', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())
  afterEach(() => vi.restoreAllMocks())

  it('envia o access token quando existe', async () => {
    gravarAcesso('token-abc')
    const cabecalhos: string[] = []
    servidorDeTeste.use(
      http.get('/pacientes', ({ request }) => {
        cabecalhos.push(request.headers.get('Authorization') ?? '')
        return HttpResponse.json([])
      }),
    )

    await pedir('/pacientes')

    expect(cabecalhos).toEqual(['Bearer token-abc'])
  })

  it('renova em 401 e repete a chamada original', async () => {
    gravarAcesso('token-velho')
    let tentativas = 0

    servidorDeTeste.use(
      http.get('/pacientes', ({ request }) => {
        tentativas += 1
        if (request.headers.get('Authorization') === 'Bearer token-velho') {
          return HttpResponse.json({ status: 401, mensagem: 'expirado', erros: [] }, { status: 401 })
        }
        return HttpResponse.json([{ nome: 'Ana' }])
      }),
      http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-novo' })),
    )

    await expect(pedir('/pacientes')).resolves.toEqual([{ nome: 'Ana' }])
    expect(tentativas).toBe(2)
    expect(lerAcesso()).toBe('token-novo')
  })

  it('seis chamadas simultaneas em 401 produzem UMA renovacao', async () => {
    gravarAcesso('token-velho')
    let renovacoes = 0

    servidorDeTeste.use(
      http.get('/pacientes', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer token-velho'
          ? HttpResponse.json({ status: 401, mensagem: 'expirado', erros: [] }, { status: 401 })
          : HttpResponse.json([]),
      ),
      http.post('/auth/refresh', () => {
        renovacoes += 1
        return HttpResponse.json({ token: 'token-novo' })
      }),
    )

    await Promise.all(Array.from({ length: 6 }, () => pedir('/pacientes')))

    expect(renovacoes).toBe(1)
  })

  it('401 na renovacao encerra a sessao e propaga o erro', async () => {
    gravarAcesso('token-velho')
    servidorDeTeste.use(
      http.get('/pacientes', () =>
        HttpResponse.json({ status: 401, mensagem: 'expirado', erros: [] }, { status: 401 }),
      ),
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sessao invalida', erros: [] }, { status: 401 }),
      ),
    )

    const erro = await pedir('/pacientes').catch((e) => e)

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro.status).toBe(401)
    expect(lerAcesso()).toBeNull()
  })

  it('401 vindo de /auth nao dispara renovacao', async () => {
    let renovacoes = 0
    servidorDeTeste.use(
      http.post('/auth/login', () =>
        HttpResponse.json({ status: 401, mensagem: 'credencial invalida', erros: [] }, { status: 401 }),
      ),
      http.post('/auth/refresh', () => {
        renovacoes += 1
        return HttpResponse.json({ token: 'nao-deveria' })
      }),
    )

    await pedir('/auth/login', { method: 'POST', body: '{}' }).catch(() => undefined)

    expect(renovacoes).toBe(0)
  })
})
```

- [ ] **Passo 5: rodar e ver falhar**

Run: `npm test`
Esperado: FALHA — o cliente ainda não manda `Authorization` nem renova.

- [ ] **Passo 6: implementar a renovação no cliente**

Substituir `src/api/client.ts` por:

```ts
import { ErroApi } from './erro'
import { gravarAcesso, lerAcesso } from './sessaoEmMemoria'
import type { EnvelopeDeErro, TokenResposta } from './tipos'

const BASE = import.meta.env.VITE_API_URL ?? ''

/**
 * UMA renovacao por vez, compartilhada por todas as chamadas que tomaram 401
 * juntas. A API rotaciona o refresh a cada uso e revoga a cadeia inteira quando
 * ve um refresh reapresentado — rotacoes concorrentes seriam lidas como token
 * roubado e derrubariam a sessao de um usuario legitimo.
 */
let renovacaoEmVoo: Promise<boolean> | null = null

async function envelopeDe(resposta: Response): Promise<EnvelopeDeErro> {
  try {
    const corpo = await resposta.json()
    if (corpo && typeof corpo.status === 'number' && typeof corpo.mensagem === 'string') {
      return corpo as EnvelopeDeErro
    }
  } catch {
    // corpo vazio ou nao-json: cai no envelope sintetico abaixo
  }
  return { status: resposta.status, mensagem: `Falha na requisição (${resposta.status})`, erros: [] }
}

function disparar(caminho: string, opcoes: RequestInit): Promise<Response> {
  const acesso = lerAcesso()
  return fetch(`${BASE}${caminho}`, {
    ...opcoes,
    credentials: 'include',
    headers: {
      ...(opcoes.body ? { 'Content-Type': 'application/json' } : {}),
      ...(acesso ? { Authorization: `Bearer ${acesso}` } : {}),
      ...opcoes.headers,
    },
  })
}

async function renovar(): Promise<boolean> {
  const resposta = await fetch(`${BASE}/auth/refresh`, { method: 'POST', credentials: 'include' })
  if (!resposta.ok) {
    gravarAcesso(null)
    return false
  }
  const { token } = (await resposta.json()) as TokenResposta
  gravarAcesso(token)
  return true
}

function renovarUmaVez(): Promise<boolean> {
  renovacaoEmVoo ??= renovar().finally(() => {
    renovacaoEmVoo = null
  })
  return renovacaoEmVoo
}

async function corpoDe<T>(resposta: Response): Promise<T> {
  if (resposta.status === 204) {
    return undefined as T
  }
  return (await resposta.json()) as T
}

export async function pedir<T>(caminho: string, opcoes: RequestInit = {}): Promise<T> {
  let resposta = await disparar(caminho, opcoes)

  const podeRenovar = resposta.status === 401 && !caminho.startsWith('/auth')
  if (podeRenovar && (await renovarUmaVez())) {
    resposta = await disparar(caminho, opcoes)
  }

  if (!resposta.ok) {
    throw new ErroApi(await envelopeDe(resposta))
  }

  return corpoDe<T>(resposta)
}

/** Renovação do boot da aplicação, antes de qualquer tela. */
export function renovarNoBoot(): Promise<boolean> {
  return renovarUmaVez()
}
```

- [ ] **Passo 7: rodar e ver passar**

Run: `npm test`
Esperado: 16 testes passando (1 harness + 5 cliente + 4 sessão + 6 renovação).

- [ ] **Passo 8: commit**

```bash
git add src/api/
git commit -m "feat(web): renova a sessao em 401 com uma promise compartilhada"
```

---

### Task 4: contexto de sessão e refresh no boot

**Arquivos:**
- Criar: `src/api/recursos/auth.ts`, `src/features/auth/SessaoProvider.tsx`,
  `src/features/auth/useSessao.ts`, `src/features/auth/SessaoProvider.test.tsx`,
  `src/teste/renderizar.tsx`

**Interfaces:**
- Consome: `pedir`, `renovarNoBoot`, `gravarAcesso`, `registrarPerdaDeSessao` (Tasks 2-3).
- Produz: `login(emailUsuario, senha): Promise<TokenResposta>`,
  `registrar(nome, email, senha): Promise<unknown>`, `logout(): Promise<void>`;
  `<SessaoProvider>`; `useSessao(): { autenticado: boolean; carregando: boolean; entrar(email, senha): Promise<void>; sair(): Promise<void> }`;
  helpers `renderizarComProvedores(elemento, rotaInicial?)` e
  `renderizarComRotas(rotas, rotaInicial?)`.

- [ ] **Passo 1: escrever os helpers de renderização**

`src/teste/renderizar.tsx` — dois helpers, porque há dois tipos de teste: um
componente isolado e uma árvore com rotas de dados (o `useBlocker` da Task 8 só
funciona em router de dados):

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import type { RouteObject } from 'react-router-dom'
import { MemoryRouter, RouterProvider, createMemoryRouter } from 'react-router-dom'

export function novoQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

export function renderizarComProvedores(elemento: ReactElement, rotaInicial = '/') {
  return render(
    <QueryClientProvider client={novoQueryClient()}>
      <MemoryRouter initialEntries={[rotaInicial]}>{elemento}</MemoryRouter>
    </QueryClientProvider>,
  )
}

export function renderizarComRotas(rotas: RouteObject[], rotaInicial = '/') {
  const router = createMemoryRouter(rotas, { initialEntries: [rotaInicial] })
  return render(
    <QueryClientProvider client={novoQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}
```

`retry: false` é obrigatório: com o retry padrão da Query, um teste de erro espera
três tentativas e estoura o timeout.

- [ ] **Passo 2: escrever o teste que falha**

`src/features/auth/SessaoProvider.test.tsx`:

```tsx
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { lerAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComProvedores } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { SessaoProvider } from './SessaoProvider'
import { useSessao } from './useSessao'

function Sonda() {
  const { autenticado, carregando, entrar, sair } = useSessao()

  if (carregando) return <p>carregando</p>

  return (
    <div>
      <p>{autenticado ? 'autenticado' : 'visitante'}</p>
      <button onClick={() => void entrar('ana@teste.com', 'senhaforte123')}>entrar</button>
      <button onClick={() => void sair()}>sair</button>
    </div>
  )
}

function renderizarSonda() {
  return renderizarComProvedores(
    <SessaoProvider>
      <Sonda />
    </SessaoProvider>,
  )
}

describe('SessaoProvider', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())

  it('restaura a sessao quando o refresh do boot responde 200', async () => {
    servidorDeTeste.use(http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-do-cookie' })))

    renderizarSonda()

    expect(await screen.findByText('autenticado')).toBeInTheDocument()
    expect(lerAcesso()).toBe('token-do-cookie')
  })

  it('trata como visitante quando o refresh do boot responde 401', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )

    renderizarSonda()

    expect(await screen.findByText('visitante')).toBeInTheDocument()
    expect(lerAcesso()).toBeNull()
  })

  it('mostra carregando antes do refresh do boot resolver', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )

    renderizarSonda()

    expect(screen.getByText('carregando')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('visitante')).toBeInTheDocument())
  })

  it('entrar guarda o access token e autentica', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
      http.post('/auth/login', () => HttpResponse.json({ token: 'token-do-login' })),
    )

    renderizarSonda()
    await screen.findByText('visitante')

    await userEvent.click(screen.getByRole('button', { name: 'entrar' }))

    expect(await screen.findByText('autenticado')).toBeInTheDocument()
    expect(lerAcesso()).toBe('token-do-login')
  })

  it('sair chama o logout e volta a visitante', async () => {
    let chamouLogout = false
    servidorDeTeste.use(
      http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-do-cookie' })),
      http.post('/auth/logout', () => {
        chamouLogout = true
        return new HttpResponse(null, { status: 204 })
      }),
    )

    renderizarSonda()
    await screen.findByText('autenticado')

    await userEvent.click(screen.getByRole('button', { name: 'sair' }))

    expect(await screen.findByText('visitante')).toBeInTheDocument()
    expect(chamouLogout).toBe(true)
    expect(lerAcesso()).toBeNull()
  })

  it('perder a sessao no meio do uso volta a visitante', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-do-cookie' })),
    )

    renderizarSonda()
    await screen.findByText('autenticado')

    const { gravarAcesso } = await import('../../api/sessaoEmMemoria')
    await act(async () => {
      gravarAcesso(null)
    })

    expect(await screen.findByText('visitante')).toBeInTheDocument()
  })
})
```

- [ ] **Passo 3: rodar e ver falhar**

Run: `npm test`
Esperado: FALHA — `./SessaoProvider` e `./useSessao` não existem.

- [ ] **Passo 4: escrever o recurso de auth**

`src/api/recursos/auth.ts`:

```ts
import { pedir } from '../client'
import type { TokenResposta } from '../tipos'

export function login(emailUsuario: string, senha: string): Promise<TokenResposta> {
  return pedir<TokenResposta>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ emailUsuario, senha }),
  })
}

export function registrar(nome: string, email: string, senha: string): Promise<unknown> {
  return pedir('/auth/registrar', {
    method: 'POST',
    body: JSON.stringify({ nome, email, senha }),
  })
}

export function logout(): Promise<void> {
  return pedir<void>('/auth/logout', { method: 'POST' })
}
```

- [ ] **Passo 5: escrever o contexto**

`src/features/auth/SessaoProvider.tsx`:

```tsx
import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'

import { renovarNoBoot } from '../../api/client'
import * as auth from '../../api/recursos/auth'
import { gravarAcesso, registrarPerdaDeSessao } from '../../api/sessaoEmMemoria'

export type Sessao = {
  autenticado: boolean
  carregando: boolean
  entrar: (emailUsuario: string, senha: string) => Promise<void>
  sair: () => Promise<void>
}

export const ContextoDeSessao = createContext<Sessao | null>(null)

export function SessaoProvider({ children }: { children: ReactNode }) {
  const [autenticado, setAutenticado] = useState(false)
  const [carregando, setCarregando] = useState(true)
  const queryClient = useQueryClient()

  /**
   * Um refresh antes de renderizar rota nenhuma: o cookie HttpOnly viaja sozinho,
   * e e assim que um F5 nao desloga sem token persistido em lugar nenhum.
   */
  useEffect(() => {
    let vivo = true
    renovarNoBoot()
      .then((renovou) => {
        if (vivo) setAutenticado(renovou)
      })
      .finally(() => {
        if (vivo) setCarregando(false)
      })
    return () => {
      vivo = false
    }
  }, [])

  useEffect(() => {
    registrarPerdaDeSessao(() => setAutenticado(false))
  }, [])

  const entrar = useCallback(async (emailUsuario: string, senha: string) => {
    const { token } = await auth.login(emailUsuario, senha)
    gravarAcesso(token)
    setAutenticado(true)
  }, [])

  /**
   * Limpar o cache faz parte de sair: sem isso, dado de paciente fica em memoria
   * para o proximo login na mesma aba.
   */
  const sair = useCallback(async () => {
    try {
      await auth.logout()
    } finally {
      gravarAcesso(null)
      setAutenticado(false)
      queryClient.clear()
    }
  }, [queryClient])

  return (
    <ContextoDeSessao.Provider value={{ autenticado, carregando, entrar, sair }}>
      {children}
    </ContextoDeSessao.Provider>
  )
}
```

`src/features/auth/useSessao.ts`:

```ts
import { useContext } from 'react'

import { ContextoDeSessao } from './SessaoProvider'
import type { Sessao } from './SessaoProvider'

export function useSessao(): Sessao {
  const sessao = useContext(ContextoDeSessao)
  if (!sessao) {
    throw new Error('useSessao exige um SessaoProvider acima na arvore')
  }
  return sessao
}
```

- [ ] **Passo 6: rodar e ver passar**

Run: `npm test`
Esperado: 22 testes passando (16 anteriores + 6 do provider).

- [ ] **Passo 7: commit**

```bash
git add src/api/recursos/ src/features/auth/ src/teste/renderizar.tsx
git commit -m "feat(web): contexto de sessao com refresh no boot"
```

---

### Task 5: tela de login

**Arquivos:**
- Criar: `src/features/auth/LoginPage.tsx`, `src/features/auth/LoginPage.test.tsx`,
  `src/ui/Campo.tsx`, `src/ui/Botao.tsx`

**Interfaces:**
- Consome: `useSessao` (Task 4), `ErroApi` (Task 2), `registrar` (Task 4).
- Produz: `<LoginPage />`; `<Campo label rotulo valor aoMudar erro? tipo? />`;
  `<Botao>`.

- [ ] **Passo 1: escrever o teste que falha**

`src/features/auth/LoginPage.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComProvedores } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { LoginPage } from './LoginPage'
import { SessaoProvider } from './SessaoProvider'

function renderizarLogin() {
  return renderizarComProvedores(
    <SessaoProvider>
      <LoginPage />
    </SessaoProvider>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )
  })

  it('exige e-mail e senha antes de chamar a API', async () => {
    let chamou = false
    servidorDeTeste.use(
      http.post('/auth/login', () => {
        chamou = true
        return HttpResponse.json({ token: 'x' })
      }),
    )

    renderizarLogin()
    await userEvent.click(await screen.findByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('campo obrigatorio')).toBeInTheDocument()
    expect(chamou).toBe(false)
  })

  it('mostra a mensagem do envelope quando a credencial e invalida', async () => {
    servidorDeTeste.use(
      http.post('/auth/login', () =>
        HttpResponse.json(
          { status: 401, mensagem: 'Usuario e/ou senha incorretos!', erros: [] },
          { status: 401 },
        ),
      ),
    )

    renderizarLogin()
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'errada')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Usuario e/ou senha incorretos!')).toBeInTheDocument()
  })

  it('marca o campo culpado quando o registro devolve 400 com erros', async () => {
    servidorDeTeste.use(
      http.post('/auth/registrar', () =>
        HttpResponse.json(
          {
            status: 400,
            mensagem: 'Erro de validação',
            erros: [{ campo: 'email', mensagem: 'formato de email invalido' }],
          },
          { status: 400 },
        ),
      ),
    )

    renderizarLogin()
    await userEvent.click(await screen.findByRole('button', { name: 'Criar conta' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Ana Moreira')
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana-arroba-teste')
    await userEvent.type(screen.getByLabelText('Senha'), 'senhaforte123')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))

    expect(await screen.findByText('formato de email invalido')).toBeInTheDocument()
  })

  it('entra com credencial valida', async () => {
    servidorDeTeste.use(http.post('/auth/login', () => HttpResponse.json({ token: 'token-bom' })))

    renderizarLogin()
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'senhaforte123')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    const { lerAcesso } = await import('../../api/sessaoEmMemoria')
    expect(await screen.findByText('psychology')).toBeInTheDocument()
    expect(lerAcesso()).toBe('token-bom')
  })
})
```

O último caso espera `psychology` porque, autenticado, a `LoginPage` deixa de
mostrar o formulário e passa a exibir a marca enquanto o roteador redireciona —
o redirecionamento em si é da Task 6.

- [ ] **Passo 2: rodar e ver falhar**

Run: `npm test`
Esperado: FALHA — `./LoginPage` não existe.

- [ ] **Passo 3: escrever os componentes de UI**

`src/ui/Campo.tsx`:

```tsx
type Props = {
  rotulo: string
  valor: string
  aoMudar: (valor: string) => void
  tipo?: 'text' | 'password'
  erro?: string
  exemplo?: string
}

export function Campo({ rotulo, valor, aoMudar, tipo = 'text', erro, exemplo }: Props) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm text-tinta-2">{rotulo}</span>
      <input
        type={tipo}
        value={valor}
        placeholder={exemplo}
        onChange={(evento) => aoMudar(evento.target.value)}
        className="border-b border-linha bg-transparent py-2 text-base outline-none focus:border-acento"
      />
      {erro ? <span className="text-sm text-perigo">{erro}</span> : null}
    </label>
  )
}
```

`src/ui/Botao.tsx`:

```tsx
import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  variante?: 'primario' | 'texto'
}

export function Botao({ children, variante = 'primario', ...resto }: Props) {
  const estilo =
    variante === 'primario'
      ? 'h-11 px-5 bg-tinta text-superficie text-sm'
      : 'text-sm text-acento py-1'

  return (
    <button type="button" className={estilo} {...resto}>
      {children}
    </button>
  )
}
```

- [ ] **Passo 4: escrever a LoginPage**

`src/features/auth/LoginPage.tsx`:

```tsx
import { useState } from 'react'

import { ErroApi } from '../../api/erro'
import { registrar } from '../../api/recursos/auth'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { useSessao } from './useSessao'

type Modo = 'login' | 'registro'

export function LoginPage() {
  const { autenticado, entrar } = useSessao()
  const [modo, setModo] = useState<Modo>('login')
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<ErroApi | null>(null)
  const [faltando, setFaltando] = useState<string[]>([])

  if (autenticado) {
    return <p className="p-14 font-serif text-2xl">psychology</p>
  }

  const ehLogin = modo === 'login'

  /**
   * O cliente so checa campo vazio — poupa uma ida ao servidor. O resto da
   * validacao e do envelope da API, que nomeia o campo culpado.
   */
  function camposVazios(): string[] {
    const vazios: string[] = []
    if (!ehLogin && !nome.trim()) vazios.push('nome')
    if (!email.trim()) vazios.push('email')
    if (!senha.trim()) vazios.push('senha')
    return vazios
  }

  async function enviar() {
    const vazios = camposVazios()
    setFaltando(vazios)
    setErro(null)
    if (vazios.length > 0) return

    try {
      if (ehLogin) {
        await entrar(email, senha)
      } else {
        await registrar(nome, email, senha)
        await entrar(email, senha)
      }
    } catch (falha) {
      if (falha instanceof ErroApi) {
        setErro(falha)
        return
      }
      throw falha
    }
  }

  function erroDoCampo(campo: string): string | undefined {
    if (faltando.includes(campo)) return 'campo obrigatorio'
    if (campo === 'email') {
      return erro?.mensagemDoCampo('email') ?? erro?.mensagemDoCampo('emailUsuario')
    }
    return erro?.mensagemDoCampo(campo)
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md flex-col justify-center gap-8 p-14">
      <div className="flex flex-col gap-3">
        <p className="font-mono text-xs tracking-widest text-tinta-3">ACESSO DO PROFISSIONAL</p>
        <h1 className="font-serif text-4xl font-light">{ehLogin ? 'Entrar' : 'Criar conta'}</h1>
      </div>

      <div className="flex flex-col gap-6">
        {ehLogin ? null : (
          <Campo rotulo="Nome" valor={nome} aoMudar={setNome} erro={erroDoCampo('nome')} />
        )}
        <Campo rotulo="E-mail" valor={email} aoMudar={setEmail} erro={erroDoCampo('email')} />
        <Campo
          rotulo="Senha"
          tipo="password"
          valor={senha}
          aoMudar={setSenha}
          erro={erroDoCampo('senha')}
        />
      </div>

      {erro && erro.erros.length === 0 ? (
        <p className="border-l-2 border-perigo bg-superficie p-4 text-sm">{erro.message}</p>
      ) : null}

      <div className="flex flex-col items-start gap-4">
        <Botao onClick={() => void enviar()}>{ehLogin ? 'Entrar' : 'Cadastrar'}</Botao>
        <Botao
          variante="texto"
          onClick={() => {
            setModo(ehLogin ? 'registro' : 'login')
            setErro(null)
            setFaltando([])
          }}
        >
          {ehLogin ? 'Criar conta' : 'Já tenho conta'}
        </Botao>
      </div>
    </main>
  )
}
```

- [ ] **Passo 5: rodar e ver passar**

Run: `npm test`
Esperado: 26 testes passando (22 anteriores + 4 do login).

- [ ] **Passo 6: commit**

```bash
git add src/features/auth/ src/ui/
git commit -m "feat(web): tela de entrar e criar conta"
```

---

### Task 6: rotas, guarda e layout

**Arquivos:**
- Criar: `src/ui/Layout.tsx`, `src/ui/RotaProtegida.tsx`, `src/rotas.tsx`,
  `src/rotas.test.tsx`
- Modificar: `src/main.tsx`, `src/teste/renderizar.tsx`
- Remover: `src/App.tsx`, `src/App.test.tsx`

**Interfaces:**
- Consome: `useSessao` (Task 4), `<LoginPage />` (Task 5).
- Produz: `definicaoDeRotas: RouteObject[]`; `<RotaProtegida />` (redireciona para
  `/login` sem sessão); `<Layout />` (nav mais `<Outlet />`).

- [ ] **Passo 1: escrever o teste que falha**

`src/rotas.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { reiniciarSessaoEmMemoria } from './api/sessaoEmMemoria'
import { definicaoDeRotas } from './rotas'
import { renderizarComRotas } from './teste/renderizar'
import { servidorDeTeste } from './teste/servidor'

describe('rotas', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())

  it('sem sessao, a rota protegida manda para o login', async () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )

    renderizarComRotas(definicaoDeRotas, '/')

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('com sessao, a rota protegida mostra o layout', async () => {
    servidorDeTeste.use(http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-bom' })))

    renderizarComRotas(definicaoDeRotas, '/')

    expect(await screen.findByRole('navigation')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
  })

  it('mostra carregando enquanto o refresh do boot nao resolve', () => {
    servidorDeTeste.use(
      http.post('/auth/refresh', () =>
        HttpResponse.json({ status: 401, mensagem: 'sem sessao', erros: [] }, { status: 401 }),
      ),
    )

    renderizarComRotas(definicaoDeRotas, '/')

    expect(screen.getByText('Carregando…')).toBeInTheDocument()
  })

  it('caminho desconhecido mostra a mensagem de rota inexistente', async () => {
    servidorDeTeste.use(http.post('/auth/refresh', () => HttpResponse.json({ token: 'token-bom' })))

    renderizarComRotas(definicaoDeRotas, '/inexistente')

    expect(await screen.findByText('Página não encontrada.')).toBeInTheDocument()
  })
})
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `npm test`
Esperado: FALHA — `./rotas` não existe.

- [ ] **Passo 3: escrever a guarda e o layout**

`src/ui/RotaProtegida.tsx`:

```tsx
import { Navigate, Outlet } from 'react-router-dom'

import { useSessao } from '../features/auth/useSessao'

export function RotaProtegida() {
  const { autenticado, carregando } = useSessao()

  if (carregando) {
    return <p className="p-14 text-sm text-tinta-3">Carregando…</p>
  }

  return autenticado ? <Outlet /> : <Navigate to="/login" replace />
}
```

`src/ui/Layout.tsx`:

```tsx
import { NavLink, Outlet } from 'react-router-dom'

import { useSessao } from '../features/auth/useSessao'
import { Botao } from './Botao'

const ITENS = [{ para: '/pacientes', rotulo: 'Pacientes' }]

export function Layout() {
  const { sair } = useSessao()

  return (
    <div className="flex min-h-screen">
      <nav
        aria-label="Navegação principal"
        className="flex w-60 shrink-0 flex-col gap-8 border-r border-linha bg-superficie p-6"
      >
        <p className="font-serif text-lg">
          psychology<span className="font-mono text-xs text-tinta-3">/api</span>
        </p>

        <ul className="flex flex-col gap-1">
          {ITENS.map((item) => (
            <li key={item.para}>
              <NavLink
                to={item.para}
                className={({ isActive }) =>
                  `block rounded px-3 py-2 text-sm ${isActive ? 'bg-fundo text-tinta' : 'text-tinta-2'}`
                }
              >
                {item.rotulo}
              </NavLink>
            </li>
          ))}
        </ul>

        <div className="mt-auto">
          <Botao variante="texto" onClick={() => void sair()}>
            Sair
          </Botao>
        </div>
      </nav>

      <main className="min-w-0 flex-1 p-10">
        <Outlet />
      </main>
    </div>
  )
}
```

- [ ] **Passo 4: escrever as rotas**

`src/rotas.tsx` — as telas de paciente e ficha entram nas Tasks 7 e 8, cada uma
acrescentando a própria rota:

```tsx
import type { RouteObject } from 'react-router-dom'

import { LoginPage } from './features/auth/LoginPage'
import { Layout } from './ui/Layout'
import { RotaProtegida } from './ui/RotaProtegida'

export const definicaoDeRotas: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <RotaProtegida />,
    children: [{ element: <Layout />, children: [] }],
  },
  { path: '*', element: <p className="p-14">Página não encontrada.</p> },
]
```

- [ ] **Passo 5: reescrever a raiz da aplicação**

`src/main.tsx`:

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createBrowserRouter } from 'react-router-dom'

import { SessaoProvider } from './features/auth/SessaoProvider'
import { definicaoDeRotas } from './rotas'
import './estilo/tokens.css'

const queryClient = new QueryClient()
const router = createBrowserRouter(definicaoDeRotas)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <SessaoProvider>
        <RouterProvider router={router} />
      </SessaoProvider>
    </QueryClientProvider>
  </StrictMode>,
)
```

O `SessaoProvider` fica **acima** do `RouterProvider`: a guarda de rota consulta a
sessão, então a sessão precisa existir antes do roteador montar.

Isso obriga o helper `renderizarComRotas` a envolver as rotas no
`SessaoProvider` também. Ajustar `src/teste/renderizar.tsx`, trocando a função
`renderizarComRotas` por:

```tsx
export function renderizarComRotas(rotas: RouteObject[], rotaInicial = '/') {
  const router = createMemoryRouter(rotas, { initialEntries: [rotaInicial] })
  return render(
    <QueryClientProvider client={novoQueryClient()}>
      <SessaoProvider>
        <RouterProvider router={router} />
      </SessaoProvider>
    </QueryClientProvider>,
  )
}
```

com o import `import { SessaoProvider } from '../features/auth/SessaoProvider'`.

- [ ] **Passo 6: remover o App provisório**

```bash
rm src/App.tsx src/App.test.tsx
```

O teste de harness da Task 1 cumpriu seu papel — provar que o Vitest roda — e
agora seria um teste de um componente que não existe mais.

- [ ] **Passo 7: rodar e ver passar**

Run: `npm test`
Esperado: 29 testes passando (26 anteriores, menos 1 do App removido, mais 4 de rotas).

- [ ] **Passo 8: commit**

```bash
git add -A src/
git commit -m "feat(web): rotas com guarda de sessao e layout"
```

O `-A` é o que registra a remoção do `App.tsx` junto das criações.

---

### Task 7: pacientes — listar e cadastrar

**Arquivos:**
- Criar: `src/api/recursos/paciente.ts`, `src/features/pacientes/queries.ts`,
  `src/features/pacientes/PacientesPage.tsx`, `src/features/pacientes/PacientesPage.test.tsx`
- Modificar: `src/rotas.tsx`

**Interfaces:**
- Consome: `pedir` (Task 2), `Campo`/`Botao` (Task 5), `definicaoDeRotas` (Task 6).
- Produz: `listarPacientes(): Promise<Paciente[]>`,
  `buscarPaciente(pacienteId): Promise<Paciente>`,
  `criarPaciente(novo: NovoPaciente): Promise<Paciente>`;
  `chavesDePaciente.lista` e `chavesDePaciente.um(id)`;
  `usePacientes()`, `usePaciente(id)`, `useCriarPaciente()`;
  `<PacientesPage />` na rota `/pacientes`.

- [ ] **Passo 1: escrever o teste que falha**

`src/features/pacientes/PacientesPage.test.tsx`:

```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { gravarAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComProvedores } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { PacientesPage } from './PacientesPage'

const ANA = {
  idPaciente: '3f9a1c04-0000-0000-0000-000000000001',
  idUsuario: 'u-1',
  nome: 'Ana Moreira',
  email: 'ana@exemplo.br',
  telefone: '(51) 99612-0184',
  dataNascimento: '1991-04-12',
  createdAt: '2026-03-11T10:00:00',
}

describe('PacientesPage', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    gravarAcesso('token-de-teste')
  })

  it('lista os pacientes com contagem', async () => {
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.json([ANA])))

    renderizarComProvedores(<PacientesPage />)

    expect(await screen.findByText('Ana Moreira')).toBeInTheDocument()
    expect(screen.getByText('1 paciente')).toBeInTheDocument()
  })

  it('mostra estado vazio quando nao ha paciente', async () => {
    servidorDeTeste.use(http.get('/pacientes', () => HttpResponse.json([])))

    renderizarComProvedores(<PacientesPage />)

    expect(await screen.findByText('Nenhum paciente cadastrado ainda.')).toBeInTheDocument()
  })

  it('cadastra e a lista passa a mostrar o novo paciente', async () => {
    let cadastrados = 0
    servidorDeTeste.use(
      http.get('/pacientes', () => HttpResponse.json(cadastrados === 0 ? [] : [ANA])),
      http.post('/pacientes', async ({ request }) => {
        const corpo = (await request.json()) as { nome: string; dataNascimento: string }
        expect(corpo.nome).toBe('Ana Moreira')
        expect(corpo.dataNascimento).toBe('1991-04-12')
        cadastrados += 1
        return HttpResponse.json(ANA, { status: 201 })
      }),
    )

    renderizarComProvedores(<PacientesPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo paciente' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Ana Moreira')
    await userEvent.type(screen.getByLabelText('Data de nascimento'), '1991-04-12')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))

    expect(await screen.findByText('Ana Moreira')).toBeInTheDocument()
  })

  it('marca o campo culpado quando a API devolve 400', async () => {
    servidorDeTeste.use(
      http.get('/pacientes', () => HttpResponse.json([])),
      http.post('/pacientes', () =>
        HttpResponse.json(
          {
            status: 400,
            mensagem: 'Erro de validação',
            erros: [{ campo: 'dataNascimento', mensagem: 'A data de nascimento é obrigatória.' }],
          },
          { status: 400 },
        ),
      ),
    )

    renderizarComProvedores(<PacientesPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo paciente' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Ana Moreira')
    await userEvent.type(screen.getByLabelText('Data de nascimento'), '12/04/1991')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))

    await waitFor(() =>
      expect(screen.getByText('A data de nascimento é obrigatória.')).toBeInTheDocument(),
    )
  })
})
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `npm test`
Esperado: FALHA — `./PacientesPage` não existe.

- [ ] **Passo 3: escrever o recurso**

`src/api/recursos/paciente.ts`:

```ts
import { pedir } from '../client'
import type { NovoPaciente, Paciente } from '../tipos'

export function listarPacientes(nome?: string): Promise<Paciente[]> {
  const filtro = nome ? `?nome=${encodeURIComponent(nome)}` : ''
  return pedir<Paciente[]>(`/pacientes${filtro}`)
}

export function buscarPaciente(pacienteId: string): Promise<Paciente> {
  return pedir<Paciente>(`/pacientes/${pacienteId}`)
}

export function criarPaciente(novo: NovoPaciente): Promise<Paciente> {
  return pedir<Paciente>('/pacientes', { method: 'POST', body: JSON.stringify(novo) })
}
```

- [ ] **Passo 4: escrever as queries**

`src/features/pacientes/queries.ts`:

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import * as recurso from '../../api/recursos/paciente'
import type { NovoPaciente } from '../../api/tipos'

export const chavesDePaciente = {
  lista: ['pacientes'] as const,
  um: (pacienteId: string) => ['paciente', pacienteId] as const,
}

export function usePacientes() {
  return useQuery({ queryKey: chavesDePaciente.lista, queryFn: () => recurso.listarPacientes() })
}

export function usePaciente(pacienteId: string) {
  return useQuery({
    queryKey: chavesDePaciente.um(pacienteId),
    queryFn: () => recurso.buscarPaciente(pacienteId),
  })
}

/**
 * Invalida tambem a auditoria: toda mutacao grava um log no servidor, e a trilha
 * e justamente a tela em que estar desatualizada e pior.
 */
export function useCriarPaciente() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (novo: NovoPaciente) => recurso.criarPaciente(novo),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: chavesDePaciente.lista })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}
```

- [ ] **Passo 5: escrever a página**

`src/features/pacientes/PacientesPage.tsx`:

```tsx
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { ErroApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { useCriarPaciente, usePacientes } from './queries'

export function PacientesPage() {
  const { data: pacientes, isPending, error } = usePacientes()
  const criar = useCriarPaciente()
  const [criando, setCriando] = useState(false)
  const [nome, setNome] = useState('')
  const [nascimento, setNascimento] = useState('')
  const [vazios, setVazios] = useState<string[]>([])

  const falha = criar.error instanceof ErroApi ? criar.error : null

  function erroDoCampo(campo: string, valorDoCampo: string): string | undefined {
    if (vazios.includes(campo) && !valorDoCampo.trim()) return 'campo obrigatorio'
    return falha?.mensagemDoCampo(campo)
  }

  async function cadastrar() {
    const faltando = [
      ...(nome.trim() ? [] : ['nome']),
      ...(nascimento.trim() ? [] : ['dataNascimento']),
    ]
    setVazios(faltando)
    if (faltando.length > 0) return

    try {
      await criar.mutateAsync({ nome, dataNascimento: nascimento })
      setCriando(false)
      setNome('')
      setNascimento('')
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  const total = pacientes?.length ?? 0

  return (
    <div className="flex flex-col gap-8">
      <header className="flex items-end justify-between gap-6">
        <div className="flex flex-col gap-3">
          <p className="font-mono text-xs tracking-widest text-tinta-3">CADASTRO</p>
          <h1 className="font-serif text-4xl font-light">Pacientes</h1>
        </div>
        <Botao onClick={() => setCriando((antes) => !antes)}>Novo paciente</Botao>
      </header>

      {criando ? (
        <section className="flex max-w-md flex-col gap-5 border border-linha bg-superficie p-6">
          <p className="font-mono text-xs tracking-widest text-tinta-3">POST /pacientes</p>
          <Campo rotulo="Nome" valor={nome} aoMudar={setNome} erro={erroDoCampo('nome', nome)} />
          <Campo
            rotulo="Data de nascimento"
            valor={nascimento}
            aoMudar={setNascimento}
            exemplo="1991-04-12"
            erro={erroDoCampo('dataNascimento', nascimento)}
          />
          <Botao onClick={() => void cadastrar()}>Cadastrar</Botao>
        </section>
      ) : null}

      {isPending ? <p className="text-sm text-tinta-3">Carregando…</p> : null}
      {error ? <p className="text-sm text-perigo">{(error as Error).message}</p> : null}

      {pacientes ? (
        <section className="flex flex-col gap-3">
          <p className="text-sm text-tinta-2">
            {total === 1 ? '1 paciente' : `${total} pacientes`}
          </p>

          {total === 0 ? (
            <p className="py-14 text-center text-sm text-tinta-3">
              Nenhum paciente cadastrado ainda.
            </p>
          ) : (
            <ul className="flex flex-col gap-px">
              {pacientes.map((paciente) => (
                <li key={paciente.idPaciente} className="bg-superficie">
                  <Link
                    to={`/pacientes/${paciente.idPaciente}`}
                    className="flex items-center justify-between gap-6 px-5 py-4 text-sm"
                  >
                    <span>{paciente.nome}</span>
                    <span className="font-mono text-xs text-tinta-3">{paciente.dataNascimento}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}
    </div>
  )
}
```

- [ ] **Passo 6: acrescentar a rota**

Em `src/rotas.tsx`, trocar o bloco protegido e acrescentar os imports
`import { Navigate } from 'react-router-dom'` e
`import { PacientesPage } from './features/pacientes/PacientesPage'`:

```tsx
  {
    path: '/',
    element: <RotaProtegida />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <Navigate to="/pacientes" replace /> },
          { path: 'pacientes', element: <PacientesPage /> },
        ],
      },
    ],
  },
```

- [ ] **Passo 7: rodar e ver passar**

Run: `npm test`
Esperado: 33 testes passando (29 anteriores + 4 de pacientes).

- [ ] **Passo 8: commit**

```bash
git add src/api/recursos/paciente.ts src/features/pacientes/ src/rotas.tsx
git commit -m "feat(web): lista e cadastra paciente"
```

---

### Task 8: ficha do paciente com anotação de sessão

**Arquivos:**
- Criar: `src/api/recursos/anotacao.ts`, `src/features/ficha/queries.ts`,
  `src/features/ficha/FichaPage.tsx`, `src/features/ficha/FichaPage.test.tsx`
- Modificar: `src/rotas.tsx`

**Interfaces:**
- Consome: `pedir` (Task 2), `usePaciente` (Task 7), `Botao` (Task 5).
- Produz: `listarAnotacoes(pacienteId, pagina): Promise<Pagina<Anotacao>>`,
  `criarAnotacao(pacienteId, anotacao): Promise<Anotacao>`;
  `chavesDeAnotacao.lista(pacienteId, pagina)`;
  `useAnotacoes(pacienteId, pagina)`, `useCriarAnotacao(pacienteId)`;
  `<FichaPage />` na rota `/pacientes/:pacienteId`.

- [ ] **Passo 1: escrever o teste que falha**

`src/features/ficha/FichaPage.test.tsx`:

```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { RouteObject } from 'react-router-dom'
import { Link } from 'react-router-dom'

import { gravarAcesso, reiniciarSessaoEmMemoria } from '../../api/sessaoEmMemoria'
import { renderizarComRotas } from '../../teste/renderizar'
import { servidorDeTeste } from '../../teste/servidor'
import { FichaPage } from './FichaPage'

const ID = '3f9a1c04-0000-0000-0000-000000000001'

const PACIENTE = {
  idPaciente: ID,
  idUsuario: 'u-1',
  nome: 'Ana Moreira',
  email: 'ana@exemplo.br',
  telefone: '(51) 99612-0184',
  dataNascimento: '1991-04-12',
  createdAt: '2026-03-11T10:00:00',
}

function pagina(itens: { id: string; conteudo: string; createdAt: string }[], total = itens.length) {
  return {
    content: itens.map((item) => ({ ...item, pacienteId: ID })),
    page: { size: 20, number: 0, totalElements: total, totalPages: Math.max(1, Math.ceil(total / 20)) },
  }
}

const ROTAS: RouteObject[] = [
  { path: '/pacientes/:pacienteId', element: <FichaPage /> },
  { path: '/pacientes', element: <p>lista de pacientes</p> },
]

describe('FichaPage', () => {
  beforeEach(() => {
    reiniciarSessaoEmMemoria()
    gravarAcesso('token-de-teste')
    servidorDeTeste.use(http.get(`/pacientes/${ID}`, () => HttpResponse.json(PACIENTE)))
  })

  it('mostra o paciente e as anotacoes', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(
          pagina([{ id: 'a1', conteudo: 'Retomou o registro de sono.', createdAt: '2026-08-19' }]),
        ),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)

    expect(await screen.findByRole('heading', { name: 'Ana Moreira' })).toBeInTheDocument()
    expect(await screen.findByText('Retomou o registro de sono.')).toBeInTheDocument()
    expect(screen.getByText('1 anotação')).toBeInTheDocument()
  })

  it('envia o campo anotacao no corpo, como o DTO exige', async () => {
    const corpos: unknown[] = []
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(pagina([]))),
      http.post(`/pacientes/${ID}/anotacoes`, async ({ request }) => {
        corpos.push(await request.json())
        return HttpResponse.json(
          { id: 'nova', conteudo: 'Sessão de hoje.', createdAt: '2026-08-26', pacienteId: ID },
          { status: 201 },
        )
      }),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)
    await userEvent.type(await screen.findByLabelText('Anotação da sessão'), 'Sessão de hoje.')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar anotação' }))

    await waitFor(() => expect(corpos).toEqual([{ anotacao: 'Sessão de hoje.' }]))
  })

  it('limpa o rascunho depois de registrar', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(pagina([]))),
      http.post(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(
          { id: 'nova', conteudo: 'Sessão de hoje.', createdAt: '2026-08-26', pacienteId: ID },
          { status: 201 },
        ),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)
    const campo = await screen.findByLabelText('Anotação da sessão')
    await userEvent.type(campo, 'Sessão de hoje.')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar anotação' }))

    await waitFor(() => expect(campo).toHaveValue(''))
  })

  it('mostra o rodape de paginacao lendo page.totalElements', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(pagina([{ id: 'a1', conteudo: 'Uma.', createdAt: '2026-08-19' }], 42)),
      ),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)

    expect(await screen.findByText('página 1 de 3 · 42 anotações')).toBeInTheDocument()
  })

  it('avisa antes de sair com rascunho pendente', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(pagina([]))),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)
    await userEvent.type(await screen.findByLabelText('Anotação da sessão'), 'texto nao enviado')
    await userEvent.click(screen.getByRole('link', { name: 'Voltar' }))

    expect(
      await screen.findByText('Você tem uma anotação não enviada. Sair perde o texto.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('lista de pacientes')).not.toBeInTheDocument()
  })

  it('sem rascunho, sair navega direto', async () => {
    servidorDeTeste.use(
      http.get(`/pacientes/${ID}/anotacoes`, () => HttpResponse.json(pagina([]))),
    )

    renderizarComRotas(ROTAS, `/pacientes/${ID}`)
    await screen.findByLabelText('Anotação da sessão')
    await userEvent.click(screen.getByRole('link', { name: 'Voltar' }))

    expect(await screen.findByText('lista de pacientes')).toBeInTheDocument()
  })
})
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `npm test`
Esperado: FALHA — `./FichaPage` não existe.

- [ ] **Passo 3: escrever o recurso**

`src/api/recursos/anotacao.ts`:

```ts
import { pedir } from '../client'
import type { Anotacao, Pagina } from '../tipos'

export function listarAnotacoes(pacienteId: string, pagina = 0): Promise<Pagina<Anotacao>> {
  return pedir<Pagina<Anotacao>>(`/pacientes/${pacienteId}/anotacoes?page=${pagina}`)
}

/**
 * O corpo leva `anotacao`, nao `conteudo`: o DTO de cadastro da API usa esse
 * nome, e a resposta usa `conteudo`. Foi exatamente essa divergencia de nome que
 * quebrou a criacao de anotacao no backend uma vez — nomear errado aqui produz
 * 400 de campo obrigatorio, sem pista melhor.
 */
export function criarAnotacao(pacienteId: string, anotacao: string): Promise<Anotacao> {
  return pedir<Anotacao>(`/pacientes/${pacienteId}/anotacoes`, {
    method: 'POST',
    body: JSON.stringify({ anotacao }),
  })
}
```

- [ ] **Passo 4: escrever as queries**

`src/features/ficha/queries.ts`:

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import * as recurso from '../../api/recursos/anotacao'

export const chavesDeAnotacao = {
  lista: (pacienteId: string, pagina: number) => ['anotacoes', pacienteId, pagina] as const,
  todas: (pacienteId: string) => ['anotacoes', pacienteId] as const,
}

export function useAnotacoes(pacienteId: string, pagina: number) {
  return useQuery({
    queryKey: chavesDeAnotacao.lista(pacienteId, pagina),
    queryFn: () => recurso.listarAnotacoes(pacienteId, pagina),
  })
}

export function useCriarAnotacao(pacienteId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (anotacao: string) => recurso.criarAnotacao(pacienteId, anotacao),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: chavesDeAnotacao.todas(pacienteId) })
      void client.invalidateQueries({ queryKey: ['auditoria'] })
    },
  })
}
```

- [ ] **Passo 5: escrever a ficha**

`src/features/ficha/FichaPage.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { Link, useBlocker, useParams, useSearchParams } from 'react-router-dom'

import { ErroApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
import { usePaciente } from '../pacientes/queries'
import { useAnotacoes, useCriarAnotacao } from './queries'

export function FichaPage() {
  const { pacienteId = '' } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const pagina = Number(parametros.get('page') ?? '0')

  const paciente = usePaciente(pacienteId)
  const anotacoes = useAnotacoes(pacienteId, pagina)
  const criar = useCriarAnotacao(pacienteId)

  const [rascunho, setRascunho] = useState('')
  const temRascunho = rascunho.trim().length > 0

  /**
   * O rascunho vive so em memoria — anotacao clinica nao fica em claro no
   * navegador. Isso obriga a guarda: sem ela, "em memoria" viraria "perde em
   * silencio" numa navegacao acidental.
   */
  const bloqueio = useBlocker(temRascunho)

  useEffect(() => {
    if (!temRascunho) return

    function avisar(evento: BeforeUnloadEvent) {
      evento.preventDefault()
    }

    window.addEventListener('beforeunload', avisar)
    return () => window.removeEventListener('beforeunload', avisar)
  }, [temRascunho])

  async function registrar() {
    if (!temRascunho) return
    try {
      await criar.mutateAsync(rascunho)
      setRascunho('')
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  const total = anotacoes.data?.page.totalElements ?? 0
  const totalPaginas = anotacoes.data?.page.totalPages ?? 1

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-3">
        <Link to="/pacientes" className="font-mono text-xs tracking-widest text-tinta-3">
          Voltar
        </Link>
        <h1 className="font-serif text-4xl font-light">
          {paciente.data?.nome ?? 'Carregando…'}
        </h1>
        {paciente.data ? (
          <p className="text-sm text-tinta-2">
            {paciente.data.telefone ?? 'sem telefone'} · {paciente.data.dataNascimento}
          </p>
        ) : null}
      </header>

      {bloqueio.state === 'blocked' ? (
        <div className="flex flex-col gap-3 border-l-2 border-atencao bg-superficie p-4">
          <p className="text-sm">Você tem uma anotação não enviada. Sair perde o texto.</p>
          <div className="flex gap-4">
            <Botao onClick={() => bloqueio.proceed()}>Sair sem salvar</Botao>
            <Botao variante="texto" onClick={() => bloqueio.reset()}>
              Continuar aqui
            </Botao>
          </div>
        </div>
      ) : null}

      <section className="flex max-w-2xl flex-col gap-3">
        <label className="flex flex-col gap-2">
          <span className="text-sm text-tinta-2">Anotação da sessão</span>
          <textarea
            rows={4}
            value={rascunho}
            onChange={(evento) => setRascunho(evento.target.value)}
            className="border border-linha bg-superficie p-4 text-sm outline-none focus:border-acento"
          />
        </label>
        <div className="flex items-center gap-4">
          <Botao onClick={() => void registrar()}>Registrar anotação</Botao>
          <span className="text-xs text-tinta-3">
            Anotação não tem PUT — o histórico não é editável.
          </span>
        </div>
        {criar.error instanceof ErroApi ? (
          <p className="text-sm text-perigo">{criar.error.message}</p>
        ) : null}
      </section>

      <section className="flex max-w-2xl flex-col gap-3">
        <p className="text-sm text-tinta-2">
          {total === 1 ? '1 anotação' : `${total} anotações`}
        </p>

        <ul className="flex flex-col gap-px">
          {(anotacoes.data?.content ?? []).map((anotacao) => (
            <li key={anotacao.id} className="flex gap-5 bg-superficie p-5">
              <span className="font-mono text-xs text-tinta-3">{anotacao.createdAt}</span>
              <span className="text-sm leading-relaxed">{anotacao.conteudo}</span>
            </li>
          ))}
        </ul>

        <div className="flex items-center gap-4">
          <Botao
            variante="texto"
            onClick={() => setParametros({ page: String(Math.max(0, pagina - 1)) })}
          >
            Anterior
          </Botao>
          <Botao
            variante="texto"
            onClick={() => setParametros({ page: String(Math.min(totalPaginas - 1, pagina + 1)) })}
          >
            Próxima
          </Botao>
          <span className="font-mono text-xs text-tinta-3">
            {`página ${pagina + 1} de ${totalPaginas} · ${total} anotações`}
          </span>
        </div>
      </section>
    </div>
  )
}
```

- [ ] **Passo 6: acrescentar a rota**

Em `src/rotas.tsx`, acrescentar o import
`import { FichaPage } from './features/ficha/FichaPage'` e a rota, depois de
`pacientes`:

```tsx
          { path: 'pacientes/:pacienteId', element: <FichaPage /> },
```

- [ ] **Passo 7: rodar e ver passar**

Run: `npm test`
Esperado: 39 testes passando (33 anteriores + 6 da ficha).

- [ ] **Passo 8: commit**

```bash
git add src/api/recursos/anotacao.ts src/features/ficha/ src/rotas.tsx
git commit -m "feat(web): ficha do paciente com registro de anotacao"
```

---

### Task 9: E2E de fumaça contra a API real

**Arquivos:**
- Criar: `playwright.config.ts`, `e2e/fluxo-de-fumaca.spec.ts`

**Interfaces:**
- Consome: a aplicação inteira (Tasks 1-8) e a API real na porta 8080.
- Produz: `npm run e2e`.

Este é o único teste capaz de pegar contrato desalinhado entre os dois
repositórios — o análogo do `FluxoCompletoHttpTest` do backend.

- [ ] **Passo 1: escrever a configuração**

`playwright.config.ts`:

```ts
import { defineConfig } from '@playwright/test'

/**
 * O webServer sobe o Vite, cujo proxy espelha os caminhos da API. A API em si e
 * pre-requisito externo: rode `./debug.sh` no psychology-api antes.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure' },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})
```

- [ ] **Passo 2: escrever o E2E**

`e2e/fluxo-de-fumaca.spec.ts`:

```ts
import { expect, test } from '@playwright/test'

/**
 * E-mail unico por execucao: o cadastro responde 409 em e-mail repetido, e o
 * teste roda contra um banco que persiste entre execucoes.
 */
function emailUnico(): string {
  return `fumaca-${Date.now()}@teste.local`
}

test('registrar, entrar, cadastrar paciente e anotar a sessao', async ({ page }) => {
  const email = emailUnico()
  const senha = 'senhaforte123'

  await page.goto('/login')

  await page.getByRole('button', { name: 'Criar conta' }).click()
  await page.getByLabel('Nome').fill('Ana da Fumaça')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha').fill(senha)
  await page.getByRole('button', { name: 'Cadastrar' }).click()

  await expect(page.getByRole('heading', { name: 'Pacientes' })).toBeVisible()

  await page.getByRole('button', { name: 'Novo paciente' }).click()
  await page.getByLabel('Nome').fill('Paciente da Fumaça')
  await page.getByLabel('Data de nascimento').fill('1991-04-12')
  await page.getByRole('button', { name: 'Cadastrar' }).click()

  await page.getByRole('link', { name: /Paciente da Fumaça/ }).click()

  await expect(page.getByRole('heading', { name: 'Paciente da Fumaça' })).toBeVisible()

  const anotacao = 'Primeira sessão registrada pelo teste de fumaça.'
  await page.getByLabel('Anotação da sessão').fill(anotacao)
  await page.getByRole('button', { name: 'Registrar anotação' }).click()

  await expect(page.getByText(anotacao)).toBeVisible()
  await expect(page.getByText('1 anotação')).toBeVisible()
})
```

- [ ] **Passo 3: instalar o navegador e rodar**

```bash
export PATH=/home/syonet/.nvm/versions/node/v20.20.2/bin:$PATH
npx playwright install chromium
```

Em outro terminal, no `psychology-api`, subir a API:

```bash
./debug.sh
```

Com a API no ar:

```bash
export PATH=/home/syonet/.nvm/versions/node/v20.20.2/bin:$PATH
npm run e2e
```

Esperado: 1 teste passando. Se falhar em `/auth/registrar` com erro de rede, a
API não está no ar — o E2E não sobe a API, por decisão registrada na spec.

- [ ] **Passo 4: commit**

```bash
git add playwright.config.ts e2e/
git commit -m "test(web): e2e de fumaca cobrindo o fluxo entre os dois repos"
```

---

### Task 10: README do repositório

**Arquivos:**
- Criar: `README.md`

- [ ] **Passo 1: escrever o README**

`README.md`:

```markdown
# psychology-web

Frontend do consultório — interface do psicólogo para a
[psychology-api](https://github.com/erickbordin/psychology-api).

> **Status:** esqueleto vertical. Entrar, cadastrar paciente e registrar anotação
> de sessão funcionam. Agenda, série recorrente, lembretes e trilha de auditoria
> ainda não foram construídos, e o visual final não foi aplicado.

## Stack

| Camada | Tecnologia |
|---|---|
| Build | Vite |
| Linguagem | React + TypeScript |
| Rotas | React Router |
| Dados | TanStack Query |
| Estilo | Tailwind v4 (tokens CSS-first) |
| Testes | Vitest + Testing Library + MSW |
| E2E | Playwright |

## Rodando localmente

**Requisitos:** Node 20 e a `psychology-api` no ar em `localhost:8080`.

```bash
npm install
npm run dev
```

O Vite proxia `/auth`, `/pacientes`, `/consultas`, `/anotacoes`, `/lembretes` e
`/auditoria` para a API. O proxy **espelha os caminhos**, sem prefixo `/api`: o
cookie de refresh é `Path=/auth`, e sob um prefixo o navegador não o enviaria.

## Sessão

O access token vive **apenas em memória**. Quem sustenta o recarregamento é o
cookie `HttpOnly` de refresh: no boot, um `POST /auth/refresh` restaura a sessão
sem que exista token persistido em lugar nenhum.

Chamadas que tomam `401` compartilham **uma** promise de renovação. A API rotaciona
o refresh a cada uso e revoga a cadeia inteira ao ver um refresh reapresentado —
renovações concorrentes seriam lidas como token roubado e derrubariam a sessão de
um usuário legítimo.

## Testes

```bash
npm test          # unitários e integração de componente, com MSW
npm run e2e       # E2E de fumaça (exige a API no ar)
```

Os handlers do MSW respondem o contrato real da API — envelope de erro,
`PagedModel`, `401`. O E2E percorre `registrar → entrar → cadastrar → anotar`
contra a API de verdade, e é o único teste que pega contrato desalinhado entre os
dois repositórios.

## Documentação

- `docs/superpowers/specs/2026-08-26-frontend-design.md` — design e alternativas rejeitadas
- `docs/superpowers/plans/2026-08-26-esqueleto-vertical.md` — plano desta entrega
```

- [ ] **Passo 2: commit**

```bash
git add README.md
git commit -m "docs: README do frontend"
```

---

## Cobertura da spec

| Requisito da spec | Task |
|---|---|
| Vite + React + TS + React Router + Query + Tailwind v4 | 1 |
| Camadas com fronteira dura (recursos / queries / componentes) | 2, 4, 7, 8 |
| Tipos escritos à mão espelhando os DTOs | 2 |
| Envelope de erro com um caminho só | 2 |
| Access token só em memória | 3 |
| Refresh no boot | 4 |
| Uma promise de refresh compartilhada | 3 |
| Uma tentativa só; `/auth/*` fora do retry | 3 |
| Logout limpando o cache da Query | 4 |
| Rotas com guarda e layout | 6 |
| Paginação e filtro na URL | 8 (`?page=`) |
| Toda mutação invalida `auditoria` | 7, 8 |
| Rascunho só em memória com guarda de navegação | 8 |
| Tokens da direção visual (Newsreader, Plex, paleta fria) | 1 |
| MSW respondendo o contrato real | 2 em diante |
| E2E de fumaça atravessando os dois repos | 9 |

## Fora deste plano, por decisão

Filtro `?nome` na lista de pacientes, edição e exclusão de paciente, lembretes,
histórico de consultas na ficha, agenda, série recorrente, trilha de auditoria e
o visual final. Tudo isso está na spec e entra nas etapas 3 a 6 da ordem de
execução — este plano é o passo 2, o esqueleto vertical.

---

## Defeitos deste plano encontrados na execução

Registrados aqui porque o plano acima ficou como está — o que rodou é o código,
não o texto. Quem reusar estes moldes num próximo plano herda os defeitos.

| # | Onde | Defeito | Como foi fechado |
|---|---|---|---|
| 1 | Task 1 | `tsconfig` do brief não compila sob TypeScript 7: projeto referenciado com `noEmit` dá TS6310, e falta `vite/client` nos `types` dá TS2882. O brief instalava `typescript` sem pin e o `latest` trouxe o 7.0.2. | `outDir: .tsbuild` em vez de `noEmit`, `vite/client` nos types. Pinar a major do TypeScript no próximo scaffold. |
| 2 | Task 2 | Código de teste do brief chamava `pedir(...)` sem type arg, `T` inferia `unknown`, `tsc` quebrava (TS18046). | Type arg explícito. |
| 3 | Task 2 | `ErroApi` do brief nunca expunha `mensagem` — só o `message` herdado. A asserção `.not.toBe('')` do próprio brief comparava `undefined` com string vazia: vacuamente verdadeira. | Campo `mensagem` próprio na classe. |
| 4 | Task 2 | Falha de transporte escapava como `TypeError` cru, furando a restrição global "todo erro chega como `ErroApi`". | `status: 0` sintético, testado com `HttpResponse.error()`. |
| 5 | Task 5 | Teste do brief usava `findByText('campo obrigatorio')` num cenário em que dois campos ficam vazios ao mesmo tempo — o singular do Testing Library lança com dois matches. | `findAllByText(...).toHaveLength(2)`. |
| 6 | Task 6 | `rotas.tsx` do brief tinha `children: []` sob uma rota sem `path` e sem `index`. Pelo `flattenRoutes` do react-router, essa rota nunca entra na árvore de matching: o `<Layout />` ficava inalcançável e `/` renderizava vazio com sessão válida. | `children: [{ index: true, element: null }]`, substituído na Task 7 por `<Navigate to="/pacientes" replace />`. |
| 7 | Task 5 + Task 7 | O `<p>psychology</p>` que a Task 5 renderizava quando `autenticado` era placeholder legítimo enquanto não existia rota interna. A Task 7 criou a rota e **não** tinha passo para remover o placeholder: registrar/entrar levava a uma tela morta. Sete arquivos de teste de componente não viam — foi o E2E da Task 9. | `<Navigate to={destino ?? '/'} replace />`. **Lição de plano: placeholder criado numa task precisa de item explícito de remoção na task que o torna obsoleto.** |
| 8 | Task 9 | O brief criou `e2e/` sem restringir o glob do Vitest, então o `vitest` passou a coletar `fluxo-de-fumaca.spec.ts` e o `test()` do Playwright explodiu dentro dele. | `include: ['src/**/*.{test,spec}.{ts,tsx}']` no `vite.config.ts`. |
| 9 | Task 1 | As três famílias declaradas em `tokens.css` nunca eram carregadas — nenhum `<link>`, nenhum `@font-face`. O requisito "tokens da direção visual" existia só no nome do token; o navegador caía em Georgia + sans do sistema. | Links do Google Fonts no `index.html`. |
| 10 | Task 5 | `Campo` renderizava o erro **dentro** do `<label>`, então o nome acessível do input virava "E-mail campo obrigatorio" — leitor de tela anunciava a falha como parte do rótulo, e busca exata por rótulo parava de achar o input assim que havia erro. | Erro fora do label, `htmlFor`/`id` explícitos, `aria-describedby` e `aria-invalid`. |
| 11 | Task 5 | Nenhum `<form>`: `Enter` no campo de senha não enviava. Numa tela de login isso é comportamento esperado universal. | `<form onSubmit>` com o primário em `type="submit"`. |
| 12 | Task 5 | `erroDoCampo` olhava só o retrato do último envio (`faltando.includes(campo)`), então "campo obrigatorio" ficava embaixo do campo depois de preenchido. A Task 7 já nasceu com a versão que também olha o valor atual — o plano ficou inconsistente entre as duas. | Mesma assinatura das duas telas: `erroDoCampo(campo, valorAtual)`. |
| 13 | Tasks 5, 7, 8 | Nenhum botão de envio travava durante a chamada. Em `registrar` o duplo clique produz 201 + 409 e mostra "e-mail já cadastrado" logo depois de criar a conta; em anotação, que não tem `PUT` nem `DELETE`, a gravação duplicada fica no histórico para sempre. | `disabled` enquanto em voo nas três telas, com `disabled:opacity-40` no `Botao`. |
| 14 | Task 6 | `RotaProtegida` mandava para `/login` sem levar o destino, então quem abre um link direto para a ficha de um paciente e precisa entrar caía sempre na lista. | Destino no `state` do `Navigate`, lido pelo `LoginPage`. |
