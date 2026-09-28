# Reestrutura do frontend — Fatia 3: a tela Hoje

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fundir pendências e sessões agendadas numa fila única "Precisa de você" ordenada por hora, com as sessões já resolvidas numa lista compacta abaixo — e fechar as duas pendências que as Fatias 1 e 2 carregaram.

**Architecture:** Esta é a única parte de toda a reestrutura que não é CSS. A API devolve pendências e sessões como dois arrays distintos, com regras de origem diferentes, e eles **se sobrepõem** — a mesma consulta aparece nos dois. A tarefa central é uma função pura de reconciliação, testada isolada da tela, e só depois a tela passa a consumi-la. Nenhuma chamada nova de API: `GET /painel/hoje` já devolve tudo.

**Tech Stack:** React 19, TypeScript, Vite, Tailwind (só preflight), `@tanstack/react-query`, Vitest + Testing Library, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-27-reestrutura-frontend-design.md` (D6 e §4.2) e `docs/superpowers/specs/2026-09-27-linguagem-apple.md` (valores).

**Base:** `main` com as Fatias 1 e 2 mergeadas. Baseline a verificar no worktree: 24 arquivos de teste, 115 testes, 0 falhas, `tsc --noEmit` exit 0.

## Global Constraints

- **NUNCA comente código.** Nenhum `//`, `/* */` ou docstring novo em arquivo de código, **incluindo arquivos de teste**. Comentário pré-existente **não se remove** — se ficou falso por causa da sua mudança, reescreva-o para ficar verdadeiro, mas não acrescente comentário que não existia. Violação = achado Importante.
- **Copy em português intocada.** Nenhuma string visível muda de palavra. As adições de texto autorizadas estão nomeadas nas tarefas; nenhuma outra.
- **Semântica de acessibilidade é invariante.** As `<section aria-label>` e as listas continuam listas. Onde uma tarefa muda um `aria-label`, ela diz explicitamente qual e por quê.
- **Nenhuma informação removida.** Pode rebaixar, agrupar ou revelar sob demanda; não pode sumir. Esta fatia é a de maior risco nesse ponto: fundir duas listas é a forma mais fácil de perder uma linha sem perceber.
- **Testes navegam por rótulo escrito, nunca por `className`, e nunca esperam animação.**
- **Dark mode não dispara sozinho.** O gatilho é `:root[data-tema='escuro']`. Nenhum `@media (prefers-color-scheme: dark)` volta a `tokens.css` — há um teste que falha se voltar.
- **Nenhum token inventado.** Todo `var(--x)` já existe em `frontend/src/estilo/tokens.css`.
- **`--separador` só na grade da agenda e no anel interno de controle de entrada.** A Tarefa 4 existe para restaurar isso.
- **A suíte fica verde e `npx tsc --noEmit` sai 0** ao fim de cada tarefa.
- **Node:** prefixe sempre `ASDF_NODEJS_VERSION=20.19.1 npx …` — o pin global é 9.11.2 e quebra `npx`.
- **Backend de dev:** `cd backend && bash debug.sh` (o arquivo não tem bit de execução). Necessário só para o e2e.

---

## O achado que define este plano

Fui ao backend em vez de assumir, e a forma real é mais rica do que "duas listas paralelas". `PainelService.java:57-121`:

| Array | De onde vem | Escopo |
|---|---|---|
| `sessoes` | `agendaDeHoje` menos `CANCELADA` | **Só hoje** |
| pendência `SEM_ANOTACAO` | `agendaDeHoje` filtrado a `REALIZADA` sem anotação | **Só hoje** |
| pendência `SEM_STATUS` | query **própria**: `status = AGENDADA AND dtConsulta < agora` | **Qualquer dia passado** |

Três consequências, e nenhuma é óbvia de fora:

