# Fase 0 — Protótipo navegável: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar o mapa de telas do redesenho num protótipo navegável, percorrido
contra cenários escritos, antes de qualquer linha de React.

**Architecture:** Um único `prototipo/index.html` com roteamento por hash e dado
falso embutido, mais `prototipo/tokens.css` herdado do front atual. Sem build,
sem framework, sem API. O protótipo é **descartável** — só os tokens e o mapa de
telas sobrevivem para a Fatia 1.

**Tech Stack:** HTML, CSS, JavaScript sem dependência. Tokens portados de
`frontend/src/estilo/tokens.css`.

**Spec:** `docs/superpowers/specs/2026-09-01-redesenho-produto-design.md`

## Global Constraints

- **Tokens são lei.** Cores, tipografia e espaçamento saem de `tokens.css`.
  Nenhum valor de cor escrito à mão no HTML.
- **Azul é só ação.** `--color-acento` aparece em botão primário, item ativo da
  navegação e ícone de seção. Mais nada no produto usa azul. Regra herdada do
  front atual, e é o que faz o olho achar o clicável sem procurar.
- **Navegação sempre com rótulo escrito.** Ícone sozinho é proibido neste
  protótipo — é o defeito que a Fase 0 existe para corrigir (spec §4).
- **Dado falso é inventado, nunca real.** Nenhum nome, telefone, e-mail ou
  anotação de pessoa real entra no protótipo, nem "só para testar".
- **Nada de conteúdo clínico em assunto de aviso.** Onde o protótipo mostrar a
  prévia de um e-mail, ela segue a regra da spec §7.1: assunto neutro, sem
  "psicólogo", "consulta", "sessão" ou "terapia".
- **Cinco destinos, nesta ordem:** Hoje · Agenda · Pacientes · Financeiro ·
  Ajustes.

## O ciclo de teste desta fase

Protótipo estático não tem teste automatizado, e fingir TDD aqui seria teatro. O
ciclo de verificação da Fase 0 é outro, e é obrigatório em cada tarefa de tela:

1. Abrir o protótipo na tela que a tarefa entregou
2. Percorrer o cenário escrito correspondente (Task 1), do começo ao fim, sem
   pular passo
3. Anotar **toda** trava em `prototipo/TRAVAS.md`: onde o olho procurou algo que
   não estava lá, onde faltou informação para decidir, onde o próximo passo não
   era óbvio
4. Corrigir no protótipo o que for de layout ou de informação
5. Trava que exigir mudança de escopo **não** se conserta aqui — vira entrada em
   `TRAVAS.md` marcada `[escopo]` e sobe para revisão humana

Uma tarefa de tela só fecha quando o cenário dela foi percorrido inteiro e as
travas estão registradas.

## File Structure

| Arquivo | Responsabilidade |
|---|---|
| `prototipo/CENARIOS.md` | os quatro roteiros que o protótipo precisa aguentar |
| `prototipo/TRAVAS.md` | o que travou em cada percurso, e o que foi feito |
| `prototipo/tokens.css` | tokens portados; a única saída da fase que vira código de verdade |
| `prototipo/estilo.css` | layout e componentes do protótipo (descartável) |
| `prototipo/dados.js` | dado falso das cinco telas, num lugar só |
| `prototipo/index.html` | as cinco telas + roteamento por hash |

---

### Task 1: Os quatro cenários

**Files:**
- Create: `prototipo/CENARIOS.md`

**Interfaces:**
- Produces: os quatro cenários nomeados `C1`..`C4`, citados por nome em todas as
  tarefas seguintes e em `TRAVAS.md`.

Sem acesso a psicólogo, o cenário escrito é o que substitui o usuário. Ele
precisa ser específico o bastante para travar: hora do dia, quantidade de
sessões, o que aconteceu semana passada. Cenário vago não reprova tela nenhuma.

- [ ] **Step 1: Escrever os cenários**

```markdown
# Cenários — Fase 0

Percursos que o protótipo precisa aguentar. Cada tarefa de tela cita o seu.
Não são casos de teste: são o dia de alguém.

## C1 — Antes do expediente (tela Hoje)

São 8h40 de uma terça. Tenho 6 sessões hoje, a primeira às 9h.
A paciente das 9h faltou sem avisar na semana passada — preciso saber disso
antes de ela entrar na sala, e não depois.
Ontem atendi 5 pessoas e esqueci de marcar presença em duas.

Preciso responder, sem clicar em nada:
- quem é a próxima e daqui a quanto tempo
- quantas sessões o dia tem
- o que ficou pendente de ontem

E preciso conseguir, em um clique: fechar a pendência de ontem.

## C2 — Antes e depois da sessão (Pacientes e Ficha)

São 8h55. Abro a ficha da paciente das 9h.
Preciso lembrar, sem caçar: o que ficou combinado na última sessão, há quanto
tempo ela vem, se ela faltou antes, e se tem alguma pendência que eu mesmo
anotei.

São 9h52, a sessão acabou. Preciso, sem sair da ficha:
registrar a anotação desta sessão, marcar que ela aconteceu, e agendar o
retorno para a próxima terça no mesmo horário.

## C3 — Fechamento do mês (Financeiro)

É dia 30. Quero saber quanto faturei, quanto entrou de fato, e quem está
devendo.
Duas pessoas pagam no cartão e sempre atrasam; uma paga por sessão, em dinheiro,
no dia.
Preciso dar baixa em 4 sessões da semana passada sem abrir 4 telas.

## C4 — Ligar o aviso (Ficha e Ajustes)

Cadastrei uma paciente nova hoje. Ela topou receber lembrete da sessão por
e-mail.
Preciso registrar esse consentimento, escolher com quanta antecedência o aviso
sai, e ver como a mensagem vai chegar para ela — inclusive o assunto, porque
o marido dela usa o mesmo computador.
```

