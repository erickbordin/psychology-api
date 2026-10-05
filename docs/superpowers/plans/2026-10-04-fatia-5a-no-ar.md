# Fatia 5A — No ar — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** o produto pronto para ir ao ar numa VM gratuita em São Paulo, mesma origem atrás do Caddy, com backup cifrado testado, sessão que se renova, cadastro por convite com aceite dos termos, e os roteiros de operação.

**Architecture:** a API ganha cookie de refresh com caminho configurável, `/saude`, termos lidos de diretório configurável, convites de uso único (migration V3) e limite de tentativas por IP nas rotas de autenticação. O front ganha prefixo de API por `VITE_API_URL`, renovação de sessão no boot e no 401 (uma promise compartilhada), e as telas públicas `/cadastro`, `/termos`, `/privacidade`. A infraestrutura é um `infra/compose.yml` (caddy com o front embutido, api, postgres, backup) com scripts de deploy, backup e teste local.

**Tech Stack:** Java 21, Spring Boot 3.5, JPA, Flyway · React 19, TS, TanStack Query 5, RHF + zod, Vitest/MSW, Playwright · Docker Compose, Caddy 2, Postgres 16, `age`, `rclone` (S3), MinIO só em teste.

**Spec:** `docs/superpowers/specs/2026-10-04-fatia-5a-no-ar-design.md` — autoridade. Decisões B1–B14 são do dono; T1–T4 são técnicas e já aprovadas na revisão da spec.

## Global Constraints