1. **Toda pendência `SEM_ANOTACAO` duplica uma entrada de `sessoes`** (mesma `consultaId`, status `REALIZADA`). Concatenar os dois arrays sem deduplicar mostra a mesma consulta duas vezes.
2. **Uma pendência `SEM_STATUS` de um dia anterior não está em `sessoes`.** A tela Hoje já mostra hoje sessões de outros dias — comportamento existente, não introduzido aqui. A fila precisa aceitar um item que não tem par.
3. **`PendenciaDoPainel` não tem `pacienteId`** (`contrato.ts:535-545`); `SessaoDoDia` tem. Um item de fila que veio só da pendência **não consegue linkar para a ficha**. Mas *consegue* ser marcado: `useMarcarStatus` só precisa de `consultaId`, que a pendência tem.

Além disso, `data.proxima` é calculado no servidor (`PainelService.java:75-78`) e **duplica de propósito** uma entrada de `sessoes` — hoje o mesmo nome aparece duas vezes na tela, e o teste `HojePage.test.tsx` documenta isso. Não é defeito; é o destaque.

---

## Registro de decisões

**D3.1 — A fila é `pendências ∪ {sessões AGENDADA}`, deduplicada por `consultaId`.**
A §4.2 do spec diz "pendências e sessões agendadas sem status fundem numa fila única". Implementado ao pé da letra: entra tudo que pede ação. Sai o que já está resolvido.

**D3.2 — No empate de `consultaId`, a pendência decide o texto e a sessão empresta o `pacienteId`.**
A pendência diz *o que fazer* ("registre a anotação"), que é mais informativo que o status. Mas só a sessão tem `pacienteId`, e sem ele não há link para a ficha. Alternativa recusada: deixar a sessão vencer e perder a frase de ação — transformaria "registre a anotação" de volta em "REALIZADA", que é o que a tela já dizia e não ajudava.

**D3.3 — Um item sem `pacienteId` não renderiza o link da ficha, e isso não é erro.**
É o caso da pendência `SEM_STATUS` de dia anterior. Alternativa recusada: buscar o paciente numa segunda chamada só para ter o link — uma requisição por linha órfã, numa tela que fica aberta o dia inteiro. Alternativa recusada: esconder esses itens — violaria "nenhuma informação removida", e são justamente os mais atrasados.

**D3.4 — `CANCELADA` não aparece em lugar nenhum.**
O backend já a exclui de `sessoes` (`PainelService.java:64`). Nada a fazer no cliente; registrado para que ninguém "conserte" a ausência.

**D3.5 — O cartão da próxima sessão continua duplicando uma linha da fila.**
É o desenho: o destaque responde "o que vem agora" e a fila responde "o que preciso fazer". Alternativa recusada: remover da fila a sessão que está em destaque — deixaria a fila mentir sobre o total de pendências.

**D3.6 — O e2e é gate desta fatia, não formalidade.**
As Fatias 1 e 2 eram quase só CSS; os dois fluxos do Playwright passavam sem esforço. Esta fatia muda a estrutura de dados que a tela Hoje consome, e o `fluxo-de-fumaca` passa exatamente por ela. Rodar o e2e é passo obrigatório da Tarefa 2.

---

## Estrutura de arquivos

| Arquivo | Responsabilidade | Tarefa |
|---|---|---|
| `src/telas/hoje/fila.ts` | **novo** — a reconciliação, função pura | 1 |
| `src/telas/hoje/fila.test.ts` | **novo** — os casos de borda da reconciliação | 1 |
| `src/telas/hoje/HojePage.tsx` | passa a renderizar fila + lista resolvida | 2 |
| `src/telas/hoje/HojePage.test.tsx` | os testes ancorados nas duas listas são reescritos | 2 |
| `src/estilo/estilo.css` | `.fila-precisa`, `.linha-resolvida`, `.destaque`, `--separador` | 2, 3, 4 |
| `src/ui/Cartao.tsx` | nenhuma mudança de API; só o CSS do `destaque` muda | 3 |

---

### Task 1: O reconciliador

Uma função pura, sem React, testada isolada. É a única parte desta fatia que pode errar em silêncio — uma linha perdida na fusão não quebra nenhum teste de tela, só some.

**Files:**
- Create: `frontend/src/telas/hoje/fila.ts`
- Create: `frontend/src/telas/hoje/fila.test.ts`