- [ ] **Step 2: Conferir a especificidade**

Reler os quatro. Cada um precisa ter: hora concreta, quantidade concreta, e pelo
menos um fato do passado que a tela tem de mostrar sem ser perguntada. Cenário
que não tem isso não trava tela nenhuma — reescrever.

- [ ] **Step 3: Commit**

```bash
git add prototipo/CENARIOS.md
git commit -m "docs(prototipo): quatro cenarios que o mapa de telas precisa aguentar"
```

---

### Task 2: Tokens, shell e navegação

**Files:**
- Create: `prototipo/tokens.css`, `prototipo/estilo.css`, `prototipo/dados.js`, `prototipo/index.html`, `prototipo/TRAVAS.md`
- Reference: `frontend/src/estilo/tokens.css` (origem dos valores)

**Interfaces:**
- Produces: `mostrarTela(nome)` — troca a tela visível pelo hash; as telas
  seguintes registram-se como `<section id="tela-hoje">`, `id="tela-agenda"`,
  `id="tela-pacientes"`, `id="tela-ficha"`, `id="tela-financeiro"`,
  `id="tela-ajustes"`.
- Produces: `DADOS` — objeto global de `dados.js` com as chaves `usuario`,
  `hoje`, `pacientes`, `financeiro`. A Task 5 acrescenta `linhaTempo` e
  `pendenciasPaciente`; nenhuma outra chave existe.

- [ ] **Step 1: Portar os tokens**

Copiar os valores de `frontend/src/estilo/tokens.css` para
`prototipo/tokens.css`, **sem** o `@import "tailwindcss"` e sem o bloco
`@theme` — o protótipo não usa Tailwind. Vira CSS puro:

```css
:root {
  --fundo: #f4f7fb;
  --superficie: #ffffff;
  --superficie-2: #f8fafc;

  --tinta: #0f172a;
  --tinta-2: #4b5a70;
  --tinta-3: #94a3b8;
  --linha: #e5eaf2;

  /* azul e SO acao: botao primario, item ativo da navegacao, icone de secao */
  --acento: #2563eb;
  --acento-escuro: #1d4ed8;
  --acento-suave: #eff6ff;
  --acento-linha: #dbeafe;

  --sucesso: #047857;
  --sucesso-suave: #ecfdf5;
  --atencao: #b45309;
  --atencao-suave: #fffbeb;
  --atencao-linha: #fde68a;
  --perigo: #dc2626;
  --perigo-suave: #fef2f2;

  --fonte: "Inter", ui-sans-serif, system-ui, sans-serif;

  --gap-1: 4px;  --gap-2: 8px;  --gap-3: 12px;
  --gap-4: 16px; --gap-5: 20px; --gap-6: 24px; --gap-7: 32px;
  --raio: 12px;  --raio-g: 16px;
}

body {
  margin: 0;
  background: var(--fundo);
  color: var(--tinta);
  font-family: var(--fonte);
  -webkit-font-smoothing: antialiased;
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: .01ms !important;
    transition-duration: .01ms !important;
  }
}
```

- [ ] **Step 2: Escrever o shell com navegação rotulada**

`prototipo/index.html`. A barra lateral tem **ícone e texto** — o icon-only é o
defeito que esta fase corrige.

```html
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Protótipo — consultório</title>
  <link rel="stylesheet" href="tokens.css">
  <link rel="stylesheet" href="estilo.css">
</head>
<body>
  <div class="moldura">
    <nav class="lateral" aria-label="Navegação principal">
      <a class="marca" href="#/hoje">Consultório</a>
      <ul>
        <li><a href="#/hoje"      data-tela="hoje">🗓 <span>Hoje</span></a></li>
        <li><a href="#/agenda"    data-tela="agenda">📆 <span>Agenda</span></a></li>
        <li><a href="#/pacientes" data-tela="pacientes">👥 <span>Pacientes</span></a></li>
        <li><a href="#/financeiro" data-tela="financeiro">💰 <span>Financeiro</span></a></li>
      </ul>
      <a class="rodape" href="#/ajustes" data-tela="ajustes">⚙ <span>Ajustes</span></a>
    </nav>
    <main id="conteudo"></main>
  </div>

  <script src="dados.js"></script>
  <script>
    const TELAS = ['hoje','agenda','pacientes','ficha','financeiro','ajustes']

    function mostrarTela(nome) {
      const alvo = TELAS.includes(nome) ? nome : 'hoje'
      document.querySelectorAll('#conteudo > section').forEach((secao) => {
        secao.hidden = secao.id !== `tela-${alvo}`
      })
      document.querySelectorAll('.lateral a[data-tela]').forEach((link) => {
        const ativo = link.dataset.tela === alvo
          || (alvo === 'ficha' && link.dataset.tela === 'pacientes')
        link.classList.toggle('ativo', ativo)
        if (ativo) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current')
      })
    }

    function rotear() { mostrarTela((location.hash || '#/hoje').replace('#/', '')) }
    window.addEventListener('hashchange', rotear)
    window.addEventListener('DOMContentLoaded', rotear)
  </script>
</body>
</html>
```

