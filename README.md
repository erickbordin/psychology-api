# psychology-api

Sistema de gestão de consultório para psicólogos autônomos. Monorepo com as duas
aplicações que compõem o produto: uma API REST em Java 21 + Spring Boot e um
frontend React + TypeScript.

O domínio lida com dado sensível de saúde — anotação de sessão, histórico de
paciente. Isso definiu as decisões de projeto de cada lado: isolamento entre
profissionais verificado por teste, exclusão reversível, registro de acesso, e
access token que nunca é persistido no navegador.

> **Status:** as duas aplicações rodam local, ponta a ponta, com os seis e2e (fumaça,
> sessão, dinheiro, aviso, ajustes e cadastro) passando contra a API real — painel, agenda, pacientes,
> cadastro por convite, ficha, trilha, Financeiro, da sessão realizada à baixa, e o aviso de consulta por e-mail com os Ajustes.
> **Nada foi para produção ainda:** o código, o Compose, o backup e os roteiros da Fatia 5A
> estão prontos no repositório, **não executados**. "No ar" só depois do deploy real e da
> restauração real do backup; o que falta, com dono e estado, está em
> [`docs/operacao/portao.md`](docs/operacao/portao.md) e o passo a passo em
> [`docs/operacao/implantacao.md`](docs/operacao/implantacao.md).

## O que o produto faz

O usuário é um psicólogo que atende sozinho. O dia dele tem três momentos, e o
produto cobre os três:

**Antes do expediente**, ele abre o painel para ver quantas sessões tem hoje e
entra na agenda. **Antes de cada sessão**, abre a ficha do paciente para lembrar
o que ficou pendente — a última anotação, os lembretes em aberto, quando foi a
sessão anterior. **Depois da sessão**, registra a anotação e agenda o retorno.

| Tela | Rota | O que resolve |
|---|---|---|
| Entrar | `/entrar` | entrar. Uma conta por profissional. Sessão expirada volta aqui com aviso e devolve à página em que estava |
| Cadastro | `/cadastro` | criar a conta com um código de convite de uso único e o aceite dos termos |
| Termos e privacidade | `/termos`, `/privacidade` | páginas públicas com o texto vindo de arquivo versionado |
| Painel | `/` | consultas de hoje, pacientes cadastrados e os atalhos |
| Agenda | `/agenda?de&ate` | agendar, remarcar, mudar status, excluir. Sem parâmetros, mostra hoje |
| Série semanal | `/agenda` → botão | mesma hora toda semana, com as datas visíveis antes de criar |
| Pacientes | `/pacientes?nome` | lista com busca, cadastro, edição e exclusão |
| Ficha | `/pacientes/:id` | visão geral: consultas, última anotação, lembretes pendentes, histórico |
| Anotações | `/pacientes/:id/anotacoes` | registrar e ler as sessões |
| Lembretes | `/pacientes/:id/lembretes` | pendências do paciente |
| Consultas | `/pacientes/:id/consultas` | histórico do paciente, só leitura |
| Trilha | `/auditoria` | tudo que foi criado, alterado e excluído na conta. Nos Ajustes, a Trilha de acesso mostra os mesmos registros em frases, com o nome do paciente |
| Financeiro | `/financeiro` | o mês: presença, faturado/recebido/a receber, quem deve, baixa e valor da sessão |
| Ajustes | `/ajustes` | perfil e Trocar senha, valor padrão da sessão, Expediente (a agenda mostra só essas horas), antecedência e frases do aviso, título profissional, resumo diário, prévia do e-mail e Trilha de acesso |
| Descadastro | `/descadastro/:token` | página pública aberta pelo link do e-mail: o paciente para de receber lembretes |

