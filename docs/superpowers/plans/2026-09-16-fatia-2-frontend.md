# Fatia 2 — Frontend (A sessão) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar as telas que faltam do ciclo clínico — lista de pacientes com busca, cadastro, e a ficha de página única com a linha do tempo — e fechar o buraco que a Fatia 1 deixou explícito: o `<select>` de paciente do diálogo de consulta.

**Architecture:** O `src/` foi reconstruído na Fatia 1 e a fundação está pronta — molde de formulário, cliente de API com envelope de erro, sessão, navegação, design system. Esta fatia **acrescenta telas sobre fundação existente**, não refaz nada. O primeiro passo é regerar os tipos do OpenAPI: sem eles, metade do que vem depois não compila.

**Tech Stack:** React 19, TypeScript 7, Vite 8, react-router-dom 7, TanStack Query 5, Radix Dialog, react-hook-form 7.88 + zod 4.6.4, Vitest 4, Testing Library, MSW 2.15, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-01-redesenho-produto-design.md`
**Backend desta fatia:** `docs/superpowers/plans/2026-09-14-fatia-2-backend.md` (feito, 296 testes)

---

## Global Constraints

- **Node:** toda invocação de `npm`/`npx` precisa de `ASDF_NODEJS_VERSION=20.19.1` na frente. O node global do asdf é velho demais para Vite 8 / Vitest 4 e falha com erro que não diz isso.
- **`npm test` verde NÃO é o bastante.** O Vitest tira os tipos com esbuild e não checa nada: `ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit` precisa sair com 0 em toda tarefa. Foi assim que um defeito de tipo passou verde a fatia inteira antes.
- **Texto de erro em português**, frase completa, com acento, dizendo o que fazer: `"Informe o nome do paciente."`, nunca `"campo obrigatorio"` (§6.3).
- **zod 4, não zod 3.** `z.email()`, `z.guid()` — e **não** `z.string().email()` / `z.string().uuid()`, que são idioma da 3 e estão deprecados. `z.uuid()` recusa os UUIDs de fixture do projeto (nibble de versão); use `z.guid()`.
- **`vi.useFakeTimers()` sempre escopado**: `vi.useFakeTimers({ toFake: ['Date'] })`. Sem escopo ele congela o `setTimeout` de que MSW e `waitFor` dependem, e os testes penduram sem mensagem.
- **Dado falso é inventado, nunca real** — nenhum nome, telefone, e-mail ou anotação de pessoa real, nem em fixture, nem em teste, nem em e2e (LGPD).
- **Não commitar.** As tarefas entregam `git add`/`git commit` escritos; o dono executa.
- **Não tocar** em `README.md`, `backend/README.md`, `frontend/README.md`.
- **Não tocar no backend.** Se faltar endpoint ou campo, **pare e reporte** em vez de contornar no cliente.
- **Toda tela nova precisa de estilo.** O Crítico da revisão de conjunto da Fatia 1 foi exatamente este: telas entregues sem uma `className`, e um modo privacidade cujo teste afirmava o atributo enquanto o CSS não borrava nada. Cada tarefa de tela aqui carrega o passo do CSS, e ele não é opcional.

---

## O que a varredura pré-voo encontrou (leia antes de começar)

Verificado em 16/09/2026 contra o código na árvore:

| Item | Situação | Onde |
|---|---|---|
| Proxy `/pacientes` rota × API | **Resolvido na Fatia 1.** O `bypass` por `Accept: text/html` devolve o `index.html` para navegação e o JSON para o cliente | `frontend/vite.config.ts:15-37` |
| `/pacientes` no proxy | **Já está** na lista `CAMINHOS_DA_API` | idem |
| Molde de formulário | **Pronto**, com as três regras de §6.2 | `src/formulario/useFormularioDoProduto.ts` |
| Cliente de API + envelope de erro | **Pronto**, com `ErroDaApi` e campos culpados | `src/api/cliente.ts`, `src/api/erro.ts` |
| `<select>` de paciente | **Espaço reservado** com UUID de teste fixo, esperando esta fatia | `src/telas/agenda/DialogoDeConsulta.tsx` |
| Tipos do OpenAPI | **Desatualizados** — apontam para o JSON da Fatia 1 | `package.json`, script `tipos` |
| CSS das telas novas | **Não existe no `src/`** — 8 classes só no protótipo | ver abaixo |

### As 8 classes que o protótipo tem e o `src/` não

`cabecalho-ficha`, `colunas-ficha`, `linha-tempo`, `busca`, `barra-agenda`,
`voltar`, `acoes-ficha`, `registro-novo`.

Todas vivem em `prototipo/estilo.css`. **Copie de lá**, não invente: o design
system desta fatia é o mesmo, e reescrever produziria duas linguagens visuais.

### O padrão da casa para consultar a API (não invente outro)

Container escrito à mão, **tipos dos campos vindos do contrato** — está assim em
`src/telas/agenda/queries.ts` e a razão está escrita lá: `Required<>` é raso e
não alcança membro de array, e o springdoc não sabe expressar "nulável de
verdade", só "opcional".

```ts
type PacienteResponseDTO = components['schemas']['PacienteResponseDTO']

