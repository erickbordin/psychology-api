# Fase 0.5 — Entrada de dado: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Desenhar a metade do produto que a Fase 0 não desenhou — entrar dado —
na linguagem visual do Google Calendar já aprovada, antes de qualquer React.

**Architecture:** Acrescenta ao protótipo existente (`prototipo/`, HTML/CSS/JS
puro, sem build). Diálogos modais, formulários com validação de verdade, e
estados vazios. Nada de framework: a Fase 0.5 é descartável igual à Fase 0 —
sobrevivem os tokens, o mapa de telas e as **regras de validação**, que viram
schemas `zod` na Fatia 1.

**Tech Stack:** HTML, CSS, JavaScript sem dependência. Tokens de
`prototipo/tokens.css` (Google Calendar). Fonte Roboto via Google Fonts.

**Spec:** `docs/superpowers/specs/2026-09-01-redesenho-produto-design.md`
(§4 mapa de telas, §5.4 consentimento, §6 validação, §7.1 privacidade do e-mail)

## Por que esta fase existe

A queixa que originou o redesenho inteiro foi **"sem validação em formulários
simples"**. A Fase 0 validou o mapa de telas e a leitura do dia, e entregou
**cinco superfícies de entrada de dado como stubs** que dizem "fora do escopo":
`+ Nova consulta`, `Série semanal`, `+ Novo paciente`, `Editar`, `Agendar
retorno`. O dono achou a sexta ao ver o protótipo aprovado: "Agendar retorno"
confirma um texto e nunca pergunta **quando**.

Ou seja: a fase que existe para o erro de desenho custar minutos passou inteira
sem tocar na metade do produto que gerou a queixa.

Duas consequências que esta fase fecha:

1. A spec §6.2 — quando o erro aparece, obrigatório visível antes do erro, foco
   no primeiro inválido, botão que nunca morre em silêncio — nunca foi desenhada.
2. **Nenhuma tela tem estado vazio.** Todas assumem consultório em regime: 6
   sessões, 3 pacientes, um mês faturado. O dia 1 do psicólogo pagante — zero de
   tudo — não existe em lugar nenhum, e é de onde "amador" costuma vir.

E desbloqueia a Fatia 2, hoje travada pelo veredito da Fase 0 por falta do
formulário de paciente (§4③, onde o consentimento é registrado) e do texto do
aviso (§4⑤).

## Global Constraints

- **Tokens são lei.** Toda cor de `prototipo/tokens.css`, via `var(--token)`.
  Nenhuma cor escrita à mão fora daquele arquivo.
- **Ação é forma, não cor.** Pílula = ação; bloco chapado com barra à esquerda =
  evento. Os dois podem ser azuis. Regra vigente, escrita em `tokens.css`.
- **Nenhum clique engolido em silêncio.** Todo botão, campo e link ou faz algo
  visível, ou diz na própria tela que está fora do protótipo.
- **Dado falso é inventado, nunca real.** Nenhum nome, telefone, e-mail ou
  anotação de pessoa real, nem "só para testar".
- **Zero conteúdo clínico em assunto ou prévia de e-mail** (spec §7.1).
- Sem framework, sem build, sem `package.json`, sem dependência. A única coisa
  externa é a fonte Roboto, por `@import` no topo de `tokens.css`.
- **Não commitar.** O dono do repositório commita. Nenhum `git add`,
  `git commit`, `git stash` ou `git checkout` em tarefa nenhuma.

## As regras de validação — a saída durável desta fase

Esta tabela é o entregável que sobrevive: na Fatia 1 ela vira schema `zod`, e os
mesmos limites entram nos DTOs do backend. Vale para **todas** as tarefas.

| Campo | Regra | Mensagem de erro |
|---|---|---|
| Nome | obrigatório, 2 a 100 caracteres | `Informe o nome do paciente.` |
| Data de nascimento | obrigatória, data real, no passado, idade < 120 | `Informe a data de nascimento.` / `A data de nascimento precisa estar no passado.` |
| Telefone | opcional, máx. 20, formato BR com máscara | `Telefone incompleto.` |
| E-mail | opcional **exceto** quando o canal de aviso é e-mail; formato; máx. 100 | `Informe um e-mail válido.` / `Para avisar por e-mail, o endereço é obrigatório.` |
| Valor da sessão | obrigatório, > 0, 2 casas decimais | `Informe o valor da sessão.` / `O valor precisa ser maior que zero.` |
| Data/hora da consulta | obrigatória; conflito checado **antes** do envio; passado avisa, não bloqueia | `Escolha data e horário.` / `Esse horário já está ocupado por {nome}.` / aviso: `Essa data já passou — a consulta será registrada como retroativa.` |
| Anotação | não vazia | `Escreva a anotação antes de registrar.` |

**Texto de erro num só registro:** frase completa, com acento, dizendo **o que
fazer**. Nunca `campo obrigatorio`.

### Quando o erro aparece — a regra que hoje não existe em lugar nenhum

1. No `blur`, **só depois do primeiro toque** no campo. Não pune quem ainda está
   digitando pela primeira vez.
2. No `submit`, **sempre**, em todos os campos.
3. Depois que um campo já errou, **revalida a cada tecla** — o erro some assim
   que for corrigido, sem esperar outro envio.

### O que todo formulário desta fase faz

- **Obrigatório é visível antes do erro.** Campo opcional leva a palavra
  `opcional` ao lado do rótulo. O usuário não descobre a regra errando.
- **Submit inválido nunca é silencioso**: marca os campos, **move o foco para o
  primeiro inválido**, e anuncia em `aria-live` quantos erros há.
- Campo com erro: `aria-invalid="true"`, `aria-describedby` apontando para a
  mensagem, borda em `var(--perigo)`.
- Tipo certo de campo: data usa `<input type="date">`, hora usa
  `<input type="time">`, telefone tem máscara brasileira.

## File Structure