**Interfaces:**
- Consome: `SessaoDoDia` e `PendenciaDoPainel` de `./queries`.
- Produz: `type ItemDaFila` e `function reconciliar(...)`, usados pela Tarefa 2.

- [ ] **Step 1: Escrever os testes que falham**

Crie `frontend/src/telas/hoje/fila.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { reconciliar } from './fila'
import type { PendenciaDoPainel, SessaoDoDia } from './queries'

const sessao = (over: Partial<SessaoDoDia>): SessaoDoDia => ({
  consultaId: 'c1',
  dtConsulta: '2026-09-28T10:00:00',
  pacienteId: 'p1',
  pacienteNome: 'Paciente Um',
  status: 'AGENDADA',
  ...over,
})

const pendencia = (over: Partial<PendenciaDoPainel>): PendenciaDoPainel => ({
  consultaId: 'c1',
  dtConsulta: '2026-09-28T10:00:00',
  pacienteNome: 'Paciente Um',
  tipo: 'SEM_STATUS',
  ...over,
})

describe('reconciliar', () => {
  it('a sessao agendada entra na fila, a realizada desce para as resolvidas', () => {
    const r = reconciliar(
      [
        sessao({ consultaId: 'a', status: 'AGENDADA' }),
        sessao({ consultaId: 'b', status: 'REALIZADA' }),
      ],
      [],
    )
    expect(r.fila.map((i) => i.consultaId)).toEqual(['a'])
    expect(r.resolvidas.map((s) => s.consultaId)).toEqual(['b'])
  })

  it('a mesma consulta nos dois arrays vira UMA linha', () => {
    const r = reconciliar(
      [sessao({ consultaId: 'a', status: 'REALIZADA' })],
      [pendencia({ consultaId: 'a', tipo: 'SEM_ANOTACAO' })],
    )
    expect(r.fila).toHaveLength(1)
    expect(r.resolvidas).toHaveLength(0)
  })

  it('no empate, a pendencia decide a acao e a sessao empresta o pacienteId', () => {
    const r = reconciliar(
      [sessao({ consultaId: 'a', status: 'REALIZADA', pacienteId: 'p9' })],
      [pendencia({ consultaId: 'a', tipo: 'SEM_ANOTACAO' })],
    )
    expect(r.fila[0].tipo).toBe('SEM_ANOTACAO')
    expect(r.fila[0].pacienteId).toBe('p9')
  })

  it('pendencia de dia anterior nao tem par, entra na fila sem pacienteId', () => {
    const r = reconciliar(
      [],
      [pendencia({ consultaId: 'velha', dtConsulta: '2026-09-27T08:00:00' })],
    )
    expect(r.fila).toHaveLength(1)
    expect(r.fila[0].pacienteId).toBeNull()
  })

  it('a fila sai ordenada por hora, misturando as duas origens', () => {
    const r = reconciliar(
      [sessao({ consultaId: 'tarde', dtConsulta: '2026-09-28T16:00:00' })],
      [pendencia({ consultaId: 'cedo', dtConsulta: '2026-09-28T08:00:00' })],
    )
    expect(r.fila.map((i) => i.consultaId)).toEqual(['cedo', 'tarde'])
  })

  it('nenhuma consulta some: toda entrada aparece na fila OU nas resolvidas, nunca nas duas', () => {
    const sessoes = [
      sessao({ consultaId: 'a', status: 'AGENDADA' }),
      sessao({ consultaId: 'b', status: 'REALIZADA' }),
      sessao({ consultaId: 'c', status: 'FALTOU' }),
    ]
    const pendencias = [
      pendencia({ consultaId: 'b', tipo: 'SEM_ANOTACAO' }),
      pendencia({ consultaId: 'z', dtConsulta: '2026-09-27T09:00:00' }),
    ]
    const r = reconciliar(sessoes, pendencias)

    const naFila = new Set(r.fila.map((i) => i.consultaId))
    const resolvidas = new Set(r.resolvidas.map((s) => s.consultaId))

    expect([...naFila].filter((id) => resolvidas.has(id))).toEqual([])
    expect(new Set([...naFila, ...resolvidas])).toEqual(new Set(['a', 'b', 'c', 'z']))
  })
})
```

