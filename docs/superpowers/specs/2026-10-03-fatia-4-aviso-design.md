# Fatia 4 — O aviso

**Data:** 3 de outubro de 2026
**Estado:** aprovado pelo dono em 3 de outubro de 2026, design e versão escrita
**Base:** `2026-09-01-redesenho-produto-design.md` §4⑤, §5.2, §5.4, §5.5, §5.7, §5.9, §7.1 e §9 (Fatia 4); `prototipo/index.html` (tela Ajustes e prévia do e-mail); decisão D7 de `2026-09-27-reestrutura-frontend-design.md`
**Execução:** dois planos — **4A** (aviso) e **4B** (resto de Ajustes)

---

## 1. O que a fatia entrega

*No fim:* o lembrete lembra alguém de alguma coisa — o paciente recebe o
e-mail antes da sessão, o psicólogo recebe o resumo de amanhã, e Ajustes existe.

- **4A:** consentimento registrado no cadastro do paciente; job que envia o
  lembrete ao paciente e o resumo diário ao psicólogo, com trava contra envio
  duplo e nova tentativa em falha; descadastro por link; a parte de aviso da
  tela Ajustes, com prévia do e-mail.
- **4B:** trilha de acesso legível em Ajustes; expediente que define a grade
  da Agenda; troca de nome e de senha.

## 2. Registro de decisões

Todas de 3 de outubro de 2026, do dono, salvo onde indicado.

| # | Decisão | Alternativa recusada |
|---|---|---|
| A1 | **E-mail por SMTP genérico** (`spring-boot-starter-mail`), configurado por variável de ambiente. Qualquer provedor entra por configuração. Em dev e nos testes, um carteiro falso. | API de um provedor; só registrar nesta fatia |
| A2 | **Psicólogo recebe um resumo diário**, na véspera às 18h, com as sessões de amanhã. | Aviso por sessão; nada nesta fatia |
| A3 | **O job deriva o aviso na hora**, com trava por índice único. Remarcar, cancelar, mudar antecedência ou revogar não recalculam nada. | Materializar ao agendar (spec 5.7 literal) |
| A4 | **Sessão agendada depois da janela recebe o aviso logo**, se ainda não começou. | Não enviar |
| A5 | **Consentimento no diálogo do paciente**, por caixa de marcar; desmarcar revoga. | Botão na ficha |
| A6 | **Descadastro por página com confirmação** — o link não descadastra sozinho. | Clique único |
| A7 | **Três tentativas** (agora, +5 min, +15 min); depois FALHOU, visível na ficha. | Uma tentativa; só registrar |
| A8 | **Resumo com nome completo** dos pacientes. | Só iniciais |
| A9 | **Ajustes inclui**: liga/desliga do resumo, trilha de acesso, expediente, troca de nome e senha. | Só valor padrão e aviso |
| A10 | **Título profissional é um campo de texto** do psicólogo, usado no remetente quando ligado. | Texto fixo "Psicólogo(a)" |
| A11 | **Reply-To é o e-mail do psicólogo** — sustenta a frase "é só responder este e-mail". | Tirar a frase |
| A12 | **Depois do descadastro, o psicólogo pode registrar de novo**, com aviso no diálogo e marca na ficha; fica na trilha. | Travar; sem marca |
| A13 | **Grade da Agenda = expediente**, estável entre semanas; estica só para incluir sessão fora dele. Sem expediente, 07h–19h como hoje. | Recolher horas fora |
| A14 | **Trocar senha derruba as outras sessões**: revoga todo refresh token e emite um novo para a aba atual. | Manter todas |
| A15 | **Trilha legível**: frase por ação, nome do paciente resolvido no servidor, link para a ficha. | Lista bruta |
| A16 | **Cópia nova com acento** (regra F20 da Fatia 3); cópia existente intocada. | — |

## 3. Modelo de dados — migration `V2__20261003.sql`

A janela de "uma migration só" fechou com o primeiro deploy. Mudança agora é
migration nova; a V1 é imutável.