| Arquivo | Responsabilidade |
|---|---|
| `prototipo/CENARIOS.md` | ganha C5, C6 e C7 — os percursos de entrada de dado |
| `prototipo/TRAVAS.md` | ganha as travas desta fase e o veredito da Fase 0.5 |
| `prototipo/dialogo.css` | **novo** — diálogo modal, campos, estados de erro, estados vazios |
| `prototipo/index.html` | ganha os diálogos e os estados vazios |
| `prototipo/dados.js` | ganha `DADOS.vazio` (o conjunto do dia 1) |
| `prototipo/validacao.js` | **novo** — as regras da tabela acima, num lugar só |

`validacao.js` existir separado é decisão de desenho, não organização: é o
arquivo que a Fatia 1 traduz para `zod`. Se as regras ficarem espalhadas pelos
diálogos, a tradução vira arqueologia.

## O ciclo de verificação

Igual ao da Fase 0, e obrigatório em toda tarefa de tela:

1. Abrir o protótipo na tela entregue
2. Percorrer o cenário correspondente, sem pular passo
3. Anotar **toda** trava em `prototipo/TRAVAS.md`
4. Corrigir o que for layout ou informação faltando
5. Trava que exigir mudança de escopo vira `[escopo]` e **sobe para revisão
   humana** — não se conserta

**Tem navegador nesta máquina.** O MCP `chrome-devtools` não conecta, mas o
Chromium do Playwright está em cache e roda — **use**. Esta fase é toda de
formulário, e formulário tem uma classe de defeito que só existe renderizado:
foi assim que apareceu `.campo { display: flex }` vencendo o atributo `hidden`,
deixando um bloco "escondido" visível. Leitura de código não pega isso.

Correção de rota registrada: as primeiras tarefas desta fase foram despachadas
com a instrução errada de que não havia navegador. Uma folha de estilo morta
(um `@import` cortado no meio) chegou a passar por todas as verificações de
leitura por causa disso.

Servidor sempre **em segundo plano** (`python3 -m http.server &`, guardar o PID,
`curl`, `kill` — nunca em primeiro plano, ele não retorna), confirmar HTTP 200,
e então percorrer o cenário no navegador.

**Node:** o do `PATH` é v9.11.2 e não entende sintaxe moderna — `node --check`
nele dá **falso negativo** em arquivo que use `??`. Use
`/home/syonet/.cache/ms-playwright-go/1.57.0/node` (v24.11.1).

---

### Task 1: Os três cenários de entrada de dado

**Files:**
- Modify: `prototipo/CENARIOS.md`

**Interfaces:**
- Produces: `C5`, `C6`, `C7` — citados por nome nas Tasks 2 a 6.

Os cenários C1–C4 existentes cobrem ler e marcar. Estes cobrem entrar dado, e
seguem o mesmo critério: hora concreta, quantidade concreta, e um fato que a
tela precisa mostrar sem ser perguntada.

- [ ] **Step 1: Acrescentar ao fim de `CENARIOS.md`**

```markdown
## C5 — Paciente novo, com o paciente na frente (Ficha e cadastro)

São 14h05. A sessão das 14h é uma primeira consulta: Diego Fontes, 28 anos,
chegou por indicação. Ele está sentado na minha frente e eu cadastro enquanto
conversamos.

Tenho o telefone dele. O e-mail ele não lembra de cabeça, vai mandar depois.
A data de nascimento ele me diz falando, não escrevendo.
Combinei R$ 200 por sessão com ele, não os R$ 180 que eu cobro dos outros.

Ele topou receber lembrete da consulta. Preciso registrar isso — e o sistema
precisa me impedir de ligar o aviso por e-mail sem ter o e-mail dele.

## C6 — Marcar a próxima, com a agenda cheia (Nova consulta e retorno)

São 14h52, a sessão do Diego acabou. Marco o retorno dele.

Quero a próxima terça, mesmo horário. A terça que vem, às 14h, já tem alguém.
Preciso descobrir isso **antes** de tentar salvar, não depois de levar erro.

Depois preciso lançar uma sessão que já aconteceu — atendi a Helena na quinta
passada num encaixe e nunca registrei. É data no passado, e o sistema tem que
deixar, avisando que é retroativa.

## C7 — Primeiro dia de uso (estados vazios)

Instalei hoje. Não tenho paciente nenhum, não tenho sessão nenhuma, não faturei
nada. É segunda-feira de manhã.

Abro o sistema e preciso entender, sem ler manual: que ele está vazio porque eu
sou novo, não porque quebrou — e qual é a primeira coisa que eu faço.
```

- [ ] **Step 2: Conferir a especificidade**

Cada um precisa ter hora concreta, quantidade concreta, e ao menos uma restrição
que reprove tela. Em C5 é o e-mail ausente contra o aviso por e-mail; em C6 é o
conflito de horário e a data no passado; em C7 é a ausência total de dado.

- [ ] **Step 3: Fechamento da tarefa**

**NÃO COMMITAR** — o dono do repositório commita. Deixar na árvore de trabalho.

---

### Task 2: As regras de validação, num arquivo só

**Files:**
- Create: `prototipo/validacao.js`
- Modify: `prototipo/index.html` (carrega o script antes do inline)

**Interfaces:**
- Produces: `VALIDACAO.nome(v)`, `.nascimento(v)`, `.telefone(v)`,
  `.email(v, obrigatorio)`, `.valor(v)`, `.dataHora(data, hora)`,
  `.anotacao(v)` — cada uma devolve `null` quando válido, ou a **string da
  mensagem** quando inválido.
- Produces: `mascaraTelefone(v)` — devolve o valor formatado como `(51) 99999-9999`.
- Produces: `ligarFormulario(form, campos, aoEnviar)` — instala a regra de
  blur/submit/revalidação em qualquer formulário. **Todas as Tasks seguintes
  usam esta função; nenhuma reimplementa a regra.**

Este é o arquivo que a Fatia 1 traduz para `zod`. Mensagens exatas da tabela em
"As regras de validação", verbatim.

- [ ] **Step 1: Escrever as regras**

