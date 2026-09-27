# Reestrutura do frontend — linguagem Apple

**Data:** 27 de setembro de 2026
**Estado:** aprovado pelo dono, pronto para plano de implementação
**Substitui:** a direção visual de `prototipo/` e o comentário de linguagem em
`frontend/src/estilo/tokens.css:12-30`

---

## 1. O que está sendo trocado, e por quê isso é grande

A Fatia 1 adotou a linguagem do **Google Calendar** por decisão do dono, com uma
regra estrutural junto: **ação é forma, não cor** — pílula é ação, bloco chapado
com barra à esquerda é evento. A separação entre superfícies vinha de uma linha
de 1px (`--linha`).

Esta reestrutura troca as duas coisas. A linguagem nova é informada pela Apple e
separa superfícies por **camada, luminância e profundidade**, não por contorno.
A regra "ação é forma" cai junto, porque ela existia para permitir que botão e
evento fossem ambos azuis sem confundir — problema que a linguagem nova não tem.

**Isto não é troca de paleta.** É troca do mecanismo pelo qual toda superfície
do app se distingue da vizinha, e por isso alcança as cinco telas de uma vez
(ver §4).

### A régua muda de lugar

Até aqui, `prototipo/` era a referência contra a qual as revisões mediam
divergência. Foi ela que pegou, na revisão da Fatia 2, o chip cinza na ficha e a
regra `.chip.alerta` que nunca tinha sido copiada.

Com a linguagem trocada, o protótipo **aposenta como régua**. O que assume o
lugar é o artifact de espécimes aprovado em 27/09/2026, que traz as cinco telas
desenhadas, mais este documento. Toda revisão futura mede contra eles.

