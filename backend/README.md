# psychology-api — backend

API REST de gestão de consultório para psicólogos autônomos. Java 21 + Spring Boot,
com autenticação JWT stateless, isolamento de dados entre profissionais, trilha de
auditoria e schema versionado.

O domínio lida com dado sensível de saúde — anotação de sessão, histórico de
paciente. Isso definiu as decisões de projeto descritas abaixo: isolamento
verificado por teste, exclusão reversível e registro de acesso.

> **Status:** MVP funcional, rodando local e coberto por testes. **Ainda não foi
> para produção** — o deploy está configurado, não executado. O frontend que
> consome esta API vive em [`frontend/`](../frontend/README.md), no mesmo
> repositório; o [README da raiz](../README.md) tem a receita do conjunto.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5.4, Spring Security, Spring Data JPA |
| Persistência | PostgreSQL, Hibernate, Flyway |
| Auth | JWT (`jjwt` 0.12.7), stateless |
| Mapeamento | MapStruct 1.6.3, Lombok |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, Mockito, Testcontainers |
| Build / Deploy | Maven, Docker multi-estágio, Render |

## O caminho de uma requisição

Vale ler uma vez antes de mexer em qualquer coisa: toda rota autenticada
atravessa os mesmos pontos, na mesma ordem.

```
POST /pacientes/{id}/anotacoes
  │
  ├─ 1. JwtCustomtAuthenticationFilter          authsecurity/
  │     valida o Bearer e popula o SecurityContextHolder.
  │     Token ausente, expirado ou com assinatura errada: NÃO lança —
  │     segue sem autenticar, e a cadeia responde 401 pelo entry point.
  │
  ├─ 2. AnotacaoController                      controller/
  │     recebe o DTO de entrada, valida com @Valid, não decide nada.
  │
  ├─ 3. AnotacaoService                         service/
  │     ├─ securityService.obterUsuarioLogado()   ← quem é, vem do token
  │     ├─ pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(...)
  │     │    não achou → 404, mesmo que exista para outro profissional
  │     ├─ regra de negócio (conflito de horário, XOR da série, ...)
  │     ├─ repository.save(...)                  ← sempre explícito
  │     └─ auditoriaService.registrar(...)       ← chamada manual, visível
  │
  ├─ 4. AnotacaoMapper                          mapper/
  │     entidade → DTO de resposta.
  │
  └─ 5. GlobalExceptionsHandler                 controller/
        @RestControllerAdvice: exceção de domínio vira status + envelope.
        Só alcança o que acontece do DispatcherServlet para dentro — por
        isso o filtro trata o próprio erro, no ponto 1.
```

Três consequências práticas, que valem para qualquer código novo aqui:

- **Nunca receba `usuarioId` do cliente.** Quem responde essa pergunta é o token,
  pelo `SecurityService`. Um `usuarioId` no corpo é um furo de segurança à espera.
- **Nunca use `findById` puro.** O par certo é sempre
  `findByIdAndUsuario...AndDeletedAtIsNull`.
- **Auditoria é linha de código, não mágica.** Se a mutação nova não chamar
  `auditoriaService.registrar(...)`, ela não aparece na trilha.

## Decisões de arquitetura

A parte do projeto que vale ler. Cada decisão tem um motivo — nenhuma é default
de gerador.

### Isolamento entre profissionais

Regra central de segurança. Todo service que toca dado de um psicólogo:

1. Resolve o usuário logado via `SecurityService.obterUsuarioLogado()` — **nunca**
   confia em `usuarioId` vindo do corpo da requisição.
2. Busca a entidade por `findByIdAndUsuario(...)`, nunca `findById` puro.
3. Registro de outro profissional responde **`404`, não `403`** — devolver `403`
   confirmaria que aquele `id` existe.

`Lembrete` não referencia `Usuario` direto (só via `Paciente`), então usa navegação
aninhada no nome do método derivado:

```java
Optional<Lembrete> findByIdAndPaciente_UsuarioAndDeletedAtIsNull(UUID id, Usuario usuario);
```

### Soft delete imposto pelo compilador

`Paciente`, `Consulta`, `Anotacao` e `Lembrete` usam `deletedAt`. Os métodos de
repository têm sufixo `AndDeletedAtIsNull` — e **as versões sem o filtro foram
removidas de propósito**, para que o compilador quebre em qualquer chamada
esquecida em vez de vazar registro apagado em silêncio.

