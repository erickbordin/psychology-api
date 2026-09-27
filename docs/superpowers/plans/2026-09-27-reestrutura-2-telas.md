# Reestrutura do frontend — Fatia 2: as telas

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reestruturar Login, Pacientes, Agenda e Ficha na linguagem nova, e fazer o modo privacidade cobrir Pacientes de verdade.

**Architecture:** A Fatia 1 entregou a fundação — tokens, chip, controles, shell, e o `PrivacidadeProvider` no shell. Esta fatia gasta essa fundação nas quatro telas que não dependem de reconciliar dois modelos de dado no cliente. A Hoje fica de fora: ela é a D6, precisa de plano próprio. Nenhuma tela ganha chamada de API nova — os quatro campos que a lista de Pacientes passa a mostrar e o `status` que o bloco da Agenda passa a carregar já vêm no payload de hoje, verificado campo a campo antes de escrever este plano.

**Tech Stack:** React 19, TypeScript, Vite, Tailwind (só preflight), Radix Dialog, `@tanstack/react-query`, `react-hook-form` + `zod` v4, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-27-reestrutura-frontend-design.md` (decisões e invariantes) e `docs/superpowers/specs/2026-09-27-linguagem-apple.md` (valores).

**Base:** branch `reestrutura/fundacao` mergeada. Baseline verificado: 23 arquivos de teste, 101 testes, 0 falhas, `tsc --noEmit` exit 0.

## Global Constraints

- **NUNCA comente código.** Nenhum `//`, `/* */` ou docstring novo em arquivo de código. Comentário pré-existente **não se remove** — só não se cria. Violação = achado Importante.
- **Copy em português intocada.** Nenhuma string visível ao usuário muda de palavra. Pode mudar de lugar. As duas adições de texto autorizadas por spec estão nomeadas nas Tarefas 3 e 6; nenhuma outra.
- **Semântica de acessibilidade é invariante (D8).** Nenhum `role`, `scope`, `aria-label`, `aria-current`, `aria-pressed` ou estrutura de lista/tabela muda. A Agenda continua `<table role="grid">` com `<th scope="col">` de dia, `<th scope="row">` de hora e `role="gridcell"` nas células. Pacientes continua `<ul>`. Login continua `<form>` com `<label>` reais.
- **Nenhuma informação removida.** Pode rebaixar, agrupar ou revelar sob demanda; não pode sumir.
- **Testes navegam por rótulo escrito, nunca por `className`, e nunca esperam animação.** Se um teste precisar de classe ou de `waitFor` numa transição, o problema é o teste.
- **Dark mode não é implementado.** Nenhum bloco `@media (prefers-color-scheme: dark)` ganha consumidor.
- **Nenhum token inventado.** Todo `var(--x)` escrito já existe em `frontend/src/estilo/tokens.css`. Confira antes de escrever — `var()` com nome inexistente falha em silêncio.
- **`--separador` só em dois lugares:** a grade da agenda e o anel interno de controle de entrada (`inset 0 0 0 1px`). Em qualquer outro lugar é erro (spec de linguagem §2.4).
- **A suíte fica verde e `npx tsc --noEmit` sai 0** ao fim de cada tarefa.
- **Node:** o pin global é 9.11.2 e quebra `npx`. Prefixe: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run`.
- **Referência visual:** os espécimes em `/tmp/claude-1001/-opt-syonet-erick/75cf2876-6278-4513-98b0-f4f1dfb77733/scratchpad/` — `mock-login.html`, `mock-pacientes.html`, `mock-agenda.html`, `mock-v2.html`. Ordem de autoridade quando divergirem: decisão vence espécime, espécime vence token.

---

## Estrutura de arquivos

| Arquivo | Responsabilidade | Tarefa |
|---|---|---|
| `src/estilo/estilo.css` | folha de telas — recebe as regras de linha de lista, grade e ficha | 1, 3, 4, 5, 6, 7 |
| `src/estilo/dialogo.css` | camada de diálogo e de campo | 2 |
| `src/telas/pacientes/PacientesPage.tsx` | a linha passa a decidir | 3, 4 |
| `src/ui/Layout.tsx` | hospeda o controle de privacidade | 4 |
| `src/telas/hoje/HojePage.tsx` | perde o controle de privacidade (só o controle) | 4 |
| `src/telas/agenda/AgendaPage.tsx` | o bloco ocupado carrega o estado | 5 |
| `src/telas/ficha/FichaPage.tsx` | pendência vira faixa, prontuário ocupa a largura | 6 |
| `src/ui/EstadoVazio.tsx` | forma do vazio, §6.8 | 7 |

---

## Decisões tomadas neste plano

Registradas aqui porque um implementador de tarefa única não tem como chegar nelas sozinho.

**D2.1 — O link da linha de Pacientes envolve nome e metadado; o chip fica fora dele.**
Alternativa recusada: envolver a linha inteira, chip incluído. O chip é sinal de status, não destino de navegação; dentro do link ele vira parte do nome acessível e o leitor de tela anuncia "Helena Vaz, 34 anos, ..., Aviso nao autorizado, link", misturando o que a linha *é* com para onde ela *leva*. Segunda alternativa recusada: link só no nome, metadado fora — deixa 80% da linha morta ao clique, pior que hoje.

**D2.2 — A Agenda deriva o estado do `status` que já vem no payload.**
`ConsultaDaAgenda` é `Required<Pick<ConsultaResponseDTO, 'id' | 'pacienteNome' | 'dtConsulta' | 'status'>>` (`src/telas/agenda/queries.ts:50-52`) e `status` é `"REALIZADA" | "AGENDADA" | "CANCELADA" | "FALTOU"` (`src/api/contrato.ts:417-418`). Hoje o campo é lido só para filtrar `CANCELADA` e nunca renderizado. Nenhuma query nova.

**D2.3 — O controle de privacidade sobe para o shell nesta fatia, não na Fatia 1.**
A Fatia 1 subiu o *estado*; o *controle* continuou dentro de `HojePage`. Com o estado compartilhado e o controle numa tela só, estar em Pacientes com alguém na sala significa não poder ligar o borrado sem navegar para a Hoje primeiro — o que anula a D5. Alternativa recusada: duplicar o controle em cada tela, que multiplica um toggle com `aria-pressed` por quatro e convida à dessincronia visual.

**D2.4 — Sob privacidade, em Pacientes, borram nome e a linha de metadado; o chip de consentimento fica nítido.**
Está no spec §4.4: "o chip de consentimento **fica visível**, porque é sinal de status e sem ele a tela borrada não serviria para nada". Alternativa recusada: borrar a linha inteira — transforma a tela num retângulo cinza sem uso.

**D2.5 — A Hoje fica fora desta fatia.** Ela é a D6 (fundir pendências e sessões numa fila única, reconciliando dois arrays distintos da API no cliente) e é a única parte que não é CSS. Vai para a Fatia 3, com plano próprio. Nesta fatia `HojePage` só perde o controle de privacidade (Tarefa 4) e nada mais.

**D2.6 — `.cartao.destaque` vai junto com a Hoje, não aqui.** Ele carregou da Fatia 1 como pendência; o bloco é o cartão da próxima sessão e só existe na Hoje. Corrigi-lo isolado agora entregaria meia tela na linguagem nova. Está no spec de linguagem §6.5 e vai para a Fatia 3 com o resto.

**D2.7 — Os comentários obsoletos ficam.** `Botao.tsx:7`, `Navegacao.tsx:14`, `estilo.css:1-3,81-84,199` e `dialogo.css:2,7` ainda descrevem a regra "ação é forma, não cor" do Calendar, que a D1 matou, e apontam para um bloco de comentário em `tokens.css` que a Fatia 1 apagou. A regra permanente do dono é não remover comentário existente, e nenhuma tarefa aqui reescreve esses arquivos inteiros. **Fica pendente, e é a única pendência deste plano que precisa de decisão do dono** — ver "Pergunta em aberto" no fim.

---

### Task 1: Login na linguagem nova

Única tela fora do shell. Nenhuma aposta estrutural (spec §4.6) — o `<form>`, os `<label>`, o `role="alert"` e os rótulos ficam byte-idênticos. Só a pele.

**Files:**
- Modify: `frontend/src/estilo/estilo.css:248-250` (`.tela-login`, `.cartao-login`)
- Modify: `frontend/src/estilo/estilo.css:78` (`.sub`), `:244` (`.anuncio`), `:242-243` (`.resumo-erros`)
- Test: `frontend/src/telas/login/LoginPage.test.tsx` (não muda — é regressão)

**Interfaces:**
- Consome: tokens de tipografia (`--t-pagina-*`, `--t-segundo-*`, `--t-legenda-*`), superfície (`--superficie-1`, `--fundo-base`), forma (`--raio-lg`), tinta (`--tinta`, `--tinta-2`, `--perigo`). Todos existem em `tokens.css`.
- Produz: nada que outra tarefa consuma.

- [ ] **Step 1: Rodar a suíte de Login antes de tocar em nada**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/login
```