O último teste é o que realmente importa: ele afirma **conservação**. Toda consulta que entrou aparece exatamente uma vez na saída. É a rede contra o modo de falha desta fatia — perder uma linha na fusão sem que nada quebre.

- [ ] **Step 2: Rodar e ver falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/hoje/fila.test.ts
```

Esperado: todos falham com `Failed to resolve import "./fila"`.

- [ ] **Step 3: Escrever o reconciliador**

Crie `frontend/src/telas/hoje/fila.ts`:

```ts
import type { PendenciaDoPainel, SessaoDoDia } from './queries'

export type ItemDaFila = {
  consultaId: string
  dtConsulta: string
  pacienteNome: string
  pacienteId: string | null
  tipo: PendenciaDoPainel['tipo'] | null
  status: SessaoDoDia['status'] | null
}

export type PainelReconciliado = {
  fila: ItemDaFila[]
  resolvidas: SessaoDoDia[]
}

export function reconciliar(
  sessoes: SessaoDoDia[],
  pendencias: PendenciaDoPainel[],
): PainelReconciliado {
  const porId = new Map<string, ItemDaFila>()

  for (const s of sessoes) {
    if (s.status !== 'AGENDADA') continue
    porId.set(s.consultaId, {
      consultaId: s.consultaId,
      dtConsulta: s.dtConsulta,
      pacienteNome: s.pacienteNome,
      pacienteId: s.pacienteId,
      tipo: null,
      status: s.status,
    })
  }

  const sessaoPorId = new Map(sessoes.map((s) => [s.consultaId, s]))

  for (const p of pendencias) {
    const par = sessaoPorId.get(p.consultaId)
    porId.set(p.consultaId, {
      consultaId: p.consultaId,
      dtConsulta: p.dtConsulta,
      pacienteNome: p.pacienteNome,
      pacienteId: par ? par.pacienteId : null,
      tipo: p.tipo,
      status: par ? par.status : null,
    })
  }

  const fila = [...porId.values()].sort((a, b) => a.dtConsulta.localeCompare(b.dtConsulta))
  const resolvidas = sessoes.filter((s) => !porId.has(s.consultaId))

  return { fila, resolvidas }
}
```

Três decisões dentro dessas trinta linhas, todas deliberadas:

`localeCompare` e não `new Date(...).getTime()`: `dtConsulta` é `yyyy-MM-dd'T'HH:mm:ss` de largura fixa, então a ordem lexicográfica **é** a ordem cronológica, sem construir um `Date` por comparação e sem depender do fuso do navegador para ordenar.

O laço de pendências roda **depois** do de sessões e sobrescreve: é a D3.2 implementada pela ordem, não por um `if`.

`resolvidas` é derivada por exclusão (`!porId.has`), não por filtro de status. Assim uma sessão `REALIZADA` que tem pendência não aparece nos dois lugares, e o teste de conservação passa por construção.

- [ ] **Step 4: Rodar e ver passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/hoje/fila.test.ts && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 6 passando, `tsc` exit 0. Nenhum arquivo de tela mudou ainda — a suíte inteira continua verde.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/telas/hoje/fila.ts frontend/src/telas/hoje/fila.test.ts
git commit -m "feat(hoje): a reconciliacao das duas listas vira funcao pura"
```

---

### Task 2: A Hoje passa a mostrar uma fila e uma lista

Agora a tela consome o reconciliador. Pendências e sessões agendadas viram **"Precisa de você"** no topo; as resolvidas descem para uma lista compacta.

**Files:**
- Modify: `frontend/src/telas/hoje/HojePage.tsx`
- Modify: `frontend/src/telas/hoje/HojePage.test.tsx`
- Modify: `frontend/src/estilo/estilo.css`

**Interfaces:**
- Consome: `reconciliar` e `ItemDaFila` de `./fila`.