Deletar, no service, é `setDeletedAt(now())` + `save(...)`. Nunca
`repository.delete(...)`.

As FKs também não têm `ON DELETE CASCADE`, deliberadamente — exclusão em cascata
acidental num histórico clínico é dano irreversível.

### Auditoria append-only

`LogAuditoria` registra `usuario`, `acao` (`CRIACAO`/`VISUALIZACAO`/`ATUALIZACAO`/
`EXCLUSAO`), `entidade` e `entidadeId`. Só `save` — nunca update nem delete.

Chamada **manual, sem AOP**, por escolha: num domínio de dado sensível, o ponto de
auditoria precisa ser visível na leitura do service, não escondido num aspecto.

A granularidade foi decidida caso a caso: listagem não gera log por item (só
`AnotacaoService.listarPorPaciente` registra o *paciente* visitado), e acesso
negado não gera log — a exceção sobe antes do `registrar(...)`.

`GET /auditoria` devolve a trilha do próprio profissional, paginada, mais recente
primeiro. A leitura da trilha **não** é auditada: além da regra de listagem acima,
`id_entidade` é `NOT NULL` — registrar uma listagem exigiria inventar um id.

### Schema versionado, Hibernate sem DDL

Flyway roda por auto-configuração no boot. Não existe `spring.jpa.hibernate.ddl-auto`
no projeto: o Hibernate não cria nem altera tabela. Mudança de schema é sempre uma
migration nova e versionada — migration já aplicada é imutável, porque editar o
arquivo muda o checksum e o Flyway falha na validação.

Uma migration só, `V1__20260901.sql`, com o schema inteiro. As oito anteriores
(de 30/07 a 26/08) foram fundidas nela antes do primeiro deploy: sem banco de
produção em lugar nenhum, o histórico de `ALTER`s não servia a ninguém — só
obrigava quem chegasse a ler oito arquivos para saber o formato de uma tabela.

A partir do primeiro deploy isso deixa de ser possível: migration aplicada em
produção é imutável, e mudança de schema passa a ser uma `V2`, `V3`, … novas.
A convenção de nome continua `V<n>__<AAAAMMDD>.sql`.

Quem já tinha o banco de desenvolvimento com o histórico antigo precisa
recriá-lo — o Flyway recusa um histórico que não bate com os arquivos:

```
Validate failed: Migrations have failed validation
```

```bash
dropdb psychology-api && createdb psychology-api
```

### Testes em três camadas

- **Unitários de service** — Mockito puro, sem Spring e sem banco. Cobrem o
  isolamento entre usuários, a janela de conflito de horário e a exigência de
  `save(...)` explícito em toda mutação.
- **Integração de repository** — `@DataJpaTest` contra um **PostgreSQL real via
  Testcontainers**, com as migrations aplicadas do zero.
- **HTTP** — `@SpringBootTest` + `MockMvc` sobre o mesmo container. Cobre filtro
  JWT, cadeia de segurança, binding, serialização do `PagedModel` e o
  `@RestControllerAdvice`. Inclui um teste de composição que percorre
  `registrar → login → criar paciente → anotar → listar` com o token que o
  próprio login devolveu — nenhum teste por endpoint prova que os passos
  encaixam.

Nenhuma das camadas é redundante. O isolamento depende do *nome* do método
derivado (`findByIdAndPaciente_UsuarioAndDeletedAtIsNull`), e nome de método só
vira SQL de verdade contra um banco de verdade — um mock aprovaria um nome
sintaticamente errado. E a camada HTTP é a única que exercita a segurança: ela
não usa `@WebMvcTest` de propósito, porque a autenticação aqui é customizada e
fatiar exigiria remontá-la com mocks, validando o mock em vez da fiação real.

Foi a camada HTTP que revelou, ao entrar, que o registro respondia `200` em vez
de `201`, que token expirado virava `500` (exceção subindo de dentro do filtro,
fora do alcance do `@RestControllerAdvice`) e que requisição sem token
respondia `403` em vez de `401`. Os três corrigidos.

E foi o teste de composição que revelou o pior deles: o mapper de anotação não
ligava o campo do DTO ao da entidade, então **toda criação de anotação
respondia `500`** — o registro de sessão, o dado central do produto. O MapStruct
não casa nomes diferentes e não avisa: gera o método, compila e não copia o
valor. Os testes de service mockavam o mapper, então validavam o mock. Mapper com
renomeação só é provado atravessando HTTP com o mapper real.