| Tabela | Coluna / índice | Regra |
|---|---|---|
| `usuario` | `titulo_profissional_usuario VARCHAR(80) NULL` | texto livre do psicólogo (A10) |
| `usuario` | `resumo_diario_usuario BOOLEAN NOT NULL DEFAULT true` | A2/A9 |
| `usuario` | `expediente_inicio_usuario SMALLINT NULL`, `expediente_fim_usuario SMALLINT NULL` | hora cheia; os dois nulos ou os dois preenchidos; `0 ≤ início < fim ≤ 24` (CHECK) |
| `paciente` | `descadastrado_em_paciente TIMESTAMP NULL` | data do último descadastro pelo link (A12) |
| `aviso_de_consulta` | `data_hora_consulta_aviso TIMESTAMP NOT NULL` | o horário da sessão **que este aviso anuncia** |
| `aviso_de_consulta` | `tentativas_aviso SMALLINT NOT NULL DEFAULT 0`, `proxima_tentativa_aviso TIMESTAMP NULL` | A7 |
| `aviso_de_consulta` | `UNIQUE (id_consulta, destinatario_aviso, data_hora_consulta_aviso)` | **a trava contra envio duplo** (A3). Remarcar muda o horário, e o aviso do horário novo é outro aviso |
| `resumo_diario` (nova) | `id_resumo UUID PK`, `id_usuario FK`, `data_resumo DATE`, `estado_resumo VARCHAR(20)`, `tentativas_resumo SMALLINT`, `proxima_tentativa_resumo TIMESTAMP`, `enviado_em_resumo TIMESTAMP`, `erro_resumo TEXT`, `created_at_resumo TIMESTAMP` + `UNIQUE (id_usuario, data_resumo)` | trava do resumo (A2) |

A tabela `aviso_de_consulta` está vazia em todo ambiente (nenhum código a
preenchia até aqui), então a coluna `NOT NULL` nova entra sem default nem backfill.

`AvisoDeConsulta` continua **sem campo de corpo** (spec 7.1). `erro` é
diagnóstico técnico, nunca conteúdo de mensagem.

## 4. O envio — 4A

### 4.1 O job

`@Scheduled(fixedDelay = 60s)`, ligado por `app.avisos.habilitado`
(padrão `true`; `false` nos testes). O relógio é o `Clock` do projeto, fuso único
`America/Sao_Paulo` (spec 5.9). Cada passada faz, nesta ordem:

1. **Avisos novos ao paciente.** Candidatas: sessões `AGENDADA`, não excluídas,
   com `agora < dtConsulta ≤ agora + 31h`, de paciente não excluído com
   `consentimentoAvisoEm` preenchido, `canalAviso = EMAIL` e e-mail não vazio.
   Para cada uma, o **momento do aviso** pela antecedência do psicólogo:
   - `H24`: `dtConsulta − 24h`; `H2`: `dtConsulta − 2h`;
   - `VESPERA`: dia anterior à sessão, às 18:00.

   Se `agora ≥ momento` (A4: inclui janela já passada) e não existe aviso para
   `(consulta, PACIENTE, dtConsulta)`, insere o aviso `PENDENTE` com
   `tentativas = 0`, `proximaTentativa = agora`, e o envia (passo 3). Violação do
   índice único = outra instância ganhou; segue sem erro.
2. **Resumo diário.** Se `agora ≥ 18:00` de hoje: para cada psicólogo com
   `resumoDiario = true` e ao menos uma sessão `AGENDADA` não excluída **amanhã**,
   insere `resumo_diario (usuario, amanhã)` `PENDENTE` se não existir, e o envia.
   Depois da meia-noite, o resumo que não saiu não sai mais.
3. **Envio e nova tentativa.** Para cada aviso/resumo `PENDENTE` com
   `proximaTentativa ≤ agora`:
   - **Reivindica** por UPDATE condicional (`... SET tentativas = tentativas + 1
     WHERE id = :id AND tentativas = :lidas AND estado = PENDENTE`); só envia
     quem afetou 1 linha.
   - **Revalida antes de enviar** (aviso): sessão ainda `AGENDADA`, não excluída,
     com o **mesmo** `dtConsulta` do aviso, ainda no futuro, e paciente ainda com
     consentimento e e-mail. Se não → `CANCELADO`, sem enviar.
   - Sucesso → `ENVIADO`, `enviadoEm = agora`.
   - Falha → `erro` = motivo técnico; se `tentativas < 3`, segue `PENDENTE` com
     `proximaTentativa = agora + 5 min` (depois da 1ª) ou `+ 15 min` (depois da
     2ª); na 3ª falha → `FALHOU`.