Esperado: 5 testes passando. Estes cinco são a sua rede — nenhum deles pode mudar, e todos devem continuar passando ao fim. São eles: `nao envia com campo vazio, e diz o que falta`, `avisa que o e-mail esta mal formado antes de ir ao servidor`, `mostra a mensagem do SERVIDOR quando a credencial esta errada`, `login que da certo guarda o token e leva para o dia`, `a senha nao aparece na tela`.

- [ ] **Step 2: Reescrever o bloco de login em `estilo.css`**

Substitua as linhas 248-250 por:

```css
.tela-login {
  display: flex; align-items: center; justify-content: center;
  min-height: 100vh;
  padding: var(--gap-5);
  background: var(--fundo-base);
  box-sizing: border-box;
}
.cartao-login {
  width: 100%; max-width: 380px;
  display: flex; flex-direction: column; gap: var(--gap-5);
  box-sizing: border-box;
}
.cartao-login h1 {
  margin: 0;
  font-size: var(--t-pagina-tam);
  line-height: var(--t-pagina-alt);
  letter-spacing: var(--t-pagina-esp);
  font-weight: var(--peso-titulo);
  color: var(--tinta);
}
.cartao-login form { display: flex; flex-direction: column; gap: var(--gap-4); }
.cartao-login .botao { margin-top: var(--gap-2); }
```

O `.cartao-login` não repete `background`, `border-radius` nem `box-shadow`: ele já carrega a classe `cartao` no JSX (`LoginPage.tsx:14`), que a Tarefa 3 da Fatia 1 colocou em `--superficie-1` / `--raio-lg` / `--sombra-cartao`. Repetir aqui criaria duas fontes para o mesmo valor.

- [ ] **Step 3: Passar `.sub`, `.anuncio` e `.resumo-erros` para os tokens de tipografia**

Substitua a linha 78:

```css
.sub {
  color: var(--tinta-2);
  font-size: var(--t-segundo-tam);
  line-height: var(--t-segundo-alt);
  letter-spacing: var(--t-segundo-esp);
  margin: 0 0 var(--gap-6);
}
```

Substitua a linha 244:

```css
.anuncio {
  font-size: var(--t-legenda-tam);
  line-height: var(--t-legenda-alt);
  color: var(--perigo);
  min-height: 18px;
  margin: 0 0 var(--gap-3);
}
```

Substitua as linhas 242-243:

```css
.resumo-erros p { margin: 0 0 var(--gap-1); font-size: var(--t-legenda-tam); color: var(--perigo); font-weight: var(--peso-medio); }
.resumo-erros ul { margin: 0; padding-left: 18px; font-size: var(--t-legenda-tam); color: var(--perigo); }
```

`.sub`, `.anuncio` e `.resumo-erros` são usados por Pacientes, Ficha e Agenda também. Isso é intencional: são classes de texto compartilhadas, e passá-las para token de uma vez evita que cada tarefa seguinte as reescreva de novo com outro valor.

- [ ] **Step 4: Rodar a suíte inteira**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 23 arquivos / 101 testes / 0 falhas, `tsc` exit 0. Nenhum teste novo nesta tarefa — a tela não mudou de comportamento nem de estrutura, e um teste que afirmasse cor seria exatamente o teste acoplado a `className` que a constraint proíbe.

- [ ] **Step 5: Conferir contra o espécime**

