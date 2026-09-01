# psychology-api

Sistema de gestão de consultório para psicólogos autônomos. Monorepo com as duas
aplicações que compõem o produto: uma API REST em Java 21 + Spring Boot e um
frontend React + TypeScript.

O domínio lida com dado sensível de saúde — anotação de sessão, histórico de
paciente. Isso definiu as decisões de projeto de cada lado: isolamento entre
profissionais verificado por teste, exclusão reversível, registro de acesso, e
access token que nunca é persistido no navegador.

> **Status:** API no ar, frontend com as telas de pé — painel, agenda, pacientes,
> cadastro, ficha e trilha — rodando ponta a ponta contra a API real, com o e2e
> de fumaça passando contra ela. A configuração de host do frontend está no
> repositório; falta criar o projeto na plataforma e apontar as variáveis
> ([passo a passo](frontend/README.md#hospedagem)).

## O que o produto faz

O usuário é um psicólogo que atende sozinho. O dia dele tem três momentos, e o
produto cobre os três:

**Antes do expediente**, ele abre o painel para ver quantas sessões tem hoje e
entra na agenda. **Antes de cada sessão**, abre a ficha do paciente para lembrar
o que ficou pendente — a última anotação, os lembretes em aberto, quando foi a
sessão anterior. **Depois da sessão**, registra a anotação e agenda o retorno.

| Tela | Rota | O que resolve |
|---|---|---|
| Entrar | `/login` | entrar e criar conta. Uma conta por profissional |
| Painel | `/` | consultas de hoje, pacientes cadastrados e os atalhos |
| Agenda | `/agenda?de&ate` | agendar, remarcar, mudar status, excluir. Sem parâmetros, mostra hoje |
| Série semanal | `/agenda` → botão | mesma hora toda semana, com as datas visíveis antes de criar |
| Pacientes | `/pacientes?nome` | lista com busca, cadastro, edição e exclusão |
| Ficha | `/pacientes/:id` | visão geral: consultas, última anotação, lembretes pendentes, histórico |
| Anotações | `/pacientes/:id/anotacoes` | registrar e ler as sessões |
| Lembretes | `/pacientes/:id/lembretes` | pendências do paciente |
| Consultas | `/pacientes/:id/consultas` | histórico do paciente, só leitura |
| Trilha | `/auditoria` | tudo que foi criado, alterado e excluído na conta |

O que **não** existe, de propósito: financeiro, relatórios, convênios,
documentos, multiempresa, chat, assinaturas, roles, login social. A lista
completa está em [Fora de escopo](#fora-de-escopo).

## Como as duas aplicações se encaixam

O frontend não tem dados próprios: tudo que ele mostra vem da API, e o navegador
guarda o mínimo possível.

```
desenvolvimento                         produção
───────────────                         ────────
navegador                               navegador
   │  tudo em localhost:5173               │  origem do host estático
   ▼                                       ├── HTML/JS ──► host (Vercel/Netlify/Render)
Vite (5173)                                │                regra /* -> /index.html
   │  proxy espelha /auth, /pacientes,      │
   │  /consultas, /anotacoes, /lembretes,   └── XHR ──────► API no Render
   │  /auditoria                                             CORS + cookie SameSite=None
   ▼
API (8080) ──► PostgreSQL
```

Em desenvolvimento o proxy torna tudo same-origin, o que tira CORS e `SameSite`
do caminho. Ele **espelha os caminhos sem prefixo `/api`**: o cookie de refresh é
marcado `Path=/auth`, e sob um prefixo o navegador veria `/api/auth/refresh` e
não o enviaria — a renovação quebraria só em desenvolvimento, o pior lugar para
descobrir isso.

Em produção não há proxy, e as duas origens são distintas. É por isso que o
backend precisa de `APP_CORS_ORIGINS` com a origem exata do frontend, e o cookie
precisa de `Secure` + `SameSite=None`.

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
cria tabela é o Flyway. Hoje é uma migration só, com o schema inteiro — as oito
originais foram fundidas antes do primeiro deploy. Depois do deploy isso deixa de
ser possível, e mudança vira `V2`, `V3`, …

## Modelo de dados

Sete tabelas, todas penduradas no profissional dono da conta:

| Tabela | Guarda | Observações |
|---|---|---|
| `usuario` | o psicólogo | e-mail único; senha com BCrypt |
| `paciente` | cadastro do paciente | soft delete; `observacoes_paciente` existe no banco mas ainda não é exposta pela API |
| `consulta` | sessão agendada | status `AGENDADA`/`REALIZADA`/`CANCELADA`/`FALTOU`, `id_serie` opcional, soft delete |
| `anotacao` | o que aconteceu na sessão | sem update; soft delete |
| `lembrete` | pendência do paciente | `concluido` é mão única; soft delete |
| `log_auditoria` | a trilha | só insere |
| `refresh_token` | sessões vivas | guarda o hash, nunca o token; `substituido_por` encadeia a rotação |

## Superfície da API

Tudo exige `Authorization: Bearer <token>`, menos `/auth/**`.

| Recurso | Endpoints |
|---|---|
| Sessão | `POST /auth/registrar` · `POST /auth/login` · `POST /auth/refresh` · `POST /auth/logout` |
| Pacientes | `POST /pacientes` · `GET /pacientes?nome=` · `GET /pacientes/{id}` · `PUT /pacientes/{id}` · `DELETE /pacientes/{id}` |
| Consultas | `POST /consultas` · `GET /consultas?de=&ate=` · `GET /consultas/{id}` · `PUT /consultas/{id}` · `DELETE /consultas/{id}` |
| Séries | `POST /consultas/recorrentes` · `DELETE /consultas/series/{serieId}` |
| Anotações | `POST` e `GET /pacientes/{id}/anotacoes` · `DELETE /anotacoes/{id}` |
| Lembretes | `POST` e `GET /pacientes/{id}/lembretes` · `PATCH /lembretes/{id}/concluir` · `DELETE /lembretes/{id}` |
| Trilha | `GET /auditoria?entidade=&entidadeId=&page=` |

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

Abre em <http://localhost:5173>. Crie uma conta pela própria tela — o primeiro
acesso é `Criar conta`, não existe usuário semeado, e cada conta enxerga só os
próprios pacientes.

## Testes

```bash
cd backend  && sh ./mvnw test    # 169 — unitários, integração (Testcontainers) e HTTP
cd frontend && npm test          # 74  — unidade e componente, com MSW
cd frontend && npm run e2e       # 2   — fumaça ponta a ponta, exige a API no ar
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

**Backend** — Render, via Docker multi-estágio. O contexto de build é o diretório
`backend/`, não a raiz do repositório:

```bash
docker build backend/
```

Variáveis (`SPRING_DATASOURCE_*`, `JWT_SECRET`, `APP_CORS_ORIGINS`,
`APP_REFRESH_*`) ficam no painel da plataforma, nunca no repositório.

**Frontend** — o build é estático e a configuração de host já está versionada
(`frontend/vercel.json`, `frontend/public/_redirects`). Falta criar o projeto na
plataforma, apontar `VITE_API_URL` para a API e ajustar as três variáveis do
backend. O passo a passo, com a checagem que prova que ficou certo, está em
[`frontend/README.md#hospedagem`](frontend/README.md#hospedagem).

O detalhe que derruba um deploy aparentemente correto: sem a regra
`/* → /index.html` no host, recarregar em `/pacientes` responde 404; e sem
`SameSite=None` + `Secure`, o login funciona mas o F5 desloga.

## Quando não sobe

| Sintoma | Causa provável |
|---|---|
| A API não sobe, `PlaceholderResolutionException` | falta `JWT_SECRET` no ambiente |
| A API não sobe, `Validate failed: Migrations have failed validation` | banco local com o histórico antigo. `dropdb` + `createdb` |
| A API não sobe, porta em uso | outra aplicação na 8080. Use `PORT=` e `API_URL=` |
| `npm install` falha com `Cannot find module 'node:path'` | Node antigo no PATH. O projeto pede Node 20+ |
| A tela de login diz "não foi possível conectar ao servidor" | a API não está no ar. O front distingue isso de credencial inválida de propósito |
| Login funciona mas o F5 desloga | o cookie de refresh não está voltando |

A tabela completa do lado do web, com mais casos, está em
[`frontend/README.md`](frontend/README.md#quando-não-sobe).

## Layout

```
psychology-api/
├── backend/     API REST — Java 21, Spring Boot, PostgreSQL, Flyway
├── frontend/    Interface do psicólogo — React 19, TypeScript, Vite, Tailwind
├── docs/        Specs e planos de implementação das duas pontas
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

## Fora de escopo

MVP deliberadamente enxuto. Não faz parte do escopo: financeiro, relatórios,
convênios, documentos, multiempresa, chat, assinaturas, dashboard complexo,
roles/múltiplos psicólogos por clínica, OAuth2 e login social.

Do lado do frontend, também ficam fora: modo escuro, responsivo de celular além
de não quebrar (o alvo é desktop de consultório), internacionalização, PWA e uso
offline, exportar prontuário e notificações.
