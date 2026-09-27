# Linguagem visual Apple-informed — consultório de psicologia

Spec de linguagem visual para o app de gestão de consultório (Login, Hoje, Agenda,
Pacientes, Ficha). Substitui a linguagem Google Calendar de
`frontend/src/estilo/tokens.css`. Desktop-first, densidade alta, uso diário,
prontuário clínico.

**Invariantes que esta spec NÃO toca:** a semântica (`<table role="grid">`,
`<ul>`, `<label>` real), a navegação e os testes por rótulo escrito, e a cópia em
português. Nenhum token abaixo depende de `className` para significar coisa
alguma — todo estado tem texto ou glifo.

**Premissa central:** o Calendar separa coisas com fio de cabelo (`--linha`,
`#dadce0`). Esta linguagem separa com **camada** — o fundo da página é mais
escuro que a superfície, a superfície flutuante é translúcida, e o fio some de
quase toda a interface. Sobra um único fio, e ele tem dono declarado (§2.4).

---

## 0. Bloco de tokens — copie e cole

```css
:root {
  color-scheme: light dark;

  /* ————— tipografia ————— */
  --fonte-ui:
    ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text",
    "Segoe UI Variable Text", "Segoe UI", Roboto, "Helvetica Neue", Helvetica,
    Arial, "Noto Sans", "Liberation Sans", sans-serif, "Apple Color Emoji",
    "Segoe UI Emoji", "Noto Color Emoji";
  --fonte-mono:
    ui-monospace, SFMono-Regular, "SF Mono", "Cascadia Mono", "Segoe UI Mono",
    "Roboto Mono", Menlo, Consolas, "Liberation Mono", monospace;

  --peso-regular: 400;
  --peso-medio:   500;
  --peso-forte:   600;
  --peso-titulo:  700;

  --t-pagina-tam:   28px;  --t-pagina-alt:   34px;  --t-pagina-esp:   -0.021em;
  --t-secao-tam:    20px;  --t-secao-alt:    26px;  --t-secao-esp:    -0.014em;
  --t-rotulo-tam:   11px;  --t-rotulo-alt:   14px;  --t-rotulo-esp:    0.062em;
  --t-corpo-tam:    15px;  --t-corpo-alt:    22px;  --t-corpo-esp:    -0.003em;
  --t-segundo-tam:  13px;  --t-segundo-alt:  19px;  --t-segundo-esp:   0.000em;
  --t-legenda-tam:  12px;  --t-legenda-alt:  16px;  --t-legenda-esp:   0.006em;
  --t-numero-tam:   15px;  --t-numero-alt:   20px;  --t-numero-esp:    0.000em;
  --t-controle-tam: 14px;  --t-controle-alt: 18px;  --t-controle-esp: -0.006em;

  /* ————— cor: superfícies e material ————— */
  --fundo-base:            #F1F1F4;
  --fundo-fosco:           rgba(22, 22, 26, 0.34);
  --superficie-1:          #FFFFFF;
  --superficie-2:          #F7F7F9;
  --superficie-3:          #EBEBEF;
  --superficie-vidro:      rgba(255, 255, 255, 0.72);
  --superficie-vidro-topo: rgba(255, 255, 255, 0.60);
  --separador:             rgba(0, 0, 0, 0.075);
  --separador-forte:       rgba(0, 0, 0, 0.14);

  /* ————— cor: tinta ————— */
  --tinta:            rgba(0, 0, 0, 0.88);
  --tinta-2:          rgba(0, 0, 0, 0.60);
  --tinta-3:          rgba(0, 0, 0, 0.44);
  --tinta-4:          rgba(0, 0, 0, 0.26);
  --tinta-sobre-cor:  #FFFFFF;

  /* ————— cor: acento ————— */
  --acento:            #0B69D4;
  --acento-pressionado:#0A5CBC;
  --acento-texto:      #0A5AB4;
  --acento-suave:      rgba(11, 105, 212, 0.10);
  --acento-contorno:   rgba(11, 105, 212, 0.26);
  --foco-anel:         rgba(11, 105, 212, 0.45);

  /* ————— cor: estado da sessão (sempre com glifo, §2.5) ————— */
  --realizada-fundo:    rgba(23, 116, 74, 0.11);
  --realizada-tinta:    #12603D;
  --realizada-contorno: rgba(23, 116, 74, 0.26);
  --faltou-fundo:       rgba(160, 91, 0, 0.12);
  --faltou-tinta:       #7A4500;
  --faltou-contorno:    rgba(160, 91, 0, 0.28);
  --alerta-fundo:       rgba(178, 40, 30, 0.10);
  --alerta-tinta:       #8E2018;
  --alerta-contorno:    rgba(178, 40, 30, 0.26);
  --neutro-fundo:       rgba(0, 0, 0, 0.055);
  --neutro-tinta:       rgba(0, 0, 0, 0.62);
  --neutro-contorno:    rgba(0, 0, 0, 0.12);

  /* ————— cor: destrutivo e marcador temporal ————— */
  --perigo:          #B3261E;
  --perigo-suave:    rgba(179, 38, 30, 0.10);
  --perigo-contorno: rgba(179, 38, 30, 0.30);
  --agora:           #C7381F;

  /* ————— profundidade ————— */
  --sombra-cartao:
    0 0.5px 1px rgba(16, 18, 24, 0.045),
    0 1px 2px  rgba(16, 18, 24, 0.035);
  --sombra-flutuante:
    0 1px 2px    rgba(16, 18, 24, 0.06),
    0 6px 16px  -4px rgba(16, 18, 24, 0.10),
    0 16px 40px -12px rgba(16, 18, 24, 0.14);
  --sombra-dialogo:
    0 2px 4px    rgba(16, 18, 24, 0.06),
    0 14px 34px  -8px rgba(16, 18, 24, 0.16),
    0 36px 84px -18px rgba(16, 18, 24, 0.24);
  --vidro:       saturate(180%) blur(24px);
  --vidro-forte: saturate(180%) blur(40px);

  /* ————— forma ————— */
  --raio-xs:    6px;
  --raio-sm:    8px;
  --raio-md:   10px;
  --raio-lg:   14px;
  --raio-xl:   20px;
  --raio-cheio: 999px;

  /* ————— espaço (grade de 4px, base 2px) ————— */
  --gap-0:  2px;  --gap-1:  4px;  --gap-2:  8px;  --gap-3: 12px;
  --gap-4: 16px;  --gap-5: 20px;  --gap-6: 24px;  --gap-7: 32px;
  --gap-8: 40px;  --gap-9: 48px;

  /* ————— movimento ————— */
  --curva-saida:   cubic-bezier(0.23, 1, 0.32, 1);
  --curva-entrada: cubic-bezier(0.32, 0, 0.67, 0);
  --curva-suave:   cubic-bezier(0.32, 0.72, 0, 1);
  --curva-cor:     cubic-bezier(0.4, 0, 0.2, 1);
  --dur-toque:   110ms;
  --dur-rapido:  150ms;
  --dur-medio:   200ms;
  --dur-dialogo: 260ms;
  --dur-saida:   170ms;

  /* ————— layout ————— */
  --largura-lateral:      228px;
  --largura-conteudo:     1080px;
  --altura-controle:       36px;
  --altura-controle-mini:  28px;
}

@media (prefers-color-scheme: dark) {
  :root {
    --fundo-base:            #17171A;
    --fundo-fosco:           rgba(0, 0, 0, 0.55);
    --superficie-1:          #212126;
    --superficie-2:          #26262C;
    --superficie-3:          #32323A;
    --superficie-vidro:      rgba(30, 30, 35, 0.68);
    --superficie-vidro-topo: rgba(255, 255, 255, 0.10);
    --separador:             rgba(255, 255, 255, 0.10);
    --separador-forte:       rgba(255, 255, 255, 0.18);

    --tinta:           rgba(255, 255, 255, 0.92);
    --tinta-2:         rgba(255, 255, 255, 0.66);
    --tinta-3:         rgba(255, 255, 255, 0.48);
    --tinta-4:         rgba(255, 255, 255, 0.28);
    --tinta-sobre-cor: #FFFFFF;

    --acento:             #3B92F5;
    --acento-pressionado: #2C7FE0;
    --acento-texto:       #79B6FA;
    --acento-suave:       rgba(59, 146, 245, 0.16);
    --acento-contorno:    rgba(59, 146, 245, 0.34);
    --foco-anel:          rgba(103, 174, 250, 0.55);

    --realizada-fundo:    rgba(62, 186, 129, 0.16);
    --realizada-tinta:    #6FD6A4;
    --realizada-contorno: rgba(62, 186, 129, 0.34);
    --faltou-fundo:       rgba(230, 154, 47, 0.16);
    --faltou-tinta:       #F0B45C;
    --faltou-contorno:    rgba(230, 154, 47, 0.34);
    --alerta-fundo:       rgba(240, 95, 82, 0.15);
    --alerta-tinta:       #FF8D80;
    --alerta-contorno:    rgba(240, 95, 82, 0.34);
    --neutro-fundo:       rgba(255, 255, 255, 0.07);
    --neutro-tinta:       rgba(255, 255, 255, 0.66);
    --neutro-contorno:    rgba(255, 255, 255, 0.14);

    --perigo:          #FF6B5E;
    --perigo-suave:    rgba(255, 107, 94, 0.14);
    --perigo-contorno: rgba(255, 107, 94, 0.34);
    --agora:           #FF7A5C;

    --sombra-cartao:
      0 0.5px 1px rgba(0, 0, 0, 0.30),
      0 1px 2px  rgba(0, 0, 0, 0.24);
    --sombra-flutuante:
      0 1px 2px    rgba(0, 0, 0, 0.36),
      0 6px 16px  -4px rgba(0, 0, 0, 0.44),
      0 16px 40px -12px rgba(0, 0, 0, 0.52);
    --sombra-dialogo:
      0 2px 4px    rgba(0, 0, 0, 0.40),
      0 14px 34px  -8px rgba(0, 0, 0, 0.50),
      0 36px 84px -18px rgba(0, 0, 0, 0.62);
  }
}

/* ————— fallbacks obrigatórios ————— */

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  :root {
    --superficie-vidro: #FAFAFC;
  }
  @media (prefers-color-scheme: dark) {
    :root { --superficie-vidro: #1E1E23; }
  }
}

@media (prefers-reduced-transparency: reduce) {
  :root {
    --vidro:       none;
    --vidro-forte: none;
    --superficie-vidro: #FAFAFC;
    --fundo-fosco: rgba(22, 22, 26, 0.55);
  }
  @media (prefers-color-scheme: dark) {
    :root { --superficie-vidro: #1E1E23; }
  }
}

@media (prefers-contrast: more) {
  :root {
    --separador:       rgba(0, 0, 0, 0.30);
    --separador-forte: rgba(0, 0, 0, 0.48);
    --tinta-2:         rgba(0, 0, 0, 0.76);
    --tinta-3:         rgba(0, 0, 0, 0.62);
    --acento-contorno: rgba(11, 105, 212, 0.70);
    --neutro-contorno: rgba(0, 0, 0, 0.42);
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --separador:       rgba(255, 255, 255, 0.34);
      --separador-forte: rgba(255, 255, 255, 0.52);
      --tinta-2:         rgba(255, 255, 255, 0.84);
      --tinta-3:         rgba(255, 255, 255, 0.70);
    }
  }
}

body {
  margin: 0;
  background: var(--fundo-base);
  color: var(--tinta);
  font-family: var(--fonte-ui);
  font-size: var(--t-corpo-tam);
  line-height: var(--t-corpo-alt);
  letter-spacing: var(--t-corpo-esp);
  font-optical-sizing: auto;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

:where(input, select, textarea, button) {
  font-family: inherit;
  letter-spacing: inherit;
}

/* mantém o kill-switch existente (tokens.css:60), sem alteração de intenção */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

---

## 1. Escala tipográfica

### 1.1 A família

**Stack de sistema, escrito por extenso em `--fonte-ui`.** Motivo Apple: a fonte
do sistema já traz optical sizing, tabelas de tracking por tamanho e ajuste de
legibilidade que nenhuma webfont baixada entrega de graça — e ela é a única que
renderiza corretamente em todos os três sistemas sem custo de rede.

Consequência direta: **os dois `<link>` de Google Fonts saem.** O `@import` de
Roboto em `tokens.css:1` sai; o `<link>` de Inter em `index.html` sai — hoje ele
já baixa 4 pesos que nenhuma regra usa. Isso remove duas conexões externas de
terceiros do caminho crítico de um app que manipula prontuário: ganho de
privacidade, não só de performance.

`--fonte-mono` existe para **um** uso: identificadores técnicos da tela de
auditoria (id de entidade, hash). Não é para horário. Horário usa `--fonte-ui`
com `font-variant-numeric: tabular-nums` — o mono do sistema é largo demais para
uma grade densa, e o `tabular-nums` da UI font já resolve o alinhamento.

### 1.2 A escala

O tracking aperta conforme o tamanho cresce e afrouxa conforme encolhe. Isto não
é enfeite: é o que faz `28px` não parecer esparramado e `11px` não parecer
empastado.

| Papel | Token | `font-size` | `line-height` | `letter-spacing` | `font-weight` | Onde |
|---|---|---|---|---|---|---|
| Título de página | `--t-pagina-*` | 28px | 34px (1.214) | −0.021em | 600 | `h1` de Hoje / Agenda / Pacientes / nome na Ficha |
| Título de seção | `--t-secao-*` | 20px | 26px (1.30) | −0.014em | 600 | título do diálogo, "Próxima sessão", cabeçalho de cartão grande |
| Rótulo de seção | `--t-rotulo-*` | 11px | 14px | +0.062em | 600, `text-transform: uppercase` | "O DIA", "FICOU PENDENTE", cabeçalho de coluna |
| Corpo | `--t-corpo-*` | 15px | 22px (1.467) | −0.003em | 400 | texto de anotação, nome de paciente na lista, conteúdo geral |
| Corpo forte | `--t-corpo-*` | 15px | 22px | −0.003em | 600 | nome do paciente na linha da sessão, valor que responde a pergunta |
| Secundário | `--t-segundo-*` | 13px | 19px (1.462) | 0 | 400 | subtítulo da página, metadado, texto de apoio |
| Legenda | `--t-legenda-*` | 12px | 16px (1.333) | +0.006em | 400 | erro de campo, data relativa, contagem de paginação |
| Numérico / tabular | `--t-numero-*` | 15px | 20px | 0 | 600 + `font-variant-numeric: tabular-nums` | horário na lista do dia, coluna de hora da agenda, valores |
| Rótulo de controle | `--t-controle-*` | 14px | 18px | −0.006em | 600 (botão) / 500 (label de campo, 13px) | texto de botão, item da navegação |

Regras que o autor do mockup não pode violar:

1. **Leading inverso ao tamanho.** 28px → 1.21; 15px → 1.47. Nunca um
   `line-height: 1.5` global.
2. **Hierarquia por peso + tamanho + leading como conjunto.** Nunca por cor
   sozinha — `--tinta-2` desce a importância, não constrói o nível.
3. **`tabular-nums` em qualquer número que se alinhe verticalmente** — coluna de
   hora, grade da agenda, paginação. Horário que "dança" entre linhas é o erro
   mais visível numa tela de agenda.
4. **Máximo de 3 níveis de tinta por tela.** `--tinta-4` é só desabilitado.
5. **Medida de linha da anotação clínica: `max-width: 68ch`.** Anotação é texto
   corrido de leitura; a coluna da Ficha não pode deixá-la atravessar 900px.
6. Espaçamento em `rem`/`em` onde acompanhar o texto (padding interno de
   controles, gap de rótulo→campo); `px` onde for estrutura de layout (largura da
   lateral, grade da agenda).

### 1.3 Vibrancy

Texto sobre `--superficie-vidro` (a lateral e o cabeçalho translúcidos) não usa
`--tinta-3`. Sobe um nível (`--tinta-2`), peso 500 em vez de 400, e ganha
`letter-spacing: +0.004em`. Cor saturada (o acento) só vai em camada sólida —
nunca como texto sobre vidro.

---

## 2. Cor e material

### 2.1 O modelo de camadas

Quatro planos, do fundo para a frente:

| Plano | Token | O que é |
|---|---|---|
| Base | `--fundo-base` | a janela. Nunca recebe conteúdo direto; é o que faz a superfície existir |
| Superfície | `--superficie-1` | cartão, linha de lista, corpo do diálogo, célula da agenda |
| Preenchimento | `--superficie-2` / `--superficie-3` | campo de formulário, coluna de rótulo da agenda, hover, pressionado |
| Flutuante | `--superficie-vidro` + `--vidro` | lateral, cabeçalho fixo, menu |

Regra dura, vinda do Apple: **nunca empilhe uma superfície translúcida sobre
outra.** A lateral é vidro; o cartão dentro do conteúdo é sólido. O diálogo é
sólido sobre um scrim — não é vidro, porque texto de prontuário sobre vidro é
onde a legibilidade quebra primeiro.

### 2.2 Estados de superfície

| Estado | Fundo | Como |
|---|---|---|
| Repouso | `--superficie-1` | — |
| Hover (ponteiro fino) | `--superficie-2` | `@media (hover: hover) and (pointer: fine)` |
| Pressionado | `--superficie-3` | mais `transform: scale(0.995)` em linha clicável |
| Selecionado | `--acento-suave` + `box-shadow: inset 0 0 0 1px var(--acento-contorno)` | — |
| Desabilitado | herda | `color: var(--tinta-4)`, `cursor: default` |

### 2.3 Acento

`--acento` (`#0B69D4` claro / `#3B92F5` escuro) tem **um** papel: ação e seleção.
Não é a cor do evento. A regra herdada "ação é forma, não cor" perde o motivo de
existir aqui, porque o bloco de sessão da agenda **deixa de ser azul chapado** —
ele passa a ser superfície tonal com barra de estado à esquerda (§6.5). Sem o
evento azul, não há nada de que o botão azul precise se distinguir pela forma.