`SchemaMigrationTest` garante que toda coluna `deleted_at_*` mapeada existe no
schema — o mapeamento não pode divergir da migration sem quebrar a suíte.

### JWT stateless com provider próprio

Não usa `DaoAuthenticationProvider`. `CustomAuthenticationProvider` +
`CustomAuthentication` carregam o `Usuario` como principal, com
`AuthenticationManager` registrado via `ProviderManager` explícito.
`JwtCustomtAuthenticationFilter` (`OncePerRequestFilter`) valida o token e popula
o `SecurityContextHolder`; sessão `STATELESS`, `/auth/**` público, resto autenticado.

Token inválido ou expirado não propaga exceção: o filtro segue sem autenticar e a
cadeia responde `401`, via `ErroRespostaAuthenticationEntryPoint`. Exceção lançada
num filtro sobe antes do `DispatcherServlet`, onde o `@RestControllerAdvice` não
alcança — sem esse tratamento, um token vencido (a validade é de 15 min) devolveria
`500`.

O entry point existe para o corpo, não para o status: um `HttpStatusEntryPoint`
resolveria o `401` com corpo vazio, e o cliente teria duas formas de `401` para
tratar — o envelope do login com credencial inválida e o nada da cadeia. A mensagem
é a mesma para os três casos: distinguir token ausente de expirado de assinatura
inválida só ajudaria quem está sondando a API.

A validade de 15 min só é viável porque existe renovação: `POST /auth/refresh`
troca um refresh token opaco — guardado como SHA-256 em `refresh_token` e entregue
em cookie `HttpOnly` — por um access token novo. A rotação é a cada uso: um refresh
reapresentado depois de revogado revoga a cadeia inteira daquele usuário, porque
duas partes com a mesma credencial significa que existe uma cópia.

Detalhes e alternativas rejeitadas em
`docs/superpowers/specs/2026-08-26-sessao-com-refresh-design.md`.

### Paginação com `PagedModel`

Aplicada em `Anotacao`, `Lembrete` e `LogAuditoria` — as listagens que crescem sem
limite com o uso. A resposta é `PagedModel`, **nunca `Page`**: serializar `PageImpl` direto
produz JSON de estrutura instável entre versões do Spring.

O `OrderByCreatedAtDesc` permanece no nome do método mesmo com `Pageable` —
`Pageable` sem `sort` respeita a ordenação do nome, e remover o `OrderBy` mudaria
a ordem padrão em silêncio.

## Modelo de domínio

| Entidade | Regras |
|---|---|
| `Usuario` | O psicólogo. Email único. Sem roles (MVP single-tenant por profissional). |
| `Paciente` | Pertence a um `Usuario`. Soft delete. Listagem aceita filtro por nome, ou devolve todos. |
| `Consulta` | Status `AGENDADA`/`REALIZADA`/`CANCELADA`/`FALTOU`. Conflito de horário validado numa janela de 50 min, ignorando canceladas e soft-deletadas. Na atualização, exclui a própria consulta da comparação. Soft delete. Série recorrente materializada por completo na criação e agrupada por `serieId`; conflito em qualquer ocorrência aborta a série inteira. |
| `Anotacao` | Registro de sessão. **Sem `PUT`** — histórico não é editável. Listagem paginada. Soft delete. |
| `Lembrete` | Vinculado ao `Paciente`. Sem `PUT` genérico: ação dedicada `PATCH /lembretes/{id}/concluir`. Listagem paginada. Soft delete. |
| `LogAuditoria` | Append-only. Sem soft delete, por definição. Leitura paginada, restrita ao próprio profissional. |

## API