```js
/**
 * As regras de validacao do produto, num lugar so.
 * Esta e a saida duravel da Fase 0.5: na Fatia 1 cada funcao daqui vira um
 * campo de schema zod, e os mesmos limites entram nos DTOs do backend.
 * Cada regra devolve `null` quando valido, ou a MENSAGEM quando invalido.
 */
const VALIDACAO = {
  nome(v) {
    const s = (v || '').trim()
    if (!s) return 'Informe o nome do paciente.'
    if (s.length < 2) return 'O nome precisa ter ao menos 2 letras.'
    if (s.length > 100) return 'O nome pode ter no máximo 100 caracteres.'
    return null
  },

  nascimento(v) {
    if (!v) return 'Informe a data de nascimento.'
    const d = new Date(v + 'T00:00:00')
    if (Number.isNaN(d.getTime())) return 'Essa data não existe.'
    const hoje = new Date()
    if (d > hoje) return 'A data de nascimento precisa estar no passado.'
    const anos = (hoje - d) / (365.25 * 24 * 3600 * 1000)
    if (anos >= 120) return 'Confira a data — são mais de 120 anos.'
    return null
  },

  telefone(v) {
    const s = (v || '').trim()
    if (!s) return null                       // opcional
    if (s.length > 20) return 'Telefone pode ter no máximo 20 caracteres.'
    const digitos = s.replace(/\D/g, '')
    if (digitos.length < 10) return 'Telefone incompleto.'
    return null
  },

  /** `obrigatorio` vem true quando o canal de aviso e e-mail (spec §5.4). */
  email(v, obrigatorio) {
    const s = (v || '').trim()
    if (!s) return obrigatorio ? 'Para avisar por e-mail, o endereço é obrigatório.' : null
    if (s.length > 100) return 'E-mail pode ter no máximo 100 caracteres.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)) return 'Informe um e-mail válido.'
    return null
  },

  valor(v) {
    const s = String(v ?? '').trim()
    if (!s) return 'Informe o valor da sessão.'
    const n = Number(s.replace(',', '.'))
    if (Number.isNaN(n)) return 'Informe só números.'
    if (n <= 0) return 'O valor precisa ser maior que zero.'
    if (Math.round(n * 100) !== n * 100) return 'Use no máximo duas casas decimais.'
    return null
  },

  /**
   * Data no passado AVISA mas nao bloqueia — o psicologo precisa registrar
   * sessao que ja aconteceu (spec §6.3). Por isso devolve `{ erro, aviso }`,
   * e nao so uma string: e a unica regra com as duas saidas.
   */
  dataHora(data, hora) {
    if (!data || !hora) return { erro: 'Escolha data e horário.', aviso: null }
    const quando = new Date(`${data}T${hora}`)
    if (Number.isNaN(quando.getTime())) return { erro: 'Data ou horário inválido.', aviso: null }
    if (quando < new Date()) {
      return { erro: null, aviso: 'Essa data já passou — a consulta será registrada como retroativa.' }
    }
    return { erro: null, aviso: null }
  },

  anotacao(v) {
    if (!(v || '').trim()) return 'Escreva a anotação antes de registrar.'
    return null
  },
}

/** Mascara brasileira progressiva: (51) 99999-9999 e (51) 9999-9999. */
function mascaraTelefone(v) {
  const d = (v || '').replace(/\D/g, '').slice(0, 11)
  if (d.length <= 2) return d
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}
```

- [ ] **Step 2: Escrever o motor de formulário**

Esta função é a regra da spec §6.2 em código. Nenhuma tarefa seguinte
reimplementa isto.

```js
/**
 * Instala a regra de quando o erro aparece (spec §6.2):
 *  - no blur, so depois do primeiro toque no campo
 *  - no submit, sempre, em todos
 *  - depois que um campo errou, revalida a cada tecla
 * Submit invalido NUNCA e silencioso: marca, move o foco para o primeiro
 * invalido, e anuncia em aria-live.
 *
 * `campos`: [{ id, regra: (valor, form) => string|null }]
 * `aoEnviar`: chamado so quando tudo passa.
 */
function ligarFormulario(form, campos, aoEnviar) {
  const tocados = new Set()
  const comErro = new Set()

  function erroDe(campo) {
    const el = form.querySelector('#' + campo.id)
    return campo.regra(el.value, form)
  }

  function pintar(campo) {
    const el = form.querySelector('#' + campo.id)
    const alvo = form.querySelector(`[data-erro-de="${campo.id}"]`)
    const erro = erroDe(campo)
    if (erro) {
      comErro.add(campo.id)
      el.setAttribute('aria-invalid', 'true')
      el.setAttribute('aria-describedby', alvo.id)
      alvo.textContent = erro
    } else {
      comErro.delete(campo.id)
      el.removeAttribute('aria-invalid')
      el.removeAttribute('aria-describedby')
      alvo.textContent = ''
    }
    return erro
  }

  campos.forEach((campo) => {
    const el = form.querySelector('#' + campo.id)

    // TOQUE E DIGITAR, nao focar. `blur` sozinho e saida de foco: quem passa de
    // Tab por um formulario sem digitar nada nao pode levar erro em todo campo
    // obrigatorio que atravessa — e isso que "nao punir quem ainda esta
    // digitando pela primeira vez" (spec §6.2) quer dizer.
    el.addEventListener('input', () => {
      tocados.add(campo.id)
      // so revalida a cada tecla DEPOIS que este campo ja errou uma vez
      if (comErro.has(campo.id)) pintar(campo)
    })

    el.addEventListener('blur', () => {
      if (!tocados.has(campo.id)) return   // focou e saiu sem digitar: nao valida
      pintar(campo)
    })
  })

  form.addEventListener('submit', (evento) => {
    evento.preventDefault()
    campos.forEach((c) => tocados.add(c.id))
    const erros = campos.map((c) => ({ campo: c, erro: pintar(c) })).filter((r) => r.erro)
    const anuncio = form.querySelector('[data-anuncio]')

    if (erros.length) {
      anuncio.textContent = erros.length === 1
        ? 'Um campo precisa de atenção.'
        : `${erros.length} campos precisam de atenção.`
      form.querySelector('#' + erros[0].campo.id).focus()   // foco no primeiro invalido
      return
    }
    anuncio.textContent = ''
    aoEnviar()
  })

  /**
   * Revalida UM campo cuja obrigatoriedade mudou por causa de OUTRO controle —
   * o e-mail vira obrigatorio quando o canal de aviso e e-mail, e volta a ser
   * opcional quando deixa de ser.
   * Repinta SO se o campo ja estava em erro: repintar sempre faria brotar erro
   * em campo que o usuario nunca tocou, que e pior que o defeito original.
   *
   * Sem isto, desmarcar o consentimento deixa o rotulo "opcional" reaparecer E
   * a mensagem de erro na tela ao mesmo tempo, uma contradizendo a outra —
   * defeito confirmado em navegador na primeira versao desta fase.
   */
  return {
    revalidar(id) {
      if (!comErro.has(id)) return
      const campo = campos.find((c) => c.id === id)
      if (campo) pintar(campo)
    },
  }
}
```

