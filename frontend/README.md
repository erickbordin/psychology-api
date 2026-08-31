# psychology-web — frontend

Frontend do consultório — a interface do psicólogo autônomo para a
[API](../backend/README.md), no mesmo repositório: agenda do dia,
cadastro de pacientes, anotação de sessão, série semanal e trilha de auditoria.

> **Status:** as cinco telas da spec estão de pé e rodam ponta a ponta contra a
> API real. Falta hospedar — hoje o conjunto só roda local.

## Telas

| Rota | O que faz |
|---|---|
| `/login` | entrar e criar conta |
| `/agenda?de&ate` | agenda do intervalo; sem parâmetros, hoje. Agendar, remarcar, mudar status, excluir |
| `/agenda` → *Série semanal* | série com preview das datas antes de criar, e cancelamento da série inteira |
| `/pacientes?nome` | lista com busca, cadastro, edição e exclusão |
| `/pacientes/:id/anotacoes` | registrar e ler as sessões |
| `/pacientes/:id/lembretes` | pendências do paciente, com conclusão otimista |
| `/pacientes/:id/consultas` | histórico de consultas, só leitura |
| `/auditoria?entidade&entidadeId&page` | tudo que foi criado, alterado e excluído |

Filtro e paginação vivem na URL, nunca em `useState`: recarregar preserva, o link
é compartilhável e cada parâmetro casa um a um com o da API.

## Subindo o conjunto

O frontend não serve para nada sozinho — ele não tem dados. A ordem é
banco → API → web. As duas aplicações vivem neste mesmo repositório; o
[README da raiz](../README.md) traz a mesma receita do ponto de vista do monorepo.

### 1. Banco

PostgreSQL em `localhost:5432` com uma database chamada `psychology-api`. As
migrations do Flyway rodam sozinhas no boot da API; não há passo manual de schema.

```bash
createdb psychology-api
```

### 2. API

Em `backend/`:

```bash
cd ../backend
./debug.sh
```

O script carrega o arquivo de ambiente local (não versionado), aponta o
datasource para o Postgres da máquina e sobe o Spring na porta 8080.

**Requisitos:** Java 21 e Docker (o Docker só é preciso para rodar os testes da
API, que usam Testcontainers — para subir a aplicação, não).

Sem `JWT_SECRET` a aplicação **não sobe**, de propósito: falhar no boot é melhor
que subir com segredo default.

### 3. Web

De volta em `frontend/`:

```bash
npm install
npm run dev
```

Abre em <http://localhost:5173>. Crie uma conta pela própria tela — o primeiro
acesso é `Criar conta`, não existe usuário semeado.

## Variáveis de ambiente

**Da API** (todas lidas em `application.properties`):

| Variável | Padrão | Para que serve |
|---|---|---|
| `SPRING_DATASOURCE_URL` | — | `jdbc:postgresql://localhost:5432/psychology-api` |
| `SPRING_DATASOURCE_USERNAME` | — | usuário do Postgres |
| `SPRING_DATASOURCE_PASSWORD` | — | senha do Postgres |
| `JWT_SECRET` | — | **obrigatória.** Segredo do HMAC-SHA256, em Base64 |
| `PORT` | `8080` | porta do Spring |
| `APP_CORS_ORIGINS` | `http://localhost:5173` | origem liberada com credenciais |
| `APP_REFRESH_EXPIRACAO_DIAS` | `14` | validade do refresh token |
| `APP_REFRESH_COOKIE_SEGURO` | `true` | `Secure` no cookie de refresh |
| `APP_REFRESH_COOKIE_SAMESITE` | `None` | `SameSite` do cookie de refresh |

As três primeiras o `debug.sh` já define para o ambiente local.

**Do web:**

| Variável | Padrão | Para que serve |
|---|---|---|
| `VITE_API_URL` | vazio | origem da API em produção. Em desenvolvimento fica vazia, e quem resolve é o proxy |

## Por que o proxy espelha os caminhos

Em desenvolvimento o Vite proxia `/auth`, `/pacientes`, `/consultas`,
`/anotacoes`, `/lembretes` e `/auditoria` para `http://localhost:8080`. Isso
torna tudo same-origin e tira CORS e `SameSite` do caminho.

O proxy **espelha os caminhos**, sem prefixo `/api`: o cookie de refresh é
marcado `Path=/auth`, e sob um prefixo o navegador veria `/api/auth/refresh` e
simplesmente não o enviaria. A renovação quebraria só em desenvolvimento — o pior
lugar para descobrir isso.

