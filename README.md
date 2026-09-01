# psychology-api

Sistema de gestão de consultório para psicólogos autônomos. Monorepo com as duas
aplicações que compõem o produto: uma API REST em Java 21 + Spring Boot e um
frontend React + TypeScript.

O domínio lida com dado sensível de saúde — anotação de sessão, histórico de
paciente. Isso definiu as decisões de projeto de cada lado: isolamento entre
profissionais verificado por teste, exclusão reversível, registro de acesso, e
access token que nunca é persistido no navegador.

> **Status:** API no ar, frontend com as telas de pé — painel, agenda, pacientes,
> cadastro, ficha e trilha — rodando ponta a ponta contra a API real. Falta
> hospedar o frontend; hoje o conjunto só roda local.

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

- [`backend/README.md`](backend/README.md)
- [`frontend/README.md`](frontend/README.md)

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
schema.

### 2. API

```bash
cd backend
./debug.sh
```

O script carrega o arquivo de ambiente local (não versionado), aponta o datasource
para o Postgres da máquina e sobe o Spring na porta 8080. Sem `JWT_SECRET` a
aplicação **não sobe**, de propósito: falhar no boot é melhor que subir com
segredo default.

### 3. Web

```bash
cd frontend
npm install
npm run dev
```

Abre em <http://localhost:5173>. Crie uma conta pela própria tela — o primeiro
acesso é `Criar conta`, não existe usuário semeado.

Em desenvolvimento o Vite proxia os caminhos da API para a 8080, o que torna tudo
same-origin e tira CORS e `SameSite` do caminho. O proxy espelha os caminhos sem
prefixo `/api` — o cookie de refresh é marcado `Path=/auth`, e sob um prefixo o
navegador não o enviaria.

## Testes

```bash
cd backend  && sh ./mvnw test    # unitários, integração (Testcontainers) e HTTP
cd frontend && npm test          # unidade e componente, com MSW
cd frontend && npm run e2e       # fumaça ponta a ponta — exige a API no ar
```

O E2E é o único teste capaz de pegar contrato desalinhado entre as duas
aplicações: o resto de cada lado roda contra mock ou container próprio.

## Deploy

O backend vai para o Render via Docker multi-estágio. O contexto de build é o
diretório `backend/`, não a raiz do repositório:

```bash
docker build backend/
```

O frontend ainda não está hospedado. Em produção não há proxy: origem própria,
`VITE_API_URL` apontando para a API, e `APP_CORS_ORIGINS` no backend liberando
essa origem.

## Documentação

Specs e planos das duas pontas ficam juntos em `docs/superpowers/`:

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