export type Paciente = {
  idPaciente: NonNullable<PacienteResponseDTO['idPaciente']>
  nome: NonNullable<PacienteResponseDTO['nome']>
  valorSessao: NonNullable<PacienteResponseDTO['valorSessao']> | null
}
```

Nomear os campos a partir do contrato garante que renomear no backend quebra o
build aqui — que é o ponto.

### O padrão de teste da casa

`renderizar()` de `src/teste/renderizar.tsx` — embrulha **QueryClient e
`MemoryRouter`, e já aceita `{ rota }`**. Ele **não** embrulha o
`SessaoProvider`: tela que dependa de sessão precisa do seu próprio wrapper, MSW via `servidor` de `src/teste/servidor.ts`, e
`servidor.use(http.get(...))` por teste. Consulte por papel e texto visível,
nunca por `className`.

---

## File Structure

**Tarefa 1 — contrato regerado, e a pendência ganha a frase certa**
- `frontend/contrato-openapi.json` *(criar)*
- `frontend/package.json` *(modificar)* — o script `tipos` aponta para o JSON novo
- `frontend/src/api/contrato.ts` *(regerar)*
- `frontend/src/telas/hoje/queries.ts` *(modificar)* — `PendenciaDoPainel` ganha `tipo`
- `frontend/src/telas/hoje/HojePage.tsx` *(modificar)* — frase por espécie
- Teste: `frontend/src/telas/hoje/HojePage.test.tsx` *(modificar)*

**Tarefa 2 — lista de pacientes com busca**
- `frontend/src/telas/pacientes/queries.ts` *(criar)*
- `frontend/src/telas/pacientes/PacientesPage.tsx` *(criar)*
- `frontend/src/rotas.tsx` *(modificar)*
- `frontend/src/estilo/estilo.css` *(modificar)* — `.busca`, `.barra-agenda`
- Teste: `frontend/src/telas/pacientes/PacientesPage.test.tsx` *(criar)*

**Tarefa 3 — cadastro e edição de paciente**
- `frontend/src/telas/pacientes/esquema.ts` *(criar)*
- `frontend/src/telas/pacientes/DialogoDePaciente.tsx` *(criar)*
- `frontend/src/telas/pacientes/queries.ts` *(modificar)* — mutações
- Teste: `frontend/src/telas/pacientes/DialogoDePaciente.test.tsx` *(criar)*

**Tarefa 4 — a ficha de página única**
- `frontend/src/telas/ficha/queries.ts` *(criar)*
- `frontend/src/telas/ficha/FichaPage.tsx` *(criar)*
- `frontend/src/telas/ficha/LinhaDoTempo.tsx` *(criar)*
- `frontend/src/rotas.tsx` *(modificar)*
- `frontend/src/estilo/estilo.css` *(modificar)* — `.cabecalho-ficha`, `.colunas-ficha`, `.linha-tempo`, `.voltar`, `.acoes-ficha`
- Teste: `frontend/src/telas/ficha/FichaPage.test.tsx` *(criar)*

**Tarefa 5 — anotação presa à sessão, escrita na ficha**
- `frontend/src/telas/ficha/RegistroDaSessao.tsx` *(criar)*
- `frontend/src/telas/ficha/queries.ts` *(modificar)* — mutação de anotação
- `frontend/src/estilo/estilo.css` *(modificar)* — `.registro-novo`
- Teste: `frontend/src/telas/ficha/RegistroDaSessao.test.tsx` *(criar)*

**Tarefa 6 — o `<select>` de paciente ganha a lista real**
- `frontend/src/telas/agenda/DialogoDeConsulta.tsx` *(modificar)*
- Teste: `frontend/src/telas/agenda/DialogoDeConsulta.test.tsx` *(modificar)*

**Tarefa 7 — o e2e do fluxo "a sessão"**
- `frontend/e2e/fluxo-da-sessao.spec.ts` *(criar)*

---

### Task 1: Contrato regerado, e a pendência ganha a frase certa

Sem esta tarefa nada depois compila: `FichaDoPacienteDTO`, `SessaoDaFichaDTO`,
`AnotacaoDaSessaoDTO`, `TipoPendencia` e os campos novos de
`PacienteResponseDTO` não existem no contrato atual, que foi congelado na
Fatia 1.

Ela também paga uma dívida declarada: em 14/09 a faixa de pendências do painel
perdeu a frase `· sem status`, porque o backend passou a mandar uma segunda
espécie e o rótulo fixo virou mentira. A frase certa depende do campo `tipo`,
que chega agora.

**Files:**
- Create: `frontend/contrato-openapi.json`
- Modify: `frontend/package.json`
- Regenerate: `frontend/src/api/contrato.ts`
- Modify: `frontend/src/telas/hoje/queries.ts`
- Modify: `frontend/src/telas/hoje/HojePage.tsx`
- Test: `frontend/src/telas/hoje/HojePage.test.tsx`

**Interfaces:**
- Produces: `components['schemas']` com `FichaDoPacienteDTO`, `SessaoDaFichaDTO`, `AnotacaoDaSessaoDTO`, `TipoPendencia`, e `PacienteResponseDTO` com `valorSessao`/`canalAviso`/`consentimentoAvisoEm`. As Tarefas 2 a 6 leem daqui.
- Produces: `PendenciaDoPainel` com `tipo: 'SEM_STATUS' | 'SEM_ANOTACAO'`.

- [ ] **Step 1: Suba o backend e capture o contrato**

O backend precisa estar no ar com o código da Fatia 2. Da raiz do repositório:

```bash
cd backend && ./debug.sh
```

Espere subir, e então, de outro terminal:

```bash
curl -s http://localhost:8080/v3/api-docs -o frontend/contrato-openapi.json
```

> **O JSON mora em `frontend/`, e não em `.superpowers/`.** A Fatia 1 o guardou
> na pasta de processo, e `.superpowers/sdd/.gitignore` é um `*` cego: o
> arquivo nunca foi versionado, e `npm run tipos` portanto **nunca funcionou
> num clone novo**. O contrato é entrada de build, não rascunho de processo.

Confira que o arquivo tem o que deveria ter — **se qualquer um destes faltar,
o backend no ar não é o da Fatia 2; pare e reporte**:

```bash
for s in FichaDoPacienteDTO SessaoDaFichaDTO AnotacaoDaSessaoDTO SEM_ANOTACAO; do
  printf "%-22s %s\n" "$s" "$(grep -c "$s" frontend/contrato-openapi.json)"
done
```

> **Procure `SEM_ANOTACAO`, e não `TipoPendencia`.** O springdoc inlina enum
> Java simples no ponto de uso — `"tipo": {"type":"string","enum":[...]}` — e
> nunca o publica como schema nomeado. Nenhum enum deste projeto (`CanalAviso`,
> `Status`) aparece nomeado em contrato nenhum. Procurar pelo nome da classe
> falharia com o backend certo no ar.

- [ ] **Step 2: Aponte o script para o contrato novo**

Em `frontend/package.json`, o script `tipos` — troque **só o caminho**, não a
invocação:

```json
"tipos": "npx -y -p openapi-typescript@7.13.0 -p typescript@5.9.2 openapi-typescript contrato-openapi.json -o src/api/contrato.ts"
```

> O `npx -p` isolado com TypeScript 5.9.2 **não é frescura**: o projeto usa
> TypeScript 7, que removeu a Compiler API clássica do import padrão, e o
> `openapi-typescript` quebra com `ts.factory is undefined`. Não "simplifique"
> para `npx openapi-typescript`.

- [ ] **Step 3: Regere e confira que o build ainda fecha**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm run tipos
ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit; echo "tsc=$?"
```

Esperado: `tsc=0`. Se algum tipo existente quebrou, **é achado, não obstáculo**:
diga qual campo mudou de forma no backend antes de mexer em qualquer tela.

- [ ] **Step 4: Escreva o teste que falha**

Em `src/telas/hoje/HojePage.test.tsx`, o teste
`'nao rotula a pendencia como "sem status" — ha duas especies na fila'` existe
e passa. Ele garante que a mentira não voltou; agora precisamos da verdade.
Acrescente ao lado dele:

```tsx
  it('escreve a frase certa para cada especie de pendencia', async () => {
    comPainel({
      proxima: null,
      sessoes: [],
      pendencias: [
        {
          consultaId: '22222222-2222-2222-2222-222222222222',
          dtConsulta: '2026-12-02T08:00:00',
          pacienteNome: 'Helena Vaz',
          tipo: 'SEM_STATUS',
        },
        {
          consultaId: '33333333-3333-3333-3333-333333333333',
          dtConsulta: '2026-12-02T09:00:00',
          pacienteNome: 'Teodoro Alencar',
          tipo: 'SEM_ANOTACAO',
        },
      ],
    })
    renderizar(<HojePage />)

    const faixa = await screen.findByRole('region', { name: /pendencia/i })
    expect(within(faixa).getByText(/marque o que aconteceu/i)).toBeInTheDocument()
    expect(within(faixa).getByText(/registre a anotacao/i)).toBeInTheDocument()
  })
```

- [ ] **Step 5: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test -- HojePage
```

Esperado: FALHA — nenhuma das duas frases está na tela.

- [ ] **Step 6: Acrescente `tipo` ao tipo de container**

Em `src/telas/hoje/queries.ts`, `PendenciaDoPainel` hoje é
`Required<components['schemas']['PendenciaDoPainelDTO']>`. Isso passa a
funcionar assim que o contrato for regerado — **confira que `tipo` chegou** e,
se `Required<>` não bastar (o enum pode vir opcional), escreva o container à
mão seguindo o padrão da casa:

```ts
type PendenciaDTO = components['schemas']['PendenciaDoPainelDTO']

