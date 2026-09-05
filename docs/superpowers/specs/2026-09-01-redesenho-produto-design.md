# Redesenho do produto — design

**Data:** 2026-09-01
**Estado:** aprovado nas seis seções, pendente de plano de implementação
**Origem:** o produto construído não corresponde ao produto pretendido. Este
documento fecha o que ele deve ser antes de qualquer reescrita.

---

## 1. Por que este documento existe

A avaliação que abriu o trabalho foi: confuso, ruim para o usuário, sem
validação em formulários simples. A investigação confirmou parte disso e
encontrou uma causa mais funda: **o produto responde perguntas que o psicólogo
não faz, e não responde as que ele faz.**

Três desalinhamentos entre o pretendido e o construído:

| Pretendido | Construído |
|---|---|
| Painel de acompanhamento da prática | Dois contadores (`.length` de listas já baixadas) |
| Lembrete de consulta | Pendência clínica sem data, sem canal, sem aviso |
| Gestão, incluindo faturamento | Financeiro declarado fora de escopo |

E uma tela inteira — a Trilha de auditoria — ocupa 1/4 da navegação principal
entregando log de conformidade a um profissional que não audita a si mesmo,
enquanto guarda exatamente a matéria-prima que o painel precisaria.

O que **não** está errado, e não será reescrito: o backend. 170 testes,
Postgres real via Testcontainers, testes HTTP de fluxo, isolamento entre
profissionais verificado, atomicidade de série verificada, migration testada.
A metade forte fica.

## 2. Público e finalidade

Psicólogo autônomo, atende sozinho, **paga pelo produto**. Produto real com
usuário real — não portfólio, não estudo.

Isso é o que põe LGPD, backup, contrato de operador e entregabilidade de e-mail
dentro do escopo, e não numa lista de "depois".

## 3. Decisões fechadas

| Eixo | Decisão | Alternativa descartada |
|---|---|---|
| Abordagem | Protótipo primeiro, depois fatias verticais | Fatias direto; fundação horizontal primeiro |
| Frontend | Reescrita do zero | Manter base e refazer telas |
| Backend | Mantido e estendido | — |
| Painel responde | Meu dia/semana · Quanto faturei · Como foi meu mês | "Quem está escapando" (ver Riscos) |
| Financeiro | Valor por sessão + baixa manual | Gateway PIX; recibo/NFS-e |
| Aviso: quem | Psicólogo **e** paciente | Só psicólogo |
| Aviso: canal | E-mail na fase 1, WhatsApp na fase 2 | WhatsApp direto; SMS |
| Consentimento | Psicólogo declara + descadastro em todo e-mail | Double opt-in; sem consentimento |
| Anotação em repouso | Texto na coluna, disco criptografado do provedor | Criptografia por coluna |
| Validação | Servidor é autoridade; cliente é atalho | Só um dos dois |
| Texto do aviso | Blocos fixos escolhidos pelo psicólogo (§4⑤) | Campo de texto livre |
| Falta | Não é cobrada; Financeiro agrega só `REALIZADA` (§5.3) | Cobrar a hora perdida |
| Reescrita do front | `src/` a zero; ferramental fica. Telas voltam por fatia | Duas bases lado a lado; arquivar em branch |
| `observacoes_paciente` | Sai do schema (§5.8) | Virar campo da ficha |
| `dataNascimento` | Opcional (§5.8) | Obrigatório |
| Fuso horário | **Um só: `America/Sao_Paulo`** (§5.9) | Multi-fuso com `TIMESTAMPTZ` |
| Excluir paciente | Cancela as sessões futuras (§5.10) | Bloquear a exclusão; só esconder das telas |

As duas últimas foram fechadas em **4 de setembro de 2026**, depois do protótipo:
a primeira porque a revisão da Fase 0.5 achou que texto livre contradizia §7.1; a
segunda porque a Fase 0 passou a afirmar em duas telas, sem ninguém decidir, que
falta virava dívida.

## 4. Mapa de telas

Navegação com **cinco destinos e rótulo escrito**. O icon-only atual sai:
quatro ícones mudos numa coluna de 64px é adivinhação.