Para texto azul sobre branco use `--acento-texto` (contraste ≥ 5.2:1), nunca
`--acento` (4.6:1, marginal a 13px).

### 2.4 O que substitui `--linha`

Hoje `--linha: #dadce0` desenha borda em campo, célula de agenda, linha de
pendência, botão secundário e barra de busca. **Some de quatro dos cinco.**

| Onde hoje tem fio | Substituto |
|---|---|
| Borda do cartão / lateral | Delta de preenchimento: `--fundo-base` atrás, `--superficie-1` na frente, mais `--sombra-cartao` |
| Borda de campo de texto | Campo preenchido: fundo `--superficie-2` + `box-shadow: inset 0 0 0 1px var(--separador)` (anel interno, não borda — não empurra layout no foco) |
| `border-bottom` da linha de pendência | Espaço (`gap: var(--gap-1)`) + hover de linha. A separação vem do vazio |
| Borda do botão secundário | Preenchimento `--superficie-2` sem contorno; contorno só na variante "no vidro" |
| Divisor sob cabeçalho fixo | **Scroll edge:** gradiente de 24px (`--superficie-vidro` → `transparent`) aplicado só quando `scrollTop > 0` |
| **Grade da agenda** | **Fica.** É o único sobrevivente — ver abaixo |

**`--separador` é reservado a dois usos e a mais nenhum:**