- [ ] **Step 1: Ler o que existe antes de mexer**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/hoje
```

Anote quantos passam. Leia `HojePage.tsx` inteiro. Você vai **preservar**: o componente `Nome` (e o docstring que explica por que ele vive fora do corpo), `QuantoFalta`, `frasePendente`, `hora`, o tratamento de erro do `marcar` e os dois `aria-label` `Proxima sessao` e `Sessoes de hoje`.

- [ ] **Step 2: Escrever os testes que falham**

Acrescente a `HojePage.test.tsx`:

```tsx
it('a fila junta pendencia e sessao agendada, ordenadas por hora', async () => {
  comPainel()
  renderizar(<HojePage />)
  const fila = await screen.findByRole('region', { name: /precisa de voce/i })
  const linhas = within(fila).getAllByRole('listitem')
  expect(linhas.length).toBeGreaterThan(1)
})

it('a sessao ja resolvida nao fica na fila', async () => {
  comPainel()
  renderizar(<HojePage />)
  const fila = await screen.findByRole('region', { name: /precisa de voce/i })
  expect(within(fila).queryByText(/REALIZADA/)).toBeNull()
})

it('a linha marcavel continua marcavel a partir da fila', async () => {
  comPainel()
  renderizar(<HojePage />)
  const fila = await screen.findByRole('region', { name: /precisa de voce/i })
  expect(within(fila).getAllByRole('button', { name: /realizada/i }).length).toBeGreaterThan(0)
})
```

Use o helper `comPainel()` que o arquivo já define. Se ele não produzir uma fixture com pelo menos uma pendência **e** uma sessão agendada, ajuste a fixture — não o teste.

**Os testes existentes que quebram são estes, e cada um tem um destino:**

| Teste | O que fazer |
|---|---|
| `lista o dia em ordem, com o status de cada sessao` | Reescrever para a lista de resolvidas. O nome fica. |
| `marca Realizada em um clique...` | Passa a buscar a linha dentro da região `Precisa de voce`. |
| `marcar Realizada falha (409)...` | Idem. |
| `mostra a faixa de pendencias` | Passa a buscar em `Precisa de voce`. **Não renomeie** — o nome é registro do que a tela pretendia. |
| `nao rotula a pendencia como 'sem status'` | Idem. |
| `escreve a frase certa para cada especie de pendencia` | Idem. |
| `dia 1 do psicologo...` | O estado vazio agora depende de fila **e** resolvidas estarem vazias. |

- [ ] **Step 3: Rodar e ver falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/hoje/HojePage.test.tsx
```

Esperado: os três novos falham (não existe região `Precisa de voce`) e os sete da tabela ainda passam, porque a tela ainda é a antiga.

- [ ] **Step 4: Reescrever o corpo da `HojePage`**

Substitua as duas `<section>` de sessões e pendências por:

```tsx
{fila.length === 0 && resolvidas.length === 0 ? (
  <EstadoVazio
    titulo="Nenhuma sessao hoje"
    acao={
      <Link className="botao secundario" to="/agenda">
        Ver a agenda
      </Link>
    }
  />
) : (
  <>
    {fila.length > 0 && (
      <section aria-label="Precisa de voce">
        <h2 className="titulo-secao">
          Precisa de voce
          <span className="contador-fila">{fila.length}</span>
        </h2>
        <ul className="lista-sessoes cartao">
          {fila.map((item) => (
            <LinhaDaFila
              key={item.consultaId}
              item={item}
              privacidade={privacidade}
              aoMarcar={(status) => marcar.mutate({ id: item.consultaId, status })}
              erro={
                marcar.isError && marcar.variables?.id === item.consultaId ? marcar.error : null
              }
            />
          ))}
        </ul>
      </section>
    )}

    {resolvidas.length > 0 && (
      <section aria-label="Sessoes de hoje">
        <h2 className="titulo-secao">Sessoes de hoje</h2>
        <ul className="lista-resolvidas cartao">
          {resolvidas.map((s) => (
            <li className="linha-resolvida" key={s.consultaId} aria-label={`Sessao de ${s.pacienteNome}`}>
              <span className="hora">{hora(s.dtConsulta)}</span>
              <Nome privacidade={privacidade}>{s.pacienteNome}</Nome>
              <Chip variante={s.status === 'REALIZADA' ? 'realizada' : 'faltou'}>{s.status}</Chip>
            </li>
          ))}
        </ul>
      </section>
    )}
  </>
)}
```