### ① Hoje — `/`

Responde *"como é meu dia"*.

- Próxima sessão em destaque: hora, paciente, quanto falta, atalho para a ficha
- Lista do dia: hora · paciente · status, com **Realizada / Faltou em um clique
  na própria linha** (hoje exige abrir diálogo)
- Faixa de pendências: sessão passada sem status, sessão realizada sem anotação,
  sessão realizada sem baixa

**Modo privacidade.** A decisão original de esconder dado clínico do painel
tinha razão: o painel fica aberto em tela cheia no consultório, com paciente
entrando. Ela não é revertida, é resolvida — um clique borra os nomes.
Preserva o motivo sem matar a utilidade.

### ② Agenda — `/agenda`

Semana por padrão (o "hoje" já é a tela ①). Agendar clicando no horário,
remarcar arrastando, série recorrente.

**Conflito de horário avisado antes do envio.** A API já sabe recusar
(`ConflitoDeHorarioException`); hoje o front só descobre apanhando.

### ③ Pacientes — `/pacientes` e `/pacientes/:id`

Lista com busca. A ficha **perde as quatro abas** e vira uma página:

- Cabeçalho: nome, idade, contato, valor de sessão, estado do consentimento
- **Linha do tempo de sessões**: uma linha por encontro, com data, status,
  valor, pago, e a anotação daquela sessão ali dentro
- Pendências do paciente ao lado

Isso funde as abas *Consultas* e *Anotações*, que hoje são a mesma realidade
partida em duas e correlacionada no olho pela data. Depende da mudança de
modelo em §5.1.

### ④ Financeiro — `/financeiro`

O mês: faturado, recebido, a receber. Lista de sessões com baixa em um clique.
Quem está devendo.

### ⑤ Ajustes — `/ajustes`

Perfil, valor padrão de sessão, antecedência e texto do aviso, e a **Trilha de
acesso movida para cá**. Ela continua existindo — com dado de saúde, registro
de acesso é ativo, não enfeite — mas sai de 1/4 da navegação principal.

**"Texto do aviso" é montagem de blocos fixos, não campo de texto livre** —
decisão do dono, 4 de setembro de 2026. O psicólogo escolhe quais frases entram,
de uma lista pronta; as frases obrigatórias não saem, e os únicos pedaços
variáveis são `{paciente}`, `{data}` e `{hora}` — que são exatamente o "só data,
hora, e quem" que §7.1 permite.

Por que não campo livre: §7.1 exige "zero conteúdo clínico" no aviso, e essa
exigência não vale só para o assunto. Com texto livre, a garantia sai do produto
e passa a depender de o psicólogo não escrever o motivo da consulta — e quem
responde por um vazamento é ele, que é o controlador. Com blocos, toda saída
possível é texto pré-aprovado: a garantia volta a ser estrutural. **§5.2 também
não tem onde guardar corpo digitado** — `AvisoDeConsulta` nunca teve campo de
texto, porque a mensagem sempre foi derivada da `Consulta`.

O que o `AvisoDeConsulta` passa a guardar é a **escolha dos blocos**, não a
mensagem montada: quais frases opcionais estão ligadas. A montagem é do envio.

Registro de rota: o protótipo da Fase 0.5 implementou isto como `<textarea>`
livre, por um erro no brief da tarefa, e a revisão pegou — a palavra "terapia"
digitada ali chegava ao corpo do e-mail sem obstáculo. Foi a única trava
`[escopo]` das duas fases de protótipo.

**Placar:** 4 destinos mudos + 4 abas na ficha → 5 destinos escritos + ficha de
página única. Duas telas novas, uma rebaixada, quatro fundidas em uma.

## 5. Modelo de dados

**Janela aberta:** uma migration só (`V1__20260901.sql`), nada em produção. O
schema pode ser remodelado agora, sem migration de compatibilidade nem
backfill. Fecha no primeiro deploy.

### 5.1 `Anotacao` liga à `Consulta`