Quem tem campo condicional guarda o retorno e chama `revalidar` quando a
condição muda:

```js
const formulario = ligarFormulario(form, campos, aoEnviar)
// ...no handler do controle que muda a condicao:
formulario.revalidar('pac-email')
```

- [ ] **Step 3: Carregar o script**

Em `prototipo/index.html`, antes do `<script>` inline que usa `VALIDACAO`:

```html
<script src="validacao.js"></script>
```

Precisa vir **depois** de `dados.js` e **antes** do inline. Ordem errada quebra
tudo em silêncio.

- [ ] **Step 4: Verificar**

`node --check prototipo/validacao.js`. Depois, num Node avulso, conferir cada
regra contra pelo menos um caso válido e um inválido — inclusive
`VALIDACAO.email('', true)` devolvendo a mensagem do canal, e
`VALIDACAO.dataHora` de ontem devolvendo `aviso` e **não** `erro`.

- [ ] **Step 5: Fechamento** — **NÃO COMMITAR.**

---

### Task 3: O diálogo — casca visual e estados de erro

**Files:**
- Create: `prototipo/dialogo.css`
- Modify: `prototipo/index.html` (carrega o CSS; nenhum diálogo ainda)

**Interfaces:**
- Produces: as classes `.mascara`, `.dialogo`, `.dialogo-cabecalho`,
  `.dialogo-corpo`, `.dialogo-rodape`, `.campo`, `.campo-rotulo`,
  `.campo-opcional`, `.campo-erro`, `.campo-aviso`, `.anuncio`.
- Produces: `abrirDialogo(id)` e `fecharDialogo(id)`.

Casca primeiro, formulários depois: as Tasks 4 e 5 só preenchem. Um diálogo com
duas caras é o defeito que esta separação evita.

**Linguagem do diálogo, na pele Calendar já aprovada:** cartão branco, raio
`var(--raio-g)`, sombra em duas camadas com `--sombra-1`/`--sombra-2`, largura
máxima 480px, máscara escura por trás. Rótulo pequeno acima do campo, campo com
borda `var(--linha)` que vira `var(--acento)` no foco e `var(--perigo)` no erro.
Ação primária é pílula azul no rodapé à direita.

- [ ] **Step 1: Escrever `dialogo.css`**

```css
/* Dialogo modal na linguagem Calendar: cartao elevado, mascara por tras.
   Acao e forma, nao cor — a pilula azul do rodape e a acao; o resto e campo. */
.mascara {
  position: fixed; inset: 0; background: rgba(32, 33, 36, .6);
  display: flex; align-items: center; justify-content: center; padding: var(--gap-4);
  z-index: 10;
}
.mascara[hidden] { display: none; }

.dialogo {
  background: var(--superficie); border-radius: var(--raio-g);
  box-shadow: 0 1px 3px 0 var(--sombra-1), 0 4px 8px 3px var(--sombra-2);
  width: 100%; max-width: 480px; max-height: 90vh;
  display: flex; flex-direction: column;
}
.dialogo-cabecalho {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--gap-4) var(--gap-4) 0;
}
.dialogo-cabecalho h2 { font-size: 18px; font-weight: 500; margin: 0; }
.dialogo-corpo {
  padding: var(--gap-4); overflow-y: auto;
  display: flex; flex-direction: column; gap: var(--gap-4);
}
.dialogo-rodape {
  display: flex; justify-content: flex-end; gap: var(--gap-3);
  padding: 0 var(--gap-4) var(--gap-4);
}

/* campo */
.campo { display: flex; flex-direction: column; gap: 6px; }
.campo-rotulo { font-size: 13px; font-weight: 500; color: var(--tinta-2); }
/* obrigatorio e visivel ANTES do erro: o opcional se declara (spec §6.2) */
.campo-opcional { font-weight: 400; color: var(--tinta-3); }
.campo input, .campo select, .campo textarea {
  height: 40px; padding: 0 var(--gap-3); font-size: 14px;
  border: 1px solid var(--linha); border-radius: var(--raio);
  background: var(--superficie); color: var(--tinta);
}
.campo textarea { height: auto; padding: var(--gap-3); resize: vertical; }
.campo input:focus, .campo select:focus, .campo textarea:focus {
  outline: none; border-color: var(--acento); box-shadow: 0 0 0 1px var(--acento);
}
.campo input[aria-invalid="true"], .campo textarea[aria-invalid="true"] {
  border-color: var(--perigo);
}
.campo input[aria-invalid="true"]:focus { box-shadow: 0 0 0 1px var(--perigo); }
.campo-erro { font-size: 12px; color: var(--perigo); min-height: 15px; }
.campo-aviso {
  font-size: 12px; color: var(--atencao); background: var(--atencao-suave);
  border-radius: var(--raio); padding: 6px var(--gap-2); min-height: 15px;
}
.campo-erro:empty, .campo-aviso:empty { display: none; }
.campo-inline { display: flex; align-items: center; gap: var(--gap-2); font-size: 14px; }
.anuncio { font-size: 13px; color: var(--perigo); min-height: 18px; padding: 0 var(--gap-4); }
.anuncio:empty { display: none; }
.campo-duplo { display: grid; grid-template-columns: 1fr 1fr; gap: var(--gap-3); }

/* estado vazio — o dia 1 do psicologo (nao existia em tela nenhuma) */
.estado-vazio {
  display: flex; flex-direction: column; align-items: center; gap: var(--gap-3);
  padding: var(--gap-7) var(--gap-4); text-align: center;
}
.estado-vazio .titulo { font-size: 16px; font-weight: 500; color: var(--tinta); }
.estado-vazio .texto { font-size: 14px; color: var(--tinta-2); max-width: 42ch; }
```