export type PendenciaDoPainel = {
  consultaId: NonNullable<PendenciaDTO['consultaId']>
  dtConsulta: NonNullable<PendenciaDTO['dtConsulta']>
  pacienteNome: NonNullable<PendenciaDTO['pacienteNome']>
  tipo: NonNullable<PendenciaDTO['tipo']>
}
```

- [ ] **Step 7: Escreva a frase por espécie**

Em `src/telas/hoje/HojePage.tsx`, substitua o comentário de dívida e a linha
sem rótulo. O `switch` fica **fora** do JSX, para que acrescentar a espécie da
Fatia 3 seja acrescentar um caso:

```tsx
/**
 * A frase que cada especie de pendencia escreve. O painel cresce por fatia
 * (spec 9): SEM_STATUS veio na Fatia 1, SEM_ANOTACAO na 2, e a de baixa chega
 * na 3 — acrescentar sera acrescentar um caso aqui.
 *
 * A frase diz O QUE FAZER, nao o que falta: quem le esta entre dois
 * atendimentos e precisa agir, nao diagnosticar.
 */
function frasePendente(tipo: PendenciaDoPainel['tipo']): string {
  switch (tipo) {
    case 'SEM_STATUS':
      return 'marque o que aconteceu'
    case 'SEM_ANOTACAO':
      return 'registre a anotacao'
  }
}
```

e na lista:

```tsx
                <span className="hora">{hora(p.dtConsulta)}</span> ·{' '}
                <Nome privacidade={privacidade}>{p.pacienteNome}</Nome> ·{' '}
                {frasePendente(p.tipo)}
```

- [ ] **Step 8: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test
ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit; echo "tsc=$?"
```

Esperado: tudo verde e `tsc=0`. **O teste que proíbe "sem status" continua
verde** — as frases novas não contêm aquela.

- [ ] **Step 9: Entregue o commit ao dono (não execute)**

```bash
git add frontend/contrato-openapi.json \
        frontend/package.json frontend/src/api/contrato.ts \
        frontend/src/telas/hoje/queries.ts frontend/src/telas/hoje/HojePage.tsx \
        frontend/src/telas/hoje/HojePage.test.tsx
git commit -m "feat(contrato): tipos da Fatia 2, e a pendencia volta a dizer o que fazer"
```

---

### Task 2: Lista de pacientes com busca

§4③: "Lista com busca". O backend já responde `GET /pacientes?nome=` desde a
fase do schema — esta tarefa é só a tela.

**Files:**
- Create: `frontend/src/telas/pacientes/queries.ts`
- Create: `frontend/src/telas/pacientes/PacientesPage.tsx`
- Modify: `frontend/src/rotas.tsx`
- Modify: `frontend/src/estilo/estilo.css`
- Test: `frontend/src/telas/pacientes/PacientesPage.test.tsx`

**Interfaces:**
- Consumes: `components['schemas']['PacienteResponseDTO']` da Tarefa 1.
- Produces: `usePacientes(busca?: string)` → `UseQueryResult<Paciente[]>` e o tipo `Paciente`. As Tarefas 3 e 6 consomem os dois.

- [ ] **Step 1: Escreva o teste que falha**

Crie `frontend/src/telas/pacientes/PacientesPage.test.tsx`:

```tsx
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { PacientesPage } from './PacientesPage'

const HELENA = {
  idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  nome: 'Helena Vaz',
  email: 'helena@example.com',
  telefone: '51998112345',
  dataNascimento: '1992-03-14',
  valorSessao: 180.0,
  canalAviso: 'NENHUM',
  consentimentoAvisoEm: null,
  createdAt: '2026-03-02T10:00:00',
}

const RUI = { ...HELENA, idPaciente: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', nome: 'Rui Sampaio' }

const comPacientes = (lista: unknown[] = [HELENA, RUI]) =>
  servidor.use(http.get('/pacientes', () => HttpResponse.json(lista)))

describe('tela de pacientes', () => {
  it('lista quem esta cadastrado', async () => {
    comPacientes()
    renderizar(<PacientesPage />)

    expect(await screen.findByText('Helena Vaz')).toBeInTheDocument()
    expect(screen.getByText('Rui Sampaio')).toBeInTheDocument()
  })

  it('busca manda o termo para a API', async () => {
    let recebido: string | null = null
    servidor.use(
      http.get('/pacientes', ({ request }) => {
        recebido = new URL(request.url).searchParams.get('nome')
        return HttpResponse.json([HELENA])
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<PacientesPage />)

    await usuario.type(await screen.findByLabelText(/buscar/i), 'Helena')

    // sem debounce: uma requisicao por tecla, cacheada por queryKey.
    // Esperar o efeito em vez de cravar um tempo
    expect(await screen.findByText('Helena Vaz')).toBeInTheDocument()
    await screen.findByText('Helena Vaz')
    expect(recebido).toBe('Helena')
  })

  it('dia 1 do psicologo: diz o que fazer, nao fica em branco', async () => {
    comPacientes([])
    renderizar(<PacientesPage />)

    expect(await screen.findByText(/nenhum paciente/i)).toBeInTheDocument()
  })

  it('busca sem resultado nao finge que o cadastro esta vazio', async () => {
    const usuario = userEvent.setup()
    comPacientes([])
    renderizar(<PacientesPage />)

    await usuario.type(await screen.findByLabelText(/buscar/i), 'Ninguem')

    expect(await screen.findByText(/nenhum paciente com esse nome/i)).toBeInTheDocument()
  })

  it('cada paciente leva para a ficha dele', async () => {
    comPacientes()
    renderizar(<PacientesPage />)

    const link = await screen.findByRole('link', { name: /helena vaz/i })
    expect(link).toHaveAttribute('href', `/pacientes/${HELENA.idPaciente}`)
  })
})
```

> **Por que os dois estados vazios são testes separados.** "Não há paciente
> nenhum" e "a busca não achou" são situações diferentes e a frase precisa ser
> diferente: a primeira pede cadastrar, a segunda pede corrigir o termo.
> Mostrar "nenhum paciente cadastrado" para quem buscou por um nome torto diz
> que o cadastro está vazio, e está mentindo.

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test -- PacientesPage
```

Esperado: FALHA — o módulo `./PacientesPage` não existe.

- [ ] **Step 3: Escreva as queries**

Crie `frontend/src/telas/pacientes/queries.ts`:

```ts
import { useQuery } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'

type PacienteDTO = components['schemas']['PacienteResponseDTO']

/**
 * Container a mao, tipos dos campos vindos do contrato — o padrao da casa,
 * pelo mesmo motivo descrito em `telas/agenda/queries.ts`: `Required<>` e raso
 * e o springdoc nao sabe dizer "nulavel de verdade", so "opcional".
 *
 * `valorSessao` e `consentimentoAvisoEm` sao nulaveis DE VERDADE: paciente sem
 * valor definido e paciente que nunca consentiu sao estados normais, nao dado
 * faltando.
 */
export type Paciente = {
  idPaciente: NonNullable<PacienteDTO['idPaciente']>
  nome: NonNullable<PacienteDTO['nome']>
  email: NonNullable<PacienteDTO['email']> | null
  telefone: NonNullable<PacienteDTO['telefone']> | null
  dataNascimento: NonNullable<PacienteDTO['dataNascimento']> | null
  valorSessao: NonNullable<PacienteDTO['valorSessao']> | null
  canalAviso: NonNullable<PacienteDTO['canalAviso']>
  consentimentoAvisoEm: NonNullable<PacienteDTO['consentimentoAvisoEm']> | null
}

export const CHAVE_DOS_PACIENTES = ['pacientes'] as const