- [ ] **Step 3: Escrever o layout base**

`prototipo/estilo.css`. Só o que o shell precisa; cada tela acrescenta o seu.

```css
.moldura { display: flex; min-height: 100vh; }

.lateral {
  position: sticky; top: 0; height: 100vh;
  width: 208px; flex-shrink: 0;
  display: flex; flex-direction: column; gap: var(--gap-1);
  padding: var(--gap-5) var(--gap-3);
  background: var(--superficie);
  border-right: 1px solid var(--linha);
}
.lateral .marca {
  font-weight: 700; font-size: 15px; color: var(--tinta);
  text-decoration: none; padding: 0 var(--gap-3) var(--gap-5);
}
.lateral ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
.lateral a[data-tela] {
  display: flex; align-items: center; gap: var(--gap-3);
  padding: 10px var(--gap-3); border-radius: var(--raio);
  color: var(--tinta-2); text-decoration: none; font-size: 14px; font-weight: 500;
}
.lateral a[data-tela]:hover { background: var(--superficie-2); color: var(--tinta); }
/* azul = acao/posicao, e so aqui na navegacao */
.lateral a.ativo { background: var(--acento); color: #fff; }
.lateral .rodape { margin-top: auto; }

main { flex: 1; min-width: 0; padding: var(--gap-7) var(--gap-7); }
main > section { max-width: 960px; margin: 0 auto; }

.cartao {
  background: var(--superficie); border: 1px solid var(--linha);
  border-radius: var(--raio-g); padding: var(--gap-5);
}
h1 { font-size: 26px; margin: 0 0 var(--gap-1); letter-spacing: -.02em; }
.sub { color: var(--tinta-2); font-size: 14px; margin: 0 0 var(--gap-6); }
.botao {
  border: 0; border-radius: var(--raio); padding: 9px var(--gap-4);
  background: var(--acento); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
}
.botao.secundario { background: var(--superficie); color: var(--tinta); border: 1px solid var(--linha); }
```

- [ ] **Step 4: Semear o dado falso**

`prototipo/dados.js`. Nomes inventados — nenhum dado de pessoa real.

```js
const DADOS = {
  usuario: { nome: 'Marina Aguiar', valorPadrao: 180, antecedencia: '24h' },
  hoje: {
    data: 'terça, 1 de setembro',
    agora: '08:40',
    sessoes: [
      { hora: '09:00', paciente: 'Helena Vaz',    status: 'agendada', faltouAntes: true },
      { hora: '10:00', paciente: 'Rui Sampaio',   status: 'agendada' },
      { hora: '11:00', paciente: 'Clara Bueno',   status: 'agendada' },
      { hora: '14:00', paciente: 'Otávio Lins',   status: 'agendada' },
      { hora: '15:00', paciente: 'Bruna Teixeira',status: 'agendada' },
      { hora: '16:00', paciente: 'Ivan Prado',    status: 'agendada' },
    ],
    pendencias: [
      { tipo: 'sem-status',   texto: 'Rui Sampaio · ontem 10:00' },
      { tipo: 'sem-status',   texto: 'Clara Bueno · ontem 11:00' },
      { tipo: 'sem-anotacao', texto: 'Otávio Lins · ontem 14:00' },
      { tipo: 'sem-baixa',    texto: 'Bruna Teixeira · 4 sessões' },
    ],
  },
  pacientes: [
    { id: 1, nome: 'Helena Vaz', desde: 'março de 2026', valor: 180, consentimento: null },
    { id: 2, nome: 'Rui Sampaio', desde: 'janeiro de 2026', valor: 180, consentimento: '2026-08-02' },
    { id: 3, nome: 'Clara Bueno', desde: 'agosto de 2026', valor: 200, consentimento: null },
  ],
  financeiro: {
    mes: 'setembro', faturado: 4320, recebido: 3060, aReceber: 1260,
    devendo: [
      { paciente: 'Bruna Teixeira', sessoes: 4, valor: 720 },
      { paciente: 'Ivan Prado',     sessoes: 3, valor: 540 },
    ],
  },
}
```

- [ ] **Step 5: Abrir e conferir a navegação**

Rodar: `python3 -m http.server 8000 --directory prototipo` e abrir
`http://localhost:8000`.

Esperado: cinco destinos com **texto legível**, o ativo em azul, o hash mudando
ao clicar. `#/hoje` e URL sem hash caem na mesma tela.

**A área de conteúdo fica vazia nesta tarefa** — as seções chegam nas Tasks 3 a
7. Vazio aqui é o resultado certo, não uma falha do roteador.

- [ ] **Step 6: Criar o registro de travas**

```markdown
# Travas — Fase 0

Uma linha por trava encontrada ao percorrer um cenário.
`[escopo]` marca o que não se conserta no protótipo e sobe para revisão humana.

| Cenário | Onde | O que travou | Resolução |
|---|---|---|---|
```

- [ ] **Step 7: Commit**

```bash
git add prototipo/
git commit -m "feat(prototipo): shell com navegacao rotulada, tokens e dado falso"
```

---

### Task 3: Tela Hoje

**Files:**
- Modify: `prototipo/index.html` (acrescenta `<section id="tela-hoje">`), `prototipo/estilo.css`
- Reference: `prototipo/CENARIOS.md` → **C1**