- [ ] **Step 2: Escrever abrir/fechar**

No `<script>` inline. O Escape fecha e o foco volta para quem abriu — diálogo que
prende o teclado é defeito de acessibilidade, não detalhe.

```js
let quemAbriu = null

function abrirDialogo(id) {
  quemAbriu = document.activeElement
  const m = document.getElementById(id)
  m.hidden = false
  const primeiro = m.querySelector('input, select, textarea')
  if (primeiro) primeiro.focus()
}

function fecharDialogo(id) {
  document.getElementById(id).hidden = true
  if (quemAbriu) { quemAbriu.focus(); quemAbriu = null }
}

document.addEventListener('keydown', (evento) => {
  if (evento.code !== 'Escape') return
  const aberto = document.querySelector('.mascara:not([hidden])')
  if (aberto) fecharDialogo(aberto.id)
})

// todo [data-fechar] fecha de verdade — um Cancelar que nao fecha e
// exatamente o defeito que este produto combate
document.addEventListener('click', (evento) => {
  const alvo = evento.target.closest('[data-fechar]')
  if (alvo) fecharDialogo(alvo.dataset.fechar)
})
```

- [ ] **Step 3: Carregar o CSS** — `<link rel="stylesheet" href="dialogo.css">`
  depois de `estilo.css` em `index.html`.

- [ ] **Step 4: Verificar** — servidor em segundo plano, HTTP 200 em
  `dialogo.css`, e conferir que nenhuma classe nova colide com as já existentes
  em `estilo.css` (`grep` por cada nome antes de criar).

- [ ] **Step 5: Fechamento** — **NÃO COMMITAR.**

---

### Task 4: Cadastro e edição de paciente — e o consentimento

**Files:**
- Modify: `prototipo/index.html`, `prototipo/dialogo.css` (se faltar algo)
- Reference: **C5**, spec §5.4 e §4③

**Interfaces:**
- Consumes: `VALIDACAO`, `ligarFormulario`, `mascaraTelefone`, `abrirDialogo`
- Produces: `<div class="mascara" id="dialogo-paciente">`

**Esta é a tarefa que desbloqueia a Fatia 2.** O veredito da Fase 0 travou a
Fatia 2 por não existir onde registrar o consentimento do paciente (§4③, §5.4).
É aqui.

**O caso de validação mais importante do produto inteiro está neste
formulário:** o e-mail é opcional — **até** o psicólogo ligar o aviso por
e-mail. Aí vira obrigatório. É validação condicional entre dois campos, e é
exatamente o que o C5 exercita: o Diego não lembra o e-mail, e o psicólogo tenta
ligar o aviso mesmo assim.

- [ ] **Step 1: Escrever o diálogo**

```html
<div class="mascara" id="dialogo-paciente" hidden>
  <div class="dialogo" role="dialog" aria-modal="true" aria-labelledby="titulo-paciente">
    <div class="dialogo-cabecalho">
      <h2 id="titulo-paciente">Novo paciente</h2>
      <button class="botao secundario mini" data-fechar="dialogo-paciente">Fechar</button>
    </div>
    <form id="form-paciente">
      <div class="dialogo-corpo">
        <div class="campo">
          <label class="campo-rotulo" for="pac-nome">Nome</label>
          <input id="pac-nome" type="text" autocomplete="name">
          <span class="campo-erro" id="erro-pac-nome" data-erro-de="pac-nome"></span>
        </div>

        <div class="campo-duplo">
          <div class="campo">
            <label class="campo-rotulo" for="pac-nascimento">Data de nascimento</label>
            <input id="pac-nascimento" type="date">
            <span class="campo-erro" id="erro-pac-nascimento" data-erro-de="pac-nascimento"></span>
          </div>
          <div class="campo">
            <label class="campo-rotulo" for="pac-valor">Valor da sessão</label>
            <input id="pac-valor" type="number" step="0.01" min="0" placeholder="180,00">
            <span class="campo-erro" id="erro-pac-valor" data-erro-de="pac-valor"></span>
          </div>
        </div>

        <div class="campo">
          <label class="campo-rotulo" for="pac-telefone">
            Telefone <span class="campo-opcional">· opcional</span>
          </label>
          <input id="pac-telefone" type="tel" inputmode="numeric" placeholder="(51) 99999-9999">
          <span class="campo-erro" id="erro-pac-telefone" data-erro-de="pac-telefone"></span>
        </div>

        <div class="campo">
          <label class="campo-rotulo" for="pac-email">
            E-mail <span class="campo-opcional" id="rotulo-email-opcional">· opcional</span>
          </label>
          <input id="pac-email" type="text" autocomplete="email" placeholder="nome@exemplo.com">
          <span class="campo-erro" id="erro-pac-email" data-erro-de="pac-email"></span>
        </div>

        <div class="campo">
          <label class="campo-rotulo">Lembrete da consulta</label>
          <label class="campo-inline">
            <input type="checkbox" id="pac-consentimento">
            O paciente autorizou receber lembrete da consulta
          </label>
          <div class="campo" id="bloco-canal" hidden>
            <label class="campo-rotulo" for="pac-canal">Por onde</label>
            <select id="pac-canal">
              <option value="EMAIL">E-mail</option>
              <option value="NENHUM">Nenhum, por enquanto</option>
            </select>
          </div>
          <p class="aviso-privacidade">
            O consentimento fica registrado com a data. O paciente pode
            cancelar a qualquer momento pelo link do próprio e-mail.
          </p>
        </div>
      </div>

      <p class="anuncio" data-anuncio aria-live="polite"></p>

      <div class="dialogo-rodape">
        <button type="button" class="botao secundario" data-fechar="dialogo-paciente">Cancelar</button>
        <button type="submit" class="botao">Salvar paciente</button>
      </div>
    </form>
  </div>
</div>
```