1. A grade semanal (`<table role="grid">`). Uma tabela de horários precisa de
   régua: sem ela, ler "quinta às 15h" vira contagem de pixels. Apple mantém
   linhas em grade de dados pelo mesmo motivo (Numbers, Calendar de macOS).
2. O anel interno de controles de entrada (`inset 0 0 0 1px`), que não é borda de
   layout — é a lente do campo.

Se um mockup usar `--separador` fora desses dois casos, está errado.

### 2.5 Estado da sessão sobrevivendo a daltonismo

Hoje o estado carrega significado **só** por cor (`.chip.realizada` verde,
`.chip.faltou` âmbar, `.chip.alerta` âmbar — âmbar duas vezes: já hoje "faltou" e
"alerta" são indistinguíveis para qualquer um, com ou sem daltonismo).

A forma nova carrega quatro sinais redundantes: **glifo + texto + tom + peso de
preenchimento.**

| Estado | Glifo (SVG inline, 12×12, `currentColor`) | Texto | Tokens |
|---|---|---|---|
| Realizada | `✓` traço, 2px, cantos arredondados | "Realizada" | `--realizada-*` |
| Faltou | círculo com barra diagonal (⊘) | "Faltou" | `--faltou-*` |
| Alerta | triângulo com haste (⚠, sem preenchimento) | "Consentimento pendente" etc. | `--alerta-*` |
| Neutro | círculo vago, traço 1.5px | "Agendada" | `--neutro-*` |