**Interfaces:**
- Consumes: `DADOS.hoje`, `mostrarTela` (Task 2)
- Produces: `.destaque`, `.linha-sessao`, `.faixa-pendencias` — classes que a
  Fatia 1 vai traduzir em componente React

Esta é a tela que responde C1 sem clique. Se o psicólogo precisar clicar para
saber quem é a próxima, a tela falhou.

- [ ] **Step 1: Escrever a seção**

Inserir dentro de `<main id="conteudo">`:

```html
<section id="tela-hoje" hidden>
  <div class="cabecalho-dia">
    <div>
      <h1>Olá, Marina</h1>
      <p class="sub">terça, 1 de setembro · 6 sessões hoje</p>
    </div>
    <button class="botao secundario" id="privacidade">👁 Modo privacidade</button>
  </div>

  <div class="cartao destaque">
    <span class="etiqueta">Próxima, em 20 minutos</span>
    <strong class="nome-grande">09:00 · Helena Vaz</strong>
    <p class="alerta-falta">⚠ Faltou sem avisar na semana passada</p>
    <a class="botao" href="#/ficha">Abrir ficha</a>
  </div>

  <h2 class="titulo-secao">O dia</h2>
  <div class="cartao lista-dia" id="lista-dia"></div>

  <h2 class="titulo-secao">Pendente de ontem</h2>
  <div class="cartao faixa-pendencias" id="pendencias"></div>
</section>
```

- [ ] **Step 2: Preencher a partir do dado falso**

Acrescentar ao `<script>` do `index.html`:

```js
function pintarHoje() {
  document.getElementById('lista-dia').innerHTML = DADOS.hoje.sessoes.map((s) => `
    <div class="linha-sessao">
      <span class="hora">${s.hora}</span>
      <span class="paciente">${s.paciente}</span>
      ${s.faltouAntes ? '<span class="marca-falta">faltou antes</span>' : '<span></span>'}
      <span class="acoes">
        <button class="botao secundario mini">Realizada</button>
        <button class="botao secundario mini">Faltou</button>
      </span>
    </div>`).join('')

  document.getElementById('pendencias').innerHTML = DADOS.hoje.pendencias.map((p) => `
    <div class="linha-pendencia" data-tipo="${p.tipo}">
      <span>${p.texto}</span>
      <button class="botao secundario mini">Resolver</button>
    </div>`).join('')
}
window.addEventListener('DOMContentLoaded', pintarHoje)
```

- [ ] **Step 3: Ligar o modo privacidade**

A decisão da spec §4: o painel fica aberto em tela cheia no consultório.

```js
document.getElementById('privacidade').addEventListener('click', () => {
  document.body.classList.toggle('privado')
})
```

```css
body.privado .paciente,
body.privado .nome-grande,
body.privado .linha-pendencia span:first-child { filter: blur(5px); }
```

- [ ] **Step 4: Estilo da tela**

```css
.cabecalho-dia { display: flex; justify-content: space-between; align-items: flex-start; }
.destaque { border-color: var(--acento-linha); background: var(--acento-suave); margin-bottom: var(--gap-6); }
.etiqueta { font-size: 13px; color: var(--tinta-2); }
.nome-grande { display: block; font-size: 24px; margin: var(--gap-1) 0 var(--gap-2); }
.alerta-falta { color: var(--atencao); background: var(--atencao-suave); border: 1px solid var(--atencao-linha);
  border-radius: var(--raio); padding: var(--gap-2) var(--gap-3); font-size: 13px; margin: 0 0 var(--gap-4); }
.titulo-secao { font-size: 13px; text-transform: uppercase; letter-spacing: .06em;
  color: var(--tinta-3); margin: var(--gap-6) 0 var(--gap-3); }
.lista-dia, .faixa-pendencias { padding: 0; }
.linha-sessao, .linha-pendencia {
  display: grid; grid-template-columns: 64px 1fr auto auto; gap: var(--gap-3);
  align-items: center; padding: var(--gap-3) var(--gap-5); border-bottom: 1px solid var(--linha); }
.linha-pendencia { grid-template-columns: 1fr auto; }
.linha-sessao:last-child, .linha-pendencia:last-child { border-bottom: 0; }
.hora { font-variant-numeric: tabular-nums; font-weight: 600; }
.marca-falta { font-size: 12px; color: var(--atencao); }
.botao.mini { padding: 5px 10px; font-size: 13px; font-weight: 500; }
```

- [ ] **Step 5: Percorrer C1**

Abrir `#/hoje` e seguir C1 do começo ao fim. Responder, **sem clicar**: quem é a
próxima e em quanto tempo; quantas sessões o dia tem; o que ficou de ontem. E
tentar fechar uma pendência de ontem em um clique.

- [ ] **Step 6: Registrar travas**

Toda trava vai para `prototipo/TRAVAS.md`, cenário `C1`. Consertar o que for
layout ou informação faltando. Marcar `[escopo]` o que exigir decisão nova.

- [ ] **Step 7: Commit**

```bash
git add prototipo/
git commit -m "feat(prototipo): tela Hoje com proxima sessao, dia e pendencias"
```

---

### Task 4: Tela Agenda

**Files:**
- Modify: `prototipo/index.html`, `prototipo/estilo.css`
- Reference: **C2** (o trecho do agendamento de retorno)