O que **não** existe, de propósito: gateway de pagamento, recibo, NFS-e,
relatórios, convênios, documentos, multiempresa, chat, assinaturas, roles, login social. A lista
completa está em [Fora de escopo](#fora-de-escopo).

## Como as duas aplicações se encaixam

O frontend não tem dados próprios: tudo que ele mostra vem da API, e o navegador
guarda o mínimo possível.

```
desenvolvimento                         produção (VM única, Docker Compose)
───────────────                         ───────────────────────────────────
navegador                               navegador
   │  tudo em localhost:5173               │  https://{dominio}, uma origem só
   ▼                                       ▼
Vite (5173)                             Caddy (80/443, HTTPS automático)
   │  proxy espelha /auth, /pacientes,      ├── /      front estático, SPA
   │  /consultas, /anotacoes, /pendencias,  └── /api/* tira o prefixo ──► API (8080)
   │  /auditoria, /termos, /saude ...                                       │
   ▼                                                                        ▼
API (8080) ──► PostgreSQL                                              PostgreSQL
                                        backup: dump diário cifrado ──► bucket
```

Em desenvolvimento o proxy torna tudo same-origin, o que tira CORS e `SameSite`
do caminho. Ele **espelha os caminhos sem prefixo `/api`**: o cookie de refresh é
marcado `Path=/auth` (o padrão de `APP_REFRESH_COOKIE_PATH`), e sob um prefixo o
navegador veria `/api/auth/refresh` e não o enviaria.

Em produção a origem também é uma só, mas o prefixo existe: o Caddy serve o front em
`/` e repassa `/api/*` à API **tirando** o prefixo. Por isso lá o cookie sai com
`Path=/api/auth` (`APP_REFRESH_COOKIE_PATH`) e `SameSite=Strict`, e o front monta as
chamadas com `VITE_API_URL=/api`. A API fica atrás do Caddy: o IP do cliente que o
limite de tentativas usa vem do cabeçalho que só o Caddy, na rede do Compose, pode mandar.

### O caminho de uma sessão

1. Login devolve um **access token** (JWT, 15 minutos) no corpo e um
   **refresh token** num cookie `HttpOnly`.
2. O access token vive **só em memória** no JavaScript. Fechou a aba, sumiu.
3. No boot, antes de renderizar rota nenhuma, o front chama `POST /auth/refresh`.
   O cookie viaja sozinho e a sessão volta — é assim que o F5 não desloga sem
   que exista token guardado no navegador.
4. Chamadas que tomam `401` compartilham **uma** promise de renovação. A API
   rotaciona o refresh a cada uso e revoga a cadeia inteira ao ver um refresh
   reapresentado; renovações concorrentes seriam lidas como token roubado.
5. Se a renovação for recusada, o front limpa a sessão e o cache, vai para `/entrar`
   com `Sua sessão expirou. Entre novamente.` e, depois de entrar, volta à rota em
   que estava. O que estava digitado num formulário aberto se perde. Falha de rede
   ou `5xx` na renovação não derruba a sessão: a chamada falha e a próxima tenta de novo.
   `/auth/*` e `/descadastro/*` nunca disparam renovação.

## Decisões que valem entender

Cada uma tem um motivo ligado ao domínio. Nenhuma é default de gerador.

**Registro de outro profissional responde `404`, não `403`.** Todo service
resolve o usuário logado pelo token — nunca por um `usuarioId` vindo do corpo — e
busca por `findByIdAndUsuario(...)`. Um `403` confirmaria que o registro existe;
num sistema de prontuário, isso já é vazamento.

**Anotação não se edita.** Registrou, está registrado. Apagar tira da ficha, mas
o registro da exclusão fica na trilha. Prontuário que se reescreve não serve como
prontuário.

**Exclusão é reversível.** Paciente, consulta, anotação e lembrete têm coluna
`deleted_at_*`: somem das consultas da aplicação e continuam no banco. As FKs
não têm `ON DELETE CASCADE` — apagar um paciente de verdade não pode levar junto,
em silêncio, o histórico dele.

**A trilha só cresce.** `log_auditoria` registra criação, visualização, alteração
e exclusão, e não tem coluna de exclusão nem endpoint de escrita. Nem o dono da
conta edita.

**Conflito de horário é janela, não igualdade.** Uma consulta ocupa 50 minutos:
agendar dentro de ±50 minutos de outra do mesmo profissional responde `409`.
Consulta cancelada não conta. Na série semanal o `409` é atômico — ou nascem
todas as ocorrências, ou nenhuma, e a tela diz isso com todas as letras.

**Série é vínculo, não entidade.** Cada ocorrência é uma consulta independente
com o mesmo `id_serie`. Remarcar uma não mexe nas outras; cancelar a série remove
as futuras e deixa as que já aconteceram. O horizonte é um XOR: número de sessões
(2 a 52) **ou** data limite, nunca os dois.

**Schema versionado, Hibernate sem DDL.** Não existe `ddl-auto` no projeto: quem
cria tabela é o Flyway. São três migrations: a `V1`, com o schema inteiro (as oito
originais foram fundidas antes do primeiro deploy), a `V2`, do aviso de consulta, e a `V3`,
do aceite dos termos e dos convites usados.
Migration aplicada é imutável, e mudança nova vira `V4`, …

## Modelo de dados

Tabelas de dado do profissional, todas penduradas na conta dele (a `convite_usado` só liga o convite à conta criada):

| Tabela | Guarda | Observações |
|---|---|---|
| `usuario` | o psicólogo | e-mail único; senha com BCrypt; versão e data do aceite dos termos |
| `convite_usado` | o convite que já criou uma conta | guarda o SHA-256 do código, nunca o código; `UNIQUE` no hash decide a corrida |
| `paciente` | cadastro do paciente | soft delete; `observacoes_paciente` existe no banco mas ainda não é exposta pela API |
| `consulta` | sessão agendada | status `AGENDADA`/`REALIZADA`/`CANCELADA`/`FALTOU`, `id_serie` opcional, soft delete |
| `anotacao` | o que aconteceu na sessão | sem update; soft delete |
| `lembrete` | pendência do paciente | `concluido` é mão única; soft delete |
| `log_auditoria` | a trilha | só insere |
| `refresh_token` | sessões vivas | guarda o hash, nunca o token; `substituido_por` encadeia a rotação |
| `aviso_de_consulta` | cada aviso a enviar ou já enviado | `UNIQUE (id_consulta, destinatario, horário)` é a trava contra envio duplo; guarda tentativas e próxima tentativa |
| `resumo_diario` | o resumo da agenda de amanhã, por psicólogo e dia | `UNIQUE (id_usuario, data)` é a trava; mesma lógica de tentativas |

## Superfície da API

Tudo exige `Authorization: Bearer <token>`, menos `/auth/**`, `/descadastro/**`, `/termos/atual` e `/saude`.

| Recurso | Endpoints |
|---|---|
| Sessão | `POST /auth/registrar` (exige `codigoConvite`, `aceiteTermos` e `versaoTermos`) · `POST /auth/login` · `POST /auth/refresh` · `POST /auth/logout`. Login, cadastro e refresh têm limite de 10 tentativas por minuto por IP e por rota (`429`) |
| Termos (público) | `GET /termos/atual` → `{versao, termos, privacidade}`; `404` enquanto os textos não estiverem instalados |
| Saúde (público) | `GET /saude` → `200` com `{"status":"ok","backup":"ok"}`; `503` com `"backup":"atrasado"` se o último dump tem mais de 36 h ou o último teste de restauração falhou. Sem dado nenhum. Em produção, `/api/saude` |
| Pacientes | `POST /pacientes` · `GET /pacientes?nome=` · `GET /pacientes/{id}` · `PUT /pacientes/{id}` · `DELETE /pacientes/{id}` |
| Consultas | `POST /consultas` · `GET /consultas?de=&ate=` · `GET /consultas/{id}` · `PUT /consultas/{id}` · `DELETE /consultas/{id}` |
| Séries | `POST /consultas/recorrentes` · `DELETE /consultas/series/{serieId}` |
| Anotações | `POST` e `GET /pacientes/{id}/anotacoes` · `DELETE /anotacoes/{id}` |
| Lembretes | `POST` e `GET /pacientes/{id}/lembretes` · `PATCH /lembretes/{id}/concluir` · `DELETE /lembretes/{id}` |
| Trilha | `GET /auditoria?entidade=&entidadeId=&page=` — cada linha traz também `pacienteId`, `pacienteNome` e `pacienteExcluido` |
| Ajustes | `GET /ajustes` · `PUT /ajustes` · `POST /ajustes/senha` (trocar a senha; as outras sessões caem na próxima renovação, o access token vale até expirar) |
| Descadastro (público) | `GET /descadastro/{token}` · `POST /descadastro/{token}` |

**Aviso de consulta.** Um job dentro da própria API (`@Scheduled`, uma passada por
minuto) manda por e-mail o lembrete da sessão ao paciente que autorizou, com a
antecedência e as frases escolhidas nos Ajustes, e às 18h o resumo da agenda de
amanhã ao psicólogo. A trava contra envio duplo é uma `UNIQUE` no banco, não um
`if` no código; falha de envio entra em nova tentativa e grava só o nome da
exceção. Cada e-mail traz um link de descadastro que não exige login. Sem
`SPRING_MAIL_HOST` o job não roda e nada é registrado como enviado; com o host
definido, `APP_EMAIL_REMETENTE` e `APP_FRONTEND_URL` passam a ser obrigatórias, ou
a API recusa subir. `APP_AVISOS_HABILITADO=false` desliga o job. Variáveis em
[backend/README.md](backend/README.md#variáveis-de-ambiente).

Erro de validação vem sempre no mesmo envelope, com o campo culpado nomeado:

```json
{ "status": 400, "mensagem": "Erro de validação",
  "erros": [{ "campo": "dataNascimento", "erro": "A data de nascimento é obrigatória." }] }
```

Listagens paginadas usam o `PagedModel` do Spring (`content` + `page`). O
springdoc expõe a documentação interativa em `/swagger-ui.html`, atrás da mesma
autenticação do resto — sem token, `401`.

## Subindo o conjunto

O frontend não serve para nada sozinho — ele não tem dados. A ordem é
banco → API → web.

**Requisitos:** Java 21, Node 20+, PostgreSQL local. Docker só para os testes do
backend (Testcontainers), não para subir a aplicação.

### 1. Banco

```bash
createdb psychology-api
```

As migrations do Flyway rodam sozinhas no boot da API; não há passo manual de
schema. Se você já tinha esse banco de antes do squash das migrations, recrie-o
(`dropdb psychology-api && createdb psychology-api`) — o Flyway recusa um
histórico que não bate com os arquivos.

### 2. API

```bash
cd backend
./debug.sh
```

O script carrega o arquivo de ambiente local (não versionado), aponta o datasource
para o Postgres da máquina e sobe o Spring na porta 8080. Sem `JWT_SECRET` a
aplicação **não sobe**, de propósito: falhar no boot é melhor que subir com
segredo default.

Se a 8080 estiver ocupada, suba em outra porta com `PORT=8090 ./debug.sh` e
aponte o proxy do front com `API_URL=http://localhost:8090 npm run dev`.

### 3. Web

```bash
cd frontend
npm install
npm run dev
```

Abre em <http://localhost:5173>. Crie uma conta em `/cadastro` — não existe usuário
semeado, e cada conta enxerga só os próprios pacientes. O cadastro exige um código de
convite e os textos dos termos; sem eles a API responde `403` e `503`. Em desenvolvimento,
no arquivo de ambiente local, defina `APP_CADASTRO_CONVITES=convite-local` e
`APP_TERMOS_DIRETORIO=file:/caminho/de/uma/pasta/` com um `termos.md` e um `privacidade.md`
**de desenvolvimento**, cada um com a primeira linha `versao: dev` (texto de teste, nunca o
texto jurídico). O formato está em [`docs/operacao/implantacao.md`](docs/operacao/implantacao.md#termos-de-uso-necessários-para-o-cadastro).

## Testes

```bash
cd backend  && sh ./mvnw test    # 570 — unitários, integração (Testcontainers) e HTTP
cd frontend && npm test          # 338 — unidade e componente, com MSW
cd frontend && npm run e2e       # 6   — fumaça, sessão, dinheiro, aviso, ajustes e cadastro ponta a ponta, exige a API no ar
bash infra/teste/testar-backup.sh  # backup e restauração de teste contra um S3 local, exige Docker
```

Cada camada pega um tipo de defeito:

- **Unitários de service** (Mockito, sem banco) — o isolamento entre
  profissionais, a janela de conflito, a exigência de `save(...)` explícito.
- **Integração de repository** (`@DataJpaTest` + Postgres real) — o mapeamento
  contra o schema de verdade, incluindo as migrations aplicadas do zero.
- **HTTP** (`@SpringBootTest` + MockMvc) — filtro JWT, códigos de status,
  serialização do `PagedModel`.
- **Unidade e componente do front** (Vitest + MSW) — os handlers respondem o
  contrato **real** da API: envelope de erro, `PagedModel`, `401`.
- **E2E** (Playwright contra a API de verdade) — é o único capaz de pegar
  contrato desalinhado entre as duas aplicações, porque o resto de cada lado roda
  contra mock ou container próprio.

O E2E já provou o valor três vezes: pegou a tela morta em que o login caía depois
de autenticar; pegou que o front lia `erros[].mensagem` onde a API manda
`erros[].erro` — com os mocks errados junto, então a suíte ficava verde enquanto
um `400` real não mostrava nada na tela; e, no redesign, pegou dois rótulos que
passaram a casar com dois elementos cada.

## Deploy

Uma VM ARM do Oracle Cloud (Always Free, região São Paulo; **confirmar no site** os
limites), com Docker Compose. Um `infra/compose.yml` sobe quatro serviços numa rede
interna, e só o Caddy publica portas:

| Serviço | O que faz |
|---|---|
| `caddy` | HTTPS automático (Let's Encrypt) para `{DOMINIO}`; serve o front em `/`; repassa `/api/*` à API tirando o prefixo; cabeçalhos de segurança |
| `api` | o `backend/Dockerfile`, construído na própria VM |
| `postgres` | Postgres 16, volume persistente, sem porta publicada |
| `backup` | dump diário às 03:00, cifrado com a chave pública do dono e enviado ao bucket; retenção de 30 diários e 12 mensais; teste de restauração aos domingos, 04:00 |

```bash
bash infra/deploy.sh
```

Na VM: `bash infra/deploy.sh` faz `git pull --ff-only`, baixa as imagens-base, faz um
backup antes de subir a versão nova (se a pilha já estiver no ar) e espera
`https://{dominio}/api/saude`. As migrations rodam pelo Flyway no boot da API. Não há CI.

As variáveis ficam num arquivo `infra/.env` **só na VM**, nunca versionado: `DOMINIO`,
`POSTGRES_*`, `JWT_SECRET`, `APP_CADASTRO_CONVITES`,
`APP_FRONTEND_URL`, `APP_CORS_ORIGINS`, `APP_AVISOS_HABILITADO`,
`BACKUP_*`. O cookie de refresh (`Path=/api/auth`, `Strict`, `Secure`) e o
`SPRINGDOC_ENABLED=false` ficam fixos no `infra/compose.yml`. A lista completa, com as regras que quebram o deploy, está em
[`docs/operacao/implantacao.md`](docs/operacao/implantacao.md).

Roteiros em `docs/operacao/`:

| Arquivo | Para quê |
|---|---|
| [`implantacao.md`](docs/operacao/implantacao.md) | do zero até `https://{dominio}`, com a checagem de cada passo |
| [`restauracao.md`](docs/operacao/restauracao.md) | restaurar um dump real fora da VM; critério de pronto |
| [`incidente.md`](docs/operacao/incidente.md) | parte técnica do plano de incidente; comunicação à ANPD **[verificar com advogado]** |
| [`convites.md`](docs/operacao/convites.md) | gerar um código de convite e liberar |
| [`portao.md`](docs/operacao/portao.md) | o que não é código, com dono e estado |

Alertas de backup: `GET /api/saude` devolve `503` quando o backup está atrasado, e um
monitor externo gratuito avisa o dono por e-mail.

Subir a pilha inteira na máquina local, com `DOMINIO=localhost` e certificado interno do
Caddy: `bash infra/teste/subir-local.sh` (e `--derrubar` para desfazer).

O detalhe que derruba um deploy aparentemente correto: com `APP_REFRESH_COOKIE_PATH`
diferente de `/api/auth`, o login funciona mas o F5 desloga. No Compose esse valor é fixo.

## Quando não sobe

| Sintoma | Causa provável |
|---|---|
| A API não sobe, `PlaceholderResolutionException` | falta `JWT_SECRET` no ambiente |
| A API não sobe, `Validate failed: Migrations have failed validation` | banco local com o histórico antigo. `dropdb` + `createdb` |
| A API não sobe, porta em uso | outra aplicação na 8080. Use `PORT=` e `API_URL=` |
| `npm install` falha com `Cannot find module 'node:path'` | Node antigo no PATH. O projeto pede Node 20+ |
| A tela de login diz "não foi possível conectar ao servidor" | a API não está no ar. O front distingue isso de credencial inválida de propósito |
| Login funciona mas o F5 desloga | o cookie de refresh não está voltando; em produção, confira `Path=/api/auth` |
| Cadastro responde `503` | os textos dos termos não estão instalados |
| Cadastro responde `403` | código de convite inválido ou já usado |
| `429 Muitas tentativas` | passou de 10 tentativas por minuto no mesmo IP e na mesma rota |

A tabela completa do lado do web, com mais casos, está em
[`frontend/README.md`](frontend/README.md#quando-não-sobe).

## Layout

```
psychology-api/
├── backend/     API REST — Java 21, Spring Boot, PostgreSQL, Flyway
├── frontend/    Interface do psicólogo — React 19, TypeScript, Vite, Tailwind
├── infra/       Compose, Caddy, backup e deploy da VM
├── docs/        Specs, planos e roteiros de operação (docs/operacao)
└── ai/          Contexto do projeto para assistentes
```

Cada aplicação tem o próprio README, com as decisões de arquitetura, a estrutura
interna e a tabela de "quando não sobe":

- [`backend/README.md`](backend/README.md) — isolamento, auditoria, soft delete,
  camadas de teste, deploy
- [`frontend/README.md`](frontend/README.md) — sistema visual, sessão em memória,
  proxy, hospedagem

## Documentação

Specs e planos das duas pontas ficam juntos em `docs/superpowers/`. As specs
valem mais que os planos: elas registram também as **alternativas rejeitadas**, e
é onde se descobre por que algo não foi feito de outro jeito.

| Documento | Sobre |
|---|---|
| `specs/2026-08-25-consulta-recorrente-design.md` | série semanal de consultas |
| `specs/2026-08-26-sessao-com-refresh-design.md` | sessão com refresh token, CORS e histórico do paciente |
| `specs/2026-08-26-frontend-design.md` | design do frontend e alternativas rejeitadas |
| `plans/2026-08-25-consulta-recorrente.md` | plano da consulta recorrente |
| `plans/2026-08-26-sessao-com-refresh.md` | plano da sessão com refresh |
| `plans/2026-08-26-esqueleto-vertical.md` | plano do esqueleto vertical do frontend |
| `specs/2026-10-04-fatia-5a-no-ar-design.md` | a Fatia 5A: VM, Compose, backup, sessão renovada, termos e convite |

## Débitos conhecidos

Os do backend (limite de tentativas em memória e o job de avisos) estão em
[`backend/README.md`](backend/README.md).

- A Inter vem do Google Fonts: o `index.html` carrega a fonte de um terceiro, e a CSP do
  Caddy libera `fonts.googleapis.com` e `fonts.gstatic.com` só por isso. Servir a fonte
  localmente fecha o débito.
- Reaceite de termos: quando a versão dos termos mudar, quem já tem conta não é chamado a
  aceitar de novo. Só importa na segunda versão.
- O `409` de termos é detectado pela mensagem do envelope, não por um código próprio.
- O link de termos no cadastro abre na mesma aba e perde o que foi digitado no formulário.
- Duas abas renovando a sessão juntas podem revogar a cadeia de refresh: a API lê o
  refresh reapresentado como token roubado, e a segunda aba cai no login.
- E-mail repetido no cadastro revela que a conta existe (`409`): mitigado por exigir um
  convite válido antes, não eliminado.
- Troca de senha × `/auth/refresh` concorrente de outro dispositivo: janela de milissegundos
  em que a sessão concorrente sobrevive à revogação em massa.
- A frase "Leu anotações" da trilha nunca aparece, porque a leitura de anotações é auditada
  como `VISUALIZACAO` de `Paciente` e a trilha mostra "Viu o cadastro".
- A tela de Ajustes tenta o `GET` uma vez a mais, sem backoff; a falha passageira já mostra o
  erro.
- A CSP bloqueia o `<style>` injetado pelo Radix (a página pode rolar atrás de um diálogo
  aberto) — não verificado no stack https.
- O Javadoc de `CookieRefreshToken` ainda cita `Path=/auth` (comentário existente, mantido
  por regra).
- O deploy não tem rollback automático.

## Fora de escopo

MVP deliberadamente enxuto. Não faz parte do escopo: gateway de pagamento, recibo,
NFS-e, relatórios, convênios, documentos, multiempresa, chat, assinaturas, dashboard complexo,
roles/múltiplos psicólogos por clínica, OAuth2 e login social.

Do lado do frontend, também ficam fora: modo escuro, responsivo de celular além
de não quebrar (o alvo é desktop de consultório), internacionalização, PWA e uso
offline, exportar prontuário e notificações.