```html
<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
  <!-- realizada -->
  <path d="M2.5 6.4 4.9 8.8 9.5 3.4" fill="none" stroke="currentColor"
        stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
  <!-- faltou -->
  <circle cx="6" cy="6" r="4.3" fill="none" stroke="currentColor" stroke-width="1.5"/>
  <path d="M3 9 9 3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
</svg>
<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
  <!-- alerta -->
  <path d="M6 1.6 11 10.4H1z" fill="none" stroke="currentColor"
        stroke-width="1.4" stroke-linejoin="round"/>
  <path d="M6 4.9v2.4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
  <circle cx="6" cy="8.9" r="0.75" fill="currentColor"/>
</svg>
<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
  <!-- neutro -->
  <circle cx="6" cy="6" r="4.2" fill="none" stroke="currentColor" stroke-width="1.5"/>
</svg>
```

O glifo é `aria-hidden` porque o texto do chip já é o nome acessível. `alerta`
saiu do âmbar e foi para o vermelho dessaturado: ele é o estado que **pede ação**
e precisa ser distinto de "faltou", que é fato consumado.

Teste de aceite: um print em escala de cinza a 200% deve permitir dizer qual é
qual sem ler o texto. Os quatro tons têm luminâncias separadas (≈ 0.29 / 0.24 /
0.19 / 0.50 de L*) justamente para isso.

---

## 3. Profundidade e elevação

| Nível | O que é | Recipe |
|---|---|---|
| 0 — ancorado | célula de agenda, linha de lista, campo de formulário | nenhuma sombra; diferencia por fill |
| 1 — superfície | cartão, painel da Ficha, cartão de login | `--sombra-cartao` (duas camadas, ~0.04 alpha) |
| 2 — flutuante | menu, popover, tooltip | `--sombra-flutuante` + `border-radius: var(--raio-md)` |
| 3 — modal | `Dialog.Content` | `--sombra-dialogo` + `--raio-xl` + scrim `--fundo-fosco` |
| Translúcido | lateral, cabeçalho fixo | `--superficie-vidro` + `backdrop-filter: var(--vidro)` + luz na aresta |

**Sombra é função do tamanho da superfície.** O cartão (grande, ancorado) tem
sombra quase imperceptível — quem o separa é o `--fundo-base`. O diálogo (grande,
flutuante) tem sombra profunda com offset grande e spread negativo, porque a
sombra é o que diz "isto está fora do plano da página".

Vidro, completo:

```css
.lateral {
  background: var(--superficie-vidro);
  -webkit-backdrop-filter: var(--vidro);
  backdrop-filter: var(--vidro);
  /* luz pegando na aresta do material, do lado interno */
  box-shadow: inset -1px 0 0 var(--superficie-vidro-topo);
}
```

Fallbacks já estão no bloco de tokens: `@supports not (backdrop-filter…)` troca
`--superficie-vidro` por um sólido quase-branco, e
`prefers-reduced-transparency` zera `--vidro` e opaca a superfície. Nenhum
componente precisa saber disso — tudo passa pelo token.

**Quando dimmar e quando não.** O diálogo dimma (`--fundo-fosco`) porque é tarefa
bloqueante. Qualquer painel paralelo e não-bloqueante (não existe hoje; se vier)
usa translucidez e deslocamento **sem** scrim.

