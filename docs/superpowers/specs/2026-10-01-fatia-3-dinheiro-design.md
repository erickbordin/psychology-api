# Fatia 3 — O dinheiro

**Data:** 1º de outubro de 2026
**Estado:** aprovado pelo dono em 1º de outubro de 2026, design e versão escrita
**Base:** `2026-09-01-redesenho-produto-design.md` §4④, §5.3, §5.6 e §9 (Fatia 3)
**Linguagem visual:** `2026-09-27-linguagem-apple.md` e o reestrutura-frontend-design

---

## 1. O que a fatia entrega

O psicólogo responde duas das três perguntas do painel: **quanto faturei** e
**como foi meu mês**. Concretamente:

- a sessão realizada carrega o valor dela, congelado no momento em que ela vira
  REALIZADA;
- dar baixa é um clique, com desfazer, e existe em lote por paciente;
- a tela `/financeiro` mostra o mês: painel de presença, os três totais, quem
  está devendo e as sessões do mês;
- a Hoje ganha a pendência "realizada sem baixa", e a ficha passa a mostrar valor
  e pagamento na linha do tempo.

*No fim:* o psicólogo fecha o mês sem planilha.

## 2. Registro de decisões

Todas de 1º de outubro de 2026, do dono, respondendo a perguntas fechadas.

| # | Decisão | Alternativa recusada |
|---|---|---|
| F1 | **Competência.** Faturado, recebido e a receber contam as sessões REALIZADAS **no mês exibido** (pela `dtConsulta`). Recebido + a receber = faturado, sempre. | Caixa (dinheiro que entrou no mês pelo `pagoEm`); os dois juntos |
| F2 | **"Quem está devendo" é tudo em aberto**, de qualquer mês, agrupado por paciente. | Só o mês exibido |
| F3 | **Baixa é um clique e grava `pagoEm = agora`**, com "Desfazer baixa" na mesma linha. | Clique que abre data; clique sem desfazer |
| F4 | **Baixa em lote por paciente**: "Dar baixa em todas (N)" em "Quem está devendo". | Caixinhas; só individual |
| F5 | **O valor congela quando a sessão vira REALIZADA.** Sessão AGENDADA acompanha o valor atual do paciente — reajustar o paciente vale para as futuras já agendadas, inclusive série. | Snapshot no agendamento (spec 5.3 literal); perguntar ao reajustar |
| F6 | **Valor de uma sessão é editável no Financeiro** ("Alterar valor"). | Não nesta fatia |
| F7 | **Sessão realizada sem valor fica à parte**: fora dos totais, contada como "N sessões sem valor definido". | Contar como R$ 0; exigir valor para agendar |
| F8 | **Sessão com baixa não sai de REALIZADA**: 409 até a baixa ser desfeita. | Limpar a baixa junto; manter a baixa |
| F9 | **"Realizada sem baixa" na Hoje é só de hoje**, mesmo recorte de SEM_ANOTACAO. | Fora da Hoje; todas em aberto |
| F10 | **O painel do mês mora no topo do Financeiro.** | Na Hoje; nos dois |
| F11 | **Taxa de falta = faltas ÷ (realizadas + faltas)**; cancelada não entra. **Ativo** = paciente com ≥ 1 REALIZADA no mês. | Faltas ÷ todas; ativo com qualquer sessão |
| F12 | **A ficha mostra valor, chip de pagamento e baixa/desfazer** em cada sessão realizada; falta mostra "Não cobrada". | Só mostrar; não mostrar |
| F13 | **Modo privacidade no Financeiro borra nomes e todo valor em R$**, totais inclusive. Chips ficam nítidos. | Só nomes |
| F14 | **Paciente excluído continua devendo**: aparece em "Quem está devendo" com o nome, sem link para a ficha, e a baixa funciona. Entra nos totais. | Some de tudo; totais sim e lista não |
| F15 | **Baixa só em REALIZADA.** Pagamento antecipado não existe nesta fatia. | Baixa em qualquer status |
| F16 | **Valor tardio é manual**: a sessão sem valor ganha "Definir valor" no Financeiro. Salvar o valor do paciente não retroage. | Retroagir automático |
| F17 | **Chips de dinheiro reaproveitam as variantes existentes**: "Pago" = `realizada` (verde ✓), "A receber" = `alerta` (âmbar !), "Não cobrada" e "Sem valor definido" = neutro. Nenhum token novo. | Todos neutros; variantes novas |
| F18 | **Sessão com baixa não é excluída** (409), **baixa exige valor** (409, "Defina o valor antes de dar baixa."), **baixa repetida é idempotente** (não muda a data nem audita de novo). | — (confirmadas como derivadas) |
| F19 | **Lote sem confirmação.** Um clique grava e anuncia; desfaz-se linha a linha. | Diálogo de confirmação |
| F20 | **Cópia nova com acento**; a cópia existente fica intocada. Os dois registros convivem até alguém decidir acentuar o resto. | Sem acento como as telas atuais; acentuar tudo agora |