- [ ] **Step 2: Ligar o formulário, com a regra condicional**

```js
const formPaciente = document.getElementById('form-paciente')
const chkConsentimento = document.getElementById('pac-consentimento')
const selCanal = document.getElementById('pac-canal')

/** E-mail e obrigatorio SE, e so se, o aviso esta ligado e o canal e e-mail. */
function emailObrigatorio() {
  return chkConsentimento.checked && selCanal.value === 'EMAIL'
}

/** O rotulo "opcional" some quando o campo passa a ser obrigatorio — a regra
    fica visivel ANTES do erro, que e o que a spec §6.2 exige. */
function refletirCanal() {
  document.getElementById('bloco-canal').hidden = !chkConsentimento.checked
  document.getElementById('rotulo-email-opcional').hidden = emailObrigatorio()
}
chkConsentimento.addEventListener('change', refletirCanal)
selCanal.addEventListener('change', refletirCanal)

document.getElementById('pac-telefone').addEventListener('input', (e) => {
  e.target.value = mascaraTelefone(e.target.value)
})

ligarFormulario(formPaciente, [
  { id: 'pac-nome',       regra: (v) => VALIDACAO.nome(v) },
  { id: 'pac-nascimento', regra: (v) => VALIDACAO.nascimento(v) },
  { id: 'pac-valor',      regra: (v) => VALIDACAO.valor(v) },
  { id: 'pac-telefone',   regra: (v) => VALIDACAO.telefone(v) },
  { id: 'pac-email',      regra: (v) => VALIDACAO.email(v, emailObrigatorio()) },
], () => {
  const nome = document.getElementById('pac-nome').value.trim()
  DADOS.pacientes.push({
    id: DADOS.pacientes.length + 1, nome,
    desde: DADOS.hoje.data, valor: Number(document.getElementById('pac-valor').value),
    consentimento: chkConsentimento.checked ? DADOS.hoje.data : null,
  })
  pintarPacientes()
  fecharDialogo('dialogo-paciente')
  const status = document.getElementById('status-pacientes')
  status.className = 'status-pacientes ok'
  status.textContent = `${nome} cadastrado.`
})
```

- [ ] **Step 3: Trocar o stub pelo diálogo**

O `+ Novo paciente` hoje escreve "fora do escopo deste protótipo". Passa a
chamar `abrirDialogo('dialogo-paciente')`. O `Editar` da ficha abre o mesmo
diálogo com o título trocado para `Editar paciente` e os campos preenchidos.

- [ ] **Step 4: Percorrer C5**

O teste de reprovação desta tarefa, na ordem:
1. Salvar tudo vazio: aparecem erros nos campos obrigatórios? O foco foi para o
   primeiro? O `aria-live` anunciou quantos?
2. Digitar o nome: o erro dele some **na hora**, sem esperar outro envio?
3. Marcar o consentimento com canal E-mail e o campo de e-mail vazio: o rótulo
   `opcional` sumiu **antes** de qualquer erro? Salvar mostra
   `Para avisar por e-mail, o endereço é obrigatório.`?
4. Telefone com máscara enquanto digita?
5. Data de nascimento no futuro é recusada com a mensagem certa?

Toda falha vira trava em `TRAVAS.md`, cenário `C5`.

- [ ] **Step 5: Fechamento** — **NÃO COMMITAR.**

---

### Task 5: Nova consulta, agendar retorno e série

**Files:**
- Modify: `prototipo/index.html`, `prototipo/dialogo.css` (se faltar algo)
- Reference: **C6**, spec §4② e §6.3

**Interfaces:**
- Consumes: `VALIDACAO.dataHora`, `ligarFormulario`, `abrirDialogo`
- Produces: `<div class="mascara" id="dialogo-consulta">`

Duas coisas que nenhum formulário do produto tem hoje, e que o C6 exercita:

**Conflito antes do envio.** A API sabe recusar (`ConflitoDeHorarioException`),
mas o front só descobre apanhando. Aqui a checagem roda ao trocar data ou hora,
e diz **quem** está no horário.

**Data no passado avisa, não bloqueia** — o psicólogo precisa registrar sessão
que já aconteceu. É a única regra do produto com aviso e erro separados, e por
isso `VALIDACAO.dataHora` devolve `{ erro, aviso }`.

**A data de retorno** — a lacuna que o dono achou no protótipo aprovado: hoje
"Agendar retorno" confirma um texto e nunca pergunta quando. Passa a abrir este
mesmo diálogo, com paciente travado e data **sugerida** em +7 dias, no mesmo
horário, editável.

- [ ] **Step 1: Escrever o diálogo**

```html
<div class="mascara" id="dialogo-consulta" hidden>
  <div class="dialogo" role="dialog" aria-modal="true" aria-labelledby="titulo-consulta">
    <div class="dialogo-cabecalho">
      <h2 id="titulo-consulta">Nova consulta</h2>
      <button class="botao secundario mini" data-fechar="dialogo-consulta">Fechar</button>
    </div>
    <form id="form-consulta">
      <div class="dialogo-corpo">
        <div class="campo">
          <label class="campo-rotulo" for="con-paciente">Paciente</label>
          <select id="con-paciente"></select>
          <span class="campo-erro" id="erro-con-paciente" data-erro-de="con-paciente"></span>
        </div>
        <div class="campo-duplo">
          <div class="campo">
            <label class="campo-rotulo" for="con-data">Data</label>
            <input id="con-data" type="date">
            <span class="campo-erro" id="erro-con-data" data-erro-de="con-data"></span>
          </div>
          <div class="campo">
            <label class="campo-rotulo" for="con-hora">Horário</label>
            <input id="con-hora" type="time" step="600">
            <span class="campo-erro" id="erro-con-hora" data-erro-de="con-hora"></span>
          </div>
        </div>
        <p class="campo-aviso" id="aviso-consulta"></p>
      </div>
      <p class="anuncio" data-anuncio aria-live="polite"></p>
      <div class="dialogo-rodape">
        <button type="button" class="botao secundario" data-fechar="dialogo-consulta">Cancelar</button>
        <button type="submit" class="botao" id="salvar-consulta">Agendar</button>
      </div>
    </form>
  </div>
</div>
```