Acima do `return`, depois dos guards de loading/erro:

```tsx
const { fila, resolvidas } = reconciliar(data.sessoes, data.pendencias)
```

E `LinhaDaSessao` vira `LinhaDaFila`, consumindo `ItemDaFila`:

```tsx
function LinhaDaFila({
  item,
  privacidade,
  aoMarcar,
  erro,
}: {
  item: ItemDaFila
  privacidade: boolean
  aoMarcar: (status: 'REALIZADA' | 'FALTOU') => void
  erro: Error | null
}) {
  return (
    <li className="linha-pendencia" aria-label={`Sessao de ${item.pacienteNome}`}>
      <span>
        <span className="hora">{hora(item.dtConsulta)}</span>{' '}
        <Nome privacidade={privacidade}>{item.pacienteNome}</Nome>
        {item.tipo && <span className="acao-pedida"> · {frasePendente(item.tipo)}</span>}
      </span>
      <span className="acoes-linha">
        {item.status === 'AGENDADA' && (
          <>
            <Botao variante="secundario" className="mini" onClick={() => aoMarcar('REALIZADA')}>
              Realizada
            </Botao>
            <Botao variante="secundario" className="mini" onClick={() => aoMarcar('FALTOU')}>
              Faltou
            </Botao>
          </>
        )}
        {item.pacienteId && (
          <Link className="botao secundario mini" to={`/pacientes/${item.pacienteId}`}>
            Abrir a ficha
          </Link>
        )}
      </span>
      {erro && (
        <p className="anuncio" role="alert">
          {erro instanceof ErroDaApi
            ? erro.mensagem
            : 'Nao foi possivel completar a acao. Tente de novo.'}
        </p>
      )}
    </li>
  )
}
```

Note o que **não** mudou: `aria-label={`Sessao de ${nome}`}` é o mesmo de antes, então os testes que buscam `listitem` por esse nome continuam valendo. O chip de status **sai** da linha da fila — quem está na fila precisa de ação, e repetir "AGENDADA" ao lado de dois botões que dizem o que fazer é ruído. O status continua visível na lista de resolvidas, que é onde ele informa.

A `frasePendente` só renderiza quando há `tipo` — uma sessão agendada futura não tem pendência e não ganha frase.

O `Abrir a ficha` só renderiza com `pacienteId` — é a D3.3. As duas strings novas são `Precisa de voce` (autorizada pela §4.2, que a nomeia) e `Abrir a ficha`, **copiada verbatim** do cartão de destaque que já a usa.

Importe `reconciliar` e o tipo `ItemDaFila` de `./fila`.

