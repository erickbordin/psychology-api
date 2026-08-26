# Consulta recorrente — design

**Data:** 2026-08-25
**Status:** aprovado, aguardando plano de implementação
**Escopo:** `psychology-api` (backend). Nenhuma mudança no frontend, que não existe.

## Objetivo

Permitir agendar uma série de consultas semanais num único pedido — o caso
dominante do consultório: paciente fixo, mesmo dia da semana, mesmo horário.

Hoje o psicólogo cria dez consultas com dez `POST /consultas`, e cancelar a
terapia significa dez `DELETE`.

## As três decisões que moldam o resto

### 1. Horizonte explícito, ocorrências materializadas na criação

A série termina onde o psicólogo disser: `quantidadeSessoes` ou `dtLimite`. O
`POST` gera **todas** as consultas de uma vez, como linhas normais em `consulta`.

Rejeitado — **série indefinida com geração rolante** ("toda terça até eu
cancelar"): é mais fiel à clínica, onde terapia não tem data de fim, mas exigiria
a primeira infraestrutura de agendamento do projeto (job periódico estendendo uma
janela) e tudo que vem com ela: idempotência, o que fazer quando o job não roda,
observabilidade de job. Fora de proporção para o MVP.

Rejeitado — **guardar só a regra e calcular as ocorrências na leitura**: sem
linha no banco, a ocorrência não pode ter status próprio, nem anotação, nem
participar da janela de conflito sem reescrever essa lógica inteira.

Consequência aceita: renovar a terapia é um `POST` novo, não uma extensão.

### 2. Ocorrência independente, série como vínculo

Cada consulta gerada é uma consulta comum. Editar, cancelar, anotar e o CRUD
inteiro seguem funcionando sem alteração nenhuma. A série é um vínculo — uma
coluna `id_serie` — que existe para **uma** operação nova: cancelar as
ocorrências futuras de uma vez.

Rejeitado — **três modos por operação** ("esta", "esta e as futuras", "toda a
série", como no Google Calendar): cada endpoint de consulta ganharia um parâmetro
de escopo e cada combinação um teste. Poder que ninguém pediu.

Rejeitado — **nenhum vínculo** (o endpoint seria só um atalho que cria N
consultas soltas): zero custo de schema, mas paciente que encerra na 3ª sessão
deixa 7 consultas para cancelar uma por uma. É metade do problema resolvido.

### 3. Conflito de horário aborta a série inteira

Valida as N ocorrências **antes** de gravar qualquer uma. Se alguma colide,
responde `409` com as datas em conflito e não cria nada.

Rejeitado — **pular a ocorrência em conflito**: devolve `201` escondendo uma
série incompleta. Fácil de não ler e descobrir semanas depois, quando o paciente
aparecer numa semana sem horário.

Rejeitado — **ignorar o conflito**: contradiz a regra que o próprio projeto impõe
no cadastro avulso.

## Modelo de dados

Uma coluna em `consulta`:

```sql
-- V7__20260825.sql
ALTER TABLE consulta ADD COLUMN id_serie UUID;
CREATE INDEX idx_consulta_serie ON consulta (id_serie);
```

Na entidade:

```java
@Column(name = "id_serie")
private UUID serieId;
```

Nullable: consulta avulsa nasce sem série, e é o caso mais comum. Sem valor
default e sem `@PrePersist` — o `serieId` é atribuído pelo service só no fluxo
recorrente.

**Sem tabela `serie`.** Ela guardaria apenas um UUID: a regra (dia da semana,
hora, quantidade) é consumida na geração e nunca reconsultada. O preço é não
saber, depois, qual era a regra original — o que elimina "estender a série" como
operação possível. Aceito, porque a operação real do consultório é renovar, não
estender.

O `id_serie` **não é** foreign key. É um agrupador. Nomeado no padrão `id_*` das
outras colunas de identificador da tabela.

## Contrato

### `POST /consultas/recorrentes` → `201`

```json
{
  "pacienteId": "3f1c...",
  "dtPrimeiraConsulta": "2026-09-02T15:00:00",
  "quantidadeSessoes": 10
}
```

ou, no lugar de `quantidadeSessoes`:

```json
{ "dtLimite": "2026-12-20" }
```

Resposta:

```json
{
  "serieId": "9b2e...",
  "quantidade": 10,
  "consultas": [ { "id": "...", "dtConsulta": "...", "status": "AGENDADA", ... } ]
}
```

DTOs, no padrão de nomes do projeto:

- `ConsultaRecorrenteCadastroDTO` — entrada. Em
  `controller/dto/consulta/`.
- `SerieConsultaResponseDTO(UUID serieId, int quantidade, List<ConsultaResponseDTO> consultas)`
  — saída. Reaproveita o `ConsultaResponseDTO` existente para os itens.

Frequência é semanal fixa, **sem campo na requisição**: é o que o roadmap pede.
Quinzenal ou mensal depois é campo novo com default semanal, sem mudança de
schema.

### `DELETE /consultas/series/{serieId}` → `200`

Soft delete das ocorrências **futuras** da série (`dtConsulta >= agora`).

```json
{ "serieId": "9b2e...", "ocorrenciasRemovidas": 7 }
```

`SerieCancelamentoResponseDTO(UUID serieId, int ocorrenciasRemovidas)`.

Ocorrência passada não é removida: é histórico clínico, e sessão realizada não
deixa de ter acontecido porque a terapia terminou.

Série de outro psicólogo, ou inexistente, responde `404` — mesma semântica do
resto da API, e pelo mesmo motivo (não revelar que o id existe).

Distinção que precisa ficar explícita, porque as duas situações produzem lista
vazia se o filtro for só de futuras:

| Série | Ocorrências futuras | Resposta |
|---|---|---|
| não existe, ou é de outro psicólogo | — | `404` |
| existe, do psicólogo logado | nenhuma (todas já passaram) | `200` com `ocorrenciasRemovidas: 0` |
| existe, do psicólogo logado | há futuras | `200` com a contagem |

Ou seja: o `404` é decidido pela **existência da série** (qualquer ocorrência,
passada ou futura), não pela existência de ocorrência cancelável.

Este `DELETE` devolve `200` com corpo, divergindo dos outros `DELETE` da API, que
devolvem `204` vazio. Deliberado: quantas sessões foram canceladas é a informação
que o psicólogo precisa ver, e um corpo vazio a esconderia.

### Rotas e classe de controller

As duas entram no `ConsultaController` existente, que já tem
`@RequestMapping("/consultas")`. Não é preciso classe nova: a regra do projeto que
exige controller separado vale para sub-recurso pendurado em `/pacientes/{id}`,
que não é o caso aqui.

### Erros

| Situação | Status | Exceção |
|---|---|---|
| Paciente inexistente ou de outro psicólogo | `404` | `PacienteNaoEncontradoException` |
| Série inexistente ou de outro psicólogo | `404` | `ConsultaNaoEncontradaException` |
| Alguma ocorrência em conflito de horário | `409` | `ConflitoDeHorarioException` |
| Horizonte ausente, duplicado ou inválido | `400` | Bean Validation |
| Campo obrigatório ausente | `400` | Bean Validation |

Nenhuma exceção nova, nenhum handler novo. O `409` leva as datas em conflito na
mensagem, formato `"Conflito de horario em: 2026-09-16T15:00, 2026-10-07T15:00"`
— o `ConflitoDeHorarioException` e seu handler ficam intocados.

**Reaproveitar `ConsultaNaoEncontradaException` para série** é decisão
consciente: a semântica de resposta é idêntica e uma classe nova só para isso não
paga o custo.

## Regras

### Geração das ocorrências

1. A primeira ocorrência é exatamente `dtPrimeiraConsulta`.
2. Cada ocorrência seguinte é `plusWeeks(1)` da anterior. `plusWeeks` preserva a
   hora local, que é o comportamento correto para "toda terça às 15h" — se o
   horário de verão voltar, a série não desliza.
3. Com `quantidadeSessoes`: gera exatamente essa quantidade.
4. Com `dtLimite` (`LocalDate`): gera enquanto a ocorrência for `<= dtLimite` no
   fim do dia (`dtLimite.atTime(LocalTime.MAX)`).
5. Status inicial de cada ocorrência é `AGENDADA`, pelo `@PrePersist` que já
   existe.
6. Todas recebem o mesmo `serieId`, gerado no service.

### Validação do horizonte

Exatamente um entre `quantidadeSessoes` e `dtLimite`. Os dois juntos, ou nenhum,
é `400`.

Bean Validation não expressa esse XOR com anotação de campo, então: **constraint
customizada de classe** no DTO, com a violação anexada ao path da propriedade
`quantidadeSessoes`. O detalhe não é estético — constraint de classe sem
`addPropertyNode` produz violação global, e o `GlobalExceptionsHandler` só lê
`getFieldErrors()`, então a resposta sairia com a lista `erros` vazia. Anexar ao
path mantém o corpo do `400` idêntico ao do resto da API.

Limites: **2 a 52 ocorrências**, valendo para os dois caminhos. Menos de 2 não é
série, é consulta avulsa. Mais de 52 é quase certamente erro de digitação — sem
teto, um `dtLimite` errado gera anos de agenda.

No caminho `dtLimite`, estourar o teto é `400`, **não truncar em 52**. Truncar
silenciosamente devolveria `201` para uma série menor do que a pedida, que é
exatamente o defeito recusado na decisão 3. Mesma coisa quando `dtLimite` cabe
apenas uma ocorrência: `400`, com a mensagem indicando o intervalo válido.

`dtLimite` anterior a `dtPrimeiraConsulta` é `400`.

Essa validação depende de `dtPrimeiraConsulta`, então **não** cabe na constraint de
campo: ela roda junto do XOR, na constraint de classe.

`dtPrimeiraConsulta` leva `@Future`, igual ao `dtConsulta` do `POST /consultas`
avulso, que já valida isso. (Correção: uma versão anterior desta spec afirmava o
contrário sobre o cadastro avulso.)

### Validação de conflito

Uma única query no intervalo `[primeira − 50min, última + 50min]`, e a janela de
±50 min avaliada em memória, ocorrência por ocorrência. Uma query por ocorrência
seria N round trips para uma série de até 52.

Reaproveita `findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull`, que já existe.

Consulta `CANCELADA` é ignorada na comparação, e consulta soft-deletada já sai
filtrada pelo nome do método — idêntico ao cadastro avulso, inclusive no
tratamento de "exatamente 50 minutos de distância" como conflito. Manter a mesma
regra vale mais que corrigi-la aqui; se ela mudar, muda para os dois fluxos.

Ocorrências da mesma série nunca conflitam entre si (7 dias de distância), então
não há checagem cruzada interna.

### Transação

`@Transactional` no método de cadastro e no de cancelamento, com `saveAll`.
**Padrão novo no projeto** — nenhum service tem `@Transactional` hoje, porque
cada um faz um `save` só. Aqui há N inserts mais N registros de auditoria, e
tudo-ou-nada é o requisito.

### Auditoria

Uma linha `CRIACAO` **por ocorrência**, não uma pela série. Cada ocorrência é um
registro real com id próprio, e a trilha é indexada por `entidadeId`; uma linha
só pela série deixaria 51 consultas sem rastro de criação. Idem `EXCLUSAO` no
cancelamento, uma por ocorrência removida.

Consequência: um `POST` de 52 sessões grava 52 linhas de auditoria. Aceito — o
teto existe justamente para limitar isso.

### Isolamento

Sem novidade de padrão: usuário logado via `securityService.obterUsuarioLogado()`,
paciente por `findByIdAndUsuarioAndDeletedAtIsNull`, e o cancelamento por um
método derivado novo:

```java
List<Consulta> findBySerieIdAndUsuarioAndDeletedAtIsNull(UUID serieId, Usuario usuario);
```

O service busca as ocorrências da série (sem filtro de data) para decidir entre
`404` e `200`, conforme a tabela do contrato, e só então filtra as futuras para
cancelar.

## Testes

**Service (Mockito):**
- gera a quantidade pedida, todas no mesmo dia da semana e na mesma hora
- `dtLimite` para no limite e não passa dele
- conflito em qualquer ocorrência aborta: nenhum `save`, nenhuma auditoria
- teto de 52 respeitado nos dois caminhos
- todas as ocorrências saem com o mesmo `serieId`
- cancelamento remove só as futuras, por soft delete, e audita cada uma
- cancelamento de série de outro psicólogo lança `ConsultaNaoEncontradaException`
- cancelamento de série cujas ocorrências já passaram devolve contagem 0, não 404
- `dtLimite` que estoura o teto de 52 é rejeitado, não truncado

**Repository (Testcontainers):**
- `findBySerieIdAndUsuarioAndDeletedAtIsNull` não devolve série de outro
  psicólogo nem ocorrência soft-deletada

**HTTP (MockMvc):**
- `201` com a lista de consultas e o `serieId`
- `409` com as datas em conflito, e nada criado no banco
- `400` quando os dois horizontes vêm juntos, e `400` quando nenhum vem — com a
  lista `erros` preenchida, não vazia
- `404` no `DELETE` de série de outro psicólogo

## Fora de escopo

Frequência quinzenal ou mensal; editar a série; estender a série; os três modos
de edição do Google Calendar; série indefinida; notificação ou lembrete
automático de sessão.

## Pontos em aberto

Nenhum bloqueante. Registrado para depois: se o uso mostrar que renovar série é
frequente, a decisão de não ter tabela `serie` é o que precisa ser revisitada
primeiro — é ela que impede guardar a regra.