Uma falha num aviso nunca interrompe a passada: os outros seguem.

### 4.2 O carteiro

Interface `Carteiro.enviar(Mensagem)`, `Mensagem(de, nomeDoRemetente, para,
responderPara, assunto, corpoTexto)`.

- `CarteiroSmtp` (`JavaMailSender`), ativo quando `spring.mail.host` está definido.
- `CarteiroDeRegistro`, ativo quando não está: registra **só** o id do aviso e
  "enviado (sem SMTP)". Nunca registra destinatário, assunto ou corpo — o log
  não pode virar o vazamento que o assunto neutro evita.

Variáveis novas: `APP_EMAIL_REMETENTE` (endereço do produto),
`APP_FRONTEND_URL` (base do link de descadastro, ex. `http://localhost:5173`),
`SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`, `APP_AVISOS_HABILITADO`.

### 4.3 O e-mail ao paciente — texto exato

Texto puro (sem HTML). Nada clínico (spec 7.1).

- **De:** `APP_EMAIL_REMETENTE`, com nome de exibição `{nome do psicólogo}` — ou
  `{nome} · {título profissional}` quando `tituloNoEmail = true` e o título não
  está em branco.
- **Responder para:** e-mail do psicólogo (A11).
- **Assunto (fixo, nunca muda):** `Lembrete de agendamento`
- **Corpo:**

```
Olá, {primeiro nome do paciente}.

Você tem um horário marcado para {dia da semana}, {d} de {mês}, às {HH:mm}.

[Se precisar remarcar, é só responder este e-mail.]
[Chegue com alguns minutos de antecedência.]
[Nosso encontro dura 50 minutos.]

Não quer mais receber estes lembretes? Acesse:
{APP_FRONTEND_URL}/descadastro/{tokenDescadastro}
```

As frases entre colchetes são os blocos opcionais `REMARCAR`, `CHEGADA`,
`DURACAO`, nesta ordem, só os ligados. Data em pt-BR por extenso
(`quinta-feira, 8 de outubro`). "Primeiro nome" = primeira palavra do nome.

### 4.4 O resumo ao psicólogo — texto exato

- **De:** `APP_EMAIL_REMETENTE`, nome de exibição `Consultório`.
- **Para:** e-mail do psicólogo. **Assunto:** `Sua agenda de amanhã`
- **Corpo:**

```
Olá, {primeiro nome do psicólogo}.

Amanhã, {dia da semana}, {d} de {mês}, você tem {N} {sessão|sessões}:

{HH:mm} · {nome completo do paciente}
...

Para deixar de receber este resumo, desligue-o em Ajustes.
```

Sessões em ordem de horário; só `AGENDADA` não excluída.

## 5. Consentimento e descadastro — 4A

### 5.1 No cadastro do paciente (A5)

- O `<select>` "Canal de aviso" do diálogo **sai**; entra a caixa
  `Paciente autorizou receber lembretes por e-mail`. Marcada → `canalAviso = EMAIL`;
  desmarcada → `NENHUM`. O contrato da API não muda: o front segue mandando
  `canalAviso`.
- **O consentimento é derivado do canal, no servidor:** canal passa a `EMAIL`
  com `consentimentoAvisoEm` nulo → grava `agora`; canal passa a `NENHUM` → grava
  nulo. Canal já `EMAIL` que continua `EMAIL` não muda a data.
- A regra existente "canal EMAIL exige e-mail" continua valendo e sua mensagem
  chega no campo e-mail, como hoje.
- Toda mudança de consentimento grava trilha `ATUALIZACAO`
  `"ConsentimentoDoPaciente"` com o id do paciente.
- Se `descadastradoEm` existe, abaixo da caixa aparece:
  `Este paciente parou de receber lembretes pelo link do e-mail em {dd/mm/aaaa}. Marque só se ele autorizou de novo.`

### 5.2 Descadastro pelo link (A6)

- `GET /descadastro/{token}` → `200 {"ativo": true|false}`; token desconhecido
  ou de paciente excluído → `404`. **Nunca devolve nome nem dado nenhum.**
- `POST /descadastro/{token}` → `200 {"ativo": false}`; idempotente. Grava
  `canalAviso = NENHUM`, `consentimentoAvisoEm = null`,
  `descadastradoEm = agora`; trilha `ATUALIZACAO` `"DescadastroDoPaciente"` no
  nome do psicólogo dono.