Abra `mock-login.html` do scratchpad e compare lado a lado com a tela rodando. O que deve bater: o cartão centrado sobre `--fundo-base` sem contorno, o título em peso de título, os dois campos com anel interno em vez de borda, o botão primário retangular de 10px (não pílula).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/estilo/estilo.css
git commit -m "feat(login): a tela de entrada na linguagem nova"
```

---

### Task 2: Diálogo modal — a forma e a saída

O bloco `.dialogo` ficou na forma antiga durante a Fatia 1 porque subir só o raio entregaria meia mudança. Agora vai inteiro: forma, scrim por token, e a animação de saída, que só funciona com `@keyframes`.

**Por que `@keyframes` e não `transition`:** o `Presence` do Radix mantém `Dialog.Content` montado durante a saída **apenas** enquanto houver animação CSS rodando. Uma `transition` não segura a montagem — o nó sai do DOM antes de a transição terminar, e a saída simplesmente não aparece.

**Files:**
- Modify: `frontend/src/estilo/dialogo.css:9-14` (`.mascara`), `:17-25` (`.dialogo`), `:26-31` (`.dialogo-cabecalho`), `:33-36` (`.dialogo-corpo`), `:37-42` (`.dialogo-rodape`)
- Test: `frontend/src/telas/pacientes/DialogoDePaciente.test.tsx` e `frontend/src/telas/agenda/DialogoDeConsulta.test.tsx` (não mudam — são regressão)

**Interfaces:**
- Consome: `--fundo-fosco`, `--raio-xl`, `--sombra-dialogo`, `--superficie-1`, `--gap-3`, `--gap-5`, `--gap-6`, `--t-secao-*`, `--peso-forte`, `--dur-dialogo`, `--dur-saida`, `--curva-entrada`, `--curva-saida`, `--curva-suave`. Todos existem.
- Produz: nada que outra tarefa consuma.

- [ ] **Step 1: Confirmar que os dois diálogos usam as classes que você vai mexer**

```bash
cd frontend && grep -n "mascara\|className=\"dialogo" src/telas/pacientes/DialogoDePaciente.tsx src/telas/agenda/DialogoDeConsulta.tsx
```

Esperado: os dois arquivos com `<Dialog.Overlay className="mascara" />` e `<Dialog.Content aria-describedby={undefined} className="dialogo">`. Se algum divergir, pare e reporte — este plano assume os dois idênticos.

- [ ] **Step 2: Substituir o bloco de forma do diálogo**

Substitua as linhas 9-42 de `dialogo.css` por:

```css
.mascara {
  position: fixed; inset: 0; z-index: 10;
  background: var(--fundo-fosco);
  -webkit-backdrop-filter: blur(2px);
  backdrop-filter: blur(2px);
}
.dialogo {
  position: fixed; top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  z-index: 11;
  width: min(520px, calc(100vw - 2 * var(--gap-6)));
  max-height: min(86vh, 720px);
  box-sizing: border-box;
  display: flex; flex-direction: column;
  background: var(--superficie-1);
  border-radius: var(--raio-xl);
  box-shadow: var(--sombra-dialogo);
}
.dialogo-cabecalho {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--gap-5) var(--gap-5) var(--gap-3);
}
.dialogo-cabecalho-titulo {
  margin: 0;
  font-size: var(--t-secao-tam); line-height: var(--t-secao-alt);
  letter-spacing: var(--t-secao-esp); font-weight: var(--peso-forte);
}
.dialogo-corpo {
  padding: 0 var(--gap-5); overflow-y: auto;
  display: flex; flex-direction: column; gap: var(--gap-4);
}
.dialogo-rodape {
  display: flex; justify-content: flex-end; gap: var(--gap-3);
  padding: var(--gap-5);
  background: var(--superficie-1);
  border-radius: 0 0 var(--raio-xl) var(--raio-xl);
}
```

O `rgba(32, 33, 36, .6)` cravado no `.mascara` sai — era o último valor de cor escrito à mão fora de `tokens.css`. O `display: flex` do `.dialogo-cabecalho` não está no trecho do spec §6.7, mas é o que existe hoje e é o que mantém o título e o botão de fechar na mesma linha; removê-lo quebraria o cabeçalho sem que nada no spec peça isso.

- [ ] **Step 3: Escrever a animação de entrada e saída**

Acrescente ao fim de `dialogo.css`:

```css
@keyframes mascara-entra { from { opacity: 0 } to { opacity: 1 } }
@keyframes mascara-sai   { from { opacity: 1 } to { opacity: 0 } }

@keyframes dialogo-entra {
  from { opacity: 0; transform: translate(-50%, -48%) scale(0.965); filter: blur(4px); }
  to   { opacity: 1; transform: translate(-50%, -50%) scale(1);     filter: blur(0);   }
}
@keyframes dialogo-sai {
  from { opacity: 1; transform: translate(-50%, -50%) scale(1);     filter: blur(0);   }
  to   { opacity: 0; transform: translate(-50%, -49%) scale(0.975); filter: blur(3px); }
}

.mascara[data-state='open']   { animation: mascara-entra var(--dur-dialogo) var(--curva-saida); }
.mascara[data-state='closed'] { animation: mascara-sai   var(--dur-saida)   var(--curva-entrada); }

.dialogo[data-state='open'] {
  animation: dialogo-entra var(--dur-dialogo) var(--curva-suave);
  transform-origin: center;
}
.dialogo[data-state='closed'] {
  animation: dialogo-sai var(--dur-saida) var(--curva-entrada);
}
```

O `data-state` é emitido pelo próprio Radix; nenhum componente precisa mudar.

- [ ] **Step 4: Verificar que o kill-switch de movimento cobre isto**

```bash
cd frontend && grep -n -A6 "prefers-reduced-motion" src/estilo/tokens.css
```

Esperado: um bloco que zera as durações. Se as durações forem zeradas por token, estas animações duram 0s sob `prefers-reduced-motion` e nada mais precisa ser escrito. **Se o kill-switch não cobrir `animation`**, acrescente a `dialogo.css`:

```css
@media (prefers-reduced-motion: reduce) {
  .mascara[data-state], .dialogo[data-state] { animation-duration: 1ms; }
}
```

`1ms` e não `none`: com `none` o `Presence` do Radix desmonta na hora e o diálogo de saída pisca; com `1ms` a saída é instantânea aos olhos e o ciclo de montagem continua íntegro.

- [ ] **Step 5: Rodar a suíte**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 23 arquivos / 101 testes / 0 falhas, `tsc` exit 0. **Nenhum teste novo.** Um teste de animação seria exatamente o teste que espera animação, proibido pelas constraints. A verificação da saída é manual, no Step 6.

- [ ] **Step 6: Conferir a saída a olho**

Rode `npm run dev`, abra Pacientes, clique em "Cadastrar paciente", e feche com Esc. O diálogo deve encolher e subir ~1% enquanto desvanece, não sumir de um quadro para o outro. Se sumir instantâneo, o `@keyframes` de saída não está pegando — confira se o seletor `[data-state='closed']` está escrito exatamente assim.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/estilo/dialogo.css
git commit -m "feat(dialogo): forma e saida do modal na linguagem nova"
```

---

### Task 3: A linha de Pacientes passa a decidir

Hoje a linha é só um nome dentro de um `<li>`. Uma lista só de nomes não ajuda a escolher ninguém (spec §4.4). A linha ganha idade, contato e valor por sessão, e o chip de consentimento sobe da Ficha.

**Nenhum campo é inventado e nenhuma query muda.** Os quatro já vêm no `Paciente` que `usePacientes` devolve (`src/telas/pacientes/queries.ts:22-31`): `dataNascimento`, `telefone`, `email`, `valorSessao`, `consentimentoAvisoEm`.

**Files:**
- Modify: `frontend/src/telas/pacientes/PacientesPage.tsx:80-88`
- Modify: `frontend/src/estilo/estilo.css:148-154` (`.linha-pendencia`)
- Test: `frontend/src/telas/pacientes/PacientesPage.test.tsx`

**Interfaces:**
- Consome: o tipo `Paciente` de `queries.ts`; o componente `Chip` de `src/ui/Chip.tsx` com `Variante = 'realizada' | 'faltou' | 'alerta' | 'neutro'`.
- Produz: nada que outra tarefa consuma, exceto os nomes de classe `linha-paciente`, `linha-paciente-nome` e `linha-paciente-meta`, que a Tarefa 4 usa para decidir o que borra.

- [ ] **Step 1: Escrever os testes que falham**

Acrescente a `PacientesPage.test.tsx`, dentro do `describe` existente:

```tsx
it('a linha mostra idade, contato e valor, nao so o nome', async () => {
  renderizar(<PacientesPage />)
  const linha = (await screen.findByText('Helena Vaz')).closest('li')!
  expect(within(linha).getByText(/anos/)).toBeInTheDocument()
  expect(within(linha).getByText(/180/)).toBeInTheDocument()
})

it('o chip de consentimento fica na linha, fora do link', async () => {
  renderizar(<PacientesPage />)
  const linha = (await screen.findByText('Helena Vaz')).closest('li')!
  const chip = within(linha).getByText(/aviso (nao )?autorizado/i)
  expect(chip).toBeInTheDocument()
  expect(chip.closest('a')).toBeNull()
})

it('paciente sem telefone e sem valor nao mostra separador orfao', async () => {
  servidor.use(
    http.get('*/pacientes', () =>
      HttpResponse.json([{ ...HELENA, telefone: null, email: null, valorSessao: null }]),
    ),
  )
  renderizar(<PacientesPage />)
  const linha = (await screen.findByText('Helena Vaz')).closest('li')!
  expect(within(linha).queryByText(/·\s*·/)).toBeNull()
  expect(within(linha).textContent).not.toMatch(/·\s*$/)
})
```

Garanta que `within` está importado de `@testing-library/react` e que `http`/`HttpResponse` vêm de `msw`, seguindo o que os outros testes do arquivo já importam. `HELENA` é a fixture que o arquivo já define.

O terceiro teste é o que impede a armadilha real desta tarefa: uma linha de metadado montada com `join(' · ')` sobre campos nuláveis produz `34 anos · · ` quando o paciente não tem telefone nem valor.

- [ ] **Step 2: Rodar e ver falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/pacientes/PacientesPage.test.tsx
```

Esperado: os três novos falham (`Unable to find an element with the text: /anos/`), os sete existentes passam.

- [ ] **Step 3: Escrever os dois helpers e a linha**

Em `PacientesPage.tsx`, acima do componente:

```tsx
function idadeEmAnos(nascimento: string | null): number | null {
  if (!nascimento) return null
  const nasc = new Date(nascimento)
  if (Number.isNaN(nasc.getTime())) return null
  const hoje = new Date()
  let anos = hoje.getFullYear() - nasc.getFullYear()
  const mes = hoje.getMonth() - nasc.getMonth()
  if (mes < 0 || (mes === 0 && hoje.getDate() < nasc.getDate())) anos -= 1
  return anos >= 0 ? anos : null
}

function linhaDeMeta(p: Paciente): string {
  const idade = idadeEmAnos(p.dataNascimento)
  const partes = [
    idade === null ? null : `${idade} anos`,
    p.telefone ?? p.email,
    p.valorSessao === null
      ? null
      : p.valorSessao.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }),
  ]
  return partes.filter(Boolean).join(' · ')
}
```

`filter(Boolean)` antes do `join` é o que resolve o separador órfão — descarta os nulos em vez de juntá-los.

Substitua as linhas 80-88 por:

```tsx
{data && data.length > 0 && (
  <ul className="cartao lista-sessoes">
    {data.map((p) => {
      const meta = linhaDeMeta(p)
      return (
        <li className="linha-pendencia linha-paciente" key={p.idPaciente}>
          <Link to={`/pacientes/${p.idPaciente}`}>
            <span className="linha-paciente-nome">{p.nome}</span>
            {meta && <span className="linha-paciente-meta">{meta}</span>}
          </Link>
          <Chip variante={p.consentimentoAvisoEm ? 'neutro' : 'alerta'}>
            {p.consentimentoAvisoEm ? 'Aviso autorizado' : 'Aviso nao autorizado'}
          </Chip>
        </li>
      )
    })}
  </ul>
)}
```

Importe `Chip` de `../../ui/Chip` e o tipo `Paciente` de `./queries`, seguindo o estilo de import que o arquivo já usa.

As duas strings do chip são copiadas **verbatim** de `FichaPage.tsx:83-85` — é a mesma copy, mudando de lugar, que é o que o invariante permite. A Ficha **continua** mostrando o dela; o chip não migra, ele passa a existir nos dois lugares, porque nenhuma informação pode ser removida.

- [ ] **Step 4: Rodar e ver passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/pacientes/PacientesPage.test.tsx
```

Esperado: 10 passando. Repare no teste pré-existente `cada paciente leva para a ficha dele`, que faz `getByRole('link', { name: /helena vaz/i })` — a regex não é ancorada, então casa como substring mesmo com o metadado dentro do link. Se ele falhar, a causa é outra e deve ser investigada, não contornada mudando o teste.

- [ ] **Step 5: Escrever o CSS da linha**

O `.linha-pendencia` já é `grid-template-columns: 1fr auto` com a coluna `auto` vazia — ela existia esperando exatamente este conteúdo. Acrescente depois do bloco dele em `estilo.css`:

```css
.linha-paciente > a {
  display: flex; flex-direction: column; gap: var(--gap-0);
  text-decoration: none; color: inherit; min-width: 0;
}
.linha-paciente-nome {
  font-size: var(--t-corpo-tam); line-height: var(--t-corpo-alt);
  font-weight: var(--peso-medio); color: var(--tinta);
}
.linha-paciente-meta {
  font-size: var(--t-segundo-tam); line-height: var(--t-segundo-alt);
  color: var(--tinta-2);
  font-variant-numeric: tabular-nums;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
```

`min-width: 0` no link é o que permite ao `text-overflow: ellipsis` funcionar dentro de um grid item — sem ele o conteúdo empurra a coluna e o chip sai da tela.