Modo privacidade (`[data-borrado='true']`) sai de `filter: blur(5px)` para
`filter: blur(6px) saturate(0.7)` com `transition: filter var(--dur-medio)
var(--curva-cor)` — a dessaturação garante que um nome borrado não continue
legível pela silhueta de cor. **Isto não conta contra o orçamento de animação do
§5**: é transição de filtro em toggle raro, não movimento.

---

## 4. Forma e espaço

### 4.1 Raios

| Classe de elemento | Token | Valor |
|---|---|---|
| Chip de estado, tag, badge | `--raio-xs` | 6px |
| Campo de texto, textarea, select, botão pequeno, item de navegação | `--raio-sm` | 8px |
| Botão primário/secundário, popover, bloco de sessão na agenda | `--raio-md` | 10px |
| Cartão, painel da Ficha, cartão de login | `--raio-lg` | 14px |
| Diálogo modal | `--raio-xl` | 20px |
| Avatar, indicador "hoje" na agenda, toggle | `--raio-cheio` | 999px |

A pílula (`999px`) **deixa de ser a forma do botão**. Ela era a marca da
linguagem Calendar; aqui ela fica só onde a forma é intrinsecamente redonda
(inicial do paciente, marcador do dia). Botão vira retângulo de canto contínuo de
10px, que é a forma do controle no macOS/iOS moderno.

Regra de aninhamento: raio interno = raio externo − padding. Campo (8px) dentro de
cartão (14px) com `--gap-4` de padding fecha corretamente; nunca aninhe dois
raios iguais.

### 4.2 Espaço

**A escala 4/8/12/16/20/24/32 fica** — ela já é uma grade de 4px correta, e trocá-la
obrigaria a reescrever cada regra de layout sem ganho visual. Duas adições:

- `--gap-0: 2px` — gap entre glifo e texto dentro do chip, gap entre itens da
  navegação. Hoje esses dois casos usam `2px` escrito à mão em `estilo.css`.
- `--gap-8: 40px` e `--gap-9: 48px` — respiro vertical entre seções da tela e
  padding do estado vazio. Hoje o teto é `--gap-7: 32px`, e o resultado é que
  "seção diferente" e "item da mesma seção" ficam separados por distâncias
  próximas demais.

Densidade alvo da tela Hoje: altura de linha de sessão **44px**, padding
horizontal `--gap-4`, gap entre linhas `--gap-1`. Isso cabe ~11 sessões sem
rolagem em 900px de viewport útil — mesma densidade de hoje, com mais ar
percebido porque as bordas saíram.

Margens verticais canônicas: título de página → subtítulo `--gap-1`; subtítulo →
primeira seção `--gap-7`; seção → seção `--gap-8`; rótulo de seção → conteúdo
`--gap-3`.

---

## 5. Movimento

### 5.1 O orçamento

Quatro animações. A auditoria anterior já rejeitou seis candidatas; esta spec
**não adiciona nenhuma**. O único acréscimo é a transição de `filter` do modo
privacidade (§3), que é mudança de estado visual e não movimento.

| # | Animação | Curva | Duração | Propriedades |
|---|---|---|---|---|
| 1 | Feedback de pressão no botão | `--curva-saida` | `--dur-toque` (110ms) | `transform` |
| 2 | Entrada / saída do diálogo | `--curva-suave` / `--curva-saida` | 260ms / 170ms | `opacity`, `transform`, `filter` |
| 3 | Troca do chip de estado | `--curva-cor` | `--dur-medio` (200ms) | `opacity`, `filter`, `transform` |
| 4 | Troca do salvar-anotação | `--curva-cor` | `--dur-medio` (200ms) | `opacity`, `filter` |

Nada mais anima. Navegação entre telas não anima (é ação repetida dezenas de
vezes por dia). Hover não anima em `transform` — só em `background-color`, e isso
é uma transição de cor, não movimento.

### 5.2 Curvas

```css
--curva-saida:   cubic-bezier(0.23, 1, 0.32, 1);    /* entrada de elemento: arranca rápido */
--curva-entrada: cubic-bezier(0.32, 0, 0.67, 0);    /* espelho da acima, para a saída */
--curva-suave:   cubic-bezier(0.32, 0.72, 0, 1);    /* diálogo: curva iOS de sheet */
--curva-cor:     cubic-bezier(0.4, 0, 0.2, 1);      /* cor/filtro, sem posição */
```

`ease-in` está proibido em qualquer elemento que entre. Saída usa
`--curva-entrada` **só** porque é o espelho matemático da entrada — caminho de ida
e volta simétricos, como exige a consistência espacial.

### 5.3 O kill-switch

`tokens.css:60` zera toda duração com `!important`. Ele **fica exatamente como
está**, e isso impõe uma regra de construção:

> Nenhuma animação pode carregar estado. Toda animação é puramente cosmética
> sobre um estado que o React já mudou.

Verificação: com `prefers-reduced-motion: reduce` ativo, o diálogo do Radix ainda
desmonta, porque `animation-duration: 0.01ms` continua disparando `animationend`
— que é o evento que o Radix espera. Se a saída fosse um `transition`, não
haveria evento algum e o diálogo ficaria montado para sempre. Daí a regra
seguinte.

### 5.4 Diálogo — obrigatoriamente `@keyframes`

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

Quatro pontos que não são opcionais:

- **Escala inicial 0.965, nunca 0.** Nada no mundo real aparece do nada.
- **`blur` entra junto com a escala** — é o que faz a superfície ler como material
  chegando, não como um retângulo opaco piscando. Máximo 4px (mais que isso custa
  caro no Safari).
- **`transform-origin: center`.** Modal é a exceção da regra origin-aware: ele não
  tem gatilho espacial, aparece no centro da viewport.
- **Saída mais curta que entrada** (170ms × 260ms) e caminho espelhado — ele
  encolhe e sobe 1%, pelo mesmo eixo por onde chegou.

### 5.5 Troca de chip e de salvar-anotação

Substituição de um elemento por outro no mesmo lugar (botão "Registrar" → chip
"Realizada"; botão "Salvar" → "Salvo"). O crossfade puro mostra dois objetos
sobrepostos; o blur funde os dois numa transformação só.