- [ ] **Step 2: Preencher o seletor e checar conflito ao trocar data ou hora**

```js
// `DADOS.hoje.data` e humano ("terca, 1 de setembro"). O <input type="date">
// fala ISO. Esta constante e a ponte, e ela e usada por quemOcupa() abaixo.
const ISO_DE_HOJE = '2026-09-01'

function pintarSelecaoPaciente() {
  document.getElementById('con-paciente').innerHTML =
    '<option value="">Escolha o paciente</option>' +
    DADOS.pacientes.map((p) => `<option value="${p.nome}">${p.nome}</option>`).join('')
}

/** Quem ja esta naquele horario, olhando o dado do prototipo. Devolve o nome
    ou null. A API real tem GET /consultas/conflito (spec §5.6). */
function quemOcupa(data, hora) {
  // no prototipo so o dia de hoje tem ocupacao conhecida
  if (data !== ISO_DE_HOJE) return null
  const s = DADOS.hoje.sessoes.find((x) => x.hora === hora)
  return s ? s.paciente : null
}

function refletirDataHora() {
  const data = document.getElementById('con-data').value
  const hora = document.getElementById('con-hora').value
  const aviso = document.getElementById('aviso-consulta')
  const ocupado = quemOcupa(data, hora)
  const r = VALIDACAO.dataHora(data, hora)
  const salvar = document.getElementById('salvar-consulta')

  salvar.disabled = false
  if (ocupado) {
    // conflito e ERRO (bloqueia); passado e AVISO (deixa seguir)
    aviso.textContent = `Esse horário já está ocupado por ${ocupado}. Escolha outro.`
    aviso.className = 'campo-erro'
    salvar.disabled = true
    return
  }
  aviso.className = 'campo-aviso'
  aviso.textContent = r.aviso || ''
}
document.getElementById('con-data').addEventListener('change', refletirDataHora)
document.getElementById('con-hora').addEventListener('change', refletirDataHora)
```

**Cuidado:** `salvar-consulta` desabilitado é a única exceção autorizada à regra
"nenhum clique engolido em silêncio" — e só porque a razão está escrita na tela,
logo acima do botão, com o nome de quem ocupa. Botão desabilitado **sem** motivo
visível continua sendo defeito.

- [ ] **Step 3: Ligar o formulário e os três pontos de entrada**

`+ Nova consulta` (Agenda), clique em slot livre da grade, e `Agendar retorno`
(Ficha) abrem o mesmo diálogo. O retorno entra com paciente travado e data
sugerida:

```js
function abrirRetorno(nomePaciente, horaAtual) {
  const d = new Date(2026, 8, 1)                   // hoje: terca, 1 de setembro
  d.setDate(d.getDate() + 7)                       // sugestao: mesma hora, +7 dias
  document.getElementById('titulo-consulta').textContent = 'Agendar retorno'
  pintarSelecaoPaciente()
  document.getElementById('con-data').value = d.toISOString().slice(0, 10)
  document.getElementById('con-hora').value = horaAtual
  const sel = document.getElementById('con-paciente')
  sel.value = nomePaciente
  sel.disabled = true                              // paciente e implicito no retorno
  abrirDialogo('dialogo-consulta')
  refletirDataHora()                               // ja mostra conflito, se houver
}
```

E o formulário em si. **Atenção ao tipo:** `VALIDACAO.dataHora` devolve
`{ erro, aviso }`, e `ligarFormulario` espera `string | null`. Por isso as duas
regras de data/hora extraem só o `.erro` — o `.aviso` é responsabilidade do
`refletirDataHora`, que roda no `change`, não na validação de envio:

```js
ligarFormulario(document.getElementById('form-consulta'), [
  { id: 'con-paciente', regra: (v) => v ? null : 'Escolha o paciente.' },
  // dataHora devolve objeto; aqui so o erro interessa, o aviso e do refletir
  { id: 'con-data', regra: (v, f) => VALIDACAO.dataHora(v, f.querySelector('#con-hora').value).erro },
  { id: 'con-hora', regra: (v, f) => VALIDACAO.dataHora(f.querySelector('#con-data').value, v).erro },
], () => {
  const nome = document.getElementById('con-paciente').value
  const data = document.getElementById('con-data').value
  const hora = document.getElementById('con-hora').value
  fecharDialogo('dialogo-consulta')
  const status = document.getElementById('status-agenda')
  status.className = 'status-agenda ok'
  status.textContent = `Consulta de ${nome} agendada para ${data} às ${hora}.`
})
```

Como as duas regras de data e hora se leem uma à outra pelo `form`, preencher
qualquer um dos dois limpa o erro do outro — que é o comportamento certo: o erro
`Escolha data e horário.` é dos dois campos juntos, não de um só.

`Série semanal` fica como stub declarado — mas o stub precisa dizer que está
fora do protótipo, como os outros. Registrar em `TRAVAS.md` como `[fatia]`.

- [ ] **Step 4: Percorrer C6**

1. Retorno do Diego na próxima terça, mesmo horário: a data veio sugerida?
2. Trocar para um horário ocupado: aparece **quem** está lá, antes de tentar
   salvar?
3. Lançar sessão de quinta passada: aparece o aviso de retroativa, e **deixa
   salvar**?

- [ ] **Step 5: Fechamento** — **NÃO COMMITAR.**

---

### Task 6: Estados vazios, texto do aviso, percurso e veredito