`anotacao.id_consulta`, nullable. A anotação passa a ser *daquela sessão*, não
do paciente em geral.

Hoje ela liga a `Usuario` + `Paciente`, e `createdAt` é *quando foi digitada*,
não quando a sessão aconteceu — registrou terça na quarta de manhã, a
correlação quebra. É a causa estrutural da confusão na ficha.

Nullable porque anotação avulsa ("liguei fora do horário") continua válida; só
não entra na linha do tempo.

### 5.2 `Lembrete` quebra em dois

Uma entidade fazendo dois trabalhos que não se parecem. É a raiz do "lembrete
não lembra nada".

| Hoje | Vira | O que é |
|---|---|---|
| `Lembrete` | **`Pendencia`** | clínica, sem data, do paciente. Serve, fica |
| — | **`AvisoDeConsulta`** | mensagem agendada, presa a uma `Consulta` |

`AvisoDeConsulta`: `consulta`, `destinatario` (PSICOLOGO \| PACIENTE), `canal`
(EMAIL \| WHATSAPP), `agendadoPara`, `enviadoEm`, `estado` (PENDENTE \| ENVIADO
\| FALHOU \| CANCELADO), `erro`.

**Nenhum campo de corpo de mensagem, de propósito.** A mensagem é derivada da
`Consulta` na hora do envio, a partir dos blocos fixos que o psicólogo escolheu
(§4⑤) — os blocos escolhidos são preferência do usuário, não do aviso, e ficam
em `Usuario` (§5.5). Guardar o corpo aqui abriria a porta para texto arbitrário
chegar ao paciente, o que §7.1 proíbe.

O **token de descadastro** (§7.1) fica no `Paciente`, não no aviso: é estável
por paciente, sobrevive a remarcação, e um link antigo continua funcionando.
Campo `tokenDescadastro`, aleatório, sem relação com o id — e revogar grava em
`consentimentoAvisoEm = null`.

### 5.3 `Consulta` ganha dinheiro

`valor` (BigDecimal) e `pagoEm` (LocalDateTime, nulo = a receber).

`pagoEm` em vez de booleano: dá a data da baixa de graça, e o Financeiro precisa
dela para dizer "recebido em setembro".

**`valor` é cópia, não referência ao paciente.** Se apontasse para o valor do
paciente, reajustar a sessão de R$150 para R$200 reescreveria todo o histórico
faturado. Snapshot no agendamento.

**Falta não é cobrada** — decisão do dono, 4 de setembro de 2026. O Financeiro
agrega **só consulta com status `REALIZADA`**. Sessão em que o paciente faltou
não entra em faturado, não entra em a receber, e não põe ninguém na lista de
quem está devendo.

Isto é regra de agregação, não de dado: a consulta que virou falta **mantém o
`valor`**, porque o snapshot registra quanto aquela hora valia, e apagá-lo
perderia informação sem necessidade. `pagoEm` fica nulo para sempre, e nunca é
consultado. Consequência prática de desenho: `pagoEm = null` deixa de significar
"a receber" por si só — significa isso **apenas** quando o status é `REALIZADA`.
Qualquer consulta ao Financeiro que filtre só por `pagoEm` vai errar.

A regra entrou no produto por acidente antes de ser decidida: o protótipo da
Fase 0 reconciliou dado entre a ficha e o Financeiro e, ao fazê-lo, passou a
afirmar em duas telas que falta era faturada e virava dívida. Ninguém havia
decidido isso. Ficou registrado como pendência do dono até ele decidir.

**Não decidido aqui:** falta avisada com antecedência x falta sem aviso. Hoje o
modelo tem um status `faltou` só, e as duas caem nele. Se algum dia isso passar a
importar (cobrar quem não avisa, não cobrar quem avisa), é status novo — não
remendo nesta regra.

### 5.4 `Paciente` ganha

`valorSessao` (padrão que a consulta copia), `consentimentoAvisoEm`,
`canalAviso`. E-mail continua opcional — **exceto** quando o canal é EMAIL, aí é
obrigatório. Validação condicional, não `@NotNull` cego.