```css
.troca-saindo {
  opacity: 0; filter: blur(3px); transform: scale(0.96);
  transition: opacity var(--dur-medio) var(--curva-cor),
              filter  var(--dur-medio) var(--curva-cor),
              transform var(--dur-medio) var(--curva-cor);
}
.troca-entrando {
  opacity: 1; filter: blur(0); transform: scale(1);
  transition: opacity var(--dur-medio) var(--curva-cor),
              filter  var(--dur-medio) var(--curva-cor),
              transform var(--dur-medio) var(--curva-cor);
  @starting-style { opacity: 0; filter: blur(3px); transform: scale(0.96); }
}
```

`transition`, não `@keyframes`: estes podem disparar em sequência rápida (marcar
três sessões seguidas) e precisam de retarget, não de restart. O contêiner da
troca leva `min-width` fixado no maior dos dois conteúdos, para o layout não
pular durante o fade — e `transition: none` em qualquer propriedade de layout.

### 5.6 Feedback de pressão

```css
.botao, .linha-clicavel, .item-nav {
  transition: transform var(--dur-toque) var(--curva-saida),
              background-color var(--dur-rapido) var(--curva-cor);
}
.botao:active        { transform: scale(0.972); }
.linha-clicavel:active { transform: scale(0.995); }
```

No `:active` (pointer-down), não no `click`. Escala menor em superfícies grandes:
0.972 num botão de 100px é o mesmo deslocamento absoluto que 0.995 numa linha de
800px.

---

## 6. Os controles

Em tudo abaixo, o foco de teclado é o mesmo, sempre:

```css
:focus-visible {
  outline: 2px solid var(--acento);
  outline-offset: 2px;
  border-radius: inherit;
}
```

`outline` (não `box-shadow`) porque não é cortado por `overflow: hidden` da
grade da agenda. `:focus-visible`, não `:focus` — clicar com mouse não deve
desenhar anel.

### 6.1 Botão

**Primário.** Retângulo de `--raio-md`, altura `--altura-controle` (36px),
padding `0 var(--gap-4)`, fundo `--acento`, texto `--tinta-sobre-cor` em
`--t-controle-*` peso 600. **Sem sombra** — a elevação de duas camadas do Calendar
sai; um botão preenchido já é o objeto mais saliente da tela.

```css
.botao {
  height: var(--altura-controle);
  padding: 0 var(--gap-4);
  border: 0;
  border-radius: var(--raio-md);
  background: var(--acento);
  color: var(--tinta-sobre-cor);
  font-size: var(--t-controle-tam);
  line-height: var(--t-controle-alt);
  letter-spacing: var(--t-controle-esp);
  font-weight: var(--peso-forte);
  display: inline-flex; align-items: center; justify-content: center;
  gap: var(--gap-2);
  cursor: pointer;
  transition: transform var(--dur-toque) var(--curva-saida),
              background-color var(--dur-rapido) var(--curva-cor);
}
@media (hover: hover) and (pointer: fine) {
  .botao:hover { background: color-mix(in oklab, var(--acento) 92%, black); }
}
.botao:active { background: var(--acento-pressionado); transform: scale(0.972); }
.botao:disabled { background: var(--superficie-3); color: var(--tinta-4); cursor: default; transform: none; }
```

**Secundário.** Mesma geometria. Fundo `--superficie-2`, texto `--tinta`, **sem
borda**. Hover → `--superficie-3`. Sobre vidro (lateral, cabeçalho) ganha
`box-shadow: inset 0 0 0 1px var(--separador)` para não sumir. Destrutivo é
secundário com `color: var(--perigo)` e hover `--perigo-suave` — nunca botão
vermelho preenchido: o preenchimento vermelho é uma isca de clique numa tela de
prontuário.

**Pequeno.** Altura `--altura-controle-mini` (28px), padding `0 var(--gap-3)`,
`--raio-sm`, fonte 13px peso 600. É o botão da linha de sessão ("Realizada",
"Faltou").

**Toggle** (`aria-pressed="true"`): fundo `--acento-suave`, texto
`--acento-texto`, `box-shadow: inset 0 0 0 1px var(--acento-contorno)`.

**Enviando** (`disabled` durante a chamada): mantém a largura, troca o rótulo pelo
spinner SVG inline 14px girando em 700ms linear + o texto. O spinner é a exceção
ao orçamento de motion porque é indicador de status, não transição.

### 6.2 Campo de texto e textarea

Campo **preenchido**, não contornado. Esta é a diferença visível mais importante
depois do fim das bordas de cartão.

```css
.campo input, .campo select, .campo textarea {
  width: 100%; box-sizing: border-box;
  height: var(--altura-controle);
  padding: 0 var(--gap-3);
  border: 0;
  border-radius: var(--raio-sm);
  background: var(--superficie-2);
  box-shadow: inset 0 0 0 1px var(--separador);
  color: var(--tinta);
  font-size: var(--t-corpo-tam);
  line-height: var(--t-corpo-alt);
  transition: background-color var(--dur-rapido) var(--curva-cor),
              box-shadow var(--dur-rapido) var(--curva-cor);
}
.campo textarea {
  height: auto; min-height: 96px; padding: var(--gap-3);
  resize: vertical; line-height: var(--t-corpo-alt);
}
.campo input:hover { background: var(--superficie-3); }
.campo input:focus {
  outline: none;
  background: var(--superficie-1);
  box-shadow: inset 0 0 0 1px var(--acento), 0 0 0 3px var(--foco-anel);
}
.campo input[aria-invalid='true'] { box-shadow: inset 0 0 0 1px var(--perigo-contorno); }
.campo input[aria-invalid='true']:focus {
  box-shadow: inset 0 0 0 1px var(--perigo), 0 0 0 3px var(--perigo-suave);
}
.campo input::placeholder { color: var(--tinta-3); }
```

No foco o campo **clareia** (`--superficie-2` → `--superficie-1`): ele sai do
plano do formulário e vem para a frente. Anel por `box-shadow`, nunca `border` —
borda muda o box model e o texto pula 1px ao focar.

Estrutura: `.campo { display: flex; flex-direction: column; gap: var(--gap-2); }`.
Rótulo em 13px peso 500 `--tinta-2`; "(opcional)" em `--tinta-3` peso 400. A
mensagem de erro fica fora do `<label>`, em `--t-legenda-*` `--perigo`, ligada por
`aria-describedby` — invariante da spec original, mantido.