- As duas rotas são públicas (`permitAll`).
- Página pública `/descadastro/:token`, fora do shell e do login:
  - título `Lembretes de agendamento`;
  - ativo: `Você recebe lembretes de agendamento por e-mail.` + botão `Parar de receber lembretes`;
  - depois do POST, ou já inativo: `Pronto. Você não vai mais receber lembretes por e-mail.`;
  - 404: `Este link não é mais válido.`;
  - outro erro: `Não foi possível carregar. Tente de novo em instantes.`

### 5.3 Na ficha

- Chip do cabeçalho: autorizado → `Aviso autorizado` (como hoje); sem
  consentimento **e** com `descadastradoEm` → chip alerta
  `Descadastrou-se em {dd/mm}`; senão → `Aviso nao autorizado` (como hoje).
- Linha da sessão: se o último aviso ao paciente **daquele horário** está
  `FALHOU` → chip alerta `Lembrete não enviado`. `SessaoDaFichaDTO` ganha
  `lembrete` (estado do aviso, nulável). `PacienteResponseDTO` ganha
  `descadastradoEm`.

## 6. Ajustes — `/ajustes`

### 6.1 API

- `GET /ajustes` → `{nome, email, valorPadraoSessao, antecedencia, blocosAviso[],
  tituloNoEmail, tituloProfissional, resumoDiario, expedienteInicio,
  expedienteFim}`.
- `PUT /ajustes` → mesmo corpo **sem** `email`; substitui tudo; audita
  `ATUALIZACAO "Ajustes"` com o id do usuário. Validação (400 no campo):

| Campo | Regra e mensagem |
|---|---|
| `nome` | obrigatório, 2–100 — `Informe seu nome.` / `O nome deve ter entre 2 e 100 caracteres.` |
| `valorPadraoSessao` | opcional; mesmas regras de `valorSessao` |
| `antecedencia` | obrigatório — `Escolha a antecedência do aviso.` |
| `tituloProfissional` | máx. 80 — `O título deve ter no máximo 80 caracteres.`; obrigatório se `tituloNoEmail` — `Informe o título profissional para incluí-lo no e-mail.` |
| `expedienteInicio/Fim` | os dois ou nenhum — `Informe o início e o fim do expediente, ou nenhum dos dois.`; `0–23` / `1–24`; fim > início — `O fim do expediente deve ser depois do início.` |

- `POST /ajustes/senha` `{senhaAtual, novaSenha}` (4B) → `204`; revoga todos os
  refresh tokens do usuário e responde com cookie de refresh novo (A14); audita
  `ATUALIZACAO "Senha"`. Erros (400 no campo): `senhaAtual` errada —
  `A senha atual não confere.`; `novaSenha` pela regra `@SenhaForte` existente;
  igual à atual — `A nova senha precisa ser diferente da atual.`

### 6.2 A tela (cópia exata, com acento)

`<h1>Ajustes</h1>`, um botão `Salvar ajustes` no fim do formulário (estado de
envio trava o botão; sucesso anuncia `Ajustes salvos.` em `role="status"`).
Seções, cada uma `<section aria-label>`:

1. **Perfil** — `Nome` (obrigatório), `E-mail` só leitura, botão `Trocar senha`
   (4B) que abre diálogo `Trocar senha` com `Senha atual`, `Nova senha`,
   `Salvar`/`Cancelar`; sucesso: `Senha trocada. As outras sessões foram encerradas.`
2. **Sessão** — `Valor padrão da sessão` (opcional). Apoio:
   `Usado quando o paciente não tem valor próprio.`
3. **Expediente** (4B) — `Início` e `Fim` (selects de hora cheia, `08:00`…;
   primeira opção `Sem expediente`). Apoio:
   `A agenda mostra só estas horas. Sem expediente, mostra das 07h às 19h.`
4. **Aviso de consulta** — `Antecedência`: `24 horas antes` · `2 horas antes` ·
   `Na véspera, às 18h`. Frases fixas mostradas como texto (`Olá, {paciente}.` /
   `Você tem um horário marcado para {data}, às {hora}.`) com o apoio
   `Estas duas frases sempre aparecem.`; caixas das frases opcionais com o texto
   exato do §4.3; caixa `Incluir meu título profissional no e-mail` + campo
   `Título profissional` (habilitado só com a caixa marcada); caixa
   `Receber às 18h o resumo da agenda de amanhã`.