### 5.5 `Usuario` ganha preferências

Hoje só nome, e-mail, senha, `createdAt`. Entra: valor padrão de sessão,
antecedência do aviso, se o título profissional aparece no e-mail ao paciente
(motivo em §7.1), e **quais blocos opcionais do aviso estão ligados** (§4⑤).

Os blocos são preferência do psicólogo, iguais para todos os pacientes dele —
por isso ficam aqui, e não no `AvisoDeConsulta` nem no `Paciente`. Guardar a
escolha, e não o texto: assim corrigir uma frase mal escrita conserta os avisos
que ainda não saíram, em vez de deixar a versão velha congelada em cada um.

### 5.6 Endpoints de agregação

Hoje todo número do painel é `.length` de lista baixada para o navegador. Já
está errado: "Pacientes cadastrados" conta a página que veio, não o total.

- `GET /painel/hoje` — próxima sessão, sessões do dia, pendências
- `GET /painel/mes?ano&mes` — realizadas × canceladas × faltas, taxa de falta,
  pacientes ativos
- `GET /financeiro/mes?ano&mes` — faturado, recebido, a receber, quem deve
- `GET /consultas/conflito?dtConsulta=` — responde se o horário está livre, sem
  criar nada. É o que permite avisar do conflito **antes** do envio (§6.3); sem
  ele, a checagem prévia não tem como acontecer e o front volta a descobrir
  apanhando

Agregação em SQL no repositório. Volume de autônomo é pequeno, mas somar em
memória é errado de graça.

### 5.7 Envio: job agendado, com trava

`@Scheduled` varrendo `AvisoDeConsulta` com `estado = PENDENTE` e
`agendadoPara <= agora`.

**Com duas instâncias no ar, as duas varrem e o paciente recebe dois e-mails.**
A varredura reivindica a linha antes de enviar (`UPDATE ... WHERE estado =
'PENDENTE'` condicional; só envia quem afetou 1 linha). Barato agora, caro de
descobrir depois.

### 5.8 Dois achados a resolver

Os dois foram **resolvidos pelo dono em 5 de setembro de 2026**, antes do plano
do schema.

- `paciente.observacoes_paciente` existia no banco e na entidade e **não
  aparecia em DTO, API nem tela nenhuma**. Campo órfão. **Sai do schema.** A
  janela de migration ainda está aberta, então some sem custo; se um dia fizer
  falta, volta desenhado — com tela, DTO e propósito — em vez de coluna que
  ninguém sabe para que serve.
- `dataNascimentoPaciente` era `nullable = true` na entidade e `@NotNull` no
  `PacienteCadastroDTO`. **Vence a entidade: é opcional.** O `@NotNull` do DTO
  sai, e o front para de tratar como obrigatório.

  **Consequência a corrigir na Fatia 2:** o protótipo aprovado da Fase 0.5 pede
  a data de nascimento como obrigatória — submeter a ficha em branco marca
  `pac-nascimento` como inválido. O protótipo diverge desta decisão. Quem
  construir o formulário de paciente segue **esta seção**, não o protótipo: a
  spec é a autoridade, e o protótipo é do dia em que a decisão ainda não
  existia. Efeito na ficha: sem data, não há idade para mostrar no cabeçalho —
  o cabeçalho tem que aguentar a ausência, não imprimir "NaN anos".

### 5.9 Fuso horário: um só, e isso é uma escolha

Decisão do dono, 5 de setembro de 2026: o produto roda em **`America/Sao_Paulo`**.
`ENV TZ` no Dockerfile, e as colunas continuam `TIMESTAMP` sem fuso.

**O problema que isto resolve.** A imagem base não define fuso, então a JVM em
produção roda em UTC enquanto o psicólogo está em UTC−3. As colunas guardam duas
grandezas diferentes sem nada dizendo qual é qual: `data_hora_consulta` e
`agendado_para_aviso` são **hora de parede** (15h é 15h no consultório);
`created_at_*`, `deleted_at_*`, `pago_em_consulta` vêm de `LocalDateTime.now()`,
o **relógio da JVM**. O código compara as duas. Três consequências medidas:

- às 21h30, `LocalDate.now()` já é o dia seguinte, e a tela ① lista o dia errado —
  a sessão das 22h que o psicólogo está prestes a atender some
- cancelar série às 10h classifica a sessão das 11h de hoje como passada e **não
  a cancela**
- o job de §5.7 dispara com 3 horas de desvio; com `VESPERA` isso atravessa a
  meia-noite e o aviso sai no dia errado

**O que se aceita junto com a decisão.** O produto fica preso a um fuso. Psicólogo
no Acre (UTC−5) ou em Fernando de Noronha (UTC−2) vê a agenda deslocada. Destravar
depois exige `TIMESTAMPTZ`, campo de fuso no `Usuario`, e converter dado de
produção — sobre colunas cujo fuso de origem **não é uniforme**, porque hora de
parede e UTC convivem na mesma linha. Errar a conversão desloca em silêncio a hora
de toda consulta já agendada.

Isso não bloqueia a janela da migration: `ENV TZ` é uma linha e não muda schema.
Fica registrado como dívida consciente, não como esquecimento.

### 5.10 Excluir paciente cancela as sessões futuras

Decisão do dono, 5 de setembro de 2026.

Hoje `deletarPaciente` marca só `deleted_at_paciente`. As consultas futuras
continuam agendadas, aparecem em ① e ② **com o nome do paciente na resposta da
API** — na tela que fica aberta no consultório com outra pessoa entrando — e, na
Fatia 4, os avisos delas sairiam.

Excluir o paciente passa a cancelar as consultas **ainda não realizadas**. O
histórico passado fica intacto: prontuário tem prazo de guarda (§7.3), e sessão
que aconteceu é fato, não agendamento.

## 6. Validação

### 6.1 O que a API aceita hoje

Estes são defeitos do **backend**, não só do front. Consertar o React não
resolve nenhum deles.

- **Senha sem regra nenhuma.** `UsuarioCadastroDTO.senha` tem apenas
  `@NotBlank`. Sem comprimento mínimo, sem qualquer outra exigência. A senha
  `"a"` é aceita. Para um produto pago que guarda anotação clínica de terceiros,
  isso precisa mudar antes do primeiro usuário real. **É a única falha desta
  análise classificada como segurança, não acabamento.**
- **E-mail do paciente não é validado.** `UsuarioCadastroDTO.email` tem
  `@Email`; `PacienteCadastroDTO.email` não tem. E esse é justamente o campo que
  vira canal de aviso — endereço inválido vira falha de envio silenciosa.
- **Sem `@Size` em campo com limite no banco.** `telefone` é `VARCHAR(20)`,
  `email` é `VARCHAR(100)`. Entrada maior estoura na constraint: o usuário leva
  **500** onde deveria levar 400 com o campo culpado apontado.
- **Mensagem contradiz a regra.** `@Past` em `dataNascimento` diz "deve ser uma
  data válida" — mas `@Past` valida se é passada, não se é válida.
- **Dois registros de escrita no mesmo arquivo.** `"campo obrigatorio"`
  (minúsculo, sem acento) convive com `"A data de nascimento é obrigatória."`.
  O texto amador na tela **vem do servidor**.

### 6.2 Como fica

**Servidor é a autoridade, cliente é o atalho.** O envelope
`ErroRespostaDTO { status, mensagem, erros[{campo, erro}] }` já existe e é bom.
Fica. O cliente valida para poupar ida ao servidor, nunca para substituí-lo.

**Front: `react-hook-form` + `zod`.** Um schema por formulário, num arquivo, ao
lado da tela. Acaba o array `vazios`/`faltando` reinventado tela a tela.

**Quando o erro aparece** — regra que hoje não existe:

- No `blur`, só depois do primeiro toque no campo
- No `submit`, sempre, em todos os campos
- Depois que um campo já errou, revalida a cada tecla

**Botão nunca morre em silêncio.** O `if (!pacienteId || !data || !hora) return`
de hoje some. Submit inválido sempre produz: erro no campo, foco no primeiro
inválido, anúncio em `aria-live`.