> **Nota sobre a régua antiga.** Ela já tinha rachadura conhecida: o protótipo
> desenha o dia como linha do tempo **horizontal**, e o app implementado usa
> lista **vertical** — divergência deliberada, registrada em
> `frontend/src/estilo/estilo.css:99-106` ("implementar o trilho horizontal
> seria reconstruir a tela, não estilizar"). Não estamos aposentando uma
> referência perfeita.

---

## 2. Registro de decisões

Todas de 27 de setembro de 2026, do dono, tomadas olhando para espécimes
renderizados — não para descrição.

| # | Decisão | Alternativa recusada, e por quê |
|---|---|---|
| D1 | **Apple substitui Calendar.** | Aplicar Apple *dentro* do Calendar (só fundamentos e tipografia). Recusada: o dono quis a troca de linguagem, não refinamento. |
| D2 | **Variante 2 — reestrutura.** Age sobre hierarquia e densidade, não só sobre a pele. | Variante 1, continuidade — mesma informação, mesma ordem, só aparência nova. Recusada por não justificar o custo do refactor. |
| D3 | **As cinco telas, reestruturadas.** | Reestruturar só Hoje e Ficha. Recusada: shell e primitivos são compartilhados, então meia refatoração entrega duas linguagens no mesmo produto. |
| D4 | **Dark mode: tokens em par agora, só o claro implementado e testado.** | (a) Entrar agora — caro, dois temas em toda tela nova para sempre. (b) Ficar fora e a linguagem se ajustar — desfaria a D1. |
| D5 | **Modo privacidade sobe para o shell** e passa a cobrir Pacientes. | Encolher a aposta de Pacientes, ou aceitar dado financeiro exposto sem borrar. |
| D6 | **A fila única "Precisa de você" entra**, incluindo a reconciliação de dois modelos de dado no cliente. | Manter pendências e sessões como listas separadas. |
| D7 | **Colapso de hora vazia na Agenda fica de fora desta reestrutura.** | (a) Aceitar a instabilidade. (b) Criar o campo de expediente agora. |
| D8 | **Semântica é invariante** em todas as telas. | Liberar a árvore para a reestrutura. Nunca esteve em jogo. |

### Por que a D7 é a mais instrutiva

A proposta colapsava uma hora vazia **na semana carregada**. Isso faria a grade
mudar de forma conforme o psicólogo navega entre semanas — e **contradiz
consistência espacial, que é princípio declarado pela própria linguagem que a
D1 adotou**. Adotar um princípio e violá-lo na primeira tela é pior que não
tê-lo.

A versão estável da regra lê um **expediente configurado**. Esse campo não
existe, e criá-lo toca `Usuario`, migration, API e a tela Ajustes — que é espaço
reservado de outra fatia. Então a regra volta quando Ajustes existir, não agora.

---

## 3. A linguagem

A especificação completa — 100 custom properties, escala tipográfica, elevação,
material, forma, movimento e o tratamento de cada controle — está em
`docs/superpowers/specs/2026-09-27-linguagem-apple.md`.

O que este documento fixa sobre ela:

- **Separação por camada, não por contorno.** A página fica num fundo base, os
  cartões flutuam numa superfície com sombra quase invisível. Sobrevive
  exatamente **uma** hairline: a da grade da Agenda, mais o anel interno dos
  campos de formulário.
- **Tokens nascem em par claro/escuro** (D4), mas só o claro é implementado e
  testado nesta reestrutura. O bloco escuro fica declarado e sem consumidor.
- **Movimento não entra aqui.** Ele tem orçamento próprio, levantado em
  18/09/2026: quatro candidatos aceitos, seis rejeitados. Ver a seção "O que já
  foi levantado" em `ai/Claude.md`.

### O custo declarado da linguagem

1. **~4% de luminância separa pior que 1px de linha** num monitor de consultório
   mal calibrado ou com sol na tela. É o preço direto da D1.
2. **Dark mode volta ao mapa.** O spec original do frontend o tinha deixado
   explicitamente fora de escopo; a D4 adia a implementação mas não o custo
   conceitual — a escala de luminância do tema claro é desenhada sabendo que
   existe um par.

---

## 4. Por tela

### 4.1 Shell (`Layout`, `Navegacao`)

Compartilhado pelas quatro telas protegidas. Recebe a linguagem nova e, pela
**D5**, passa a hospedar o estado do modo privacidade.

> **Isto é mudança de comportamento, não de aparência.** Hoje o estado vive em
> `HojePage` e some ao navegar. Compartilhado, ele **sobrevive à navegação**:
> atravessar para Pacientes mantém borrado. É o comportamento certo com alguém
> na sala, e precisa de teste próprio.

### 4.2 Hoje

Aposta aprovada: pendências e sessões agendadas sem status fundem numa **fila
única "Precisa de você"** no topo; sessões já resolvidas descem para uma lista
compacta. Peso visual proporcional à ação que a linha exige.

**É a parte que não é CSS.** Hoje `HojePage` recebe pendências e sessões como
dois arrays distintos da API. Intercalá-los num componente único ordenado por
hora exige reconciliar dois modelos de dado no cliente (D6).

### 4.3 Ficha

As pendências saem da coluna de peso igual e viram faixa-resumo subordinada
(contador + lista aberta); a linha do tempo clínica passa a ocupar a largura
inteira. O prontuário deixa de competir com o lembrete.

### 4.4 Pacientes

A linha deixa de ser só um nome: ganha **idade, contato e valor por sessão**, e o
chip de consentimento sobe da Ficha. Uma lista só de nomes não ajuda a escolher
ninguém.

Nenhum campo novo foi inventado — os quatro já existem no registro do paciente.

> **A aposta obrigou a D5.** Promover dado financeiro e de contato para uma tela
> sem controle de privacidade, na mesma sala onde a justificativa do modo
> privacidade foi escrita, não era aceitável. Sob privacidade: nome e a linha de
> metadado borram; o chip de consentimento **fica visível**, porque é sinal de
> status e sem ele a tela borrada não serviria para nada.

### 4.5 Agenda

Aposta aprovada: **o bloco ocupado carrega o estado da sessão**, não só o nome.
Sai o bloco azul chapado com texto branco; entra superfície tonal com barra de
estado à esquerda e duas linhas — nome, depois glifo e palavra de estado. No
foco, o bloco sobe um plano e revela o horário.

`status` já existe em `ConsultaDaAgenda`, então não há consulta nova.

**Colapso de hora vazia: fora (D7).** Todas as horas mantêm altura uniforme. A
grade fica alta e rola; é aceito.

**Aposta recusada, com o motivo preservado:** dar mais espaço ao dia em foco
exigiria largura de coluna variável, o que obriga a abandonar
`table-layout: fixed`, quebra o alinhamento vertical que faz a grade ser legível
como grade, e reabre a navegação por teclado entre colunas desiguais. É
reconstruir a tela — o que o time já recusou uma vez com o trilho horizontal.

### 4.6 Login

**Nenhuma aposta estrutural.** Única tela fora do shell, quase vazia. Uma tela
que tenta ser interessante aqui é uma tela errada. Só a linguagem nova.

---

## 5. Invariantes — não negociáveis pela reestrutura

1. **Semântica (D8).** A Agenda continua `<table role="grid">` com `<th scope="col">`
   de dia e `<th scope="row">` de hora, células com `role="gridcell"`. Pacientes
   continua `<ul>`. Login continua `<form>` com `<label>` reais e o anúncio de
   erro em `role="alert"`.
2. **Navegação e teste por rótulo escrito, nunca por `className`.** Se um teste
   precisar de classe ou de esperar animação para passar, o problema é o teste.
3. **Nenhuma informação removida.** Pode rebaixar, agrupar ou revelar sob
   demanda; não pode sumir.
4. **Copy em português intocada.** Pode mudar de lugar, não de palavra.
5. **A suíte continua verde.** 86 testes de unidade/componente, `tsc` limpo, e
   os dois fluxos e2e contra a API real.

---

## 6. Fora de escopo, de propósito

- **Implementar e testar dark mode** — D4. Os tokens existem; o tema não.
- **Movimento** — orçamento próprio, já levantado.
- **Colapso de hora vazia e o campo de expediente** — D7, volta com Ajustes.
- **Corrigir `Usuario e/ou senha incorretos!`** — a mensagem real do 401 viola a
  convenção da casa (sem acento, com exclamação, não diz o que fazer), mas é
  defeito **pré-existente** e trocar texto de erro de autenticação não é escopo
  de refatoração visual. Fica registrado.
- **A fonte Inter carregada e não usada** em `frontend/index.html`, enquanto
  `tokens.css` usa Roboto. Peso morto pré-existente; a linguagem nova define seu
  próprio stack e deve resolver isso de passagem, mas não é objetivo.
- **`DialogoDeConsulta` falando HTTP direto do componente**, furando a fronteira
  que as outras três telas de escrita respeitam. Pré-existente.

---

## 7. Riscos em aberto

| # | Risco | Estado |
|---|---|---|
| 1 | Separação por luminância falha em monitor mal calibrado | Aceito conscientemente (§3) |
| 2 | A reconciliação de dois modelos de dado na Hoje (D6) é a única parte fora do CSS, e a mais cara | Aprovada; precisa de plano próprio |
| 3 | O toggle de privacidade indica estado ligado só por cor de fundo (`aria-pressed`). A linguagem nova troca contorno por luminância, o que pode **piorar** isso | **Verificar na implementação**: o estado ligado precisa continuar legível sem depender só de tom |
| 4 | Estado de privacidade sobrevivendo à navegação é mudança de comportamento | Precisa de teste próprio (§4.1) |
| 5 | Status de sessão passa a ser carregado por cor tonal mais glifo e palavra | Mitigado por desenho — a palavra de estado é texto, não só cor |

---

## 8. Como isto se verifica

A revisão do conjunto desta reestrutura mede contra **três** fontes, nesta
ordem de autoridade:

1. **Este documento** — para as decisões e os invariantes.
2. **O artifact de espécimes** de 27/09/2026 — para o resultado visual das cinco
   telas.
3. **`2026-09-27-linguagem-apple.md`** — para valor exato de token, curva,
   duração e tratamento de controle.

Divergência entre eles resolve-se nesta ordem: decisão vence espécime, espécime
vence token.

**Para avaliação técnica:** o app troca a linguagem visual inteira e rearranja a
hierarquia de informação de cinco telas, sem remover nenhuma informação, sem
mexer no texto que o usuário lê e sem quebrar a estrutura que leitor de tela e
teste percorrem.