- **NÃO faça commit, `git add`, `git stash`, `git checkout`, `git reset`** nem nenhum git que escreva. O dono comita. Reporte a lista exata de arquivos.
- **NENHUM comentário novo** em código, teste, SQL, CSS, YAML, Caddyfile, Dockerfile ou shell (`//`, `/* */`, Javadoc, `{/* */}`, `--`, `#`). Exceção única: a linha `#!/usr/bin/env bash` dos scripts. Não edite nem remova comentário existente, **exceto** o que a tarefa nomeia. Documentação em markdown é prosa, não comentário.
- **Não rode formatador.** Estilo da casa à mão: front sem ponto e vírgula, aspas simples.
- **Só os arquivos listados na tarefa.** Precisou de outro → pare e reporte `NEEDS_CONTEXT`.
- **Cópia nova com acento**, exatamente como a spec escreve. Cópia existente sem acento fica intocada.
- **Nenhum segredo** (senha, chave, código de convite real, token, domínio real) em arquivo versionado. Exemplos usam `exemplo.com`, `troque-me` e chaves geradas na hora em teste.
- **Isolamento:** leitura/escrita de dado de psicólogo via `securityService.obterUsuarioLogado()`; as rotas públicas novas não revelam dado de ninguém.
- **Toda mutação de serviço** termina em `save`, em `@Transactional`, com auditoria depois.
- **Backend:** de `backend/`, `sh ./mvnw -q test -Dtest='Classe'`; suíte inteira `sh ./mvnw -q test` (baseline **512** verdes). Docker no ar.
- **Frontend:** todo comando de node com `ASDF_NODEJS_VERSION=20.19.1`. De `frontend/`: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run <caminho>`, `ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit -p .` (baseline **296** verdes, tsc limpo).
- **Testes de front por rótulo escrito**, nunca `className`; MSW com o envelope real `{status, mensagem, erros[]}`; UUIDs de verdade.
- **Se um comando for negado pelo sistema de permissões, pare e reporte `BLOCKED` com o comando** — não procure contorno.
- **Não despache subagentes.**
- **Hook de arquivo sensível:** neste ambiente, comandos cujo texto menciona arquivos de segredo (o arquivo de variáveis do Compose, chaves) são bloqueados pelo ai-toolkit. Não contorne (não renomeie arquivo nem codifique o nome para escapar): reporte `BLOCKED` com o comando, e o orquestrador pede ao dono para rodá-lo.

## Review Focus

1. **Renovação concorrente:** três chamadas tomam 401 juntas → **um** `POST /auth/refresh`; as três repetem uma vez; a API nunca vê o mesmo refresh duas vezes (Tarefa 5).
2. **Convite queimado duas vezes:** dois cadastros simultâneos com o mesmo código → um 201, um 403; e um cadastro que falha depois do convite (e-mail repetido) **não** queima o código (Tarefa 3).
3. **Cookie atrás do prefixo:** em produção o cookie sai com `Path=/api/auth` e volta no `POST /api/auth/refresh` via Caddy; no dev continua `/auth` (Tarefas 1 e 9).
4. **IP forjado:** um cliente que manda `X-Forwarded-For` direto à API (sem passar pelo Caddy) não escapa do limite (Tarefa 3).
5. **Backup:** falha no envio ao storage ou na restauração de teste deixa `/saude` em 503; nenhum arquivo decifrado sobra no disco (Tarefa 7).

## Mapa de arquivos

`M/` = `backend/src/main/java/com/psychologyapi/psychologyapi/`; `T/` = `backend/src/test/java/com/psychologyapi/psychologyapi/`; `R/` = `backend/src/main/resources/`; `RT/` = `backend/src/test/resources/`; `F/` = `frontend/src/`.

| Tarefa | Arquivos |
|---|---|
| 1 Cookie, proxy, saúde | `R/application.properties`; `M/authsecurity/CookieRefreshToken.java`; `M/controller/SaudeController.java` (novo); `M/service/SaudeService.java` (novo); `M/authsecurity/SecurityConfiguration.java` (uma linha `permitAll`); `T/controller/SaudeHttpTest.java` (novo); `T/controller/RefreshHttpTest.java` (caso do path) |
| 2 Termos + V3 | `R/db/migration/V3__20261004.sql` (novo); `M/model/Usuario.java`; `M/model/ConviteUsado.java` (novo); `M/repository/ConviteUsadoRepository.java` (novo); `M/service/TermosService.java` (novo); `M/controller/TermosController.java` (novo); `M/controller/dto/usuario/UsuarioCadastroDTO.java`; `M/service/UsuarioService.java`; `M/exceptions/CadastroIndisponivelException.java`, `TermosDesatualizadosException.java` (novos); `M/controller/common/GlobalExceptionsHandler.java`; `RT/termos-teste/termos.md`, `privacidade.md` (novos); `T/support/HttpTest.java` (propriedade do diretório); `T/controller/TermosHttpTest.java` (novo); `T/repository/SchemaMigrationTest.java`; `T/controller/AuthControllerTest.java` e todo teste que chama `/auth/registrar` (corpo novo) |
| 3 Convite + limite | `M/service/ConviteService.java` (novo); `M/exceptions/ConviteInvalidoException.java` (novo); `M/authsecurity/LimiteDeTentativas.java` (novo, filtro); `M/controller/dto/usuario/UsuarioCadastroDTO.java`; `M/service/UsuarioService.java`; `M/controller/common/GlobalExceptionsHandler.java`; `R/application.properties`; `T/support/HttpTest.java`; `T/controller/ConviteHttpTest.java` (novo); `T/controller/LimiteDeTentativasHttpTest.java` (novo); `T/authsecurity/LimiteDeTentativasTest.java` (novo) |
| 4 Contrato | orquestrador |
| 5 Sessão no front | `F/api/cliente.ts`, `cliente.test.ts`; `F/sessao/SessaoProvider.tsx`, `SessaoProvider.test.tsx`, `RotaProtegida.tsx`, `RotaProtegida.test.tsx`; `F/telas/login/LoginPage.tsx` (+ teste); `F/vite-env.d.ts` se faltar o tipo |
| 6 Telas públicas | `F/telas/cadastro/` (novo: `CadastroPage.tsx`, `esquema.ts`, `CadastroPage.test.tsx`); `F/telas/termos/` (novo: `TermosPage.tsx`, `queries.ts`, `TermosPage.test.tsx`); `F/telas/login/LoginPage.tsx` (link); `F/rotas.tsx`; `frontend/vite.config.ts` (`/termos` no proxy); `F/estilo/estilo.css` (fim) |
| 7 Infra | `infra/compose.yml`, `infra/.env.exemplo`, `infra/caddy/Dockerfile`, `infra/caddy/Caddyfile`, `infra/backup/Dockerfile`, `infra/backup/crontab`, `infra/backup/backup.sh`, `infra/backup/testar-restauracao.sh`, `infra/backup/retencao.sh`, `infra/deploy.sh`, `infra/teste/compose.teste.yml`, `infra/teste/testar-backup.sh`, `infra/teste/subir-local.sh` (todos novos); `.gitignore` (`infra/.env`) |
| 8 Docs de operação | `docs/operacao/implantacao.md`, `restauracao.md`, `incidente.md`, `portao.md`, `convites.md` (novos); `README.md`, `backend/README.md`, `frontend/README.md`, `ai/Claude.md` |
| 9 e2e | `frontend/e2e/*.spec.ts` (cadastro pelo convite + termos); `frontend/e2e/fluxo-do-cadastro.spec.ts` (novo); `frontend/playwright.config.ts` (`baseURL` por variável, `ignoreHTTPSErrors` só quando a variável pede) |

Ordem: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9. 7 e 8 podem andar em paralelo com 5–6 (arquivos disjuntos).

---

### Task 1: Cookie com caminho configurável, proxy confiável e `/saude`

**Interfaces — Produces:** propriedade `app.refresh.cookie-path` (`APP_REFRESH_COOKIE_PATH`, padrão `/auth`); `server.forward-headers-strategy=native`; `GET /saude` público; propriedade `app.backup.arquivo-de-estado` (`APP_BACKUP_ARQUIVO_DE_ESTADO`, padrão vazio).

Formato do arquivo de estado (escrito pela Tarefa 7, lido aqui), uma chave por linha:

```
ultimo_dump_ok=2026-10-04T03:00:12
ultimo_teste=2026-10-04T04:00:40
ultimo_teste_ok=true
falha=
```

Regra de `/saude` (relógio do bean `Clock`): propriedade vazia → `200 {"status":"ok","backup":"desligado"}`; arquivo ausente/ilegível, `falha` não vazia, `ultimo_dump_ok` com mais de 36 h, ou `ultimo_teste_ok=false` → `503 {"status":"ok","backup":"atrasado"}`; senão `200 {"status":"ok","backup":"ok"}`. Nunca devolve caminho, horário ou texto da falha.

- [ ] **Step 1: Testes que falham.** `RefreshHttpTest`: com `@TestPropertySource(properties = "app.refresh.cookie-path=/api/auth")` numa classe nova `CookiePathHttpTest` (contexto próprio é aceitável), o login devolve `Set-Cookie` com `Path=/api/auth`. `SaudeHttpTest` (`@HttpTest` + arquivos temporários em `@TempDir`, propriedade trocada por `@DynamicPropertySource` numa classe própria): desligado → 200 `desligado`; arquivo ok e recente → 200 `ok`; dump de 37 h atrás → 503 `atrasado`; `ultimo_teste_ok=false` → 503; `falha=envio` → 503; arquivo ausente → 503; sem token → acessível; corpo nunca contém `envio` nem a data.
- [ ] **Step 2: Implemente.** `CookieRefreshToken` recebe `@Value("${app.refresh.cookie-path}") String caminho` e usa no lugar da constante `CAMINHO` (remova a constante; **não** mexa no Javadoc da classe). `application.properties`: `app.refresh.cookie-path=${APP_REFRESH_COOKIE_PATH:/auth}`, `server.forward-headers-strategy=native`, `app.backup.arquivo-de-estado=${APP_BACKUP_ARQUIVO_DE_ESTADO:}`. `SaudeService.estado()` lê o arquivo com `Files.readAllLines`, faz `split("=", 2)`, aplica a regra. `SaudeController` `@GetMapping("/saude")` devolve `ResponseEntity` 200/503 com `Map.of("status","ok","backup",...)`. `SecurityConfiguration`: `.requestMatchers("/saude").permitAll()` logo depois do `/descadastro/**`.
- [ ] **Step 3:** focados e suíte inteira verdes. **Não comite.**

---

### Task 2: Termos de uso e migration V3

**Interfaces — Produces:** `V3__20261004.sql`; `GET /termos/atual` → `{versao, termos, privacidade}`; `UsuarioCadastroDTO` ganha `Boolean aceiteTermos`, `String versaoTermos` (e na Tarefa 3, `String codigoConvite`); propriedade `app.termos.diretorio` (`APP_TERMOS_DIRETORIO`, padrão `classpath:termos/`).

```sql
ALTER TABLE usuario ADD COLUMN termos_versao_usuario VARCHAR(20);
ALTER TABLE usuario ADD COLUMN termos_aceitos_em_usuario TIMESTAMP;

CREATE TABLE convite_usado (
    id_convite UUID PRIMARY KEY,
    hash_convite VARCHAR(64) NOT NULL UNIQUE,
    id_usuario UUID NOT NULL REFERENCES usuario(id_usuario),
    usado_em_convite TIMESTAMP NOT NULL
);
```

Regras (spec §6): cada arquivo começa com `versao: X`; as duas versões iguais, senão o serviço trata como indisponível. Ausente → `GET /termos/atual` 404 (envelope, `Termos ainda não publicados.`) e `POST /auth/registrar` 503 `Cadastro ainda não está disponível.` antes de qualquer validação. `aceiteTermos` diferente de `true` → 400 no campo `aceiteTermos` com `Aceite os termos para criar a conta.` (`@NotNull` + `@AssertTrue` com a mesma mensagem). `versaoTermos` em branco → 400 `Informe a versão dos termos.`; diferente da atual → 409 `Os termos mudaram. Leia a versão atual.` Sucesso grava versão e `LocalDateTime.now(clock)`; trilha `CRIACAO "AceiteDosTermos"` com o id do usuário, depois do save. `UsuarioService.cadastrar` vira `@Transactional`.

- [ ] **Step 1:** `RT/termos-teste/termos.md` e `privacidade.md` (`versao: teste-1`, um parágrafo de texto fictício **marcado como fixture de teste no próprio texto**, ex. `Texto de teste. Não é o termo do produto.`). `HttpTest` ganha `"app.termos.diretorio=classpath:termos-teste/"`. `R/termos/` **não** é criado.
- [ ] **Step 2: Testes que falham** (`TermosHttpTest`): `GET /termos/atual` público devolve versão e textos sem a linha de versão; cadastro sem aceite → 400 no campo; versão errada → 409; certo → 201 e o usuário no banco tem versão e data, e a trilha tem a linha; numa classe com `app.termos.diretorio` apontando para um `@TempDir` vazio → `GET` 404 e cadastro 503 mesmo com corpo inválido; versões divergentes entre os dois arquivos → 503. `SchemaMigrationTest`: colunas e tabela novas.
- [ ] **Step 3:** atualize **todo** teste que chama `/auth/registrar` (busque por `auth/registrar` em `T/`) para mandar `aceiteTermos: true` e `versaoTermos: "teste-1"`. Não mude mais nada neles.
- [ ] **Step 4: Implemente** `TermosService` (`ResourceLoader` para resolver `classpath:`/`file:`; lê a cada chamada — arquivos pequenos, sem cache), entidade e repositório de `ConviteUsado` (usados na Tarefa 3), exceções com handlers 503/409 no `GlobalExceptionsHandler`, `TermosController` (`/termos/atual`), `permitAll` para `/termos/**`.
- [ ] **Step 5:** suíte inteira verde. **Não comite.**

---

### Task 3: Convite de uso único e limite de tentativas

**Interfaces — Produces:** `codigoConvite` no cadastro; `APP_CADASTRO_CONVITES`; `APP_LIMITE_POR_MINUTO` (padrão 10).

Regras (spec §7): ordem — termos (503) → campos (400; `codigoConvite` em branco → `Informe o código de convite.`) → convite → versão (409) → e-mail repetido (409). Convite: o código (com `trim`) precisa estar na lista da propriedade **e** o SHA-256 dele não pode estar em `convite_usado`; senão `403 Código de convite inválido.` A gravação do `ConviteUsado` acontece **depois** do save do usuário, na mesma transação, com `saveAndFlush` para o índice único decidir a corrida (violação → `ConviteInvalidoException` → 403, rollback do usuário). E-mail repetido é checado **antes** de queimar o convite. O código nunca aparece em log, exceção ou resposta.

Limite: filtro `LimiteDeTentativas` (`OncePerRequestFilter`, registrado antes da cadeia de segurança) só para `POST` em `/auth/login`, `/auth/registrar`, `/auth/refresh`; chave `rota + request.getRemoteAddr()`; janela fixa de 60 s com o `Clock`; acima de `app.limite.por-minuto` → `429` com o envelope `{status:429, mensagem:"Muitas tentativas. Tente de novo em alguns minutos.", erros:[]}` escrito pelo `ObjectMapper`. Mapa com limpeza das janelas vencidas a cada chamada (sem crescer para sempre).

- [ ] **Step 1: Testes que falham.**
  - `LimiteDeTentativasTest` (unitário, `Clock` controlado): 10 passam, a 11ª é 429; nova janela libera; rotas diferentes contam separado; IPs diferentes contam separado; rota fora da lista nunca conta.
  - `LimiteDeTentativasHttpTest` (contexto próprio com `app.limite.por-minuto=3`): 4º login → 429 no envelope; um `X-Forwarded-For: 1.2.3.4` mandado direto (MockMvc, sem proxy interno) **não** muda a chave — a 5ª com o header continua 429.
  - `ConviteHttpTest` (`@HttpTest` com `app.cadastro.convites=conv-a,conv-b,conv-c`): válido → 201 e `convite_usado` tem o hash (não o código); reuso → 403; desconhecido → 403 com a mesma mensagem; em branco → 400; e-mail repetido com convite novo → 409 e o convite **segue válido** (um cadastro seguinte com ele → 201); dois cadastros concorrentes com o mesmo código (duas threads, classe **não** transacional, limpeza manual no `@AfterEach`) → um 201 e um 403.
- [ ] **Step 2:** `HttpTest` ganha `"app.cadastro.convites=teste-1,teste-2,teste-3,teste-4,teste-5"` e `"app.limite.por-minuto=100000"`. Como todo `@HttpTest` é `@Transactional` e desfaz o que gravou, cada teste pode usar `teste-1` de novo; dentro de um mesmo teste, um código diferente por cadastro. Atualize os testes que chamam `/auth/registrar` (`AuthControllerTest`, `FluxoCompletoHttpTest`, `FluxoDeSessaoHttpTest` e os novos) para mandar `codigoConvite`. Confira que o `HttpTest` é mesmo `@Transactional`; se não for, `NEEDS_CONTEXT`. Nenhum atalho de teste em código de produção.
- [ ] **Step 3: Implemente** `ConviteService` (`@Value("${app.cadastro.convites:}") String lista`, `Set` imutável, hash SHA-256 hex), `UsuarioService.cadastrar` na ordem acima, handler 403, o filtro, `application.properties` (`app.cadastro.convites=${APP_CADASTRO_CONVITES:}`, `app.limite.por-minuto=${APP_LIMITE_POR_MINUTO:10}`).
- [ ] **Step 4:** suíte inteira verde. **Não comite.**

---

### Task 4: Contrato (orquestrador)

API na 8090 com `APP_TERMOS_DIRETORIO=file:<repo>/backend/src/test/resources/termos-teste/` (o `classpath:termos-teste/` não existe no jar de produção). `curl /v3/api-docs` cru → `frontend/contrato-openapi.json` (troca 8090→8080) → `npm run tipos` → conferir `aceiteTermos`, `versaoTermos`, `codigoConvite`, `/termos/atual`, `/saude` → `tsc`.

---

### Task 5: Prefixo da API e renovação de sessão no front

**Interfaces — Consumes:** `POST /auth/refresh` (200 `{token}` / 401), `api()`, `definirToken`. **Produces:** `api()` com prefixo e renovação; `SessaoProvider` com `restaurando: boolean`; `RotaProtegida` que espera a restauração e manda para `/entrar` com `state: { de: location, expirou?: boolean }`.

Regras (spec §5):
- `const BASE = import.meta.env.VITE_API_URL ?? ''`; `fetch(BASE + caminho, ...)`.
- Renovação em `cliente.ts`: `let renovacao: Promise<string | null> | null`; `renovar()` faz `fetch(BASE + '/auth/refresh', { method: 'POST', credentials: 'include' })`, 200 → `definirToken(token)` e devolve o token; senão `definirToken(null)` e devolve `null`; `finally` zera `renovacao`. Toda chamada que recebe 401 **e** cujo caminho não começa com `/auth/` nem `/descadastro/` chama `renovacao ??= renovar()`, espera, e se veio token repete a chamada **uma** vez; se veio `null`, dispara o evento de sessão expirada (callback registrado pelo `SessaoProvider` via `aoExpirar(fn)`) e lança o `ErroDaApi` original.
- `SessaoProvider`: no mount, `restaurando=true`, `renovarSessao()` (exportada de `cliente.ts`, usa a mesma promise) → token → `setUsuario({ email })`; `restaurando=false`. Registra `aoExpirar` que limpa usuário, token e cache e navega para `/entrar` com `state: { expirou: true, de: location atual }` (use `useNavigate` dentro do provider — confirme que ele está dentro do `BrowserRouter` em `main.tsx` e no helper `renderizar`; se não estiver, `NEEDS_CONTEXT`).
- `RotaProtegida`: `restaurando` → `<p>Carregando…</p>`; sem usuário → `<Navigate to="/entrar" replace state={{ de: location }} />`.
- `LoginPage`: com `state.expirou`, mostra acima do formulário `<p className="anuncio" role="status">Sua sessão expirou. Entre novamente.</p>`; depois de entrar, navega para `state.de?.pathname ?? '/'`.

- [ ] **Step 1: Testes que falham** (`cliente.test.ts` com MSW): prefixo aplicado quando `VITE_API_URL` existe (use `vi.stubEnv`); 401 → um refresh → repete e devolve o dado; **três chamadas paralelas com 401 → exatamente um `POST /auth/refresh`** e as três resolvem; refresh 401 → `aoExpirar` chamado uma vez e a chamada rejeita; 401 em `/auth/login` não tenta renovar; 401 em `/descadastro/x` não tenta renovar. `SessaoProvider.test.tsx`: boot com refresh 200 → usuário restaurado sem login; refresh 401 → sem usuário. `RotaProtegida.test.tsx`: mostra `Carregando…` durante a restauração; manda para `/entrar` guardando a origem. `LoginPage`: mensagem de expirada; volta para a rota de origem.
- [ ] **Step 2: Implemente.** Ajuste os testes existentes que quebrarem por causa do refresh no boot (handler MSW padrão de `/auth/refresh` com 401 no setup de teste, se houver setup comum — senão, nos arquivos que precisarem; liste-os).
- [ ] **Step 3:** suíte inteira e `tsc` verdes. **Não comite.**

---

### Task 6: Telas públicas — cadastro, termos, privacidade

Cópia exata (spec §6, §7): `/cadastro` com `<h1>Criar conta</h1>`, campos `Código de convite`, `Nome`, `E-mail`, `Senha` (`autoComplete` `off`, `name`, `email`, `new-password`), caixa `Li e aceito os Termos de Uso e a Política de Privacidade` (os dois nomes como links para `/termos` e `/privacidade`, abrindo na mesma aba), botão `Criar conta` (`Criando…` em voo), link `Já tenho conta` → `/entrar`. Em `/entrar`, link `Criar conta` → `/cadastro`. Validação do cliente com as mensagens do servidor (`Informe o código de convite.`, `Informe o nome.`, `Informe um e-mail.`, `Informe um e-mail em formato válido.`, `Informe uma senha.`, `A senha precisa de pelo menos 10 caracteres.`, `A senha deve ter no máximo 64 caracteres.` + checagem de 72 bytes igual a `F/telas/ajustes/senha.ts`, `Aceite os termos para criar a conta.`). Fluxo: carrega `GET /termos/atual` (404 → página mostra `Cadastro ainda não está disponível.` sem formulário); envia com `versaoTermos` da resposta; 201 → `entrar({ emailUsuario, senha })` → `/`; 403/409/400 → erro no campo ou alerta; 409 de termos → recarrega os termos e mostra `Os termos mudaram. Leia a versão atual.`; 503 → `Cadastro ainda não está disponível.` `/termos` e `/privacidade`: `<h1>Termos de Uso</h1>` / `<h1>Política de Privacidade</h1>`, `Versão {versao}`, o markdown renderizado como parágrafos de texto (separa por linha em branco; **nunca** `dangerouslySetInnerHTML`); 404 → `Ainda não publicado.`

- [ ] **Step 1: Testes que falham** para cada regra acima (MSW), incluindo: o corpo do POST leva `codigoConvite`, `aceiteTermos: true`, `versaoTermos`; sucesso chama o login com o mesmo e-mail e senha; nenhum HTML do markdown é interpretado (`<script>` aparece como texto).
- [ ] **Step 2: Implemente**; `rotas.tsx` com as três rotas fora da `RotaProtegida`; `vite.config.ts` acrescenta `/termos` (rota de tela **e** de API — o bypass por `Accept` já resolve) e `/saude` a `CAMINHOS_DA_API`; CSS no fim de `estilo.css`.
- [ ] **Step 3:** suíte inteira e `tsc` verdes. **Não comite.**

---

### Task 7: Infraestrutura

Conteúdo (spec §3, §4):
- `infra/compose.yml`: serviços `caddy` (build `infra/caddy`, contexto na raiz do repo; portas 80/443; volumes `caddy_data`, `caddy_config`), `api` (build `backend/`; `env_file: .env`; `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}`; `APP_BACKUP_ARQUIVO_DE_ESTADO=/estado/backup.estado`; volume `backup_estado:/estado:ro`), `postgres` (`postgres:16-alpine`, volume `pg_dados`, `healthcheck` com `pg_isready`, **sem** `ports`), `backup` (build `infra/backup`; volumes `backup_estado:/estado`, `backup_local:/backups`; `env_file: .env`); todos `restart: unless-stopped`; `api` e `backup` `depends_on: postgres: condition: service_healthy`.
- `infra/caddy/Dockerfile`: estágio `node:20-alpine` com `ARG VITE_API_URL=/api`, `npm ci`, `npm run build` em `frontend/`; estágio `caddy:2-alpine` copia `dist` para `/srv/front` e o `Caddyfile`.
- `infra/caddy/Caddyfile`: site `{$DOMINIO}`; `encode zstd gzip`; `header` com os quatro cabeçalhos da spec §3 (texto exato da CSP da spec); `handle_path /api/* { reverse_proxy api:8080 }`; `handle { root * /srv/front; try_files {path} /index.html; file_server }`.
- `infra/.env.exemplo`: todas as variáveis com valores de exemplo (`DOMINIO=exemplo.com`, `POSTGRES_PASSWORD=troque-me`, `JWT_SECRET=troque-me-base64`, `APP_CADASTRO_CONVITES=`, `APP_REFRESH_COOKIE_PATH=/api/auth`, `APP_REFRESH_COOKIE_SAMESITE=Strict`, `APP_REFRESH_COOKIE_SEGURO=true`, `APP_FRONTEND_URL=https://exemplo.com`, `APP_CORS_ORIGINS=https://exemplo.com`, `APP_AVISOS_HABILITADO=false`, `SPRINGDOC_ENABLED=false`, `BACKUP_CHAVE_PUBLICA=age1...`, `BACKUP_S3_ENDPOINT`, `BACKUP_S3_REGIAO`, `BACKUP_S3_BUCKET`, `BACKUP_S3_CHAVE`, `BACKUP_S3_SEGREDO`, `TZ=America/Sao_Paulo`). `.gitignore`: `infra/.env`.
- `infra/backup/Dockerfile`: `alpine:3.20` + `postgresql16-client`, `age`, `rclone`, `supercronic` (binário oficial do GitHub releases, versão fixada **e conferida por SHA-256 no build**), `bash`, `coreutils`; `crontab`: `0 3 * * * /app/backup.sh` e `0 4 * * 0 /app/testar-restauracao.sh`; `CMD ["supercronic","/app/crontab"]`; `TZ` do ambiente.
- `backup.sh` (`set -euo pipefail`): `pg_dump -Fc` → `age -r "$BACKUP_CHAVE_PUBLICA"` → `/backups/psychology-$(date +%F).dump.age` (o dump em claro vai por pipe, **nunca** a disco) → `rclone copyto` para o bucket (configuração por variáveis `RCLONE_CONFIG_DESTINO_*`, sem arquivo) → `retencao.sh` → atualiza `/estado/backup.estado` (`ultimo_dump_ok=agora`, `falha=`); qualquer erro: `trap` grava `falha={etapa}` sem apagar `ultimo_dump_ok` e sai com erro; apaga cópias locais com mais de 1 dia.
- `retencao.sh`: mantém os 30 diários mais recentes e os do dia `01` dos últimos 12 meses; apaga o resto no bucket (`rclone deletefile`), só arquivos `psychology-*.dump.age`.
- `testar-restauracao.sh`: gera par de teste efêmero (`age-keygen` em `mktemp -d` com `trap` que apaga), `pg_dump` → cifra com a pública de teste → sobe para `teste/` → baixa → decifra **por pipe** para `pg_restore` num Postgres efêmero (`postgres -D` num `mktemp -d` dentro do próprio container com `initdb`, porta local) → confere `SELECT max(version) FROM flyway_schema_history` igual à do banco vivo e a existência de `usuario`, `paciente`, `consulta`, `anotacao` → apaga do bucket e local → grava `ultimo_teste=agora`, `ultimo_teste_ok=true|false`.
- `infra/deploy.sh`: `git pull --ff-only`, `docker compose -f infra/compose.yml --env-file infra/.env build`, `up -d`, espera `https://$DOMINIO/api/saude` responder (`curl -fsS` com até 30 tentativas) e imprime o resultado.
- `infra/teste/compose.teste.yml` + `testar-backup.sh`: sobe `postgres` + `minio` + `backup` com variáveis de teste, cria dados mínimos, roda `backup.sh` e `testar-restauracao.sh`, confere o arquivo de estado (`falha=` vazio, `ultimo_teste_ok=true`), confere que **nenhum** arquivo `.dump` em claro existe nos volumes, depois para o MinIO e roda `backup.sh` de novo esperando `falha=envio`; derruba tudo.
- `infra/teste/subir-local.sh`: sobe o `compose.yml` inteiro com `DOMINIO=localhost` (Caddy usa a CA interna) e um `.env` de teste gerado em `mktemp`, espera `https://localhost/api/saude`, imprime a URL; `--derrubar` derruba.

- [ ] **Step 1:** escreva os arquivos.
- [ ] **Step 2:** `docker compose -f infra/compose.yml --env-file infra/.env.exemplo config -q` sem erro; `bash -n` em todos os scripts; `sh infra/teste/testar-backup.sh` verde (as duas rodadas); `sh infra/teste/subir-local.sh` → `curl -k https://localhost/api/saude` 200 e `curl -k https://localhost/` devolve o `index.html`; `--derrubar`.
- [ ] **Step 3:** se algum comando de docker for negado, `BLOCKED`. **Não comite.**

---

### Task 8: Documentação de operação

- `docs/operacao/implantacao.md`: do zero ao ar — conta Oracle (Always Free, região São Paulo; confirmar limites no site), VM ARM Ubuntu, regra de firewall 80/443 (lista de segurança da VCN **e** `iptables`/`ufw` da VM), SSH por chave, `unattended-upgrades`, Docker, clonar o repo, bucket privado + chave de acesso S3 do Object Storage, domínio `.com` com registro `A` para o IP da VM, gerar o par `age` **fora da VM**, preencher `infra/.env`, `infra/deploy.sh`, e a checagem que prova cada passo (`/api/saude`, login, F5 sem deslogar, cookie com `Path=/api/auth` nas ferramentas do navegador). Tudo que depende de preço/limite marcado "confirmar no site".
- `restauracao.md`: baixar um dump do bucket, `age -d -i chave.txt`, `pg_restore` num Postgres local (docker), conferir contagens, registrar data e resultado em `portao.md`.
- `incidente.md`: parte técnica — sinais, contenção (derrubar a API, trocar `JWT_SECRET` derruba todas as sessões, trocar senha do banco), preservação (logs, cópia da trilha), avaliação do que vazou; a parte de comunicação à ANPD e aos titulares marcada **[verificar com advogado]**, citando a Resolução CD/ANPD nº 15/2024 como referência **a confirmar**, sem cravar prazo.
- `portao.md`: a tabela da spec §8 com coluna `Estado` (`aberto`) e coluna `Evidência` vazia.
- `convites.md`: gerar um código (`openssl rand -hex 8`), acrescentar em `APP_CADASTRO_CONVITES`, `docker compose up -d api`, mandar à pessoa; código usado não volta a valer.
- READMEs/`ai/Claude.md`: status, Deploy (VM/Compose, sai Render), sessão verdadeira, variáveis novas (`APP_REFRESH_COOKIE_PATH`, `APP_TERMOS_DIRETORIO`, `APP_CADASTRO_CONVITES`, `APP_LIMITE_POR_MINUTO`, `APP_BACKUP_ARQUIVO_DE_ESTADO`, `VITE_API_URL` agora lida), débitos (sai "VITE_API_URL não lida" e "sem renovação"; entra "fonte do Google", "reaceite de termos", "e-mail repetido revela conta — mitigado por convite"), contagens reais, linha 1 de `ai/Claude.md` intocada.

- [ ] **Step 1:** escreva. **Step 2:** o revisor confere cada comando citado contra os arquivos da Tarefa 7. **Não comite.**

---

### Task 9: e2e

- `playwright.config.ts`: `baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173'`, `ignoreHTTPSErrors: process.env.E2E_BASE_URL?.startsWith('https://localhost') ?? false`, `webServer` só quando `E2E_BASE_URL` não existe.
- Os cinco specs passam a cadastrar com um código por spec, lido da variável de ambiente `E2E_CONVITES` (lista separada por vírgula) por índice fixo de cada spec (fumaça 0, sessão 1, dinheiro 2, aviso 3, ajustes 4, cadastro 5), mais `aceiteTermos: true` e `versaoTermos` lido de `GET /termos/atual`.
- `fluxo-do-cadastro.spec.ts`: `/entrar` → `Criar conta` → preenche convite, nome, e-mail, senha, marca a caixa → `Criar conta` → chega em Hoje; recarrega a página → continua logado (renovação no boot); `Sair` → `/entrar`.
- Execução (orquestrador sobe a API): banco **novo** a cada rodada (`dropdb --if-exists psychology_e2e && createdb psychology_e2e`), API na 8090 com `APP_TERMOS_DIRETORIO=file:.../termos-teste/`, `APP_CADASTRO_CONVITES=e2e-1,…,e2e-10`, `APP_LIMITE_POR_MINUTO=1000`, `APP_REFRESH_COOKIE_SAMESITE=Strict`, `APP_REFRESH_COOKIE_SEGURO=false` (http local); `E2E_CONVITES=e2e-1,…,e2e-10 API_URL=http://localhost:8090 npx playwright test` → **seis** fluxos verdes.
- Fumaça no stack local: `infra/teste/subir-local.sh` com convites de teste, `E2E_BASE_URL=https://localhost E2E_CONVITES=... npx playwright test fluxo-do-cadastro fluxo-de-fumaca` → verdes (prova prefixo `/api`, cookie `Path=/api/auth`, Caddy e renovação no boot de ponta a ponta).

- [ ] **Step 1:** escreva/adapte. **Step 2:** as duas execuções verdes; vitest, `npm run build` e, por último, `sh ./mvnw -q test`. **Não comite.**