**Interfaces:**
- Consumes: `DADOS.hoje.sessoes`, `mostrarTela`
- Produces: `.grade-semana`, `.slot`, `.slot.ocupado`

- [ ] **Step 1: Escrever a seção**

A semana é o padrão — "hoje" já é a tela ①.

```html
<section id="tela-agenda" hidden>
  <h1>Agenda</h1>
  <p class="sub">Semana de 31 de agosto a 6 de setembro</p>

  <div class="barra-agenda">
    <button class="botao secundario mini">← Semana anterior</button>
    <button class="botao secundario mini">Semana seguinte →</button>
    <button class="botao">+ Nova consulta</button>
    <button class="botao secundario">Série semanal</button>
  </div>

  <div class="cartao grade-semana" id="grade"></div>

  <p class="nota-conflito">
    Horário ocupado aparece em vermelho <strong>antes</strong> de tentar salvar.
  </p>
</section>
```

- [ ] **Step 2: Desenhar a grade**

```js
function pintarAgenda() {
  const dias = ['seg 31','ter 1','qua 2','qui 3','sex 4']
  const horas = ['09:00','10:00','11:00','14:00','15:00','16:00']
  const ocupados = { 'ter 1': DADOS.hoje.sessoes.map((s) => s.hora) }

  const cabecalho = `<div class="slot rotulo"></div>` +
    dias.map((d) => `<div class="slot rotulo">${d}</div>`).join('')

  const corpo = horas.map((h) =>
    `<div class="slot rotulo hora">${h}</div>` +
    dias.map((d) => {
      const ocupado = (ocupados[d] || []).includes(h)
      const quem = ocupado ? DADOS.hoje.sessoes.find((s) => s.hora === h).paciente : ''
      return `<div class="slot ${ocupado ? 'ocupado' : ''}">${quem}</div>`
    }).join('')
  ).join('')

  document.getElementById('grade').innerHTML = cabecalho + corpo
}
window.addEventListener('DOMContentLoaded', pintarAgenda)
```

- [ ] **Step 3: Estilo da grade**

```css
.barra-agenda { display: flex; gap: var(--gap-3); margin-bottom: var(--gap-5); flex-wrap: wrap; }
.grade-semana { display: grid; grid-template-columns: 72px repeat(5, 1fr); gap: 1px;
  background: var(--linha); padding: 1px; }
.slot { background: var(--superficie); min-height: 46px; padding: var(--gap-2);
  font-size: 13px; display: flex; align-items: center; }
.slot.rotulo { background: var(--superficie-2); color: var(--tinta-2); font-weight: 600; justify-content: center; }
.slot.hora { font-variant-numeric: tabular-nums; }
.slot.ocupado { background: var(--acento-suave); color: var(--acento-escuro); font-weight: 500; }
.nota-conflito { font-size: 13px; color: var(--tinta-2); margin-top: var(--gap-4); }
```

- [ ] **Step 4: Percorrer o trecho de C2**

"agendar o retorno para a próxima terça no mesmo horário" — tentar fazer isso
olhando só a agenda. Contar quantos passos custa.

- [ ] **Step 5: Registrar travas em `TRAVAS.md` (cenário C2)**

- [ ] **Step 6: Commit**

```bash
git add prototipo/
git commit -m "feat(prototipo): agenda semanal com horario ocupado visivel"
```

---

### Task 5: Pacientes e Ficha

**Files:**
- Modify: `prototipo/index.html`, `prototipo/estilo.css`, `prototipo/dados.js`
- Reference: **C2** inteiro

**Interfaces:**
- Consumes: `DADOS.pacientes`, `mostrarTela`
- Produces: `.linha-tempo`, `.sessao-item` — a linha do tempo unificada que
  substitui as abas *Consultas* e *Anotações* (spec §4 ③, §5.1)

Esta é a tarefa que prova a decisão mais forte da spec: **a ficha vira uma
página só**. Se a linha do tempo não resolver C2, a decisão precisa voltar.

- [ ] **Step 1: Acrescentar a linha do tempo ao dado falso**

Em `dados.js`, dentro do objeto `DADOS`:

```js
DADOS.linhaTempo = [
  { data: '26 ago', status: 'realizada', valor: 180, pago: true,
    anotacao: 'Trouxe o registro de sono da semana. Dormiu mal nas noites de domingo.' },
  { data: '19 ago', status: 'faltou', valor: 180, pago: false, anotacao: null },
  { data: '12 ago', status: 'realizada', valor: 180, pago: true,
    anotacao: 'Combinamos que ela anota o sono até a próxima sessão.' },
]
DADOS.pendenciasPaciente = [
  { texto: 'Retomar o registro de sono', concluida: false },
]
```

- [ ] **Step 2: Escrever a lista de pacientes**

```html
<section id="tela-pacientes" hidden>
  <h1>Pacientes</h1>
  <p class="sub">3 cadastrados</p>
  <div class="barra-agenda">
    <input class="busca" type="search" placeholder="Buscar por nome" aria-label="Buscar paciente">
    <button class="botao">+ Novo paciente</button>
  </div>
  <div class="cartao" id="lista-pacientes"></div>
</section>
```

```js
function pintarPacientes() {
  document.getElementById('lista-pacientes').innerHTML = DADOS.pacientes.map((p) => `
    <a class="linha-paciente" href="#/ficha">
      <span class="paciente">${p.nome}</span>
      <span class="desde">desde ${p.desde}</span>
      <span class="valor">R$ ${p.valor},00</span>
    </a>`).join('')
}
window.addEventListener('DOMContentLoaded', pintarPacientes)
```