5. **Como o aviso chega** — prévia ao vivo do e-mail ao paciente com dados de
   exemplo (`Ana`, sessão amanhã às 14:00): linha `De`, linha `Assunto`, corpo.
   Muda conforme as caixas, sem salvar.
6. **Trilha de acesso** (4B) — ver §7.1.

Montagem: valores do `GET /ajustes`; `Salvar ajustes` manda o `PUT`. Sob o modo
privacidade, só os nomes de paciente da trilha borram.

## 7. 4B

### 7.1 Trilha legível

`GET /auditoria` (existente, paginado, filtro `?entidade`) passa a devolver, por
linha, `pacienteId`, `pacienteNome` e `pacienteExcluido` resolvidos no servidor,
em lote por página, para as entidades `Paciente`, `FichaDoPaciente`, `Consulta`,
`Anotacao`, `Pendencia`, `ConsentimentoDoPaciente`, `DescadastroDoPaciente`
(nulos para as demais). A leitura continua não auditada e isolada por usuário.

Na tela: lista `aria-label="Trilha de acesso"`, 20 por página com `Mais antigos` /
`Mais recentes`; filtro `Tipo`: `Tudo` · `Fichas` (`FichaDoPaciente`) ·
`Pacientes` · `Sessões` (`Consulta`) · `Anotações` · `Ajustes`. Linha:
`{dd/mm HH:mm} · {frase}{ · nome do paciente}`; nome com link para a ficha
quando o paciente não foi excluído. Frases por `(ação, entidade)`:

| Entidade | CRIACAO | VISUALIZACAO | ATUALIZACAO | EXCLUSAO |
|---|---|---|---|---|
| `FichaDoPaciente` | — | Abriu a ficha | — | — |
| `Paciente` | Cadastrou paciente | Viu o cadastro | Editou paciente | Excluiu paciente |
| `Consulta` | Agendou sessão | Viu sessão | Alterou sessão | Excluiu sessão |
| `Anotacao` | Registrou anotação | Leu anotações | — | Excluiu anotação |
| `Pendencia` | Criou pendência | — | Concluiu pendência | Excluiu pendência |
| `ConsentimentoDoPaciente` | — | — | Alterou a autorização de aviso | — |
| `DescadastroDoPaciente` | — | — | Paciente parou de receber lembretes | — |
| `Ajustes` | — | — | Alterou os ajustes | — |
| `Senha` | — | — | Trocou a senha | — |

Combinação fora da tabela: `{ação em minúsculas} · {entidade}`. Vazio:
`Nenhum registro ainda.`

### 7.2 Expediente na Agenda (A13)

A grade mostra as horas `[expedienteInicio, expedienteFim)` — com
`expedienteFim = 18`, a última linha é 17h. Sem expediente, `07h…19h` (hoje).
Se a semana exibida tem sessão (não cancelada) antes do início ou a partir do
fim, a grade estende até incluí-la, só naquela semana. Semântica da grade
(`role="grid"`, `th scope`) intocada.

## 8. Invariantes

1. Nenhum conteúdo clínico em e-mail, log ou erro de envio.
2. Assunto do aviso ao paciente nunca muda.
3. Envio duplo impossível com duas instâncias (índice único + reivindicação).
4. Isolamento por psicólogo em toda leitura e escrita nova; o descadastro não
   revela nada sobre o paciente.
5. Nenhum comentário novo em código; testes por rótulo escrito; as suítes
   continuam verdes; os três e2e continuam passando.

## 9. Fora de escopo

WhatsApp; domínio próprio e SPF/DKIM/DMARC (Fatia 5); retenção e purga;
troca de e-mail do psicólogo; e-mail em HTML; aviso por sessão ao psicólogo;
concorrência das escritas de dinheiro (dívida da Fatia 3, vai para a 5).

## 10. Em aberto para o dono

Nada.

**Para avaliação técnica:** o produto passa a mandar o lembrete ao paciente e o
resumo ao psicólogo, com texto fixo que não revela terapia nem conteúdo clínico,
sem enviar duas vezes e com nova tentativa em falha; o paciente sai da lista com
dois cliques; o psicólogo configura tudo em Ajustes, onde também vê quem acessou
o quê.