Textarea de anotação clínica: `--raio-md` (é maior), `min-height: 140px`,
`max-width: 68ch`, e nenhuma animação de altura.

### 6.3 Select

`appearance: none`, mesma caixa preenchida do input, `padding-right: var(--gap-7)`
e um chevron SVG inline como `background-image` (data-URI, `currentColor` não
funciona em background — use o hex do `--tinta-2` de cada tema via duas regras, ou
posicione um `<svg>` absoluto no wrapper, que é a opção preferida):

```html
<div class="campo-select">
  <select>…</select>
  <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
    <path d="M2.8 4.6 6 7.8l3.2-3.2" fill="none" stroke="currentColor"
          stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>
</div>
```

```css
.campo-select { position: relative; display: flex; }
.campo-select svg {
  position: absolute; right: var(--gap-3); top: 50%;
  transform: translateY(-50%);
  color: var(--tinta-3); pointer-events: none;
}
```

O `<select>` continua sendo `<select>` nativo — dropdown do SO, teclado do SO,
sem reimplementação.

### 6.4 Chip de estado

Não é botão e não pode parecer um: sem sombra, sem hover, `cursor: default`.

```css
.chip {
  display: inline-flex; align-items: center; gap: var(--gap-0);
  height: 22px; padding: 0 var(--gap-2) 0 6px;
  border-radius: var(--raio-xs);
  font-size: var(--t-legenda-tam);
  line-height: 1;
  letter-spacing: var(--t-legenda-esp);
  font-weight: var(--peso-forte);
  background: var(--neutro-fundo);
  color: var(--neutro-tinta);
  box-shadow: inset 0 0 0 1px var(--neutro-contorno);
  white-space: nowrap;
}
.chip.realizada { background: var(--realizada-fundo); color: var(--realizada-tinta); box-shadow: inset 0 0 0 1px var(--realizada-contorno); }
.chip.faltou    { background: var(--faltou-fundo);    color: var(--faltou-tinta);    box-shadow: inset 0 0 0 1px var(--faltou-contorno); }
.chip.alerta    { background: var(--alerta-fundo);    color: var(--alerta-tinta);    box-shadow: inset 0 0 0 1px var(--alerta-contorno); }
```

O glifo (§2.5) é o primeiro filho, `aria-hidden`. O padding esquerdo é 6px (menor
que o direito) porque o glifo tem ar óptico próprio — padding simétrico faria o
chip parecer torto. `gap-0` = 2px entre glifo e texto; o glifo já traz 1px de
margem interna no viewBox.

Chip sozinho nunca comunica: ele sempre acompanha o nome do paciente e o horário
na mesma linha.

### 6.5 Superfície de cartão

```css
.cartao {
  background: var(--superficie-1);
  border-radius: var(--raio-lg);
  padding: var(--gap-5);
  box-shadow: var(--sombra-cartao);
}
```

Sem borda. Sem `overflow: hidden` por padrão (corta o `outline-offset` do foco).

**Cartão de destaque** (próxima sessão, na tela Hoje): fundo
`--superficie-1`, mais `box-shadow: var(--sombra-cartao), inset 3px 0 0 var(--acento)`
— a barra à esquerda vira anel interno, não `border-left`, para não deslocar o
conteúdo em 3px. O acento aqui é legítimo: "esta é a próxima" é seleção temporal.

**Bloco de sessão na grade da agenda** (substituindo o `.slot.ocupado` azul
chapado): fundo `--acento-suave`, texto `--tinta` (não branco),
`box-shadow: inset 2px 0 0 var(--acento)`, `--raio-md`, padding `4px 6px`. Motivo:
texto branco sobre azul saturado, repetido 30 vezes numa grade, é a coisa mais
cansativa que uma tela de agenda pode fazer com quem a olha oito horas por dia. A
barra à esquerda troca de cor conforme o estado — `--realizada-tinta`,
`--faltou-tinta`, `--acento` para agendada.

Marcador de horário atual na agenda: linha de 1.5px `--agora` com um disco de 7px
na ponta esquerda, `position: absolute`, `z-index` acima dos blocos.

### 6.6 Item de navegação lateral

```css
.lateral {
  width: var(--largura-lateral);
  padding: var(--gap-5) var(--gap-3);
  background: var(--superficie-vidro);
  -webkit-backdrop-filter: var(--vidro);
  backdrop-filter: var(--vidro);
  box-shadow: inset -1px 0 0 var(--superficie-vidro-topo);
  position: sticky; top: 0; height: 100vh; box-sizing: border-box;
}
.lateral ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--gap-0); }
.lateral a {
  display: flex; align-items: center; gap: var(--gap-3);
  height: 34px; padding: 0 var(--gap-3);
  border-radius: var(--raio-sm);
  color: var(--tinta-2);
  font-size: var(--t-controle-tam);
  letter-spacing: var(--t-controle-esp);
  font-weight: var(--peso-medio);
  text-decoration: none;
  transition: background-color var(--dur-rapido) var(--curva-cor),
              color var(--dur-rapido) var(--curva-cor);
}
@media (hover: hover) and (pointer: fine) {
  .lateral a:hover { background: var(--superficie-3); color: var(--tinta); }
}
.lateral a:active { background: var(--superficie-3); }
.lateral a.ativo {
  background: var(--superficie-1);
  color: var(--tinta);
  font-weight: var(--peso-forte);
  box-shadow: var(--sombra-cartao);
}
```

Três mudanças de comportamento em relação a hoje:

1. **Ativo não é mais pílula azul-suave.** É uma lastra **sólida e elevada** sobre
   o vidro — o item selecionado vira o objeto mais próximo do usuário. Isso é a
   sidebar do macOS, e é mais legível que o realce de cor porque funciona sem cor.
2. **Peso muda de 500 para 600 no ativo**, redundância que sobrevive a daltonismo
   e a monocromia.
3. Raio `--raio-sm` (8px), não `999px`. O rótulo escrito é o que identifica o
   destino — invariante da spec original, intocado.