- [ ] **Step 3: Escrever a ficha de página única**

Sem abas. Cabeçalho, linha do tempo, pendências ao lado.

```html
<section id="tela-ficha" hidden>
  <a class="voltar" href="#/pacientes">← Pacientes</a>
  <div class="cabecalho-ficha cartao">
    <div>
      <h1>Helena Vaz</h1>
      <p class="sub">34 anos · desde março de 2026 · R$ 180,00 por sessão</p>
    </div>
    <div class="acoes-ficha">
      <span class="chip alerta">Aviso por e-mail: não autorizado</span>
      <button class="botao secundario mini">Editar</button>
    </div>
  </div>

  <div class="colunas-ficha">
    <div>
      <h2 class="titulo-secao">Sessões</h2>
      <div class="cartao registro-novo">
        <textarea placeholder="Anotação da sessão de hoje…" rows="3"></textarea>
        <div class="barra-agenda">
          <button class="botao">Registrar sessão</button>
          <button class="botao secundario">Agendar retorno</button>
        </div>
      </div>
      <div class="linha-tempo" id="linha-tempo"></div>
    </div>

    <aside>
      <h2 class="titulo-secao">Pendências</h2>
      <div class="cartao" id="pendencias-paciente"></div>
    </aside>
  </div>
</section>
```

- [ ] **Step 4: Pintar a linha do tempo**

Consulta e anotação **na mesma linha** — é o ponto inteiro da mudança.

```js
function pintarFicha() {
  const rotulo = { realizada: 'Realizada', faltou: 'Faltou', cancelada: 'Cancelada' }
  document.getElementById('linha-tempo').innerHTML = DADOS.linhaTempo.map((s) => `
    <article class="cartao sessao-item">
      <header>
        <strong>${s.data}</strong>
        <span class="chip ${s.status}">${rotulo[s.status]}</span>
        <span class="chip ${s.pago ? 'pago' : 'devendo'}">${s.pago ? 'pago' : 'a receber'}</span>
      </header>
      <p class="${s.anotacao ? '' : 'sem-anotacao'}">
        ${s.anotacao || 'Sem anotação registrada.'}
      </p>
    </article>`).join('')

  document.getElementById('pendencias-paciente').innerHTML = DADOS.pendenciasPaciente.map((p) => `
    <label class="linha-pendencia">
      <span><input type="checkbox"> ${p.texto}</span>
    </label>`).join('')
}
window.addEventListener('DOMContentLoaded', pintarFicha)
```

- [ ] **Step 5: Estilo da ficha**

```css
.busca { flex: 1; min-width: 200px; height: 38px; padding: 0 var(--gap-3);
  border: 1px solid var(--linha); border-radius: var(--raio); font-size: 14px; }
.linha-paciente { display: grid; grid-template-columns: 1fr auto auto; gap: var(--gap-4);
  padding: var(--gap-3) var(--gap-5); border-bottom: 1px solid var(--linha);
  text-decoration: none; color: var(--tinta); align-items: center; }
.linha-paciente:last-child { border-bottom: 0; }
.linha-paciente:hover { background: var(--superficie-2); }
.desde, .valor { font-size: 13px; color: var(--tinta-2); }
.voltar { font-size: 13px; color: var(--tinta-2); text-decoration: none; }
.cabecalho-ficha { display: flex; justify-content: space-between; align-items: flex-start;
  margin: var(--gap-3) 0 var(--gap-6); }
.acoes-ficha { display: flex; gap: var(--gap-3); align-items: center; }
.colunas-ficha { display: grid; grid-template-columns: 1fr 260px; gap: var(--gap-6); align-items: start; }
.registro-novo textarea { width: 100%; box-sizing: border-box; border: 1px solid var(--linha);
  border-radius: var(--raio); padding: var(--gap-3); font-size: 14px; resize: vertical; margin-bottom: var(--gap-3); }
.linha-tempo { display: flex; flex-direction: column; gap: var(--gap-3); margin-top: var(--gap-4); }
.sessao-item header { display: flex; gap: var(--gap-3); align-items: center; margin-bottom: var(--gap-2); }
.sessao-item p { margin: 0; font-size: 14px; color: var(--tinta-2); }
.sem-anotacao { color: var(--tinta-3); font-style: italic; }
.chip { font-size: 12px; padding: 2px 9px; border-radius: 999px; background: var(--superficie-2); color: var(--tinta-2); }
.chip.realizada { background: var(--sucesso-suave); color: var(--sucesso); }
.chip.faltou, .chip.devendo, .chip.alerta { background: var(--atencao-suave); color: var(--atencao); }
.chip.pago { background: var(--sucesso-suave); color: var(--sucesso); }
@media (max-width: 860px) { .colunas-ficha { grid-template-columns: 1fr; } }
```

- [ ] **Step 6: Percorrer C2 inteiro**

Os dois momentos: 8h55 (o que ficou combinado, há quanto tempo vem, se faltou,
pendências) e 9h52 (registrar, marcar realizada, agendar retorno **sem sair da
ficha**).

Pergunta de reprovação: **a linha do tempo respondeu, ou o olho ainda teve de
cruzar duas listas?** Se cruzou, a fusão falhou e isso é `[escopo]`.