- [ ] **Step 5: Rodar e consertar os sete**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/hoje/HojePage.test.tsx
```

Trabalhe a tabela do Step 2 um teste por vez. **Nenhum deles pode ser resolvido buscando por `className`** — se a única saída parecer ser `container.querySelector('.fila-precisa')`, pare e reporte; significa que a região não está alcançável por rótulo, e isso é defeito da implementação, não do teste.

- [ ] **Step 6: Escrever o CSS**

Acrescente a `estilo.css`, depois do bloco `.linha-pendencia`:

```css
.contador-fila {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 5px;
  border-radius: var(--raio-cheio);
  background: var(--alerta-fundo); color: var(--alerta-tinta);
  font-size: var(--t-legenda-tam);
  font-weight: var(--peso-forte);
  font-variant-numeric: tabular-nums;
}
.acao-pedida { color: var(--tinta-2); }
.lista-resolvidas {
  list-style: none; margin: 0 0 var(--gap-8); padding: 0;
  display: flex; flex-direction: column; gap: var(--gap-0);
}
.linha-resolvida {
  display: grid; grid-template-columns: auto 1fr auto;
  gap: var(--gap-3); align-items: center;
  padding: var(--gap-1) var(--gap-4);
  color: var(--tinta-2);
  font-size: var(--t-segundo-tam); line-height: var(--t-segundo-alt);
}
```

A lista resolvida usa `--gap-0` e `--gap-1` onde a fila usa `--gap-1` e `--gap-2`: menos respiro é o que a faz ler como secundária, sem precisar encolher a fonte a ponto de ficar ilegível.

O `.contador-fila` é o mesmo desenho do `.contador-pendencias` que a Fatia 2 pôs na Ficha. **Não unifique os dois nesta tarefa** — são telas diferentes, e juntá-los agora acopla duas mudanças que precisam ser revisadas em separado. Se ao fim da fatia ainda parecerem iguais, o Plano 4 funde.

- [ ] **Step 7: Rodar a suíte inteira**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 25 arquivos, ~121 testes, 0 falhas, `tsc` exit 0.

- [ ] **Step 8: Rodar o e2e — gate desta tarefa**

```bash
cd backend && bash debug.sh
```

Em outro terminal, com a `:8080` respondendo:

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm run e2e
```

Esperado: 2 passando. O `fluxo-de-fumaca` atravessa a Hoje — é a D3.6. **Se ele quebrar, a causa é esta tarefa até prova em contrário**, porque as Fatias 1 e 2 o deixaram verde.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/telas/hoje/HojePage.tsx frontend/src/telas/hoje/HojePage.test.tsx frontend/src/estilo/estilo.css
git commit -m "feat(hoje): pendencia e sessao viram uma fila unica ordenada por hora"
```

---

### Task 3: O cartão da próxima sessão na linguagem nova

`.destaque` é a última regra do app ainda no tratamento pré-spec. Veio marcada desde a Fatia 1.

**Files:**
- Modify: `frontend/src/estilo/estilo.css` (bloco `.destaque`)

- [ ] **Step 1: Localizar o bloco pelo seletor**

```bash
cd frontend && grep -n -A5 "^\.destaque" src/estilo/estilo.css
```

Esperado, hoje: `background: var(--acento-suave); border-left: 3px solid var(--acento);` mais o flex.

- [ ] **Step 2: Substituir pelo tratamento da §6.5**

```css
.destaque {
  background: var(--superficie-1);
  box-shadow: var(--sombra-cartao), inset 3px 0 0 var(--acento);
  margin-bottom: var(--gap-6);
  display: flex; flex-direction: column; align-items: flex-start; gap: var(--gap-1);
}
```

O spec de linguagem §6.5 é explícito sobre o porquê: a barra à esquerda vira **anel interno**, não `border-left`, para não deslocar o conteúdo em 3px. E o fundo volta a `--superficie-1` — o `--acento-suave` tingia o cartão inteiro, o que na linguagem nova compete com a barra em vez de reforçá-la.

O comentário em `Cartao.tsx:5` diz "fundo suave + barra a esquerda" e fica **falso** com esta mudança. Reescreva-o para descrever o tratamento novo. Não acrescente comentário onde não havia.

- [ ] **Step 3: Rodar a suíte**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: sem mudança de contagem. Nenhum teste afirma cor — se algum quebrar, ele estava acoplado a estilo e isso é o achado.

- [ ] **Step 4: Conferir contra o espécime**

Abra `mock-v2.html` do scratchpad, aba Hoje, e compare o cartão da próxima sessão.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/estilo/estilo.css frontend/src/ui/Cartao.tsx
git commit -m "feat(hoje): o cartao da proxima sessao ganha a barra como anel interno"
```

---

### Task 4: `--separador` volta aos dois usos permitidos

A revisão do conjunto da Fatia 2 achou dois consumidores fora da regra e confirmou por `git show` que ambos são anteriores àquela branch. São os últimos.

**Files:**
- Modify: `frontend/src/estilo/estilo.css` (`.lateral .botao.secundario`, `.chip`)