Sem ícone. Cinco rótulos escritos numa coluna de 228px não precisam de ícone, e
ícone mudo é exatamente o que essa navegação já rejeitou uma vez.

### 6.7 Diálogo modal

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
.dialogo-cabecalho { padding: var(--gap-5) var(--gap-5) var(--gap-3); }
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

Sólido, não translúcido — é onde se lê e escreve prontuário. O scrim leva 2px de
blur (não os 24px do vidro): o suficiente para empurrar o fundo para trás sem
custo de render. Rodapé grudado embaixo, ação primária à direita. Quando o corpo
rola, um gradiente de 20px (`--superficie-1` → `transparent`) aparece no topo do
rodapé, no lugar de um divisor. Animação em §5.4.

### 6.8 Estado vazio

```css
.estado-vazio {
  display: flex; flex-direction: column; align-items: center;
  gap: var(--gap-3);
  padding: var(--gap-9) var(--gap-5);
  text-align: center;
}
.estado-vazio svg { color: var(--tinta-4); }       /* 28px, traço 1.5px */
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

Sem cartão e sem fundo — o vazio é vazio. Ícone opcional (SVG inline de traço,
28px, `--tinta-4`), título em 15px/600, apoio em 13px `--tinta-2` com
`max-width: 34ch`, e a ação como botão **secundário** — um botão primário azul
num estado vazio grita mais alto que o resto da tela. A cópia é invariante: esta
spec só define a forma.

---

## 7. O que esta linguagem custa

Nenhuma dessas é hipótese; são consequências diretas do que está escrito acima.

**1. Superfície de manutenção dobra.** Passar a ter modo escuro significa que todo
token de cor tem dois valores e toda revisão visual tem duas passadas. Hoje o modo
escuro está explicitamente **fora de escopo** na spec de frontend
(`2026-08-26-frontend-design.md`, "Fora de escopo"). Esta linguagem o traz de
volta — e trazê-lo pela metade (tokens declarados, telas não conferidas) é pior
que não trazer. É a maior conta deste documento.

**2. Separar por camada é mais frágil que separar por fio.** O fio de cabelo
funciona sobre qualquer fundo. O delta `--fundo-base` → `--superficie-1` é de ~4%
de luminância: some em monitor mal calibrado, em modo alto brilho sob luz de
janela, e em qualquer impressão. Mitigação parcial: `--sombra-cartao` e o bloco
`prefers-contrast: more`. Não é equivalente. Consultório com projetor ou monitor
barato vai ver cartões flutuando num cinza indistinto.

**3. `backdrop-filter` custa render.** Duas superfícies de vidro fixas (lateral +
cabeçalho) recompostas a cada scroll. Em máquina modesta — e consultório roda em
notebook de entrada — isso aparece como scroll menos liso na lista de pacientes.
Os fallbacks cobrem ausência de suporte, não lentidão com suporte. Se medir mal,
a saída é tornar a lateral sólida: `--superficie-vidro: var(--superficie-1)` e
`--vidro: none`, uma linha.

**4. O azul deixa de ser um sinal forte.** No Calendar, azul chapado = sessão
ocupada, lido de relance a 2 metros. O bloco tonal com barra à esquerda é mais
calmo e menos gritante para quem passa o dia olhando — e também menos
escaneável de longe. Painel de sala de espera projetado na parede perde legibilidade
com esta linguagem. Se esse uso existir, ele precisa de uma variante de alta
saturação própria.

**5. `--separador` vira uma regra que só existe na cabeça de quem lê esta spec.**
"Fio só na grade da agenda e no anel de campo" não é verificável por lint. A
tendência natural de quem implementar a próxima tela é encostar uma borda em algo,
e a linguagem degrada de volta para o Calendar um `1px solid` por vez. Isso quer
uma regra de revisão escrita, não boa vontade.

**6. Fonte do sistema significa render diferente por SO.** Roboto e Inter davam o
mesmo desenho em toda máquina; SF Pro no Mac, Segoe no Windows e Roboto no Linux
não dão. As métricas da escala (28/34/−0.021em) foram calibradas para SF; em Segoe
o título fica ~2% mais estreito e o tracking negativo pesa um pouco mais. Aceitável
— mas print de referência deixa de ser contrato entre máquinas, e QA visual precisa
dizer em qual SO rodou.

**7. Mais tokens, mais indireção.** 100 propriedades contra 22. Um dev novo lendo
`estilo.css` precisa de mais saltos para descobrir por que um campo é cinza.
Compensação: quase nenhuma cor literal sobra no CSS de componente — o preço é
pago uma vez, na leitura da tabela.

**8. Cantos maiores comem densidade.** `--raio-lg: 14px` num cartão de 44px de
altura interna deixa menos área retangular útil; combinado com `--gap-8`/`--gap-9`
no espaço entre seções, a tela Hoje mostra aproximadamente uma sessão a menos por
viewport que hoje. Foi uma escolha, não um descuido — mas é densidade perdida num
app que se vende por densidade.

**9. Chip com glifo ocupa ~16px a mais.** Numa linha de sessão com `1fr auto`, isso
espreme o nome do paciente. Em nome longo com "Consentimento pendente" ao lado, vai
truncar antes do que trunca hoje. O ganho — estado legível sem cor — vale; o custo
aparece na Ficha de paciente com nome composto.

---

## 8. Checklist de aceite do mockup

- [ ] Nenhum `<link>` ou `@import` de fonte externa; nenhuma URL remota no arquivo.
- [ ] Nenhum `1px solid` fora da grade da agenda.
- [ ] Todo chip de estado tem glifo SVG inline + texto.
- [ ] Print em escala de cinza distingue os quatro estados.
- [ ] `tabular-nums` em toda coluna de horário.
- [ ] Exatamente 4 animações; nenhuma delas carrega estado.
- [ ] Saída do diálogo é `@keyframes`, não `transition`.
- [ ] `:focus-visible` com `outline` visível em todo controle, inclusive dentro da grade.
- [ ] Claro e escuro conferidos nas cinco telas.
- [ ] Hover atrás de `@media (hover: hover) and (pointer: fine)`.
- [ ] Marcação semântica e cópia em português intocadas.