- [ ] **Step 7: Registrar travas em `TRAVAS.md` (cenário C2)**

- [ ] **Step 8: Commit**

```bash
git add prototipo/
git commit -m "feat(prototipo): ficha de pagina unica com linha do tempo de sessoes"
```

---

### Task 6: Tela Financeiro

**Files:**
- Modify: `prototipo/index.html`, `prototipo/estilo.css`
- Reference: **C3**

**Interfaces:**
- Consumes: `DADOS.financeiro`
- Produces: `.indicadores`, `.indicador`

- [ ] **Step 1: Escrever a seção**

```html
<section id="tela-financeiro" hidden>
  <h1>Financeiro</h1>
  <p class="sub">setembro de 2026</p>

  <div class="indicadores">
    <div class="cartao indicador"><span>Faturado</span><strong>R$ 4.320</strong></div>
    <div class="cartao indicador"><span>Recebido</span><strong class="ok">R$ 3.060</strong></div>
    <div class="cartao indicador"><span>A receber</span><strong class="pendente">R$ 1.260</strong></div>
  </div>

  <h2 class="titulo-secao">Quem está devendo</h2>
  <div class="cartao" id="devendo"></div>

  <h2 class="titulo-secao">Sessões da semana passada</h2>
  <div class="cartao" id="baixa-lote">
    <div class="linha-pendencia"><span><input type="checkbox" checked> Bruna Teixeira · 24 ago · R$ 180</span><span></span></div>
    <div class="linha-pendencia"><span><input type="checkbox" checked> Bruna Teixeira · 26 ago · R$ 180</span><span></span></div>
    <div class="linha-pendencia"><span><input type="checkbox" checked> Ivan Prado · 25 ago · R$ 180</span><span></span></div>
    <div class="linha-pendencia"><span><input type="checkbox" checked> Ivan Prado · 27 ago · R$ 180</span><span></span></div>
    <div class="linha-pendencia"><span><strong>4 selecionadas · R$ 720</strong></span><button class="botao">Dar baixa</button></div>
  </div>
</section>
```

- [ ] **Step 2: Pintar quem está devendo**

```js
function pintarFinanceiro() {
  document.getElementById('devendo').innerHTML = DADOS.financeiro.devendo.map((d) => `
    <div class="linha-pendencia">
      <span>${d.paciente} · ${d.sessoes} sessões</span>
      <span><strong>R$ ${d.valor},00</strong></span>
    </div>`).join('')
}
window.addEventListener('DOMContentLoaded', pintarFinanceiro)
```

- [ ] **Step 3: Estilo**

```css
.indicadores { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--gap-4); }
.indicador { display: flex; flex-direction: column; gap: var(--gap-1); }
.indicador span { font-size: 13px; color: var(--tinta-2); }
.indicador strong { font-size: 26px; font-variant-numeric: tabular-nums; }
.indicador .ok { color: var(--sucesso); }
.indicador .pendente { color: var(--atencao); }
@media (max-width: 860px) { .indicadores { grid-template-columns: 1fr; } }
```

- [ ] **Step 4: Percorrer C3**

Dia 30: quanto faturei, quanto entrou, quem está devendo, e dar baixa em 4
sessões **sem abrir 4 telas**. Contar os cliques.

- [ ] **Step 5: Registrar travas em `TRAVAS.md` (cenário C3)**

- [ ] **Step 6: Commit**

```bash
git add prototipo/
git commit -m "feat(prototipo): financeiro do mes com baixa em lote"
```

---

### Task 7: Tela Ajustes e a prévia do aviso

**Files:**
- Modify: `prototipo/index.html`, `prototipo/estilo.css`
- Reference: **C4**, spec §7.1

**Interfaces:**
- Consumes: `DADOS.usuario`

A prévia do e-mail é o entregável mais importante desta tarefa: é onde a regra de
assunto neutro deixa de ser texto na spec e vira coisa que se olha.

- [ ] **Step 1: Escrever a seção**

```html
<section id="tela-ajustes" hidden>
  <h1>Ajustes</h1>
  <p class="sub">Marina Aguiar · uma conta por profissional</p>

  <h2 class="titulo-secao">Sessão</h2>
  <div class="cartao campo-linha">
    <label>Valor padrão da sessão <input type="number" value="180"></label>
  </div>

  <h2 class="titulo-secao">Aviso de consulta</h2>
  <div class="cartao campo-linha">
    <label>Antecedência
      <select><option>24 horas antes</option><option>2 horas antes</option><option>Na véspera, às 18h</option></select>
    </label>
    <label class="inline">
      <input type="checkbox"> Incluir meu título profissional no e-mail
    </label>
    <p class="aviso-privacidade">
      Desligado, o e-mail não revela que a mensagem é de um psicólogo. Quem vir a
      caixa de entrada do paciente não descobre que ele faz terapia.
    </p>
  </div>

  <h2 class="titulo-secao">Como o aviso chega</h2>
  <div class="cartao previa-email">
    <div class="previa-cabecalho">
      <span class="de">Marina Aguiar</span>
      <span class="assunto">Lembrete de agendamento</span>
    </div>
    <div class="previa-corpo">
      <p>Olá, Helena.</p>
      <p>Você tem um horário marcado para <strong>terça, 2 de setembro, às 09:00</strong>.</p>
      <p>Se precisar remarcar, é só responder este e-mail.</p>
      <p class="descadastro">Não quer mais receber estes lembretes? <a href="#/ajustes">Descadastrar</a></p>
    </div>
  </div>

  <h2 class="titulo-secao">Trilha de acesso</h2>
  <div class="cartao">
    <p class="sub" style="margin:0">
      Tudo que foi criado, alterado, consultado e excluído nesta conta.
      <a href="#/ajustes">Abrir trilha</a>
    </p>
  </div>
</section>
```