**Obrigatório é visível antes do erro.** Campo obrigatório marcado, opcional
escrito "opcional". O usuário não descobre a regra errando.

**Tipo certo de campo.** Data de nascimento vira seletor de data — hoje é caixa
de texto pedindo `1991-04-12` digitado à mão. Telefone com máscara brasileira.

### 6.3 As regras

| Campo | Regra | Onde |
|---|---|---|
| Senha | mínimo 10 caracteres; recusa lista de senhas comuns (arquivo versionado no projeto, não serviço externo) | DTO **+** zod |
| E-mail (usuário e paciente) | formato + máx. 100 | DTO **+** zod |
| Nome | 2 a 100 | DTO + zod |
| Telefone | máx. 20, formato BR, opcional | DTO + zod |
| Nascimento | data real, no passado, idade < 120 | DTO + zod |
| Valor da sessão | > 0, 2 casas | DTO + zod |
| Data/hora da consulta | conflito checado antes do envio; passado avisa, não bloqueia | zod + API |
| Anotação | não vazia | DTO + zod |

Texto de erro num só registro: frase completa, com acento, dizendo o que fazer.
`"Informe o nome do paciente."`, não `"campo obrigatorio"`. Nos dois lados.

Consulta no passado **avisa mas não bloqueia**: o psicólogo precisa registrar
sessão que já aconteceu.

## 7. Dado de saúde e LGPD

> Esta seção é análise de apoio para orientar o desenho técnico, **não parecer
> jurídico**. Os pontos marcados **[verificar]** dependem de fonte primária ou
> de consulta a advogado antes de virarem decisão de produto.

### 7.1 O e-mail vaza dado de saúde pelo assunto

> **Assunto:** Lembrete: sua consulta com o psicólogo Erick, amanhã às 15h

Quem lê o assunto — tela de bloqueio, celular emprestado, computador de casa,
e-mail corporativo — descobre que aquela pessoa faz terapia. O paciente
consentiu em *receber o aviso*; não consentiu em contar isso a quem passar perto
da tela. E o assunto é a parte que aparece sem ninguém abrir nada.

- **Assunto neutro por padrão**: "Lembrete de agendamento". Sem "psicólogo",
  "consulta", "sessão", "terapia"
- **Remetente sem título profissional por padrão** (preferência de §5.5; o
  psicólogo escolhe, ciente do que expõe)
- **Zero conteúdo clínico.** Nunca anotação, motivo ou pendência. Só data, hora,
  e quem. Vale para o **aviso inteiro**, não só para o assunto: os cenários acima
  — computador de casa, e-mail corporativo — são de e-mail aberto e lido. É o que
  faz o corpo ser montagem de blocos fixos e não texto livre (§4⑤)
- **Descadastro por token opaco**, não pelo id do paciente na URL — link de
  e-mail vaza em encaminhamento e em log de proxy

O mesmo vale para o WhatsApp na fase 2, onde é pior: a prévia aparece na tela
bloqueada por padrão.

### 7.2 Papéis

O psicólogo é **controlador** dos dados dos pacientes. O produto é **operador**.
Define que o dado não pode ser usado para nada além de prestar o serviço, e
exige instrumento escrito com cada assinante — Termo de Uso mais cláusula de
operador. **[verificar com advogado]** Entregável da spec junto com o código.

### 7.3 Exclusão: há um conflito real

Hoje `deletedAt` é definitivo — "excluir" paciente não elimina nada, o dado fica
no banco para sempre. Colide com o direito de eliminação do titular.

Mas eliminar colide com outra coisa: o psicólogo tem **dever profissional de
guardar o prontuário por prazo mínimo**, e nesse período não *pode* apagar,
mesmo a pedido do paciente. **[verificar]** O prazo e a resolução exata do
Conselho Federal de Psicologia não foram confirmados nesta análise e **não devem
ser assumidos de memória** — conferir a resolução vigente no site do CFP antes
de virar regra no código.

Desenho que aguenta os dois lados:

- Separar **dado cadastral** (nome, contato — eliminável a pedido) de
  **prontuário** (anotação — retido pelo prazo legal, depois purgado)
- Purga real agendada no vencimento, não `deletedAt` eterno
- Exclusão pedida durante o prazo: anonimiza o cadastral, retém o prontuário,
  **registra o pedido** — a recusa parcial precisa ser justificável

### 7.4 O resto que produto pago com dado de saúde exige

- **Backup testado.** Hoje não existe. Sem ele, um incidente apaga o prontuário
  de todos os pacientes de todos os psicólogos. Backup nunca restaurado não
  conta como backup.
- **Exportação pelo psicólogo.** Portabilidade, e também o que impede o produto
  de virar refém — argumento de venda, não só de conformidade.
- **Plano de incidente.** Vazamento tem dever de comunicação à ANPD e aos
  titulares. Escrito antes de acontecer, não durante.
- **Trilha de acesso fica.** Já registra `VISUALIZACAO`, que é o que um registro
  de acesso a dado de saúde precisa ter. Sai da navegação principal, não do
  produto.

## 8. Testes

### 8.1 A dívida da reescrita do front

Vão junto 1 spec e2e e os testes de componente. Dois pegaram bug real e
**voltam como teste no projeto novo, antes da tela correspondente**:

- **Placeholder morto pós-login** — usuário entrava e caía em tela sem saída.
  Quem pegou foi o e2e, não o unitário.
- **Mock com nome de campo errado** — os mocks usavam `mensagem` onde a API
  manda `erro`. A suíte ficava verde enquanto, contra a API real, nenhum campo
  de erro era marcado.

### 8.2 O segundo caso tem cura estrutural

`springdoc-openapi` **já está no `pom.xml`** e ninguém usa. Os tipos TypeScript
passam a ser **gerados do OpenAPI**, em vez de escritos à mão em `tipos.ts`.
Mock que inventa campo para de compilar. O bug deixa de ser um que se pega com
atenção e vira um que não se escreve.

### 8.3 Onde cada coisa é testada

| Camada | Ferramenta | O que prova |
|---|---|---|
| Domínio/serviço | JUnit + Mockito | regra de negócio isolada |
| Repositório | Testcontainers (Postgres real) | query, isolamento, soft delete |
| HTTP | `HttpTest` existente | contrato, status, envelope de erro |
| Componente | Vitest + Testing Library | validação, foco, estado de erro |
| Contrato | tipos gerados do OpenAPI | front e API não divergem |
| Fluxo | Playwright | os quatro momentos do dia |

### 8.4 Invariantes que viram teste

- Remarcou a consulta → o aviso reagenda para a nova antecedência
- Cancelou a consulta → o aviso cancela e **não** é enviado
- Duas instâncias varrendo → o paciente recebe **um** e-mail
- Reajustou o valor do paciente → consulta já agendada **mantém** o valor antigo
- Paciente sem consentimento → nenhum aviso é agendado
- Descadastro clicado → revoga, e o próximo aviso não sai
- Soma do painel = soma das consultas no período

### 8.5 E2E: de 1 spec para 4 fluxos

Hoje `fluxo-de-fumaca.spec.ts`, um só. Passa a cobrir os quatro momentos:
**meu dia** (entra, vê a agenda, marca realizada), **a sessão** (abre ficha,
registra anotação, agenda retorno), **o dinheiro** (dá baixa, confere o mês),
**o aviso** (consente, agenda, aviso é criado).

Se um desses quebra, o produto quebrou para o usuário — não importa quantos
testes unitários estejam verdes.

## 9. Sequência de entrega

### Fase 0 — Protótipo

As cinco telas de §4 navegáveis, com dado falso realista.

**Sem acesso a psicólogo**, o substituto é cenário escrito: os quatro momentos
como roteiro concreto — *"são 8h40, tenho 6 sessões hoje, a de 9h faltou semana
passada, preciso saber disso antes dela chegar"* — e o protótipo percorrido
contra cada um.