### O que F5 muda na spec original

A spec 5.3 dizia "snapshot no agendamento". A razão dela continua de pé —
reajustar não pode reescrever **histórico faturado** — e F5 a cumpre por outro
caminho: o que já foi realizado nunca muda, e o que ainda não aconteceu não é
histórico. Sem F5, uma série de 52 semanas criada hoje prenderia o preço de hoje
por um ano.

## 3. Regras do dinheiro — o backend

### 3.1 O valor (`Consulta.valor`)

- **Preenchido ao virar REALIZADA, se estiver nulo:** `paciente.valorSessao`; se
  nulo, `usuario.valorPadraoSessao`; se nulo, fica nulo (F7). Vale nos dois
  caminhos que mudam status: `PATCH /consultas/{id}/status` e `PUT /consultas/{id}`.
- **Nunca é apagado automaticamente.** Sair de REALIZADA (sem baixa) mantém o
  valor, como a spec 5.3 já pedia para a falta. Voltar a REALIZADA mantém o valor
  que já estava, sem recalcular.
  - *Borda conhecida e aceita:* uma sessão REALIZADA devolvida a AGENDADA pelo
    `PUT` fica com o valor antigo. Caminho raro, sem tela que o ofereça.
- **Agendar não grava valor.** `cadastrar` e `cadastrarSerie` seguem sem tocar no
  campo.