- [ ] **Step 6: Rodar a suíte inteira e conferir o espécime**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 23 arquivos / 104 testes / 0 falhas, `tsc` exit 0. Depois abra `mock-pacientes.html` do scratchpad e compare a densidade da linha.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/telas/pacientes/PacientesPage.tsx frontend/src/telas/pacientes/PacientesPage.test.tsx frontend/src/estilo/estilo.css
git commit -m "feat(pacientes): a linha mostra o que decide, nao so o nome"
```

---

### Task 4: Privacidade cobre Pacientes

A D5 do spec diz que o modo privacidade sobe para o shell e passa a cobrir Pacientes. A Fatia 1 subiu o estado; falta subir o **controle** e aplicar o borrado na tela que a Tarefa 3 acabou de encher de dado financeiro e de contato.

**Isto é mudança de comportamento**, não de aparência, e por isso leva teste próprio.

**Files:**
- Modify: `frontend/src/ui/Layout.tsx` (ganha o controle)
- Modify: `frontend/src/telas/hoje/HojePage.tsx` (perde o controle, mantém o borrado)
- Modify: `frontend/src/telas/pacientes/PacientesPage.tsx` (passa a borrar)
- Modify: `frontend/src/estilo/estilo.css:167` (`[data-borrado='true']`)
- Modify: `frontend/src/privacidade/PrivacidadeProvider.tsx` (prop `inicial?: boolean`)
- Modify: `frontend/src/teste/renderizar.tsx` (opção `privacidadeLigada?: boolean`)
- Test: `frontend/src/telas/pacientes/PacientesPage.test.tsx`, `frontend/src/ui/Layout.test.tsx`

**Interfaces:**
- Consome: `usePrivacidade(): { privacidade: boolean; alternar: () => void }` de `src/privacidade/usePrivacidade.ts`, entregue pela Tarefa 6 da Fatia 1; as classes `linha-paciente-nome` e `linha-paciente-meta` da Tarefa 3.
- Produz: `PrivacidadeProvider({ children, inicial?: boolean })` e `renderizar(ui, { rota?, cliente?, privacidadeLigada? })`. Ambos com default que preserva o comportamento atual.

- [ ] **Step 1: Ler o controle como ele existe hoje**

```bash
cd frontend && grep -n -B3 -A10 "Modo privacidade" src/telas/hoje/HojePage.tsx
```

Copie o JSX exato do botão. Ele tem `aria-pressed` e o rótulo `Modo privacidade`. Os dois são invariantes — o rótulo não muda de palavra, o `aria-pressed` não desaparece. Você vai **mover** esse JSX, não reescrevê-lo.

- [ ] **Step 2: Escrever os testes que falham**

Em `Layout.test.tsx`:

```tsx
it('o controle de privacidade vive no shell, alcancavel de qualquer tela', async () => {
  renderizar(<Layout />)
  const alternador = await screen.findByRole('button', { name: /modo privacidade/i })
  expect(alternador).toHaveAttribute('aria-pressed', 'false')
  await userEvent.click(alternador)
  expect(alternador).toHaveAttribute('aria-pressed', 'true')
})
```

Em `PacientesPage.test.tsx`:

```tsx
it('sob privacidade o nome e o metadado borram, o consentimento nao', async () => {
  renderizar(<PacientesPage />, { privacidadeLigada: true })
  const linha = (await screen.findByText('Helena Vaz')).closest('li')!
  expect(within(linha).getByText('Helena Vaz')).toHaveAttribute('data-borrado', 'true')
  expect(within(linha).getByText(/aviso (nao )?autorizado/i).closest('[data-borrado="true"]')).toBeNull()
})
```

**A opção `privacidadeLigada` não existe ainda; esta tarefa a cria.** Hoje
`renderizar` (`src/teste/renderizar.tsx`) embrulha tudo em
`<PrivacidadeProvider>` sem forma de nascer ligado, e `renderizar` aceita
exatamente `{ rota, cliente }`. Duas mudanças, nesta ordem:

Em `src/privacidade/PrivacidadeProvider.tsx`, o provider ganha um prop opcional
de estado inicial. A assinatura vira
`PrivacidadeProvider({ children, inicial = false }: { children: ReactNode; inicial?: boolean })`
e o `useState` passa a nascer de `inicial`. O default `false` mantém produção
byte-idêntica — nenhum chamador existente muda.

Em `src/teste/renderizar.tsx`, a terceira opção entra ao lado das duas que já
existem:

```tsx
export function renderizar(
  ui: ReactElement,
  {
    rota = '/',
    cliente = clienteDeTeste(),
    privacidadeLigada = false,
  }: { rota?: string; cliente?: QueryClient; privacidadeLigada?: boolean } = {},
) {
```

e o `<PrivacidadeProvider>` dentro de `Envolvido` passa a receber
`inicial={privacidadeLigada}`. Os docstrings que já existem nos dois arquivos
**não se removem**.

Não embrulhe `<PacientesPage />` num `PrivacidadeProvider` próprio dentro do
teste: `renderizar` já embrulha, e o provider aninhado cria um segundo estado
que o controle do shell não alcança — o teste passaria medindo a coisa errada.

- [ ] **Step 3: Rodar e ver falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/ui/Layout.test.tsx src/telas/pacientes
```

Esperado: os dois novos falham. O de `Layout` falha com "Unable to find role button"; o de Pacientes falha porque nenhum `data-borrado` existe na tela.

- [ ] **Step 4: Mover o controle para o `Layout`**

Recorte o JSX do botão de `HojePage.tsx` e cole-o em `Layout.tsx`, dentro da lateral, abaixo da navegação. `HojePage` **mantém** o `usePrivacidade()` e o `data-borrado` nos elementos que já borra — ela perde só o controle. Se depois disso `HojePage` ficar com algum import ou variável sem uso, remova; `tsc` vai acusar.

- [ ] **Step 5: Aplicar o borrado em Pacientes**

Em `PacientesPage.tsx`, leia `const { privacidade } = usePrivacidade()` e marque os dois spans que a Tarefa 3 criou:

```tsx
<span className="linha-paciente-nome" data-borrado={privacidade}>{p.nome}</span>
{meta && <span className="linha-paciente-meta" data-borrado={privacidade}>{meta}</span>}
```

O `Chip` fica **sem** `data-borrado` — é a D2.4.

- [ ] **Step 6: Atualizar a regra de borrado ao spec §3**

Substitua a linha 167 de `estilo.css`:

```css
[data-borrado='true'] {
  filter: blur(6px) saturate(0.7);
  transition: filter var(--dur-medio) var(--curva-cor);
}
```

A dessaturação não é enfeite: um nome só borrado continua legível pela silhueta de cor. O comentário que existe acima dessa regra (linhas 163-166) **não se remove**.

- [ ] **Step 7: Rodar a suíte inteira**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 23 arquivos / 106 testes / 0 falhas, `tsc` exit 0. Confira em especial que os testes de `HojePage` continuam passando — ela perdeu o controle mas não o borrado, e qualquer teste da Hoje que clicava no botão agora precisa achá-lo no shell. Se algum teste da Hoje quebrar porque renderiza `HojePage` isolada sem o `Layout`, **não mude o teste para procurar por classe** — ajuste-o para renderizar com o shell, ou para exercitar o estado pelo provider.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/ui/Layout.tsx frontend/src/ui/Layout.test.tsx frontend/src/telas/hoje/HojePage.tsx frontend/src/telas/pacientes/PacientesPage.tsx frontend/src/telas/pacientes/PacientesPage.test.tsx frontend/src/estilo/estilo.css
git commit -m "feat(privacidade): o controle sobe para o shell e cobre Pacientes"
```

---

### Task 5: O bloco da Agenda carrega o estado

Sai o bloco azul chapado com texto branco; entra superfície tonal com barra de estado à esquerda e duas linhas — nome, depois glifo e palavra de estado (spec §4.5). Motivo escrito no spec de linguagem §6.5: texto branco sobre azul saturado, repetido trinta vezes numa grade, é a coisa mais cansativa que uma tela de agenda pode fazer com quem a olha oito horas por dia.

**A semântica da grade é invariante e é o risco desta tarefa.** O `<table role="grid">`, os sete `<th scope="col">`, os `<th scope="row">` de hora, o `role="gridcell"` e o `tabIndex={0}` de cada célula ficam exatamente onde estão. Você muda o **conteúdo** da célula ocupada, não a célula.

**Files:**
- Modify: `frontend/src/telas/agenda/AgendaPage.tsx:117-130`
- Modify: `frontend/src/estilo/estilo.css:230-233` (`.slot.ocupado`)
- Test: `frontend/src/telas/agenda/AgendaPage.test.tsx`

**Interfaces:**
- Consome: `ConsultaDaAgenda` com `status: "REALIZADA" | "AGENDADA" | "CANCELADA" | "FALTOU"`; o componente `Chip` de `src/ui/Chip.tsx`.
- Produz: nada.

- [ ] **Step 1: Escrever os testes que falham**

Acrescente a `AgendaPage.test.tsx`:

```tsx
it('o bloco diz o estado da sessao, nao so o nome', async () => {
  await screen.findByRole('grid', { name: /semana/i })
  const bloco = (await screen.findByText(/otavio lins/i)).closest('[role="gridcell"]')!
  expect(within(bloco).getByText(/realizada/i)).toBeInTheDocument()
})

it('a grade nao perde nem ganha celula ao mostrar estado', async () => {
  const grade = await screen.findByRole('grid', { name: /semana/i })
  expect(within(grade).getAllByRole('columnheader')).toHaveLength(7)
  expect(within(grade).getAllByRole('rowheader')).toHaveLength(13)
})
```

Use a fixture de consultas que o arquivo já define e garanta que a de `Otavio Lins` tem `status: 'REALIZADA'`. O `13` é literal de propósito: `AgendaPage.tsx:20` define `const HORAS = Array.from({ length: 13 }, (_, i) => i + 7)` — 07h às 19h — e **não** o exporta. Importar essa constante para o teste faria o teste concordar com o componente por construção, que é o oposto do que ele existe para provar.

O segundo teste é a rede da semântica: ele falha se a reestrutura mexer na contagem de cabeçalhos, que é exatamente o modo como uma grade acessível se quebra em silêncio.

- [ ] **Step 2: Rodar e ver falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/agenda/AgendaPage.test.tsx
```

Esperado: o primeiro falha (nenhum texto de estado na grade); o segundo **passa desde já** — ele é regressão, e é para ficar verde do começo ao fim. Registre que passou e siga.

- [ ] **Step 3: Escrever o mapa de estado e o conteúdo da célula**

Em `AgendaPage.tsx`, acima do componente:

```tsx
const ESTADO: Record<'REALIZADA' | 'AGENDADA' | 'FALTOU', { variante: 'realizada' | 'neutro' | 'faltou'; texto: string }> = {
  REALIZADA: { variante: 'realizada', texto: 'Realizada' },
  AGENDADA: { variante: 'neutro', texto: 'Agendada' },
  FALTOU: { variante: 'faltou', texto: 'Faltou' },
}
```

`CANCELADA` fica fora do mapa de propósito: `encontrarConsulta` (`AgendaPage.tsx:44`) já filtra cancelada antes, então uma consulta cancelada nunca chega aqui. Se chegar, o `ESTADO[...]` devolve `undefined` e a linha de estado não renderiza — o bloco degrada para o nome, que é o comportamento de hoje.

Substitua o conteúdo da célula ocupada (linha 129) por:

```tsx
{ocupada && (
  <span className="bloco-sessao" data-estado={ocupada.status.toLowerCase()}>
    <span className="bloco-sessao-nome">{ocupada.pacienteNome}</span>
    {ESTADO[ocupada.status as keyof typeof ESTADO] && (
      <Chip variante={ESTADO[ocupada.status as keyof typeof ESTADO].variante}>
        {ESTADO[ocupada.status as keyof typeof ESTADO].texto}
      </Chip>
    )}
  </span>
)}
```

O `<td>` que envolve isso — `role="gridcell"`, `tabIndex={0}`, `aria-label`, `onClick`, `className={ocupada ? 'slot ocupado' : 'slot'}` — **não muda em nada**. Nem o `aria-label`, que continua `Consulta de ${nome} as ${data}`: ele já é o nome acessível da célula e acrescentar o estado ali duplicaria o que o chip já diz.

Importe `Chip` de `../../ui/Chip`.

- [ ] **Step 4: Rodar e ver passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/agenda/AgendaPage.test.tsx
```

Esperado: 8 passando. Se `consulta cancelada nao ocupa a grade` quebrar, a causa é o mapa de estado tendo mudado o filtro — reveja o Step 3, não o teste.

- [ ] **Step 5: Escrever o CSS do bloco**

Substitua as linhas 230-233 de `estilo.css`:

```css
.slot.ocupado { background: transparent; padding: 2px; cursor: pointer; }
.bloco-sessao {
  display: flex; flex-direction: column; gap: var(--gap-0);
  padding: 4px 6px;
  border-radius: var(--raio-md);
  background: var(--acento-suave);
  color: var(--tinta);
  box-shadow: inset 2px 0 0 var(--acento);
}
.bloco-sessao[data-estado='realizada'] { box-shadow: inset 2px 0 0 var(--realizada-tinta); }
.bloco-sessao[data-estado='faltou']    { box-shadow: inset 2px 0 0 var(--faltou-tinta); }
.bloco-sessao-nome {
  font-size: var(--t-legenda-tam); line-height: var(--t-legenda-alt);
  font-weight: var(--peso-medio);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
```

A barra à esquerda é `inset 2px 0 0`, não `border-left: 3px`: borda empurra o conteúdo em 3px e desalinha o bloco dos vizinhos na mesma linha da grade; anel interno não empurra nada. O `background: transparent` no `.slot.ocupado` é o que deixa o bloco flutuar dentro da célula em vez de preenchê-la de ponta a ponta.

As bordas de `--separador` da grade (`estilo.css:206-215`) **ficam intactas** — a grade é o único sobrevivente do fio de 1px em todo o app (spec §2.4).

- [ ] **Step 6: Rodar a suíte e conferir o espécime**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 23 arquivos / 108 testes / 0 falhas, `tsc` exit 0. Abra `mock-agenda.html` e confira: bloco tonal claro com texto escuro, barra colorida à esquerda, duas linhas.

- [ ] **Step 7: Conferir o teste de escala de cinza**

Tire um print da grade com pelo menos uma sessão de cada estado e olhe em escala de cinza a 200%. Deve dar para dizer qual é qual sem ler o texto — é o teste de aceite do spec de linguagem §2.5, e é manual de propósito.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/telas/agenda/AgendaPage.tsx frontend/src/telas/agenda/AgendaPage.test.tsx frontend/src/estilo/estilo.css
git commit -m "feat(agenda): o bloco ocupado carrega o estado da sessao"
```

---

### Task 6: Na Ficha, a pendência desce e o prontuário sobe

Hoje `.colunas-ficha` dá 260px fixos à coluna de pendências e o resto à linha do tempo, como se as duas tivessem peso parecido. Não têm: o prontuário é o trabalho, a pendência é o lembrete. As pendências viram faixa-resumo subordinada com contador, e a linha do tempo passa a ocupar a largura inteira (spec §4.3).

**Files:**
- Modify: `frontend/src/telas/ficha/FichaPage.tsx:111-135`
- Modify: `frontend/src/estilo/estilo.css:286` e `:297` (`.colunas-ficha`)
- Test: `frontend/src/telas/ficha/FichaPage.test.tsx`

**Interfaces:**
- Consome: `data.pendencias` e `data.linhaDoTempo` da query da ficha, sem mudança de forma.
- Produz: nada.

- [ ] **Step 1: Escrever os testes que falham**

Acrescente a `FichaPage.test.tsx`:

```tsx
it('a faixa de pendencias diz quantas sao', async () => {
  renderizar(<FichaPage />, { rota: `/pacientes/${HELENA.idPaciente}` })
  const faixa = await screen.findByRole('complementary', { name: 'Pendencias' })
  expect(within(faixa).getByText('1')).toBeInTheDocument()
})

it('a linha do tempo nao divide mais a largura com as pendencias', async () => {
  renderizar(<FichaPage />, { rota: `/pacientes/${HELENA.idPaciente}` })
  const lista = await screen.findByRole('list', { name: 'Sessoes' })
  const faixa = screen.getByRole('complementary', { name: 'Pendencias' })
  expect(faixa.contains(lista)).toBe(false)
  expect(lista.closest('.colunas-ficha')).toBeNull()
})
```

Use a forma de `renderizar` que os outros testes do arquivo já usam para chegar na rota da ficha; não invente assinatura nova. `HELENA` é a fixture existente.

O segundo teste toca `.colunas-ficha` por `closest`, o que parece violar a constraint de não navegar por classe. Não viola: ele não **encontra** nada por classe — encontra por `role`/`name` e depois afirma a **ausência** de um contêiner que este plano está removendo. É a única forma de provar que a coluna morreu, e ele morre junto com ela assim que a Fatia 2 fechar. Se o revisor discordar, a correção é apagar esse `expect`, não reescrever a tarefa.

- [ ] **Step 2: Rodar e ver falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/ficha/FichaPage.test.tsx
```

Esperado: o primeiro falha (não há contador); o segundo falha no `closest('.colunas-ficha')`, que hoje acha o div.

- [ ] **Step 3: Reescrever o corpo da Ficha**

Substitua as linhas 111-135 de `FichaPage.tsx` por:

```tsx
<aside aria-label="Pendencias" className="faixa-pendencias-bloco">
  <h2 className="titulo-secao">
    Pendencias
    {data.pendencias.length > 0 && (
      <span className="contador-pendencias">{data.pendencias.length}</span>
    )}
  </h2>
  {data.pendencias.length === 0 ? (
    <p className="sub">Nenhuma pendencia.</p>
  ) : (
    <ul className="faixa-pendencias">
      {data.pendencias.map((p) => (
        <li className="linha-pendencia" key={p.id}>
          {p.descricao}
        </li>
      ))}
    </ul>
  )}
</aside>
<div className="prontuario">
  <h2 className="titulo-secao">Sessoes</h2>
  {data.linhaDoTempo.length === 0 ? (
    <EstadoVazio titulo="Nenhuma sessao registrada ainda." />
  ) : (
    <LinhaDoTempo sessoes={data.linhaDoTempo} pacienteId={pacienteId} />
  )}
</div>
```

O que **não** muda e não pode mudar: `<aside aria-label="Pendencias">` continua `aside` com o mesmo rótulo; os textos `Pendencias`, `Sessoes`, `Nenhuma pendencia.` e `Nenhuma sessao registrada ainda.` são os mesmos; a lista continua `<ul>` com `<li>`. O `<div className="colunas-ficha">` que embrulhava os dois **sai** — as duas regiões viram irmãs no fluxo da `.tela`.

O contador é a única adição de texto desta tarefa, e é um número, não uma frase — autorizado nominalmente pelo spec §4.3 ("contador + lista aberta").

A ordem inverte: a faixa vem **antes** da linha do tempo. Uma pendência que aparece depois de trinta sessões não é lembrete, é arquivo.

- [ ] **Step 4: Rodar e ver passar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/ficha/FichaPage.test.tsx
```

Esperado: 11 passando. O teste pré-existente `pendencias do paciente aparecem ao lado` continua passando — ele só afirma que o texto está presente, nunca afirmou posição, apesar do nome. **Não renomeie o teste**: o nome é copy de teste, e trocá-lo agora apaga o registro de que a intenção mudou.

- [ ] **Step 5: Escrever o CSS**

Remova a linha 286 e a linha 297 (`.colunas-ficha` e sua media query) e acrescente:

```css
.faixa-pendencias-bloco { margin: var(--gap-5) 0 var(--gap-8); }
.titulo-secao { display: flex; align-items: center; gap: var(--gap-2); }
.contador-pendencias {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 5px;
  border-radius: var(--raio-cheio);
  background: var(--alerta-fundo); color: var(--alerta-tinta);
  font-size: var(--t-legenda-tam);
  font-weight: var(--peso-forte);
  font-variant-numeric: tabular-nums;
}
.prontuario { margin-bottom: var(--gap-8); }
```

O `.titulo-secao` ganha `display: flex` sem perder o que já tem nas linhas 136-137 — acrescente o bloco novo **depois** daquele, não no lugar dele.

`--gap-8` separando faixa de prontuário é deliberado: o spec de linguagem §4.2 acrescentou `--gap-8`/`--gap-9` exatamente porque o teto de `--gap-7` fazia "seção diferente" e "item da mesma seção" ficarem a distâncias parecidas demais.

- [ ] **Step 6: Rodar a suíte inteira e conferir o espécime**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 23 arquivos / 110 testes / 0 falhas, `tsc` exit 0. Compare com a aba Ficha de `mock-v2.html`.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/telas/ficha/FichaPage.tsx frontend/src/telas/ficha/FichaPage.test.tsx frontend/src/estilo/estilo.css
git commit -m "feat(ficha): a pendencia vira faixa e o prontuario ocupa a largura"
```

---

### Task 7: O estado vazio ganha forma

`EstadoVazio` é usado por Pacientes, Ficha e Hoje, e hoje é um `<p>` dentro de um div. O spec de linguagem §6.8 dá a ele uma forma própria: sem cartão e sem fundo, título em peso forte, apoio opcional com `max-width: 34ch`, e a ação como botão **secundário** — um botão primário azul num estado vazio grita mais alto que o resto da tela.

**A copy é invariante.** Esta tarefa define forma. Nenhum texto de estado vazio muda de palavra, e nenhum texto de apoio é inventado — o prop `apoio` nasce opcional e sem nenhum chamador nesta fatia.

**Files:**
- Modify: `frontend/src/ui/EstadoVazio.tsx`
- Modify: `frontend/src/estilo/estilo.css:195-199`
- Test: `frontend/src/ui/EstadoVazio.test.tsx` (criar)

**Interfaces:**
- Produz: `EstadoVazio({ titulo, apoio?, acao? })`. O prop `apoio?: string` é novo e opcional; nenhum chamador existente muda.

- [ ] **Step 1: Escrever o teste que falha**

Crie `frontend/src/ui/EstadoVazio.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EstadoVazio } from './EstadoVazio'

describe('EstadoVazio', () => {
  it('o titulo e o apoio sao textos distintos, nao um paragrafo so', () => {
    render(<EstadoVazio titulo="Nenhum paciente cadastrado ainda." apoio="Cadastre o primeiro." />)
    expect(screen.getByText('Nenhum paciente cadastrado ainda.')).toBeInTheDocument()
    expect(screen.getByText('Cadastre o primeiro.')).toBeInTheDocument()
  })

  it('sem apoio, nao renderiza paragrafo vazio', () => {
    const { container } = render(<EstadoVazio titulo="Nenhuma sessao registrada ainda." />)
    expect(container.querySelectorAll('p')).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Rodar e ver falhar**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/ui/EstadoVazio.test.tsx
```

Esperado: o primeiro falha (`apoio` não é prop reconhecido, o texto não aparece); o segundo passa desde já.

- [ ] **Step 3: Acrescentar o prop**

```tsx
export function EstadoVazio({
  titulo,
  apoio,
  acao,
}: {
  titulo: string
  apoio?: string
  acao?: ReactNode
}) {
  return (
    <div className="estado-vazio">
      <p className="titulo">{titulo}</p>
      {apoio && <p className="apoio">{apoio}</p>}
      {acao}
    </div>
  )
}
```

O docstring que já existe acima do componente **não se remove**.

- [ ] **Step 4: Escrever o CSS**

Substitua as linhas 195-199 de `estilo.css`:

```css
.estado-vazio {
  display: flex; flex-direction: column; align-items: center;
  gap: var(--gap-3);
  padding: var(--gap-9) var(--gap-5);
  text-align: center;
}
.estado-vazio .titulo {
  margin: 0;
  font-size: var(--t-corpo-tam); line-height: var(--t-corpo-alt);
  font-weight: var(--peso-forte); color: var(--tinta);
}
.estado-vazio .apoio {
  margin: 0; max-width: 34ch;
  font-size: var(--t-segundo-tam); line-height: var(--t-segundo-alt);
  color: var(--tinta-2);
}
```

Sem `background` e sem `border-radius`: o vazio é vazio.

- [ ] **Step 5: Rodar a suíte inteira**

```bash
cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit
```

Esperado: 24 arquivos / 112 testes / 0 falhas, `tsc` exit 0.

- [ ] **Step 6: Verificar a ação secundária nos chamadores existentes**

```bash
cd frontend && grep -rn "EstadoVazio" src --include=*.tsx | grep -v EstadoVazio.tsx
```

Para cada chamador que passa `acao`, confira se o botão dentro dela é `variante="secundario"`. Se algum for primário, troque — é o §6.8. Se todos já forem secundários, registre que nada mudou.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/ui/EstadoVazio.tsx frontend/src/ui/EstadoVazio.test.tsx frontend/src/estilo/estilo.css
git commit -m "feat(vazio): o estado vazio ganha a forma da linguagem nova"
```

---

## Fora de escopo, de propósito

- **A tela Hoje inteira** — D2.5. É a D6 do spec (fila única "Precisa de você", reconciliando dois modelos de dado no cliente). Fatia 3.
- **`.cartao.destaque`** — D2.6. Vai com a Hoje.
- **Dark mode** — D4. Os tokens existem em par; o tema não.
- **Colapso de hora vazia na Agenda** — D7. Volta quando a tela Ajustes existir e houver campo de expediente.
- **Scroll edge** (`§2.4`: gradiente de 24px sob cabeçalho fixo quando `scrollTop > 0`) — não há cabeçalho fixo rolável hoje; criar um para justificar o gradiente é inverter a ordem.
- **Marcador de horário atual na Agenda** (`§6.5`: linha de 1.5px `--agora`) — o token existe, o elemento não. É funcionalidade nova, não restyling.
- **`DialogoDeConsulta` falando HTTP direto do componente** — defeito pré-existente, fura a fronteira que as outras três telas de escrita respeitam. Não é escopo de refatoração visual.
- **A fonte Inter carregada e não usada** em `frontend/index.html` — peso morto pré-existente.
- **`Usuario e/ou senha incorretos!`** — a mensagem real do 401 viola a convenção da casa, mas trocar texto de erro de autenticação não é escopo desta fatia.

---

## Riscos

| # | Risco | Mitigação |
|---|---|---|
| 1 | A Tarefa 5 quebra a semântica da grade em silêncio | O segundo teste da Tarefa 5 conta `columnheader` e `rowheader` e roda verde do começo ao fim |
| 2 | `idadeEmAnos` erra na virada do aniversário | O cálculo compara mês **e** dia antes de decrementar; o teste de linha usa fixture com data fixa |
| 3 | A linha de metadado estoura a largura e empurra o chip para fora | `min-width: 0` + `text-overflow: ellipsis` no link, exercitado com nome longo na conferência visual |
| 4 | Mover o controle de privacidade quebra testes da Hoje que clicavam nele | Tarefa 4 Step 7 nomeia o caso e proíbe a saída fácil (procurar por classe) |
| 5 | A animação de saída do diálogo não aparece por `transition` em vez de `@keyframes` | Tarefa 2 Step 6 é conferência manual explícita |

---

## D2.7 — Os comentários obsoletos ficam. Decidido pelo dono em 27/09/2026.

`Botao.tsx:7`, `Navegacao.tsx:14`, `estilo.css:1-3,81-84,199` e `dialogo.css:2,7` ainda dizem que "ação é forma, não cor" e mandam ver um bloco em `tokens.css` que não existe mais. A D1 matou essa regra na Fatia 1.

**Nenhuma tarefa deste plano os toca** — nem para atualizar o texto, nem para remover. Alternativas recusadas pelo dono: atualizar a redação nesta fatia, e remover de vez.

**O custo, registrado para não virar surpresa:** quem abrir esses arquivos antes de abrir o spec vai ler que pílula é ação e bloco chapado é evento, e vai construir sobre uma premissa que esta reestrutura apagou. A mitigação é este parágrafo e a seção §8 do spec de design, que diz qual é a régua — não o fonte.

**Para o implementador:** se um comentário obsoleto estiver numa linha que você precisa editar por outro motivo, edite o código e deixe o comentário intacto. Se isso ficar impossível, pare e reporte em vez de decidir sozinho.

---

**Para avaliação técnica:** quatro telas trocam de aparência e de hierarquia de informação, a lista de pacientes passa a mostrar quatro dados que já vinham da API e ninguém exibia, a agenda passa a dizer o estado de cada sessão, e o modo privacidade finalmente funciona na tela onde dado financeiro aparece. Nada de texto que o usuário lê muda de palavra, nenhuma chamada nova de API entra, e a estrutura que leitor de tela e teste percorrem fica intacta.