- [ ] **Step 1: Confirmar que ainda são só dois**

```bash
cd frontend && grep -n "var(--separador)" src/estilo/*.css
```

Esperado: a grade da agenda (`.agenda-grade`), os controles de entrada em `dialogo.css` e `.busca` — **legítimos** — mais `.lateral .botao.secundario` e `.chip`. Se aparecer um sexto consumidor, pare e reporte: alguma tarefa o introduziu e isso precisa de ruling antes de apagar.

- [ ] **Step 2: Trocar os dois**

Em `.lateral .botao.secundario`, `--separador` vira `--acento-contorno`: o botão está sobre vidro, e o contorno existe para ele não sumir contra o fundo translúcido — é a mesma função que `--acento-contorno` já cumpre nos outros controles sobre cor.

Em `.chip`, o anel vira o contorno da própria variante — `--realizada-contorno`, `--faltou-contorno`, `--alerta-contorno`, `--neutro-contorno` — seguindo o bloco que a Tarefa 2 da Fatia 1 já escreveu para os fundos e as tintas. O chip neutro usa `--neutro-contorno`.

- [ ] **Step 3: Rodar a suíte e conferir a regra**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
grep -n "var(--separador)" src/estilo/*.css
```

Esperado: a suíte verde, e o `grep` devolvendo só a grade da agenda e os anéis internos de controle de entrada.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/estilo/estilo.css
git commit -m "fix(estilo): o separador volta a existir so na grade e no anel de campo"
```

---

## Fora de escopo, de propósito

- **Colapso de hora vazia na Agenda** — D7 do spec. Volta quando existir o campo de expediente e a tela Ajustes.
- **Dark mode implementado e testado** — D4. O par existe, o gatilho é opt-in, e ligá-lo de verdade é fatia própria com conferência visual das cinco telas em dois temas.
- **Marcador de horário atual na Agenda** (`--agora`) — o token existe, o elemento não. Funcionalidade nova.
- **Scroll edge** (§2.4) — não há cabeçalho fixo rolável.
- **Unificar `.contador-fila` e `.contador-pendencias`** — Tarefa 2, Step 6. Só depois de as duas telas estarem prontas.
- **Os comentários que descrevem a regra morta "ação é forma"** — D2.7 do Plano 2, decidida pelo dono: ficam.
- **`DialogoDeConsulta` falando HTTP direto do componente** — defeito pré-existente, fora de refatoração visual.
- **A fonte Inter carregada e não usada** em `frontend/index.html`.
- **`Usuario e/ou senha incorretos!`** — a mensagem do 401 viola a convenção da casa; trocar texto de erro de autenticação não é escopo visual.

---

## Riscos

| # | Risco | Mitigação |
|---|---|---|
| 1 | Uma consulta some na fusão sem quebrar teste nenhum | O teste de conservação da Tarefa 1 — toda entrada aparece exatamente uma vez na saída |
| 2 | A mesma consulta aparece duas vezes na fila | Deduplicação por `consultaId` num `Map`, e o teste que afirma `fila).toHaveLength(1)` |
| 3 | O e2e quebra porque o `fluxo-de-fumaca` atravessa a Hoje | Tarefa 2 Step 8 é gate, não formalidade (D3.6) |
| 4 | Um teste de tela é "consertado" buscando por `className` | Tarefa 2 Step 5 proíbe explicitamente e manda reportar |
| 5 | Pendência sem `pacienteId` quebra o link da ficha | D3.3: o link só renderiza com `pacienteId`; teste de reconciliação cobre o caso órfão |

---

**Para avaliação técnica:** a tela do dia deixa de mostrar duas listas que se sobrepõem e passa a mostrar uma fila do que precisa de ação, ordenada por hora, com o que já foi resolvido numa lista discreta abaixo. A fusão é feita por uma função pura testada à parte, com um teste que prova que nenhuma consulta se perde no caminho. Nenhuma chamada nova de API, nenhum texto que o usuário lê muda de palavra, e a estrutura que leitor de tela e teste percorrem continua alcançável por rótulo escrito.