export function usePacientes(busca = '') {
  const termo = busca.trim()
  return useQuery({
    queryKey: [...CHAVE_DOS_PACIENTES, termo],
    queryFn: () => {
      const caminho = termo
        ? `/pacientes?${new URLSearchParams({ nome: termo })}`
        : '/pacientes'
      return api<Paciente[]>(caminho)
    },
    // mantem a lista anterior na tela enquanto a busca nova viaja: sem isto a
    // lista pisca em branco a cada tecla e parece que o cadastro sumiu
    placeholderData: (anterior) => anterior,
  })
}
```

- [ ] **Step 4: Escreva a tela**

Crie `frontend/src/telas/pacientes/PacientesPage.tsx`:

```tsx
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { EstadoVazio } from '../../ui/EstadoVazio'
import { usePacientes } from './queries'

export function PacientesPage() {
  const [busca, setBusca] = useState('')
  const { data, isPending, isError } = usePacientes(busca)
  const buscando = busca.trim().length > 0

  return (
    <div className="tela">
      <h1>Pacientes</h1>

      <div className="barra-agenda">
        <input
          className="busca"
          type="search"
          placeholder="Buscar por nome"
          aria-label="Buscar paciente"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
      </div>

      {/* A regiao viva existe SEMPRE, ainda que vazia: leitor de tela so
          anuncia conteudo que entra numa regiao que ja estava no DOM. A mesma
          razao que faz o ResumoDeErros manter a dele. */}
      <p className="sub" aria-live="polite">
        {isPending ? 'Carregando…' : `${data?.length ?? 0} na lista`}
      </p>

      {isError && (
        <p className="anuncio" role="alert">
          Nao foi possivel carregar os pacientes. Tente de novo.
        </p>
      )}

      {data && data.length === 0 && (
        <EstadoVazio
          titulo={
            buscando
              ? 'Nenhum paciente com esse nome.'
              : 'Nenhum paciente cadastrado ainda.'
          }
        />
      )}

      {data && data.length > 0 && (
        <ul className="cartao lista-sessoes">
          {data.map((p) => (
            <li className="linha-pendencia" key={p.idPaciente}>
              <Link to={`/pacientes/${p.idPaciente}`}>{p.nome}</Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
```

`EstadoVazio` recebe `{ titulo: string; acao?: ReactNode }` — **não recebe
`children`**. O `acao` é opcional e existe porque, como diz o comentário dele,
"estado vazio diz o que fazer, não só que está vazio"; aqui a ação (cadastrar
paciente) chega na Tarefa 3, então por ora só o `titulo`.

- [ ] **Step 5: Ligue a rota**

Em `frontend/src/rotas.tsx`, troque o espaço reservado:

```tsx
        <Route path="/pacientes" element={<PacientesPage />} />
```

e acrescente o import. **Não mexa** nas rotas `/financeiro` e `/ajustes`, que
continuam como espaço reservado.

- [ ] **Step 6: Traga o estilo do protótipo**

Em `frontend/src/estilo/estilo.css`, copie de `prototipo/estilo.css` as regras
de `.barra-agenda` e `.busca` — **as regras, não uma reescrita**. Confira no
navegador que a barra de busca aparece com a mesma cara do protótipo.

> Este passo não é enfeite. O Crítico da revisão de conjunto da Fatia 1 foi
> uma fatia inteira entregue sem uma `className`, com testes verdes o tempo
> todo. Tela sem estilo passa no Vitest e não passa no olho de quem usa.

- [ ] **Step 7: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test
ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit; echo "tsc=$?"
```

- [ ] **Step 8: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/telas/pacientes/ frontend/src/rotas.tsx frontend/src/estilo/estilo.css
git commit -m "feat(pacientes): lista com busca, e os dois estados vazios dizendo coisas diferentes"
```

---

### Task 3: Cadastro e edição de paciente

§4③ e §5.4. O backend aceita `valorSessao` e `canalAviso` desde a Fatia 2, com
a regra condicional: **canal `EMAIL` exige e-mail**. A regra vale nos dois
lados (§6.3) — o zod avisa antes do envio, o servidor continua sendo a
autoridade.

**Files:**
- Create: `frontend/src/telas/pacientes/esquema.ts`
- Create: `frontend/src/telas/pacientes/DialogoDePaciente.tsx`
- Modify: `frontend/src/telas/pacientes/queries.ts`
- Modify: `frontend/src/telas/pacientes/PacientesPage.tsx`
- Test: `frontend/src/telas/pacientes/DialogoDePaciente.test.tsx`

**Interfaces:**
- Consumes: `usePacientes`, `Paciente`, `CHAVE_DOS_PACIENTES` da Tarefa 2; `useFormularioDoProduto` e `ResumoDeErros` da Fatia 1.
- Produces: `<DialogoDePaciente aberto aoFechar paciente?>` — sem `paciente` é cadastro, com `paciente` é edição.

- [ ] **Step 1: Escreva o teste que falha**

Crie `frontend/src/telas/pacientes/DialogoDePaciente.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { DialogoDePaciente } from './DialogoDePaciente'

const abrir = (props = {}) =>
  renderizar(<DialogoDePaciente aberto aoFechar={vi.fn()} {...props} />)

describe('dialogo de paciente', () => {
  it('nome vazio nao envia, e o erro diz o que fazer', async () => {
    let enviou = false
    servidor.use(
      http.post('/pacientes', () => {
        enviou = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(await screen.findByText('Informe o nome do paciente.')).toBeInTheDocument()
    expect(enviou).toBe(false)
  })

  it('canal EMAIL sem e-mail e recusado ANTES do envio — 5.4', async () => {
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.selectOptions(screen.getByLabelText(/canal/i), 'EMAIL')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(
      await screen.findByText('Informe o e-mail do paciente para avisar por e-mail.'),
    ).toBeInTheDocument()
  })

  it('canal NENHUM sem e-mail passa — e-mail continua opcional', async () => {
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.post('/pacientes', async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(corpo.nome).toBe('Helena Vaz')
  })

  it('erro do servidor sem campo culpado aparece, o botao nao morre em silencio', async () => {
    servidor.use(
      http.post('/pacientes', () =>
        HttpResponse.json(
          { status: 409, mensagem: 'Ja existe um paciente com esse e-mail.', erros: [] },
          { status: 409 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    const aviso = await screen.findByText(/ja existe um paciente com esse e-mail/i)
    expect(aviso).toHaveAttribute('role', 'alert')
  })

  it('editar abre com os campos preenchidos e manda PUT', async () => {
    let metodo = ''
    servidor.use(
      http.put('/pacientes/:id', ({ request }) => {
        metodo = request.method
        return HttpResponse.json({}, { status: 200 })
      }),
    )
    const usuario = userEvent.setup()
    abrir({
      paciente: {
        idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        nome: 'Helena Vaz',
        email: null,
        telefone: null,
        dataNascimento: null,
        valorSessao: 180.0,
        canalAviso: 'NENHUM',
        consentimentoAvisoEm: null,
      },
    })

    expect(screen.getByLabelText(/nome/i)).toHaveValue('Helena Vaz')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    expect(metodo).toBe('PUT')
  })
})
```

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test -- DialogoDePaciente
```

Esperado: FALHA — o módulo não existe.

- [ ] **Step 3: Escreva o esquema**

Crie `frontend/src/telas/pacientes/esquema.ts`:

```ts
import { z } from 'zod'

/**
 * Espelha `PacienteCadastroDTO`. As mensagens sao as MESMAS do servidor, de
 * proposito: a regra e a mesma nos dois lados (spec 6.3), e duas redacoes
 * diferentes para a mesma recusa fazem o usuario achar que sao dois problemas.
 *
 * zod 4: `z.email(msg)` e `z.guid(msg)` sao a forma atual — a encadeada
 * `z.string().email()` esta deprecada. Nao volte para ela.
 */
export const esquemaDoPaciente = z
  .object({
    nome: z
      .string()
      .min(2, 'O nome deve ter de 2 a 100 caracteres.')
      .max(100, 'O nome deve ter de 2 a 100 caracteres.'),

    // opcional de verdade: string vazia do formulario vira ausencia
    email: z.union([z.literal(''), z.email('Informe um e-mail em formato válido.')]),

    // conta digitos, nao policia mascara — a mesma decisao do @TelefoneBR
    // tomada na Fatia 1 depois de a revisao medir o custo de policiar
    // pontuacao: recusava "(51) 99811-2345 " e aceitava "(5199811234"
    telefone: z
      .string()
      .refine(
        (v) => v === '' || [10, 11, 12, 13].includes(v.replace(/\D/g, '').length),
        'Informe um telefone com DDD.',
      ),

    dataNascimento: z.string(),

    valorSessao: z
      .string()
      .refine(
        (v) => v === '' || Number(v) > 0,
        'O valor da sessão deve ser maior que zero.',
      ),

    canalAviso: z.enum(['NENHUM', 'EMAIL', 'WHATSAPP']),
  })
  // A regra condicional de 5.4 le DOIS campos, entao vive no objeto e nao num
  // campo. `path` aponta o erro para `email`: o envelope da casa entrega campo
  // culpado e o formulario poe o foco nele — erro sem campo nao tem onde pousar.
  .refine((v) => v.canalAviso !== 'EMAIL' || v.email !== '', {
    message: 'Informe o e-mail do paciente para avisar por e-mail.',
    path: ['email'],
  })

export type DadosDoPaciente = z.infer<typeof esquemaDoPaciente>
```

- [ ] **Step 4: Escreva as mutações**

Acrescente a `frontend/src/telas/pacientes/queries.ts`:

```ts
import { useMutation, useQueryClient } from '@tanstack/react-query'

/**
 * Cadastro e edicao na mesma mutacao: a unica diferenca e o metodo e a rota, e
 * separar em duas duplicaria a invalidacao e o tratamento de erro.
 *
 * Campo vazio do formulario vira `null` no corpo: o backend trata ausencia como
 * "nao mexa" desde a correcao do I6, e mandar string vazia gravaria vazio.
 */
export function useSalvarPaciente(idExistente?: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (valores: DadosDoPaciente) => {
      const corpo = {
        nome: valores.nome,
        email: valores.email || null,
        telefone: valores.telefone || null,
        dataNascimento: valores.dataNascimento || null,
        valorSessao: valores.valorSessao ? Number(valores.valorSessao) : null,
        canalAviso: valores.canalAviso,
      }
      return api<Paciente>(idExistente ? `/pacientes/${idExistente}` : '/pacientes', {
        method: idExistente ? 'PUT' : 'POST',
        body: JSON.stringify(corpo),
      })
    },
    onSuccess: () => cliente.invalidateQueries({ queryKey: CHAVE_DOS_PACIENTES }),
  })
}
```

Importe `DadosDoPaciente` de `./esquema`.

- [ ] **Step 5: Escreva o diálogo**

Crie `frontend/src/telas/pacientes/DialogoDePaciente.tsx`, seguindo a forma de
`telas/agenda/DialogoDeConsulta.tsx` — mesmo `Dialog.Root`/`Portal`/`Overlay`,
mesmas classes `mascara`, `dialogo`, `dialogo-cabecalho`, `dialogo-corpo`,
`dialogo-rodape`:

```tsx
import * as Dialog from '@radix-ui/react-dialog'

import { ResumoDeErros } from '../../formulario/ResumoDeErros'
import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { esquemaDoPaciente, type DadosDoPaciente } from './esquema'
import { useSalvarPaciente, type Paciente } from './queries'

type Props = {
  aberto: boolean
  aoFechar: () => void
  /** presente na EDICAO; ausente e cadastro novo */
  paciente?: Paciente
}

export function DialogoDePaciente({ aberto, aoFechar, paciente }: Props) {
  const salvar = useSalvarPaciente(paciente?.idPaciente)

  const f = useFormularioDoProduto(
    esquemaDoPaciente,
    async (valores: DadosDoPaciente) => {
      await salvar.mutateAsync(valores)
      aoFechar()
    },
    {
      defaultValues: {
        nome: paciente?.nome ?? '',
        email: paciente?.email ?? '',
        telefone: paciente?.telefone ?? '',
        dataNascimento: paciente?.dataNascimento ?? '',
        valorSessao: paciente?.valorSessao != null ? String(paciente.valorSessao) : '',
        canalAviso: paciente?.canalAviso ?? 'NENHUM',
      },
    },
  )

  return (
    <Dialog.Root open={aberto} onOpenChange={(a) => !a && aoFechar()}>
      <Dialog.Portal>
        <Dialog.Overlay className="mascara" />
        <Dialog.Content aria-describedby={undefined} className="dialogo">
          <div className="dialogo-cabecalho">
            <Dialog.Title className="dialogo-cabecalho-titulo">
              {paciente ? 'Editar paciente' : 'Novo paciente'}
            </Dialog.Title>
          </div>

          <form onSubmit={f.handleSubmit(f.enviar)} noValidate>
            <div className="dialogo-corpo">
              <ResumoDeErros erros={f.formState.errors} />

              {/* erro sem campo culpado: 409 de duplicado, 401 de sessao
                  expirada. Sem isto o botao morre em silencio — a 6.2 */}
              {f.erroDeEnvio && f.erroDeEnvio.erros.length === 0 && (
                <p className="anuncio" role="alert">
                  {f.erroDeEnvio.mensagem}
                </p>
              )}

              <Campo
                rotulo="Nome"
                obrigatorio
                erro={f.formState.errors.nome?.message}
                {...f.register('nome')}
              />
              <Campo
                rotulo="E-mail"
                type="email"
                erro={f.formState.errors.email?.message}
                {...f.register('email')}
              />
              <Campo
                rotulo="Telefone"
                erro={f.formState.errors.telefone?.message}
                {...f.register('telefone')}
              />
              <Campo
                rotulo="Data de nascimento"
                type="date"
                erro={f.formState.errors.dataNascimento?.message}
                {...f.register('dataNascimento')}
              />
              <Campo
                rotulo="Valor da sessao"
                type="number"
                step="0.01"
                erro={f.formState.errors.valorSessao?.message}
                {...f.register('valorSessao')}
              />

              <div className="campo">
                <label className="campo-rotulo" htmlFor="canal">
                  Canal de aviso
                </label>
                <select id="canal" {...f.register('canalAviso')}>
                  <option value="NENHUM">Nenhum</option>
                  <option value="EMAIL">E-mail</option>
                </select>
              </div>
            </div>

            <div className="dialogo-rodape">
              <Botao type="submit" disabled={f.enviando}>
                Salvar
              </Botao>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
```

> **WHATSAPP fica fora do `<select>` de propósito.** O enum do backend o tem
> porque o schema fechou antes das fatias, mas o canal é da fase 2 (§3).
> Oferecer uma opção que não envia nada seria prometer o que o produto não faz.

- [ ] **Step 6: Ligue o botão na lista**

Em `PacientesPage.tsx`: um estado `const [dialogo, setDialogo] = useState(false)`,
um `<Botao onClick={() => setDialogo(true)}>+ Novo paciente</Botao>` dentro da
`div.barra-agenda`, e o `<DialogoDePaciente aberto={dialogo} aoFechar={() => setDialogo(false)} />`.

Aproveite e dê ao estado vazio de cadastro a sua ação:

```tsx
          acao={!buscando ? <Botao onClick={() => setDialogo(true)}>Cadastrar o primeiro</Botao> : undefined}
```

- [ ] **Step 7: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test
ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit; echo "tsc=$?"
```

- [ ] **Step 8: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/telas/pacientes/
git commit -m "feat(pacientes): cadastro e edicao, com a regra condicional do canal de aviso"
```

---

### Task 4: A ficha de página única

§4③: a ficha "perde as quatro abas e vira uma página" — cabeçalho, linha do
tempo de sessões com a anotação dentro, pendências ao lado. O backend entrega
tudo numa chamada: `GET /pacientes/{id}/ficha`.

**Files:**
- Create: `frontend/src/telas/ficha/queries.ts`
- Create: `frontend/src/telas/ficha/FichaPage.tsx`
- Create: `frontend/src/telas/ficha/LinhaDoTempo.tsx`
- Modify: `frontend/src/rotas.tsx`
- Modify: `frontend/src/estilo/estilo.css`
- Test: `frontend/src/telas/ficha/FichaPage.test.tsx`

**Interfaces:**
- Consumes: `components['schemas']['FichaDoPacienteDTO' | 'SessaoDaFichaDTO' | 'AnotacaoDaSessaoDTO']` da Tarefa 1.
- Produces: `useFicha(pacienteId)` → `UseQueryResult<Ficha>`, `CHAVE_DA_FICHA`, e os tipos `Ficha`/`SessaoDaFicha`. A Tarefa 5 consome os três.

- [ ] **Step 1: Escreva o teste que falha**

Crie `frontend/src/telas/ficha/FichaPage.test.tsx`:

```tsx
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { FichaPage } from './FichaPage'

const ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'

const FICHA = {
  paciente: {
    idPaciente: ID,
    nome: 'Helena Vaz',
    email: null,
    telefone: '51998112345',
    dataNascimento: '1992-03-14',
    valorSessao: 180.0,
    canalAviso: 'NENHUM',
    consentimentoAvisoEm: null,
    createdAt: '2026-03-02T10:00:00',
  },
  linhaDoTempo: [
    { consultaId: 'c3', dtConsulta: '2026-12-02T09:00:00', status: 'AGENDADA', anotacoes: [] },
    {
      consultaId: 'c2',
      dtConsulta: '2026-11-25T09:00:00',
      status: 'REALIZADA',
      anotacoes: [
        { anotacaoId: 'a1', conteudo: 'Retomamos o combinado da semana.', createdAt: '2026-11-25T10:00:00' },
      ],
    },
    { consultaId: 'c1', dtConsulta: '2026-11-18T09:00:00', status: 'FALTOU', anotacoes: [] },
  ],
  pendencias: [
    { id: 'p1', descricao: 'Enviar recibo de novembro', concluido: false, createdAt: '2026-11-30T10:00:00' },
  ],
}

const comFicha = (ficha: unknown = FICHA) =>
  servidor.use(http.get(`/pacientes/${ID}/ficha`, () => HttpResponse.json(ficha)))

// a tela le o id da rota: renderizar precisa entrar NA rota da ficha
const abrir = () => renderizar(<FichaPage />, { rota: `/pacientes/${ID}` })

describe('ficha do paciente', () => {
  it('cabecalho mostra nome e valor de sessao', async () => {
    comFicha()
    abrir()

    expect(await screen.findByRole('heading', { name: 'Helena Vaz' })).toBeInTheDocument()
    expect(screen.getByText(/180/)).toBeInTheDocument()
  })

  it('linha do tempo vem do encontro mais recente para o mais antigo', async () => {
    comFicha()
    abrir()

    const linhas = await screen.findAllByRole('listitem')
    expect(within(linhas[0]).getByText(/02\/12/)).toBeInTheDocument()
  })

  it('a anotacao aparece DENTRO da linha da sessao dela', async () => {
    comFicha()
    abrir()

    const texto = await screen.findByText(/retomamos o combinado/i)
    const linhaDaSessao = texto.closest('li')
    expect(within(linhaDaSessao!).getByText(/25\/11/)).toBeInTheDocument()
  })

  it('sessao sem anotacao nao finge ter uma', async () => {
    comFicha()
    abrir()

    const linhas = await screen.findAllByRole('listitem')
    expect(within(linhas[0]).queryByText(/retomamos/i)).not.toBeInTheDocument()
  })

  it('pendencias do paciente aparecem ao lado', async () => {
    comFicha()
    abrir()

    expect(await screen.findByText(/enviar recibo de novembro/i)).toBeInTheDocument()
  })

  it('paciente sem sessao nenhuma diz o que fazer', async () => {
    comFicha({ ...FICHA, linhaDoTempo: [], pendencias: [] })
    abrir()

    expect(await screen.findByText(/nenhuma sessao registrada/i)).toBeInTheDocument()
  })

  it('404 da API vira mensagem, nao tela quebrada', async () => {
    servidor.use(
      http.get(`/pacientes/${ID}/ficha`, () =>
        HttpResponse.json({ status: 404, mensagem: 'Paciente nao encontrado', erros: [] }, { status: 404 }),
      ),
    )
    abrir()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
```

> **`renderizar` já aceita `{ rota }`** — verificado em 16/09 na Tarefa 2:
> `export function renderizar(ui, { rota = '/' } = {})`, com `MemoryRouter
> initialEntries={[rota]}`. Não precisa mexer na infraestrutura de teste.

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test -- FichaPage
```

- [ ] **Step 3: Escreva as queries**

Crie `frontend/src/telas/ficha/queries.ts`:

```ts
import { useQuery } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'
import type { Paciente } from '../pacientes/queries'

type SessaoDTO = components['schemas']['SessaoDaFichaDTO']
type AnotacaoDTO = components['schemas']['AnotacaoDaSessaoDTO']

export type AnotacaoDaSessao = {
  anotacaoId: NonNullable<AnotacaoDTO['anotacaoId']>
  conteudo: NonNullable<AnotacaoDTO['conteudo']>
  createdAt: NonNullable<AnotacaoDTO['createdAt']>
}

/** `anotacoes` e LISTA porque nada no modelo impede duas anotacoes na mesma
 *  consulta, e esconder a segunda seria esconder prontuario (spec 7.3). */
export type SessaoDaFicha = {
  consultaId: NonNullable<SessaoDTO['consultaId']>
  dtConsulta: NonNullable<SessaoDTO['dtConsulta']>
  status: NonNullable<SessaoDTO['status']>
  anotacoes: AnotacaoDaSessao[]
}

export type Ficha = {
  paciente: Paciente
  linhaDoTempo: SessaoDaFicha[]
  pendencias: { id: string; descricao: string; concluido: boolean; createdAt: string }[]
}

export const CHAVE_DA_FICHA = (pacienteId: string) => ['ficha', pacienteId] as const

/** Uma chamada devolve a tela inteira (spec 5.6 e 4(3)). Nao quebre em tres —
 *  correlacionar sessao com anotacao no cliente e exatamente o defeito que a
 *  ficha de pagina unica existe para matar. */
export function useFicha(pacienteId: string) {
  return useQuery({
    queryKey: CHAVE_DA_FICHA(pacienteId),
    queryFn: () => api<Ficha>(`/pacientes/${pacienteId}/ficha`),
  })
}
```

- [ ] **Step 4: Escreva a linha do tempo**

Crie `frontend/src/telas/ficha/LinhaDoTempo.tsx`. Use `dataHoraBR`/`dataBR` de
`src/api/data.ts` — **confira o nome real exportado lá** antes de importar:

```tsx
import type { SessaoDaFicha } from './queries'

export function LinhaDoTempo({ sessoes }: { sessoes: SessaoDaFicha[] }) {
  return (
    <ul className="linha-tempo cartao">
      {sessoes.map((s) => (
        <li key={s.consultaId}>
          <div className="acoes-linha">
            <span className="hora">{dataBR(s.dtConsulta)}</span>
            <span className="chip">{s.status}</span>
          </div>
          {/* A anotacao vive DENTRO da linha da sessao — e a fusao das abas
              Consultas e Anotacoes que 4(3) pede. Sessao sem anotacao nao
              ganha caixa vazia: ausencia de anotacao e informacao, e uma
              moldura vazia parece defeito de carregamento. */}
          {s.anotacoes.map((a) => (
            <p key={a.anotacaoId} className="sub">
              {a.conteudo}
            </p>
          ))}
        </li>
      ))}
    </ul>
  )
}
```

- [ ] **Step 5: Escreva a tela**

Crie `frontend/src/telas/ficha/FichaPage.tsx` com `useParams()` do
react-router-dom para ler o id, cabeçalho em `div.cabecalho-ficha.cartao`,
`Link` de volta com classe `voltar`, corpo em `div.colunas-ficha` com a linha
do tempo à esquerda e as pendências num `<aside>`. Estados: `isPending` com
aviso em região viva, `isError` com `role="alert"`, e `EstadoVazio` com título
`"Nenhuma sessao registrada ainda."` quando a linha do tempo vier vazia.

- [ ] **Step 6: Ligue a rota**

Em `frontend/src/rotas.tsx`, **acrescente** (sem mexer na rota `/pacientes`,
que é da Tarefa 2):

```tsx
        <Route path="/pacientes/:pacienteId" element={<FichaPage />} />
```

- [ ] **Step 7: Traga o estilo do protótipo**

Copie de `prototipo/estilo.css` as regras de `.cabecalho-ficha`,
`.colunas-ficha`, `.linha-tempo`, `.voltar` e `.acoes-ficha`. Abra a tela no
navegador e confira contra o protótipo — as duas colunas precisam virar uma só
em largura de telefone (§ responsivo do design system).

- [ ] **Step 8: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test
ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit; echo "tsc=$?"
```

- [ ] **Step 9: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/telas/ficha/ frontend/src/rotas.tsx frontend/src/estilo/estilo.css
git commit -m "feat(ficha): a pagina unica do paciente, com a anotacao dentro da sessao"
```

---

### Task 5: Anotação presa à sessão, escrita na ficha

O que a Fatia 2 do backend existiu para entregar: `POST /pacientes/{id}/anotacoes`
com `consultaId`. Sem esta tarefa, o campo continua sem ninguém que o preencha.

**Files:**
- Create: `frontend/src/telas/ficha/RegistroDaSessao.tsx`
- Modify: `frontend/src/telas/ficha/queries.ts`
- Modify: `frontend/src/telas/ficha/LinhaDoTempo.tsx`
- Modify: `frontend/src/estilo/estilo.css`
- Test: `frontend/src/telas/ficha/RegistroDaSessao.test.tsx`

**Interfaces:**
- Consumes: `useFicha`, `CHAVE_DA_FICHA`, `SessaoDaFicha` da Tarefa 4.
- Produces: `useRegistrarAnotacao(pacienteId)` e `<RegistroDaSessao pacienteId sessao />`.

- [ ] **Step 1: Escreva o teste que falha**

Crie `frontend/src/telas/ficha/RegistroDaSessao.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { RegistroDaSessao } from './RegistroDaSessao'

const ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
const SESSAO = {
  consultaId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
  dtConsulta: '2026-11-25T09:00:00',
  status: 'REALIZADA' as const,
  anotacoes: [],
}

describe('registro da anotacao da sessao', () => {
  it('manda o consultaId junto — a anotacao e DAQUELA sessao', async () => {
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.post(`/pacientes/${ID}/anotacoes`, async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<RegistroDaSessao pacienteId={ID} sessao={SESSAO} />)

    await usuario.type(screen.getByLabelText(/anotacao/i), 'Trabalhamos a exposicao gradual.')
    await usuario.click(screen.getByRole('button', { name: /registrar/i }))

    expect(corpo.consultaId).toBe(SESSAO.consultaId)
    expect(corpo.anotacao).toBe('Trabalhamos a exposicao gradual.')
  })

  it('anotacao vazia nao envia, e diz o que fazer', async () => {
    let enviou = false
    servidor.use(
      http.post(`/pacientes/${ID}/anotacoes`, () => {
        enviou = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<RegistroDaSessao pacienteId={ID} sessao={SESSAO} />)

    await usuario.click(screen.getByRole('button', { name: /registrar/i }))

    expect(await screen.findByText(/informe o conteudo da anotacao/i)).toBeInTheDocument()
    expect(enviou).toBe(false)
  })

  it('erro do servidor aparece, o botao nao morre em silencio', async () => {
    servidor.use(
      http.post(`/pacientes/${ID}/anotacoes`, () =>
        HttpResponse.json(
          {
            status: 409,
            mensagem: 'Esta sessão é de outro paciente. A anotação não pode ser presa a ela.',
            erros: [],
          },
          { status: 409 },
        ),
      ),
    )
    const usuario = userEvent.setup()
    renderizar(<RegistroDaSessao pacienteId={ID} sessao={SESSAO} />)

    await usuario.type(screen.getByLabelText(/anotacao/i), 'Qualquer coisa.')
    await usuario.click(screen.getByRole('button', { name: /registrar/i }))

    const aviso = await screen.findByText(/e de outro paciente/i)
    expect(aviso).toHaveAttribute('role', 'alert')
  })
})
```

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test -- RegistroDaSessao
```

- [ ] **Step 3: Escreva a mutação**

Acrescente a `frontend/src/telas/ficha/queries.ts`:

```ts
/**
 * `consultaId` NAO e opcional aqui, embora seja no DTO do backend: uma
 * anotacao escrita a partir de uma linha da ficha e sempre daquela sessao.
 * Anotacao avulsa existe no modelo (spec 5.1) e nao tem tela nesta fatia.
 */
export function useRegistrarAnotacao(pacienteId: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (dados: { anotacao: string; consultaId: string }) =>
      api(`/pacientes/${pacienteId}/anotacoes`, {
        method: 'POST',
        body: JSON.stringify(dados),
      }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: CHAVE_DA_FICHA(pacienteId) }),
  })
}
```

- [ ] **Step 4: Escreva o componente**

Crie `frontend/src/telas/ficha/RegistroDaSessao.tsx` usando
`useFormularioDoProduto` com um esquema local
(`z.object({ anotacao: z.string().min(1, 'Informe o conteúdo da anotação.') })`),
um `<textarea>` com `<label>` associado, o bloco de erro sem campo culpado
(`role="alert"`) igual ao dos outros diálogos, e um `<Botao type="submit">` com
texto **Registrar**. A caixa vai em `div.registro-novo.cartao`.

- [ ] **Step 5: Ligue na linha do tempo**

Em `LinhaDoTempo.tsx`, dentro da linha de cada sessão **sem anotação e já
realizada**, renderize o `<RegistroDaSessao>`. A linha do tempo passa a receber
`pacienteId` como prop.

> Sessão `AGENDADA` não ganha a caixa: escrever anotação de sessão que ainda não
> aconteceu não é um fluxo do produto, e a caixa ali convida ao engano.

- [ ] **Step 6: Traga o estilo**

Copie `.registro-novo` de `prototipo/estilo.css`.

- [ ] **Step 7: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test
ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit; echo "tsc=$?"
```

- [ ] **Step 8: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/telas/ficha/ frontend/src/estilo/estilo.css
git commit -m "feat(ficha): a anotacao e escrita na linha da sessao a que pertence"
```

---

### Task 6: O `<select>` de paciente ganha a lista real

Esta é a tarefa que fecha o buraco que a Fatia 1 deixou **por escrito**. O
`DialogoDeConsulta` tem hoje uma única `<option>` com UUID de teste fixo e um
comentário dizendo que a lista real é da Fatia 2. A spec registrou a decisão do
dono em 14/09: a Fatia 1 fecharia sem caminho de paciente, e agendar de verdade
passaria a fechar aqui.

**Files:**
- Modify: `frontend/src/telas/agenda/DialogoDeConsulta.tsx`
- Test: `frontend/src/telas/agenda/DialogoDeConsulta.test.tsx`

**Interfaces:**
- Consumes: `usePacientes` e `Paciente` da Tarefa 2.

- [ ] **Step 1: Escreva o teste que falha**

Acrescente a `DialogoDeConsulta.test.tsx`:

```tsx
  it('o select traz os pacientes cadastrados, nao um id de teste', async () => {
    servidor.use(
      http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })),
      http.get('/pacientes', () =>
        HttpResponse.json([
          { idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', nome: 'Helena Vaz' },
          { idPaciente: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', nome: 'Rui Sampaio' },
        ]),
      ),
    )
    abrir()

    expect(await screen.findByRole('option', { name: 'Helena Vaz' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Rui Sampaio' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /paciente de teste/i })).not.toBeInTheDocument()
  })

  it('sem paciente cadastrado, o dialogo diz o que fazer em vez de oferecer lista vazia', async () => {
    servidor.use(
      http.get('/consultas/conflito', () => HttpResponse.json({ livre: true })),
      http.get('/pacientes', () => HttpResponse.json([])),
    )
    abrir()

    expect(await screen.findByText(/cadastre um paciente/i)).toBeInTheDocument()
  })
```

> **O segundo teste não é zelo excessivo.** É exatamente o estado do dia 1 do
> psicólogo, e é o que a revisão de conjunto da Fatia 1 mediu contra a API real:
> sem paciente no banco, `POST /consultas` responde 404 e o diálogo ficava
> parado. Um `<select>` com só "Escolha…" não diz onde cadastrar.

- [ ] **Step 2: Rode e veja falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test -- DialogoDeConsulta
```

- [ ] **Step 3: Troque a `<option>` reservada pela lista**

Em `DialogoDeConsulta.tsx`: `const pacientes = usePacientes()`, e no lugar da
`<option>` fixa e do comentário que a explicava:

```tsx
                <select id="paciente" {...f.register('pacienteId')}>
                  <option value="">Escolha…</option>
                  {pacientes.data?.map((p) => (
                    <option key={p.idPaciente} value={p.idPaciente}>
                      {p.nome}
                    </option>
                  ))}
                </select>
                {pacientes.data?.length === 0 && (
                  <p className="campo-aviso" role="status">
                    Cadastre um paciente antes de agendar.
                  </p>
                )}
```

**Apague o comentário** que dizia que a lista real era da Fatia 2 — ele deixa de
ser verdade neste commit, e comentário desatualizado mente com autoridade.

- [ ] **Step 4: Rode e veja passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm test
ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit; echo "tsc=$?"
```

Os testes antigos do diálogo selecionam
`'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'`. Eles precisam de um handler de
`/pacientes` que devolva esse id — **acrescente o handler**, não mude o id: ele
é o mesmo UUID de fixture usado na fatia inteira, e `z.guid()` o aceita.

- [ ] **Step 5: Entregue o commit ao dono (não execute)**

```bash
git add frontend/src/telas/agenda/
git commit -m "feat(agenda): o select de paciente recebe a lista real, e agendar passa a funcionar"
```

---

### Task 7: O e2e do fluxo "a sessão"

§8.5 pede quatro fluxos e hoje existe um. O segundo é esta fatia: **abre ficha,
registra anotação, agenda retorno**. Ele prova o que nenhum teste com MSW prova
— que a resposta que a tela espera é a que a API real dá.

**Files:**
- Create: `frontend/e2e/fluxo-da-sessao.spec.ts`

- [ ] **Step 1: Escreva o spec**

Crie `frontend/e2e/fluxo-da-sessao.spec.ts`, seguindo a forma de
`fluxo-de-fumaca.spec.ts`: usuário novo por execução registrado pela própria
API (o banco não tem usuário semeado e `POST /auth/registrar` responde 409 em
e-mail repetido), senha `senhaforte123`, e navegação por rótulo escrito.

O fluxo:

```ts
  // 1. registra usuario pela API, entra pela tela
  // 2. vai em Pacientes, cadastra "Paciente Um" pelo dialogo
  // 3. abre a ficha dele pelo link da lista
  // 4. agenda uma sessao (o select agora tem o paciente — Tarefa 6)
  // 5. marca a sessao como realizada
  // 6. escreve a anotacao NA LINHA daquela sessao
  // 7. recarrega a ficha e confere que a anotacao voltou dentro da sessao certa
```

O passo 7 é o que o e2e existe para provar: que `consultaId` viajou, foi
gravado, e volta ligado à sessão. Um teste com MSW não conseguiria — ele
afirmaria contra um servidor que você mesmo escreveu.

- [ ] **Step 2: Rode contra a API real**

O backend precisa estar no ar (`cd backend && ./debug.sh`).

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm run e2e
```

Esperado: 2 passed (o de fumaça da Fatia 1 e este).

> **Se este spec falhar, é achado, não obstáculo.** Foi um e2e contra a API real
> que descobriu, na Fatia 1, que o diálogo de consulta não mostrava erro de
> envio — com 41 testes de MSW verdes. Reporte a falha com a saída antes de
> mexer em qualquer tela.

- [ ] **Step 3: Entregue o commit ao dono (não execute)**

```bash
git add frontend/e2e/fluxo-da-sessao.spec.ts
git commit -m "test(e2e): o fluxo da sessao contra a API real, o segundo dos quatro de 8.5"
```

---

## Ordem de despacho e colisões

Do pré-voo, os arquivos que mais de uma tarefa toca:

| Arquivo | Tarefas | Decisão |
|---|---|---|
| `src/api/contrato.ts` | 1 | só a 1 regenera; **todas as outras dependem dela** |
| `src/rotas.tsx` | 2, 4 | sequencial: a 2 troca `/pacientes`, a 4 acrescenta `/pacientes/:id` |
| `src/estilo/estilo.css` | 2, 4, 5 | sequencial; cada uma acrescenta as suas regras |
| `src/telas/pacientes/queries.ts` | 2 cria, 3 e 6 consomem | 2 obrigatoriamente antes |
| `src/telas/ficha/queries.ts` | 4 cria, 5 acrescenta | 4 antes da 5 |
| `src/teste/renderizar.tsx` | 4 (talvez) | se precisar de rota inicial, a 4 acrescenta |

**Ordem: 1 → 2 → 3 → 4 → 5 → 6 → 7, sequencial.** Nenhum par é seguro em
paralelo: ou compartilham arquivo, ou a segunda depende do tipo que a primeira
produz.

## Fora do escopo desta fatia, de propósito

- **Financeiro e Ajustes** continuam como espaço reservado nas rotas — Fatias 3 e 4.
- **Consentimento como ação** (o botão que grava `consentimentoAvisoEm`) — Fatia 4. Aqui ele é só de leitura no cabeçalho.
- **WHATSAPP no `<select>` de canal** — fase 2 (§3).
- **Fluxo de refresh do token** — recarregar a aba ainda leva ao login. Dívida herdada da Fatia 1.
- **Paginação da ficha e da lista** — a ficha não pagina, e isso está no laudo do backend como item em aberto.
- **`valor` e `pago` na linha do tempo** — Fatia 3, o dinheiro.