- [ ] **Step 2: Estilo**

```css
.campo-linha { display: flex; flex-direction: column; gap: var(--gap-4); }
.campo-linha label { display: flex; flex-direction: column; gap: var(--gap-2); font-size: 14px; font-weight: 500; }
.campo-linha label.inline { flex-direction: row; align-items: center; gap: var(--gap-2); font-weight: 400; }
.campo-linha input[type=number], .campo-linha select {
  height: 40px; padding: 0 var(--gap-3); border: 1px solid var(--linha);
  border-radius: var(--raio); font-size: 14px; max-width: 260px; }
.aviso-privacidade { font-size: 13px; color: var(--tinta-2); margin: 0;
  background: var(--superficie-2); padding: var(--gap-3); border-radius: var(--raio); }
.previa-email { padding: 0; overflow: hidden; }
.previa-cabecalho { display: flex; flex-direction: column; gap: 2px;
  padding: var(--gap-4) var(--gap-5); background: var(--superficie-2); border-bottom: 1px solid var(--linha); }
.previa-cabecalho .de { font-size: 13px; color: var(--tinta-2); }
.previa-cabecalho .assunto { font-weight: 600; }
.previa-corpo { padding: var(--gap-5); font-size: 14px; color: var(--tinta-2); }
.previa-corpo p { margin: 0 0 var(--gap-3); }
.descadastro { font-size: 12px; color: var(--tinta-3); }
```

- [ ] **Step 3: Conferir a regra de assunto neutro**

Ler o assunto e o remetente da prévia como se fosse na tela de bloqueio de um
celular. Procurar por: "psicólogo", "consulta", "sessão", "terapia", nome de
clínica. **Nenhum pode aparecer** com o título profissional desligado.

Se aparecer, é violação da spec §7.1 — consertar antes de fechar a tarefa.

- [ ] **Step 4: Percorrer C4**

Registrar o consentimento, escolher a antecedência, ver como a mensagem chega.
O caso do marido que usa o mesmo computador está respondido pela prévia?

- [ ] **Step 5: Registrar travas em `TRAVAS.md` (cenário C4)**

- [ ] **Step 6: Commit**

```bash
git add prototipo/
git commit -m "feat(prototipo): ajustes com previa do aviso e assunto neutro"
```

---

### Task 8: Percurso final e fechamento da fase

**Files:**
- Modify: `prototipo/TRAVAS.md`
- Create: `prototipo/README.md`

**Interfaces:**
- Produces: o veredito da Fase 0 — mapa de telas fechado, ou lista do que muda
  na spec antes de a Fatia 1 começar

Os percursos anteriores foram tela a tela. Este é o dia inteiro, seguido, sem
pular de hash na mão — é onde aparece a costura que falta entre telas.

- [ ] **Step 1: Percorrer os quatro cenários em sequência**

C1 → C2 → C3 → C4, do começo ao fim, navegando só pelo que a interface oferece.
Cada vez que for preciso editar a URL para chegar em algum lugar, é uma trava de
navegação: registrar.

- [ ] **Step 2: Separar as travas em três baldes**

Em `TRAVAS.md`, classificar cada linha:

- **`[layout]`** — já corrigido no protótipo
- **`[fatia]`** — não é problema de desenho, é trabalho da fatia correspondente;
  vira entrada no plano daquela fatia
- **`[escopo]`** — contradiz a spec. **Bloqueia** o fim da fase até revisão humana

- [ ] **Step 3: Escrever o README do protótipo**

```markdown
# Protótipo — Fase 0

Descartável. Só os tokens (`tokens.css`) e o mapa de telas sobrevivem
para a Fatia 1; o resto é apagado quando o front novo tiver as cinco telas.

Rodar: `python3 -m http.server 8000 --directory prototipo`

- `CENARIOS.md` — os quatro percursos que este protótipo precisa aguentar
- `TRAVAS.md` — o que travou, e o que foi feito

**Isto não é um produto validado.** Foi percorrido contra cenário escrito, não
usado por psicólogo nenhum. Ver spec §9, Fase 0.
```

- [ ] **Step 4: Fechar o veredito**

No fim de `TRAVAS.md`, escrever uma dessas duas linhas, sem meio-termo:

- `MAPA FECHADO — nenhuma trava [escopo] em aberto. Fatia 1 liberada.`
- `MAPA EM ABERTO — N travas [escopo]. Spec precisa mudar antes da Fatia 1.`

- [ ] **Step 5: Commit**

```bash
git add prototipo/
git commit -m "docs(prototipo): percurso dos quatro cenarios e veredito da Fase 0"
```

---

## O que esta fase NÃO faz

Nenhum destes entra na Fase 0, e tentar antecipar é o que faz protótipo virar
produto pela metade:

- React, build, dependência, `package.json`
- Chamada de API, backend, banco, migration
- Validação de formulário de verdade — os campos do protótipo não validam nada
- Envio de e-mail
- Autenticação
- Responsividade além dos dois `@media` já escritos