**Isto é validação contra hipótese, não pesquisa com usuário.** Não descobre o
que não foi pensado; descobre o que foi pensado e não fecha. Um protótipo
aprovado aqui **não** é um produto validado.

Sai da fase: mapa de telas fechado, cenários escritos, tokens visuais.

### Antes das fatias — o schema, de uma vez

A janela de §5 fecha no primeiro deploy. Todo o modelo entra junto, antes das
fatias.

Fere a pureza da fatia vertical — parte do schema fica sem tela por algumas
semanas. Vale: uma migration reescrita agora contra quatro rodadas depois, sobre
banco com prontuário real dentro.

### As fatias

**Fatia 1 — Meu dia** *(a maior; carrega a fundação)*
Login, painel Hoje, Agenda. Junto: sistema de design tirado do protótipo,
`react-hook-form` + `zod`, tipos gerados do OpenAPI, navegação com rótulo,
tratamento de erro. **Senha com mínimo entra aqui** — é segurança, é barata, não
espera fatia nenhuma.
*No fim:* o psicólogo entra, vê o dia, agenda e marca presença.

**O painel Hoje cresce por fatia.** Na Fatia 1 ele traz sessão sem status; a
pendência "realizada sem anotação" chega na Fatia 2 e "realizada sem baixa" na
Fatia 3. A tela de §4 descreve o estado final, não o da Fatia 1 — o plano de
implementação não deve tentar construir as três de uma vez.

**Fatia 2 — A sessão**
Pacientes, cadastro, ficha de página única com linha do tempo. Anotação presa à
consulta.
*No fim:* o ciclo clínico inteiro funciona.

**Fatia 3 — O dinheiro**
Valor na consulta, baixa em um clique, tela Financeiro, painel do mês com taxa
de falta.
*No fim:* duas das três perguntas do painel respondidas.

**Fatia 4 — O aviso**
Consentimento na ficha, `AvisoDeConsulta`, job com trava, envio por e-mail,
descadastro por token, Ajustes.
*No fim:* o lembrete lembra alguém de alguma coisa.

**Fatia 5 — Portão de usuário real**
Não é polimento, é bloqueio. Nenhum psicólogo pagante entra antes de: backup
testado com restauração real, exportação dos dados, purga com prazo de retenção,
Termo de Uso e contrato de operador, plano de incidente, deploy do backend e
host do frontend.

**Fase 2, fora deste ciclo:** WhatsApp trocando o adaptador de canal.

### Tamanho relativo

Fatia 1 é de longe a maior (fundação embutida). 2 e 3 médias e parecidas. 4
média, com risco externo (provedor de e-mail, entregabilidade). 5 pequena em
código, grande em coisa que não é código.

Sem prazo em semanas: a disponibilidade não é conhecida, e número inventado aqui
vira cobrança depois.

## 10. Riscos e questões abertas

| # | Item | Estado |
|---|---|---|
| 1 | Prazo de guarda do prontuário (CFP) | **[verificar]** em fonte primária antes da Fatia 5 |
| 2 | Termo de Uso e contrato de operador | **[verificar com advogado]**; bloqueia a Fatia 5 |
| 3 | "Quem está escapando" ficou fora do painel | Recomendado e recusado. Encaixa na Fatia 3 sem retrabalho se houver mudança de ideia |
| 4 | Entregabilidade de e-mail | Risco externo da Fatia 4. Domínio próprio, SPF/DKIM/DMARC desde o início |
| 5 | Anotação em texto no banco | Aceito conscientemente. Quem tiver acesso ao banco lê tudo em claro |
| 6 | Protótipo sem usuário real | Aceito. Ver §9, Fase 0 |
| 7 | Assinatura do psicólogo (cobrança) | Fora deste ciclo. Produto é pago, mas o meio de cobrança não foi desenhado |

## 11. Fora de escopo

Mantido do escopo original: convênios, documentos, multiempresa, chat, roles,
login social.

Adicionado por decisão deste documento: gateway de pagamento, recibo, NFS-e,
WhatsApp na fase 1, double opt-in de paciente, criptografia por coluna,
cobrança da assinatura.