**Files:**
- Modify: `prototipo/index.html`, `prototipo/dados.js`, `prototipo/TRAVAS.md`
- Reference: **C7**, spec §4⑤

**Interfaces:**
- Produces: `DADOS.vazio`, `alternarConjunto()`, e o veredito da Fase 0.5

Duas coisas e o fechamento.

**Estados vazios.** Nenhuma tela tem um. Todas assumem consultório em regime.
Para poder olhar o dia 1 sem apagar o dado de demonstração, entra um alternador
no canto — `Ver como fica vazio` — que troca entre os dois conjuntos.

**Texto do aviso editável** (§4⑤) — a segunda coisa que trava a Fatia 2. Com uma
consequência de privacidade que precisa estar na tela: se o psicólogo edita
livremente, a garantia de "zero conteúdo clínico" de §7.1 deixa de ser
estrutural e vira confiança. Por isso o campo é editável **mas o assunto não**,
e a tela diz isso.

- [ ] **Step 1: O conjunto vazio**

```js
/** O dia 1 do psicologo pagante: zero de tudo. A Fase 0 nao desenhou isto em
    tela nenhuma, e e de onde "amador" costuma vir. */
DADOS.vazio = {
  usuario: { nome: 'Marina Aguiar', valorPadrao: 180, antecedencia: '24h' },
  hoje: { data: 'segunda, 7 de setembro', agora: '08:40', sessoes: [], pendencias: [] },
  pacientes: [],
  financeiro: { mes: 'setembro', faturado: 0, recebido: 0, aReceber: 0, devendo: [] },
  linhaTempo: [],
  pendenciasPaciente: [],
}
```

- [ ] **Step 2: Os quatro estados vazios**

Cada um diz **por que está vazio** e **qual é a primeira coisa a fazer**. Vazio
que não explica parece quebrado — é o ponto inteiro do C7.

```js
const VAZIOS = {
  'lista-dia': {
    titulo: 'Nenhuma sessão hoje',
    texto: 'Quando você agendar consultas, elas aparecem aqui na linha do dia.',
    acao: { rotulo: 'Abrir a agenda', href: '#/agenda' },
  },
  'lista-pacientes': {
    titulo: 'Nenhum paciente ainda',
    texto: 'Cadastre o primeiro paciente para começar a agendar sessões e registrar anotações.',
    acao: { rotulo: '+ Novo paciente', dialogo: 'dialogo-paciente' },
  },
  'devendo': {
    titulo: 'Ninguém devendo',
    texto: 'Sessões realizadas sem baixa aparecem aqui.',
  },
  'pendencias': {
    titulo: 'Nada pendente',
    texto: 'Sessão sem status, sem anotação ou sem baixa aparece aqui.',
  },
}

function pintarVazio(id) {
  const v = VAZIOS[id]
  const acao = v.acao
    ? (v.acao.href
        ? `<a class="botao" href="${v.acao.href}">${v.acao.rotulo}</a>`
        : `<button class="botao" data-abre="${v.acao.dialogo}">${v.acao.rotulo}</button>`)
    : ''
  document.getElementById(id).innerHTML = `
    <div class="estado-vazio">
      <span class="titulo">${v.titulo}</span>
      <p class="texto">${v.texto}</p>
      ${acao}
    </div>`
}
```

Cada função de pintura (`pintarHoje`, `pintarPacientes`, `pintarDevendo`,
`pintarLinhaTempo`) passa a checar lista vazia **antes** de montar o markup
normal, e chama `pintarVazio` quando for o caso.

O `[data-abre]` precisa de handler — botão de estado vazio que não faz nada é o
pior lugar possível para um clique morto, porque é o primeiro que o usuário novo
encontra. Delegado, igual ao `[data-fechar]` da Task 3:

```js
document.addEventListener('click', (evento) => {
  const alvo = evento.target.closest('[data-abre]')
  if (alvo) abrirDialogo(alvo.dataset.abre)
})
```

- [ ] **Step 3: O alternador**

Um botão no cabeçalho de Hoje: `Ver como fica vazio` / `Ver com dados`. Troca as
chaves de `DADOS` pelas de `DADOS.vazio` (guardando as originais) e repinta
tudo. É andaime de protótipo — **rotular como tal na tela**, do mesmo jeito que
"Provisório" foi rotulado na Agenda.

- [ ] **Step 4: Texto do aviso editável**

Em Ajustes, abaixo da antecedência: um `<textarea>` com o corpo do lembrete,
pré-preenchido com o texto atual, que atualiza a prévia ao vivo. Ao lado, em
`.aviso-privacidade`:

> O assunto é fixo e neutro, e não pode ser editado: ele aparece na tela de
> bloqueio do celular do paciente. Não escreva aqui nada sobre o motivo da
> consulta.

**O assunto não vira campo.** Se virar, a garantia de §7.1 sai do produto.

- [ ] **Step 5: Percorrer C5, C6 e C7 em sequência**

Seguidos, navegando só pelo que a interface oferece. Depois abrir o conjunto
vazio e percorrer C7 inteiro: dá para entender que está vazio por ser novo, e
qual é o primeiro passo?

- [ ] **Step 6: Classificar as travas e escrever o veredito**

Mesmos três baldes da Fase 0 — `[layout]`, `[fatia]`, `[escopo]` — mais o
`[decisão do dono]` que já existe. No fim de `TRAVAS.md`, um veredito da Fase
0.5 que responda explicitamente:

- A spec §6.2 saiu desta fase **com desenho**? (era o Critical da Fase 0)
- O formulário de paciente com consentimento existe? (destravava §4③)
- O texto do aviso existe? (destravava §4⑤)
- **A Fatia 2 continua bloqueada, ou foi liberada?**

Sem meio-termo, e sem soar mais confiante do que a evidência sustenta.

- [ ] **Step 7: Fechamento** — **NÃO COMMITAR.**

---

## O que esta fase NÃO faz

- React, build, dependência, `package.json`
- Chamada de API, backend, banco, migration
- Envio de e-mail de verdade
- Autenticação, tela de login
- Série semanal (fica stub declarado, registrado como `[fatia]`)