Documentação interativa em `/swagger-ui.html`.

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/auth/registrar` | Cria psicólogo |
| `POST` | `/auth/login` | Devolve o JWT e o cookie de refresh |
| `POST` | `/auth/refresh` | Renova o access token a partir do cookie de refresh |
| `POST` | `/auth/logout` | Revoga o refresh corrente e limpa o cookie |
| `POST` `GET` | `/pacientes` | Cadastra / lista pacientes (`?nome` opcional filtra) |
| `GET` `PUT` `DELETE` | `/pacientes/{id}` | Detalha / atualiza / remove (soft) |
| `GET` | `/pacientes/{pacienteId}/ficha` | A ficha inteira numa chamada: cabeçalho, linha do tempo e pendências |
| `GET` | `/pacientes/{pacienteId}/consultas` | Histórico de consultas do paciente (paginado) |
| `POST` `GET` | `/consultas` | Agenda consulta / lista a agenda (`?de`+`?ate` opcionais; sem eles, hoje) |
| `GET` `PUT` `DELETE` | `/consultas/{id}` | Detalha / atualiza / remove (soft) |
| `PATCH` | `/consultas/{id}/status` | Marca realizada ou faltou, num clique |
| `GET` | `/consultas/conflito` | Checa choque de horário antes do envio |
| `POST` | `/consultas/recorrentes` | Agenda uma série semanal (2 a 52 sessões) |
| `DELETE` | `/consultas/series/{serieId}` | Cancela as ocorrências futuras da série |
| `PATCH` | `/consultas/{id}/valor` | Altera o valor de uma sessão realizada sem baixa |
| `PATCH` `DELETE` | `/consultas/{id}/baixa` | Dá baixa no pagamento / desfaz a baixa |
| `POST` | `/consultas/baixas` | Baixa em lote, por lista de ids (1 a 200; tudo ou nada) |
| `GET` | `/painel/hoje` | O painel Hoje: as sessões do dia e a fila de pendências |
| `GET` | `/painel/mes` | O painel do mês: realizadas, faltas, canceladas, taxa de falta e pacientes ativos |
| `GET` | `/financeiro/mes` | O mês em dinheiro: faturado, recebido, a receber, sessões do mês e quem está devendo |
| `POST` `GET` | `/pacientes/{pacienteId}/anotacoes` | Cria / lista anotações (paginado) |
| `DELETE` | `/anotacoes/{anotacaoId}` | Remove anotação (soft) |
| `POST` `GET` | `/pacientes/{pacienteId}/pendencias` | Cria / lista pendências (paginado) |
| `PATCH` | `/pendencias/{pendenciaId}/concluir` | Conclui pendência |
| `DELETE` | `/pendencias/{pendenciaId}` | Remove pendência (soft) |
| `GET` | `/auditoria` | Trilha de auditoria do profissional (paginado; `?entidade`+`?entidadeId` opcionais) |

Toda resposta de consulta carrega o `serieId` da sua série (`null` quando a consulta
é avulsa). Sem isso o cancelamento da série só seria alcançável por um cliente que
tivesse guardado a resposta da criação — e o psicólogo voltaria a cancelar uma
ocorrência por vez, que é o problema que a série existe para resolver.

Sub-recurso fica sob o paciente (a URL é a fonte da verdade do `pacienteId`);
ação sobre o recurso individual fica em rota própria, porque só precisa do
próprio id.

**O `PUT /pacientes/{id}` é parcial, e distingue ausente de vazio.** Campo que
não vem no corpo não é tocado — é o que impede um cliente que mande meia ficha
de apagar a outra metade em silêncio. Campo de contato que vem **em branco**
(`""`) limpa o valor salvo: vale para `email` e `telefone`, que são os dados que
o paciente tem direito de pedir para remover. `valorSessao`, `dataNascimento` e
`canalAviso` ficam de fora — nenhuma tela pede a limpeza deles, e `""` nem valor
válido para eles seria. Limpar o e-mail de um paciente cujo canal de aviso é
`EMAIL` é recusado com `400`, porque o canal deixaria de ter para onde avisar.

A regra do canal é avaliada sobre o **estado efetivo** (o que o paciente terá
depois do PUT), não sobre o corpo isolado: mandar só `{"canalAviso":"EMAIL"}`
num paciente que já tem e-mail salvo passa, e antes da revisão da Fatia 2 não
passava.

Erros seguem semântica HTTP explícita, centralizados num `@RestControllerAdvice`:
`401` credencial inválida, `404` não encontrado (inclui registro de outro
profissional), `409` duplicado ou conflito de horário, `400` validação.
Requisição sem token, ou com token expirado ou inválido, responde `401` pela
própria cadeia de segurança.

**Toda resposta de erro sai no mesmo envelope**, o `ErroRespostaDTO`:

```json
{ "status": 404, "mensagem": "Paciente não encontrado.", "erros": [] }
```

O `400` preenche `erros` com um item `{campo, mensagem}` por campo; `401`, `404` e
`409` não têm campo culpado e mandam a lista vazia — vazia, e não ausente, para que
a forma do envelope não mude entre um status e outro. Um cliente lê uma forma de
erro, não duas: com JSON em uns status e texto puro em outros, tratar falha exigiria
dois caminhos de parsing.

Isso vale também para os `400` de regra — intervalo de datas pela metade ou
invertido em `/consultas`, `entidadeId` sem `entidade` em `/auditoria` —, que usam
o mesmo envelope da validação de bean, com o campo culpado nomeado.

## Rodando localmente

**Requisitos:** Java 21, PostgreSQL local. Docker só para os testes
(Testcontainers) — para subir a aplicação, não.

Com o ambiente já montado, é um comando:

```bash
./debug.sh
```

O script carrega o arquivo de ambiente local, aponta o datasource para o Postgres
da máquina (`localhost:5432`, database `psychology-api`, usuário e senha
`postgres`) e sobe o Spring na 8080. O Flyway aplica a migration sozinho no boot.

### Máquina nova, do zero

Quatro coisas precisam existir antes do `./debug.sh`, e nenhuma delas vem no
clone. Na ordem:

**1. Java 21.** Não é "21 ou mais novo": o build fixa
`<java.version>21</java.version>`.

```bash
java -version          # precisa dizer 21
```

Se a máquina tiver outra versão como padrão, aponte o `JAVA_HOME` só para este
terminal — com SDKMAN, `sdk use java 21.0.10-tem`; com asdf,
`asdf shell java temurin-21.0.10+7.0.LTS`.

**2. PostgreSQL instalado e no ar.**

```bash
sudo apt install postgresql postgresql-client   # Debian/Ubuntu
brew install postgresql@16                      # macOS
pg_isready                                      # "accepting connections"
```

O `debug.sh` conecta como usuário `postgres` com senha `postgres`. Numa instalação
nova essa senha não existe — defina uma vez:

```bash
sudo -u postgres psql -c "ALTER USER postgres PASSWORD 'postgres';"
```

**3. O banco.** `createdb` é um programa do PostgreSQL, não do projeto: roda em
**qualquer terminal, de qualquer diretório**, e não é dentro do `psql`.

```bash
createdb psychology-api
```

Se der erro de permissão, `sudo -u postgres createdb psychology-api`. Se preferir
SQL: `CREATE DATABASE "psychology-api";` — as aspas são obrigatórias, o nome tem
hífen.

Não crie tabela nenhuma na mão: o banco fica **vazio**, e quem cria o schema é o
Flyway no boot.

**4. O arquivo de ambiente**, com o `JWT_SECRET`. Ele **não é versionado** — cada
máquina tem o seu, e é o passo que trava quem clona o projeto pela primeira vez.
Na raiz de `backend/`, crie o arquivo de ambiente que o `debug.sh` procura (o
`.gitignore` já o ignora) com uma linha:

```
JWT_SECRET=<segredo em Base64>
```

O valor precisa ser **Base64** e decodificar para pelo menos 32 bytes — o
`TokenService` faz `Decoders.BASE64.decode(...)` e assina em HMAC-SHA256. Gere um:

```bash
openssl rand -base64 48
```

Segredo de desenvolvimento é descartável e não tem relação com o de produção, que
vive no painel da plataforma. Nunca commite nenhum dos dois.

### Conferindo que subiu

```bash
psql -d psychology-api -c "\dt"                # 7 tabelas + flyway_schema_history
curl -i -X POST localhost:8080/auth/refresh    # 401 é o certo: ainda não há sessão
```

No log do boot, a linha que confirma o schema:

```
Successfully applied 1 migration to schema "public", now at version v1
```

Daí em diante a conta se cria pela tela do frontend (`Criar conta`), ou direto:

```bash
curl -X POST localhost:8080/auth/registrar -H 'Content-Type: application/json' \
  -d '{"nome":"Ana","email":"ana@teste.local","senha":"senhaforte123"}'
