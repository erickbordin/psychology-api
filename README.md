# psychology-api

API REST de gestão de consultório para psicólogos autônomos. Java 21 + Spring Boot,
com autenticação JWT stateless, isolamento de dados entre profissionais, trilha de
auditoria e schema versionado.

O domínio lida com dado sensível de saúde — anotação de sessão, histórico de
paciente. Isso definiu as decisões de projeto descritas abaixo: isolamento
verificado por teste, exclusão reversível e registro de acesso.

> **Status:** MVP funcional, no ar. Frontend React/TypeScript planejado em
> repositório separado, ainda não iniciado.

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

Sete migrations até aqui (`V1` a `V7`), nomeadas `V<n>__<AAAAMMDD>.sql`.

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
cadeia responde `401`, via `HttpStatusEntryPoint`. Exceção lançada num filtro sobe
antes do `DispatcherServlet`, onde o `@RestControllerAdvice` não alcança — sem esse
tratamento, um token vencido (a validade é de 15 min) devolveria `500`.

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
| `POST` | `/auth/login` | Devolve o JWT |
| `POST` `GET` | `/pacientes` | Cadastra / lista pacientes (`?nome` opcional filtra) |
| `GET` `PUT` `DELETE` | `/pacientes/{id}` | Detalha / atualiza / remove (soft) |
| `POST` `GET` | `/consultas` | Agenda consulta / lista a agenda (`?de`+`?ate` opcionais; sem eles, hoje) |
| `GET` `PUT` `DELETE` | `/consultas/{id}` | Detalha / atualiza / remove (soft) |
| `POST` | `/consultas/recorrentes` | Agenda uma série semanal (2 a 52 sessões) |
| `DELETE` | `/consultas/series/{serieId}` | Cancela as ocorrências futuras da série |
| `POST` `GET` | `/pacientes/{pacienteId}/anotacoes` | Cria / lista anotações (paginado) |
| `DELETE` | `/anotacoes/{anotacaoId}` | Remove anotação (soft) |
| `POST` `GET` | `/pacientes/{pacienteId}/lembretes` | Cria / lista lembretes (paginado) |
| `PATCH` | `/lembretes/{lembreteId}/concluir` | Conclui lembrete |
| `DELETE` | `/lembretes/{lembreteId}` | Remove lembrete (soft) |
| `GET` | `/auditoria` | Trilha de auditoria do profissional (paginado; `?entidade`+`?entidadeId` opcionais) |

Toda resposta de consulta carrega o `serieId` da sua série (`null` quando a consulta
é avulsa). Sem isso o cancelamento da série só seria alcançável por um cliente que
tivesse guardado a resposta da criação — e o psicólogo voltaria a cancelar uma
ocorrência por vez, que é o problema que a série existe para resolver.

Sub-recurso fica sob o paciente (a URL é a fonte da verdade do `pacienteId`);
ação sobre o recurso individual fica em rota própria, porque só precisa do
próprio id.

Erros seguem semântica HTTP explícita, centralizados num `@RestControllerAdvice`:
`401` credencial inválida, `404` não encontrado (inclui registro de outro
profissional), `409` duplicado ou conflito de horário, `400` validação — com a
lista de campo + mensagem. Requisição sem token, ou com token expirado ou
inválido, responde `401` pela própria cadeia de segurança.

Os `400` de regra — intervalo de datas pela metade ou invertido em
`/consultas`, `entidadeId` sem `entidade` em `/auditoria` — devolvem o mesmo
envelope da validação de bean, com o campo culpado nomeado. Um cliente trata uma
forma de `400`, não três.

## Rodando localmente

**Requisitos:** Java 21, Docker (para os testes), PostgreSQL local.

```bash
# banco: localhost:5432, database "psychology-api"
# defina JWT_SECRET no arquivo de ambiente local (não versionado)
./debug.sh
```

Sem `JWT_SECRET` a aplicação não sobe — o `TokenService` lança
`PlaceholderResolutionException`. Isso é intencional: falhar no boot é melhor que
subir com segredo default.

## Testes

```bash
sh ./mvnw test
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

src/main/resources/db/migration/   # migrations Flyway (V1..V7)
src/test/java/.../service/         # unitários (Mockito)
src/test/java/.../repository/      # integração (Testcontainers)
src/test/java/.../controller/      # HTTP (MockMvc sobre o mesmo container)
```

## Deploy

Render, via Docker multi-estágio — Maven compila o `.jar`, a imagem final carrega
só o JRE. Deploy automático a cada push na `main`. Variáveis de ambiente
(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`) ficam no painel da plataforma, nunca
no repositório. `server.port=${PORT:8080}` — a porta é injetada pela plataforma.

## Roadmap

- Frontend React + TypeScript, em repositório separado

## Fora de escopo

MVP deliberadamente enxuto. Não faz parte do escopo: financeiro, relatórios,
convênios, documentos, multiempresa, chat, assinaturas, dashboard complexo,
roles/múltiplos psicólogos por clínica, OAuth2 e login social.