- **`PATCH /consultas/{id}/valor`** `{ "valor": 180.00 }` troca o valor (F6, F16).
  Só em REALIZADA **sem** baixa: status ≠ REALIZADA → 409 "Só sessão realizada tem
  valor a alterar. Marque a sessão como realizada antes."; com baixa → a 409 do
  §3.3. Validação idêntica à de `valorSessao`:
  `@NotNull` ("Informe o valor da sessão."), `@DecimalMin("0.01")` ("O valor da
  sessão deve ser maior que zero."), `@Digits(integer = 8, fraction = 2)` ("O
  valor da sessão deve ter no máximo duas casas decimais."), mesmo `@Schema`.

### 3.2 A baixa (`Consulta.pagoEm`)

- **`PATCH /consultas/{id}/baixa`** (sem corpo) grava `pagoEm = LocalDateTime.now(clock)`.
  - REALIZADA com valor e sem baixa → grava, audita, 200 com a consulta.
  - Já tem baixa → 200 com a consulta **inalterada**, sem auditar (F18).
  - Status ≠ REALIZADA → 409 "Só sessão realizada recebe baixa. Marque a sessão como realizada antes."
  - REALIZADA sem valor → 409 "Defina o valor antes de dar baixa."
- **`DELETE /consultas/{id}/baixa`** limpa `pagoEm`.
  - Com baixa → limpa, audita, 200 com a consulta.
  - Sem baixa → 200 inalterada, sem auditar.
- **`POST /consultas/baixas`** `{ "consultaIds": [...] }` — lote (F4).
  - 1 a 200 ids; vazio ou acima disso → 400 no campo `consultaIds`. Ids repetidos
    contam uma vez.
  - **Tudo ou nada.** Qualquer id que não seja do psicólogo logado → 404, nada
    gravado. Qualquer um que não seja REALIZADA com valor → 409 (as mesmas frases
    acima), nada gravado. Os que já têm baixa são pulados, sem erro.
  - Responde 200 `{ "consultasBaixadas": <quantas ganharam baixa agora> }`.
  - Vai por lista de ids, e não por paciente, por causa de F14: o repositório não
    tem — e não deve ter — busca de paciente excluído.
- Toda escrita que muda alguma coisa audita `ATUALIZACAO "Consulta" <id>` **depois**
  do `save`, dentro de `@Transactional` — uma linha de auditoria por consulta no lote.

### 3.3 As travas da baixa (F8, F18)

| Caminho | Com baixa | Resposta |
|---|---|---|
| `PATCH /status` para qualquer status ≠ REALIZADA | recusa | 409 "Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status." |
| `PUT /consultas/{id}` com `status` ≠ REALIZADA | recusa | mesma 409 |
| `PUT /consultas/{id}` só com `dtConsulta` | permite | — |
| `PATCH /valor` | recusa | 409 "Esta sessão já tem baixa. Desfaça a baixa antes de alterar o valor." |
| `DELETE /consultas/{id}` | recusa | 409 "Esta sessão tem baixa registrada. Desfaça a baixa antes de excluir a sessão." |

A guarda de anotação do `DELETE` continua vindo primeiro; a de baixa vem logo
depois, e as duas antes de qualquer mutação. `cancelarSerie` e `deletarPaciente`
não são tocados: os dois só alcançam sessão futura e não realizada.

### 3.4 O que as respostas ganham

- `ConsultaResponseDTO` ganha `valor` (nulável) e `pagoEm` (nulável).
- `SessaoDaFichaDTO` ganha `valor` e `pagoEm` (o comentário que dizia "não
  antecipar" sai).
- `SessaoDoDiaDTO` ganha `valor` e `pagoEm`.
- `TipoPendencia` ganha `SEM_BAIXA`: sessão de **hoje**, REALIZADA, com valor e
  sem baixa (F9). Uma mesma sessão pode gerar SEM_ANOTACAO **e** SEM_BAIXA — são
  duas linhas em `pendencias`, com o mesmo `consultaId`.

### 3.5 As duas leituras novas

Parâmetros `ano` e `mes` (inteiros). Os dois ausentes → mês corrente pelo `Clock`.
Só um deles → 400 no campo que faltou ("Informe 'ano' e 'mes' juntos, ou nenhum
dos dois."). `mes` fora de 1–12 → 400 em `mes` ("O mês deve estar entre 1 e 12.");
`ano` fora de 2000–2100 → 400 em `ano` ("O ano deve estar entre 2000 e 2100.").
Mesmo `PeriodoInvalidoException` de `GET /consultas`.

O mês vai de `primeiroDia 00:00` a `ultimoDia 23:59:59.999999999`, sobre
`dtConsulta`. Consulta com `deletedAt` não entra em nada. Os totais do mês e as
contagens do painel são agregados em SQL no repositório (spec 5.6). "Devendo" lê
as sessões em aberto e agrupa no serviço, porque o lote precisa dos ids de cada
uma — somar a lista que já veio não é baixar dado à toa. Nenhuma das duas
leituras é auditada: é listagem.

**`GET /financeiro/mes`** → `FinanceiroMesDTO`

| Campo | Regra |
|---|---|
| `ano`, `mes` | o mês respondido |
| `faturado` | Σ `valor` das REALIZADAS do mês com valor |
| `recebido` | Σ `valor` das REALIZADAS do mês com valor e `pagoEm` |
| `aReceber` | `faturado − recebido` |
| `sessoesSemValor` | quantas REALIZADAS do mês têm `valor` nulo |
| `sessoes[]` | todas as REALIZADAS do mês, por `dtConsulta` crescente: `consultaId`, `dtConsulta`, `pacienteId`, `pacienteNome`, `pacienteExcluido`, `valor`, `pagoEm` |
| `devendo[]` | **todo** o débito em aberto (F2): REALIZADAS com valor e sem baixa, de qualquer data, agrupadas por paciente: `pacienteId`, `pacienteNome`, `pacienteExcluido`, `quantidadeSessoes`, `total`, `consultaIds[]`. Ordem: `total` decrescente, depois nome |

Dinheiro sai como número com duas casas. Mês sem nada → `0.00` nos três totais e
listas vazias, nunca nulo.

**`GET /painel/mes`** → `PainelMesDTO`

| Campo | Regra |
|---|---|
| `ano`, `mes` | o mês respondido |
| `realizadas`, `faltas`, `canceladas` | contagem por status no mês |
| `taxaDeFalta` | `faltas ÷ (realizadas + faltas)`, número entre 0 e 1 com 4 casas; **nulo** quando o denominador é zero |
| `pacientesAtivos` | pacientes distintos com ≥ 1 REALIZADA no mês |

## 4. As telas

Toda cópia nova abaixo é **com acento** (F20) e deve sair exatamente assim.

### 4.1 Financeiro — `/financeiro`

De cima para baixo:

1. **Cabeçalho**: `<h1>Financeiro</h1>`, o mês por extenso ("outubro de 2026")
   e os botões "Mês anterior" / "Próximo mês". Abre no mês corrente; o mês vive
   em `useState`, como filtro nas outras telas.
2. **"O mês"** (`<section aria-label="O mês">`): Realizadas · Faltas ·
   Canceladas · Taxa de falta (`12%`, arredondado; "—" quando nula) · Pacientes
   ativos.
3. **Totais** (`<section aria-label="Totais">`): Faturado · Recebido · A
   receber, em R$. Abaixo, quando `sessoesSemValor > 0`: "1 sessão sem valor
   definido, fora dos totais" / "N sessões sem valor definido, fora dos totais".
4. **"Quem está devendo"** (`<section aria-label="Quem está devendo">`): uma
   linha por paciente com nome, "N sessões" (singular "1 sessão"), total, o botão "Dar baixa em todas
   (N)" e "Abrir a ficha" — o link some quando `pacienteExcluido`, e o nome ganha
   "(paciente excluído)". Ao dar baixa, a região viva anuncia "N sessões de
   <nome> com baixa." (singular "1 sessão de <nome> com baixa.") Vazio:
   `EstadoVazio` "Ninguém devendo" / "Sessões realizadas sem baixa aparecem aqui."
5. **"Sessões do mês"** (`<section aria-label="Sessões do mês">`): uma linha
   por sessão realizada: data · hora, nome, valor ou "Sem valor definido", o chip
   ("Pago" / "A receber" / "Sem valor definido") e as ações:
   - com valor e sem baixa: "Dar baixa", "Alterar valor";
   - com baixa: "Desfazer baixa";
   - sem valor: "Definir valor".
   Vazio: `EstadoVazio` "Nenhuma sessão realizada em <mês>." (ex.: "em outubro de 2026").
6. **Diálogo de valor**: título "Alterar valor da sessão" ou "Definir valor da
   sessão", campo "Valor da sessão" (obrigatório), "Salvar" / "Cancelar". Usa
   `useFormularioDoProduto` e o mesmo zod de `valorSessao` dos pacientes, mais o
   obrigatório ("Informe o valor da sessão."). Erro do servidor pousa no campo
   ou no `role="alert"`.

**Privacidade (F13):** nome de paciente e todo valor em R$ ganham
`data-borrado`. Chips, contagens do painel e a porcentagem ficam nítidos.

**Erro de ação** (baixa, desfazer, lote): `role="alert"` na própria linha com a
mensagem do servidor, como na Hoje. Botão trava enquanto a chamada está em voo.

### 4.2 Hoje

- `frasePendente('SEM_BAIXA')` = "dê baixa no pagamento".
- O item da fila passa a carregar **`tipos[]`**, não um `tipo`: a mesma sessão
  pode estar sem anotação e sem baixa ao mesmo tempo, e o `reconciliar` de hoje
  sobrescreveria uma pela outra. As frases se juntam com " · ".
- Linha com SEM_BAIXA ganha o botão "Dar baixa".
- Na lista "Sessoes de hoje" (rótulo existente, sem acento, intocado), sessão com baixa ganha o chip "Pago" e o botão
  "Desfazer baixa".
- A Hoje **não mostra valor em R$** em lugar nenhum.

### 4.3 Ficha

Em cada sessão da linha do tempo:

- REALIZADA com valor: o valor ("R$ 150,00"), o chip "Pago" ou "A receber", e
  "Dar baixa" / "Desfazer baixa".
- REALIZADA sem valor: chip neutro "Sem valor definido", sem ação (o valor se
  define no Financeiro).
- FALTOU: chip neutro "Não cobrada".
- AGENDADA e CANCELADA: nada de dinheiro.

A ficha não borra nada sob privacidade, como hoje.

### 4.4 Invalidação

Toda escrita de dinheiro **e** a troca de status invalidam os quatro prefixos:
`['financeiro']`, `['painel']` (cobre `hoje` e `mes`) e `['ficha']`. A
mutação de status da Hoje, que hoje só derruba `['painel','hoje']`, passa a
derrubar os três.

## 5. Invariantes

1. Semântica e testes por rótulo escrito, nunca por `className` (reestrutura §5).
2. A cópia existente não muda.
3. Isolamento: toda leitura e escrita nova passa por `securityService` e por
   métodos `...AndUsuario...AndDeletedAtIsNull`; o que é de outro psicólogo é 404.
4. As duas suítes continuam verdes; `tsc` limpo; os dois e2e existentes passam.
5. Nenhum comentário novo no código-fonte (regra do dono).

## 6. Fora de escopo

Ajustes e o valor padrão editável (Fatia 4); recibo, NFS-e e gateway; pagamento
antecipado e pacote de sessões; cobrança de falta; caixa (F1); exportação; o
"quem está escapando" (risco 3 da spec original).

## 7. Em aberto para o dono

Nada. A única pergunta da revisão escrita (acento) virou F20.

**Para avaliação técnica:** a sessão realizada passa a carregar quanto valeu e
se foi paga; uma tela nova soma o mês e lista quem deve; a baixa é um clique,
reversível, e protegida contra apagar dinheiro registrado sem querer.