```

### Porta ocupada

```bash
PORT=8090 ./debug.sh
```

E aponte o proxy do frontend para ela: `API_URL=http://localhost:8090 npm run dev`.

## Variáveis de ambiente

Todas lidas em `application.properties`. As três do datasource o `debug.sh` já
define para o ambiente local; o `JWT_SECRET` vem do arquivo de ambiente.

| Variável | Padrão | Para que serve |
|---|---|---|
| `SPRING_DATASOURCE_URL` | — | `jdbc:postgresql://localhost:5432/psychology-api` |
| `SPRING_DATASOURCE_USERNAME` | — | usuário do Postgres |
| `SPRING_DATASOURCE_PASSWORD` | — | senha do Postgres |
| `JWT_SECRET` | — | **obrigatória.** Segredo do HMAC-SHA256, em Base64 |
| `PORT` | `8080` | porta do Spring |
| `APP_CORS_ORIGINS` | `http://localhost:5173` | origens liberadas com credenciais, separadas por vírgula. Nunca `*` |
| `APP_REFRESH_EXPIRACAO_DIAS` | `14` | validade do refresh token |
| `APP_REFRESH_COOKIE_SEGURO` | `true` | `Secure` no cookie de refresh |
| `APP_REFRESH_COOKIE_SAMESITE` | `None` | `SameSite` do cookie de refresh |