## Sessão

O access token vive **apenas em memória**. Quem sustenta o recarregamento é o
cookie `refresh_token`, que o JavaScript não alcança:

```
refresh_token  Path=/auth  HttpOnly  Secure  SameSite=None
```

No boot, antes de renderizar rota nenhuma, um `POST /auth/refresh` restaura a
sessão. É assim que o F5 não desloga sem que exista token persistido em lugar
nenhum.

Chamadas que tomam `401` compartilham **uma** promise de renovação. A API
rotaciona o refresh a cada uso e revoga a cadeia inteira ao ver um refresh
reapresentado — renovações concorrentes seriam lidas como token roubado e
derrubariam a sessão de um usuário legítimo.

## Testes

```bash
npm test          # 70 testes de unidade e de componente, com MSW
npm run e2e       # 2 testes de fumaça — exigem a API no ar
```

Na primeira vez, o Playwright precisa do navegador:

```bash
npx playwright install chromium
```

Os handlers do MSW respondem o contrato **real** da API — envelope de erro,
`PagedModel`, `401`. O E2E percorre `registrar → entrar → cadastrar paciente →
anotar → lembrar → agendar → série → trilha` contra a API de verdade, e é o único
teste capaz de pegar contrato desalinhado entre as duas aplicações.

Ele já provou o valor duas vezes: pegou a tela morta em que o login caía depois
de autenticar, e pegou que este projeto lia `erros[].mensagem` onde a API manda
`erros[].erro` — com todos os mocks errados junto, então a suíte inteira ficava
verde enquanto, contra a API real, um `400` de validação não mostrava nada na
tela. Por isso existe um teste de fumaça que provoca um `400` de verdade.

## Build

```bash
npm run build     # tsc -b && vite build, saída em dist/
npm run preview   # serve o dist/ para conferir o build
```

Em produção não há proxy: origem própria, `VITE_API_URL` apontando para a API, e
`APP_CORS_ORIGINS` no backend liberando essa origem.

## Quando não sobe

| Sintoma | Causa provável |
|---|---|
| `npm install` falha com `Cannot find module 'node:path'` | Node antigo no PATH. Este projeto pede Node 20 ou mais novo (Vite 8) |
| A API não sobe, `PlaceholderResolutionException` | falta `JWT_SECRET` no ambiente |
| A API não sobe, porta em uso | outra aplicação na 8080. Suba com `PORT=8081` e ajuste o alvo do proxy em `vite.config.ts` |
| A tela de login diz "Nao foi possivel conectar ao servidor" | a API não está no ar. O front distingue isso de credencial inválida de propósito |
| `npm run e2e` falha logo no `/auth/registrar` | mesma coisa: o E2E **não** sobe a API, só o Vite |
| Login funciona mas o F5 desloga | o cookie de refresh não está voltando. Confira se o proxy espelha `/auth` sem prefixo |

## Estrutura

```
src/
├── api/
│   ├── client.ts        fetch com base, Authorization, envelope de erro, retry de 401
│   ├── erro.ts          ErroApi
│   ├── tipos.ts         espelho dos DTOs
│   └── recursos/        auth · paciente · consulta · anotacao · lembrete · auditoria
├── features/
│   ├── auth/            LoginPage · SessaoProvider · useSessao
│   ├── pacientes/       lista · busca · edição · queries.ts
│   ├── ficha/           abas de anotações, lembretes e consultas · queries.ts
│   ├── agenda/          agenda · consulta · série · queries.ts
│   └── auditoria/       trilha · queries.ts
├── ui/                  Botao · Campo · Chip · Dialogo · Paginacao · Tabela · EstadoVazio · Selecao
├── estilo/              tokens
└── teste/               setup · servidor MSW · helpers de render
```

Três camadas com fronteira dura: `api/recursos/*` só falam HTTP e devolvem tipos;
`features/*/queries.ts` embrulha cada função numa query ou mutation, com as
invalidações declaradas ali; componentes consomem só hooks. Nenhum componente
monta URL ou toca em `Response`.

## Documentação

- `../docs/superpowers/specs/2026-08-26-frontend-design.md` — design, alternativas rejeitadas e as correções vindas da execução
- `../docs/superpowers/plans/2026-08-26-esqueleto-vertical.md` — plano do esqueleto vertical e os defeitos que ele teve