Não há default para o datasource nem para o segredo, de propósito: a aplicação não
sobe apontando para lugar nenhum, e não sobe com segredo de exemplo.

## Quando não sobe

| Sintoma | Causa provável |
|---|---|
| `PlaceholderResolutionException: Could not resolve placeholder 'JWT_SECRET'` | falta o arquivo de ambiente, ou a variável dentro dele |
| Erro ao decodificar a chave, ou `WeakKeyException` | o `JWT_SECRET` não é Base64, ou decodifica para menos de 32 bytes. Gere com `openssl rand -base64 48` |
| `Connection refused` na 5432 | Postgres não está no ar. `pg_isready`, depois `sudo systemctl start postgresql` |
| `FATAL: password authentication failed for user "postgres"` | a senha do papel `postgres` não é `postgres`. Veja o passo 2 acima |
| `FATAL: database "psychology-api" does not exist` | falta o `createdb psychology-api` |
| `Validate failed: Migrations have failed validation` | banco com o histórico antigo, de antes do squash. `dropdb psychology-api && createdb psychology-api` |
| `Found more than one migration with version 1` | cópias velhas em `target/`. `sh ./mvnw clean` |
| Porta 8080 em uso | outra aplicação na porta. Suba com `PORT=` |
| Testes falham com `Could not find a valid Docker environment` | Docker parado. Ele é necessário só para os testes |

## Testes

```bash
sh ./mvnw test          # 169 testes: unitários, integração e HTTP
```

Precisa de Docker rodando — a imagem `postgres:16-alpine` é baixada uma vez.
O container do teste é independente do banco de desenvolvimento: nenhuma camada
depende da configuração local de ambiente nem do Postgres da máquina.

## Estrutura

```
src/main/java/com/psychologyapi/psychologyapi/
├── authsecurity/   # SecurityConfiguration, filtro JWT, provider e Authentication próprios
├── controller/     # REST + DTOs por domínio + GlobalExceptionsHandler
├── service/        # regra de negócio, isolamento, auditoria
├── repository/     # Spring Data JPA (métodos derivados com filtro de soft delete)
├── mapper/         # MapStruct (DTO <-> entidade)
├── model/          # entidades JPA
└── exceptions/     # exceções de domínio

src/main/resources/db/migration/   # migration Flyway (V1, schema inteiro)
src/test/java/.../service/         # unitários (Mockito)
src/test/java/.../repository/      # integração (Testcontainers)
src/test/java/.../controller/      # HTTP (MockMvc sobre o mesmo container)
```

## Deploy

Render, via Docker multi-estágio — Maven compila o `.jar`, a imagem final carrega
só o JRE. Deploy automático a cada push na `main`. O contexto de build é este
diretório, não a raiz do monorepo: `docker build backend/`. Variáveis de ambiente
(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`) ficam no painel da plataforma, nunca
no repositório. `server.port=${PORT:8080}` — a porta é injetada pela plataforma.

O frontend acrescenta quatro variáveis: `APP_CORS_ORIGINS` (a origem do frontend,
separada por vírgula se houver mais de uma — nunca `*`, que o próprio spec de CORS
proíbe combinar com credenciais), `APP_REFRESH_EXPIRACAO_DIAS` (padrão 14),
`APP_REFRESH_COOKIE_SEGURO` e `APP_REFRESH_COOKIE_SAMESITE` (`true` e `None` em
produção, onde API e frontend têm origens distintas).

## Roadmap

- Hospedar o frontend — a configuração de host já está no repositório
  (`frontend/vercel.json` e `frontend/public/_redirects`); falta criar o projeto
  na plataforma e apontar as variáveis

## Fora de escopo

MVP deliberadamente enxuto. Não faz parte do escopo: financeiro, relatórios,
convênios, documentos, multiempresa, chat, assinaturas, dashboard complexo,
roles/múltiplos psicólogos por clínica, OAuth2 e login social.
