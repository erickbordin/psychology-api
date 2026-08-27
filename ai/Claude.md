# CLAUDE.md

Contexto do projeto para qualquer sessão de IA trabalhando neste repositório.

## Visão geral

Sistema de gestão para psicólogos autônomos (MVP). Backend Java/Spring Boot,
frontend React/TypeScript (repositório separado, ainda não iniciado).

**Filosofia do projeto:** MVP enxuto e deliberado. Antes de sugerir uma
feature nova, verifique se ela está fora do escopo combinado — ver seção
"Fora de escopo" no fim deste arquivo.

## Stack

- Java 21, Spring Boot 3.5.4, Spring Security, Hibernate/JPA, PostgreSQL
- MapStruct 1.6.3 (mapeamento DTO ↔ entidade)
- Lombok (`@Data`, `@RequiredArgsConstructor`)
- `jjwt` 0.12.7 (JWT) — **atenção**: API mudou bastante desde 0.9.x; tutoriais
  antigos (`.setSubject()`, `WebSecurityConfigurerAdapter`) não se aplicam
- `springdoc-openapi` (Swagger)
- Flyway — **versiona o schema**, roda por auto-configuração no boot (ver
  seção "Banco de dados" abaixo)
- Testes: JUnit 5 + Mockito (`spring-boot-starter-test`) e Testcontainers
  com Postgres para os testes de repository (ver seção "Testes" abaixo)

## Rodando localmente

```bash
./debug.sh
```

Carrega `JWT_SECRET` de um `.env` local (não commitado, está no
`.gitignore`) e sobe com `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` fixos
para Postgres local (`localhost:5432`, banco `psychology-api`, usuário/senha
`postgres`/`postgres`).

Sem essas variáveis de ambiente definidas, a aplicação **não sobe** —
`PlaceholderResolutionException` no `TokenService`.

## Testes

```bash
sh ./mvnw test
```

Tres camadas, todas em `src/test/java/com/psychologyapi/psychologyapi`:

- **Unitários de service** (`service/`) — Mockito puro, sem Spring e sem
  banco. Cobrem a regra de isolamento entre usuários (paciente/consulta/
  anotação/lembrete de outro psicólogo responde `404`), a janela de conflito
  de horário de ±50 min e a exigência de `save(...)` explícito em toda
  mutação.
- **Integração de repository** (`repository/`) — `@DataJpaTest` contra um
  **Postgres real via Testcontainers**, com as migrations do Flyway aplicadas
  do zero. Necessário porque o isolamento depende do *nome* do método derivado
  (`findByIdAndUsuarioAndDeletedAtIsNull`,
  `findByIdAndPaciente_UsuarioAndDeletedAtIsNull`), e nome de método só vira
  SQL de verdade contra um banco de verdade. `SchemaMigrationTest` também
  garante que toda coluna `deleted_at_*` mapeada existe no schema.
- **HTTP** (`controller/`) — `@SpringBootTest` + `MockMvc` sobre o mesmo
  container, via a anotacao composta `support/HttpTest`. Cobre o que so existe
  sobre HTTP e nenhuma das outras camadas exercita: o filtro JWT, a cadeia de
  seguranca, o binding de `@RequestBody`/`Pageable`, a serializacao do
  `PagedModel` e o `GlobalExceptionsHandler`. Nao usa `@WebMvcTest` de
  proposito: a seguranca aqui e toda customizada (filtro proprio,
  `CustomAuthentication`, `ProviderManager` explicito), entao fatiar exigiria
  remontar essa fiacao com mocks e o teste passaria a validar o mock, nao a
  fiacao. Os testes pegam o token injetando o `TokenService` e chamando
  `gerarToken(email)` — nao passam pelo `/auth/login`, que tem teste proprio.

  A exceção deliberada é `FluxoCompletoHttpTest`, que percorre
  `registrar → login → criar paciente → anotar → listar` usando o token que o
  próprio `/auth/login` devolveu. Ele existe porque nenhum outro teste prova que
  os passos **compõem**: token que o login emite mas o filtro rejeita, ou
  cadastro que responde sucesso sem produzir conta usável, passaria em todos os
  testes por endpoint. Foi ele que encontrou o bug do `AnotacaoMapper`, na
  primeira execução.

Requisitos: Docker rodando (a imagem `postgres:16-alpine` é baixada uma vez).
O `pom.xml` fixa `api.version=1.44` no surefire — o docker-java negocia 1.32
por padrão e daemons Docker 25+ recusam. Em daemon mais antigo, sobrescreva
com `-Ddocker.api.version=...`.

Nenhuma das camadas depende do arquivo de ambiente local nem do Postgres da
máquina: o container do teste é independente do banco de desenvolvimento.

`support/HttpTest` é uma anotação composta (`@SpringBootTest` com `JWT_SECRET`
de teste + `@Import(PostgresContainerConfig)` + `@AutoConfigureMockMvc` +
`@Transactional`) e **toda** classe de teste que sobe contexto completo usa ela,
inclusive `PsychologyApiApplicationTests`. Dois motivos: `MockMvc` roda na mesma
thread do teste, então o `@Transactional` rola a transação de volta e uma classe
não suja o banco da outra; e anotação idêntica significa a mesma chave de cache
de contexto no Spring — um único boot para a suíte inteira. Declarar
`@SpringBootTest` direto numa classe nova cria um segundo contexto e dobra o
tempo da suíte.

## Pacote raiz

`com.psychologyapi.psychologyapi` (sem underline — atenção se encontrar
referências antigas a `psychology_api`, são de uma versão anterior do
projeto).

## Convenções de código (seguir sempre, sem exceção)

### Nomenclatura
- **Campos Java:** `camelCase` puro (`id`, `dtConsulta`, `createdAt`) —
  nunca `snake_case` nem prefixado com o nome da entidade.
- **Colunas do banco:** `snake_case`, sempre mapeadas explicitamente via
  `@Column(name = "...")`, mesmo quando a conversão automática do Hibernate
  já resolveria — várias colunas não seguem o padrão direto
  (`id_consulta` em vez de `id`, `created_at_consulta` em vez de
  `created_at`), então o mapeamento explícito é obrigatório, não estético.
- **Parâmetros de método:** nomeie pelo que representam, nunca genérico
  `id` quando há ambiguidade (ex.: `pacienteId` vs. `anotacaoId` no mesmo
  service). Erros de troca de parâmetro já causaram bugs reais neste
  projeto.

### Toda entidade nova precisa de
```java
@Entity
@Table(name = "...", schema = "public")
@Data                                          // sem isso, MapStruct não enxerga as propriedades
public class NomeEntidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)  // sem isso, id fica null no insert
    @Column(name = "id_...", nullable = false)
    private UUID id;

    @Column(name = "created_at_...", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        // + qualquer outro default de criação (ex.: concluido = false)
    }
}
```
Enums em `@Column` **sempre** precisam de `@Enumerated(EnumType.STRING)` —
sem isso, o JPA grava como `ORDINAL` (número), frágil a reordenação do enum.

### Isolamento entre usuários (regra de segurança central do projeto)
**Todo** service que lida com dado de um psicólogo específico:
1. Busca o usuário logado via `securityService.obterUsuarioLogado()` — nunca
   confia em `usuario`/`usuarioId` vindo de DTO de entrada.
2. Busca/valida entidades relacionadas via `findByIdAndUsuario(...)` (ou a
   navegação aninhada equivalente, ver `Lembrete` abaixo) — nunca
   `findById` puro.
3. Se o registro pertence a outro usuário, o resultado é "não encontrado"
   (`404`), nunca `403` — evita revelar que o `id` existe.

`Lembrete` não tem `Usuario` direto (só via `Paciente`), então usa
navegação de relacionamento aninhado no nome do método:
```java
Optional<Lembrete> findByIdAndPaciente_Usuario(UUID id, Usuario usuario);
```

### Soft delete (aplicado em `Paciente`, `Anotacao`, `Consulta` e `Lembrete`)

`refresh_token` é a única tabela **sem** `deleted_at`, de propósito:
`revoked_at_refresh_token` já cobre o encerramento lógico, e token vencido é lixo
de infraestrutura, não prontuário. A limpeza física acontece no login, sem job.
Campo `deletedAt` (`LocalDateTime`, nullable, sem `@PrePersist` — nasce
`null`). Métodos de repository usados nos fluxos normais têm o sufixo
`AndDeletedAtIsNull`; **não existem versões sem esse filtro** no repository
(foram removidas de propósito, para o compilador forçar a atualização de
qualquer chamada esquecida). "Deletar" no service = `setDeletedAt(now())` +
`save(...)`, nunca `repository.delete(...)`.

Cobertura atual: `Paciente`, `Anotacao`, `Consulta` e `Lembrete` — todas as
entidades de dado do psicólogo. `Usuario` e `LogAuditoria` não têm (o segundo é
append-only por definição).

### DTOs
- Nunca receber entidade JPA completa num DTO de entrada (`Usuario`,
  `Paciente` inteiros) — só o `id` relevante (`pacienteId: UUID`), a
  entidade completa é resolvida no service via repository.
- `usuario`/`usuarioId` nunca aparece em DTO de entrada — sempre resolvido
  internamente via `securityService`.
- DTOs de sub-recurso (ex.: criar uma `Anotacao` de um paciente) recebem o
  `pacienteId` pelo `@PathVariable` da URL, não duplicado no corpo — a URL
  é a fonte da verdade.
- Padrão de nomes: `XCadastroDTO` (entrada de criação), `XAtualizacaoDTO`
  (entrada de update, geralmente sem validações `@NotNull` para permitir
  atualização parcial), `XResponseDTO` (saída — nunca inclui senha/hash).

### Mapper (MapStruct)
```java
@Mapper(componentModel = "spring")
public interface XMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)   // resolvido no service
    @Mapping(target = "createdAt", ignore = true)
    X toEntity(XCadastroDTO dto);

    XResponseDTO toResponseDTO(X entidade);       // nomes divergentes precisam de @Mapping explícito

    @Mapping(target = "campo", source = "dto.campo")
    void atualizarXAPartirDoDto(XAtualizacaoDTO dto, @MappingTarget X entidade);
}
```
Update usa `@MappingTarget` (muta a entidade existente) — nunca recria via
`toEntity` para update, isso perderia `id`/`createdAt`/relacionamentos.

**Campo com nome diferente entre DTO e entidade SEM `@Mapping` explícito é o
erro mais caro deste projeto, e não dá aviso nenhum.** O MapStruct não casa
nomes diferentes: ele gera o método, compila, sobe, e simplesmente não copia o
valor. Dois bugs de produção nasceram exatamente disso, ambos descobertos em
2026-08-26 e corrigidos:

- `AnotacaoMapper.toEntity` não ligava o `anotacao` do DTO ao `conteudo` da
  entidade. O `toEntity` gerado era literalmente `new Anotacao()` e `return` —
  toda criação de anotação estourava a constraint `NOT NULL` da coluna e
  devolvia `500`. O endpoint mais central do produto ficou quebrado sem ninguém
  notar.
- `PacienteMapper.toResponseDTO` não ligava `telefonePaciente`/
  `dataNascimentoPaciente` aos campos `telefone`/`dataNascimento` do DTO. Os
  dados eram gravados certo e **nunca devolvidos** — a API respondia sempre
  nulo nos dois.

Por que a suíte não pegou: os testes de service mockam o mapper, então validam o
mock. **Mapper com renomeação só é provado por teste que atravessa HTTP com o
mapper real.** Ao criar ou alterar um mapper, confira o `*MapperImpl.java`
gerado em `target/generated-sources` — se um campo não aparece lá, ele não é
copiado.

`pom.xml` precisa de `lombok-mapstruct-binding` em
`annotationProcessorPaths`, na ordem: `lombok` → `lombok-mapstruct-binding`
→ `mapstruct-processor`. Sem isso, o mapper compila mas o Spring não
encontra o bean gerado (`NoSuchBeanDefinitionException`).

### Service
- Após `repository.save(x)`, sempre captura o retorno e usa ele (não a
  variável original) para montar a resposta — mais correto mesmo quando
  "funciona" sem isso.
- Toda mutação de estado (`salvar`, `deletar`, `atualizar`) precisa
  terminar com `save(...)` explícito — inclusive em soft delete e em
  métodos tipo `concluir()` que só setam um campo. Objeto mutado em
  memória sem `save()` não persiste.

### Transação
**Todo método de service que grava mais de uma linha leva
`@Transactional`** (`org.springframework.transaction.annotation`). E isso é
quase todo método de mutação do projeto, porque **toda mutação grava duas
linhas**: a entidade e o `log_auditoria`. São 13 métodos hoje — os três de
`Paciente`, os três de `Consulta`, os dois de `Anotacao`, os três de
`Lembrete`, mais `cadastrarSerie` e `cancelarSerie`, que gravam N e não 2.

Sem a anotação, cada `save` roda na transação própria do `SimpleJpaRepository`
e commita sozinho: um erro entre os dois writes deixa dado sem trilha, ou
trilha sem dado.

Método que grava **uma** linha só não precisa: `buscarPorId` e
`AnotacaoService.listarPorPaciente` gravam apenas a linha de auditoria da
leitura, e um `save` isolado já é atômico. Esses seguem sem anotação de
propósito.

**A auditoria vem sempre DEPOIS do `save`**, nunca antes. Os quatro
`deletar*` faziam o contrário até 2026-08-26: auditavam e depois salvavam, e um
save que falhasse deixava trilha de `EXCLUSAO` de uma exclusão que não
aconteceu. Cada um tem hoje um teste com `InOrder` fixando a ordem — verificado
por mutação: inverter a ordem faz o teste falhar com
`Verification in order failure`.

### Controller
- `@RequestBody` deve vir de `org.springframework.web.bind.annotation`,
  **não** de `io.swagger.v3.oas.annotations.parameters` (erro recorrente
  neste projeto — o do Swagger não faz bind de nada).
- `@PathVariable` precisa que o nome do parâmetro bata com `{nome}` na URL,
  ou usar `@PathVariable("nomeNaUrl") tipoNome`.
- Todo método retorna `ResponseEntity<X>` explicitamente com `return` —
  nunca `void` "esquecendo" de devolver o `ResponseEntity` construído.
- Recursos aninhados: `POST`/`GET` de um sub-recurso (anotação, lembrete)
  ficam em `/pacientes/{pacienteId}/recurso`; ações sobre o recurso
  individual (`DELETE`, `PATCH`) ficam numa rota "solta"
  `/recurso/{recursoId}`, porque só precisam do próprio id. Se a classe
  tem `@RequestMapping` fixo no nível do paciente, isso exige duas classes
  controller separadas (ex.: `AnotacaoController` +
  `AnotacaoDeleteController`).

### Erros / exceções
Toda exceção de "não encontrado" é uma classe própria
(`XNaoEncontradoException extends RuntimeException`), tratada em
`GlobalExceptionsHandler` (`@RestControllerAdvice`) com `@ResponseStatus`
ou `ResponseEntity` explícito:
- `BadCredentialsException` → `401`
- `RegistroDuplicadoException` → `409`
- `ConflitoDeHorarioException` → `409`
- `XNaoEncontradoException` (Paciente, Consulta, Anotacao, Lembrete) → `404`
- `MethodArgumentNotValidException` → `400`, com lista de campo+mensagem
- `PeriodoInvalidoException` → `400` — intervalo de datas parcial (só `de` ou só
  `ate`) ou invertido em `GET /consultas`
- `FiltroAuditoriaInvalidoException` → `400` — `entidadeId` sem `entidade` em
  `GET /auditoria`

As duas últimas devolvem o **mesmo** `ErroRespostaDTO` da validação de bean, com
`campo` preenchido. O contrato de `400` da API é um só, independente de qual
caminho de validação disparou.

Requisição sem token, ou com token expirado/malformado/assinado com outro
segredo, responde `401` — vindo do `HttpStatusEntryPoint` registrado no
`SecurityConfiguration`, **não** do `GlobalExceptionsHandler`. Exceção lançada
dentro de um filtro sobe antes do `DispatcherServlet`, onde
`@RestControllerAdvice` não alcança; por isso o filtro JWT engole
`JwtException` e segue sem autenticação, deixando a cadeia responder.

## Autenticação (JWT stateless, implementação manual)

Não usa `DaoAuthenticationProvider` padrão do Spring — implementação
própria, decisão consciente:
- `CustomAuthenticationProvider` — busca usuário via `UsuarioService`,
  compara senha com `PasswordEncoder.matches()`, devolve `CustomAuthentication`
- `CustomAuthentication implements Authentication` — carrega o `Usuario`
  como principal; `getAuthorities()` retorna `List.of()` (sem roles no MVP,
  nunca `null`)
- `AuthenticationManager` registrado via `ProviderManager` explícito no
  `SecurityConfiguration`, não `AuthenticationConfiguration` automático
- `TokenService` — lê o segredo de `@Value("${server.jwt.secret}")`, que o
  `application.properties` resolve a partir da variável de ambiente
  `JWT_SECRET`; gera/valida JWT via `Jwts.builder()`/`Jwts.parser()`
  (API do jjwt 0.12.x — `subject()`, `issuedAt()`, `expiration()`,
  `signWith()`, sem prefixo `set`). Token carrega só o `email` como
  subject + `iat`/`exp` padrão.
- `JwtCustomtAuthenticationFilter` (`OncePerRequestFilter`) — lê
  `Authorization: Bearer ...` em toda requisição, valida token, popula
  `SecurityContextHolder`. Token inválido ou expirado é `catch` de
  `JwtException`/`IllegalArgumentException` + `clearContext()`, não exceção
  propagada: token vive 15 min, então expirar é caso de uso normal, e exceção
  de filtro viraria `500`
- `SecurityConfiguration` — `SecurityFilterChain` com CSRF desabilitado,
  sessão `STATELESS`, `/auth/**` público, resto autenticado, e
  `authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED))` — sem isso
  o default do Spring Security devolve `403` para requisição não autenticada,
  que é a resposta errada para uma API Bearer stateless
- `SecurityService.obterUsuarioLogado()` — ponto único de acesso ao usuário
  logado a partir do contexto; lança `IllegalStateException` (não retorna
  `null`) se a autenticação não for um `CustomAuthentication` — sinal de
  bug de configuração, não caso de uso normal

## Banco de dados

**Schema é versionado via Flyway.** `flyway-core` +
`flyway-database-postgresql` estão no `pom.xml` e as migrations vivem em
`src/main/resources/db/migration/`. Não há `spring.flyway.enabled=false`
nem `spring.jpa.hibernate.ddl-auto` no `application.properties` — ou seja,
o Flyway roda por auto-configuração no boot e o Hibernate **não** cria nem
altera tabela (default `none` para datasource não-embarcado).

**Nunca rodar `ALTER TABLE` manual em produção.** Mudança de schema = nova
migration versionada. Rodar DDL na mão dessincroniza o
`flyway_schema_history` e quebra o próximo boot (coluna já existente,
migration marcada como pendente, etc.).

Convenção de nome das migrations deste projeto: `V<n>__<AAAAMMDD>.sql`
(ex.: `V5__20260820.sql`). Migration já aplicada é imutável — editar o
arquivo muda o checksum e o Flyway falha na validação. Correção = nova
migration.

Migrations atuais:
- `V1__20260730.sql` — cria `usuario`, `paciente`, `consulta`, `anotacao`,
  `lembrete`
- `V2__20260812.sql` — `lembrete.concluido`
- `V3__20260817.sql` — `paciente.deleted_at_paciente`
- `V4__20260819.sql` — cria `log_auditoria` + índices
- `V5__20260820.sql` — `consulta.deleted_at_consulta` e
  `lembrete.deleted_at_lembrete`
- `V6__20260824.sql` — `anotacao.deleted_at_anotacao`
- `V7__20260825.sql` — `consulta.id_serie` + índice `idx_consulta_serie`

Tabelas atuais: `usuario`, `paciente`, `consulta`, `anotacao`, `lembrete`,
`log_auditoria`. FKs sem `ON DELETE CASCADE` (deliberado — impede exclusão
acidental em cascata; ver soft delete acima como mitigação real).

**Ponto em aberto sobre produção:** se as tabelas de produção foram criadas
manualmente antes de o Flyway entrar no projeto e nunca houve baseline, a
`V1` falha no boot ("relation already exists") — não há
`spring.flyway.baseline-on-migrate` configurado. Confirmar antes do próximo
deploy:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

## Entidades e regras específicas

- **`Usuario`**: email `unique = true`. Sem roles (MVP single-tenant por
  psicólogo).
- **`Paciente`**: pertence a um `Usuario`. Soft delete ativo. Campos
  telefone/email/observações opcionais (`nullable = true`). `GET /pacientes`
  aceita `?nome` **opcional**: com o parâmetro, filtra por nome; sem ele,
  devolve todos os pacientes não apagados do psicólogo logado
  (`findByUsuarioAndDeletedAtIsNull`). O parâmetro era obrigatório até
  2026-08-26, o que deixava a listagem de pacientes inalcançável — um `400`
  para quem só queria a lista.
- **`Consulta`**: `Status` enum = `AGENDADA, REALIZADA, CANCELADA, FALTOU`.
  Validação de conflito de horário (janela de 50 min ao redor de
  `dtConsulta`, ignorando `CANCELADA`) roda em `cadastrar` e
  `atualizarConsulta` — na atualização, exclui a própria consulta da
  comparação e só roda se `dtConsulta` vier preenchido no DTO (permite
  update parcial). Soft delete ativo — a janela de conflito ignora consulta
  soft-deletada (o filtro está no próprio
  `findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull`, com o
  `AndDeletedAtIsNull` **depois** do `Between`, que já consome os dois
  parâmetros de intervalo). A agenda usa `BETWEEN`, não igualdade —
  `dtConsulta` é `LocalDateTime`. `GET /consultas` aceita `?de` e `?ate`
  (`LocalDate`) **opcionais**: sem nenhum dos dois, devolve a agenda de hoje
  (`LocalDate.now().atStartOfDay()` até `.atTime(LocalTime.MAX)`), que é o
  comportamento histórico e segue sendo o default; com os dois, devolve o
  intervalo inclusivo. Um só dos dois, ou `ate` antes de `de`, é
  `PeriodoInvalidoException` → `400` — intervalo pela metade é erro do cliente,
  não algo para adivinhar. Série recorrente é
  materializada por completo na criação (`POST /consultas/recorrentes`) e
  agrupada por `serieId` (`id_serie`, `UUID` nullable — sem tabela `serie`,
  de propósito: uma tabela assim só guardaria o `UUID`, e a regra original
  não é persistida, então a série não pode ser "estendida", só recriada com
  um novo `POST`). Cada ocorrência é uma `Consulta` comum — o CRUD normal
  (`GET`/`PUT`/`DELETE` individuais) vale para ela sem tratamento especial.
  Horizonte de 2 a 52 ocorrências, explícito (`quantidadeSessoes` ou
  `dtLimite`, nunca os dois, nunca nenhum). A regra semanal (o `MIN`/`MAX`
  de ocorrências incluído) mora numa única classe,
  `service/serie/OcorrenciasSemanais`, lida tanto pelo validador do DTO
  quanto pelo service — a série gerada nunca diverge da validação que a
  guarda. Conflito de horário em qualquer ocorrência aborta a série inteira
  com `409`, antes de qualquer `save`. `DELETE /consultas/series/{serieId}`
  cancela (soft delete) só as ocorrências futuras; passadas ficam, são
  histórico clínico. **Esse `DELETE` devolve `200` com corpo**
  (`{serieId, ocorrenciasRemovidas}`), divergindo de propósito de todos os
  outros `DELETE` da API, que devolvem `204` vazio — quantas sessões foram
  canceladas é a informação que o psicólogo precisa ver, e um corpo vazio a
  esconderia. Não "corrija" essa inconsistência. `serieId` também sai no
  `ConsultaResponseDTO` de toda consulta (`null` quando avulsa), senão o
  cancelamento seria inalcançável para um cliente que perdesse a resposta da
  criação.
- **`Anotacao`**: sem `PUT` (registro histórico de sessão, não editável).
  Listagem paginada (ver "Paginação" abaixo).
  Soft delete ativo. Campo da entidade chama-se `anotacao`, DTO usa
  `conteudo` — precisa de `@Mapping` explícito no mapper.
- **`Lembrete`**: sem `Usuario` direto (só via `Paciente`). Campo
  `concluido` (`Boolean`, default `false` no `@PrePersist`). Sem `PUT`
  genérico — endpoint dedicado `PATCH /lembretes/{id}/concluir`. Soft delete
  ativo — no nome do método de listagem o `AndDeletedAtIsNull` vem **antes** do
  `OrderBy`
  (`findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc`);
  depois do `OrderBy` não parseia. Listagem paginada (ver "Paginação"
  abaixo).
- **`LogAuditoria`**: append-only (só `save`, nunca update/delete). Campos:
  `usuario`, `acao` (`CRIACAO/VISUALIZACAO/ATUALIZACAO/EXCLUSAO`), `entidade`
  (String livre, ex. `"Anotacao"`), `entidadeId`. Chamado manualmente (sem
  AOP, por escolha deliberada de manter explícito) nas quatro entidades de
  dado do psicólogo:
  - `Paciente` e `Consulta` — os 4 pontos (criação, leitura individual,
    atualização, exclusão)
  - `Anotacao` — 3 pontos; não tem `PUT`, então não há `ATUALIZACAO`
  - `Lembrete` — 3 pontos; não tem busca por id, então não há
    `VISUALIZACAO`; `ATUALIZACAO` vem do `PATCH .../concluir`

  **Listagem nunca é auditada por item.** A única exceção é
  `AnotacaoService.listarPorPaciente`, que loga o **paciente** visitado (não
  cada anotação). `PacienteService.listarPacientes`,
  `ConsultaService.listarAgenda` e `LembreteService.listarPorPaciente`
  não geram log. Acesso negado (registro de outro psicólogo) também não gera
  log — a exceção sobe antes do `registrar(...)`.

  **Leitura da trilha:** `GET /auditoria` (`AuditoriaService.listar`) devolve
  os logs do próprio psicólogo logado, paginados, mais recente primeiro
  (`findByUsuarioOrderByCreatedAtDesc`). Aceita dois filtros **opcionais**:
  `?entidade` (ex. `"Paciente"`) restringe ao tipo, e `?entidade` +
  `?entidadeId` devolve o histórico de um registro só — a pergunta útil da
  trilha, servida pelo índice `idx_log_entidade` que a `V4` já criou. São três
  métodos derivados, um por combinação, e não um `@Query` com parâmetro
  nullable: o nome do método é o SQL, e essa é a convenção do projeto.
  `entidadeId` **sem** `entidade` é `400`: id não é único entre tipos de
  entidade nesta tabela, então filtrar só por id misturaria o histórico de um
  paciente com o de uma consulta. Não há filtro por ação nem por período — se
  for pedido, entra como método derivado novo. **A leitura da auditoria não é
  auditada:**
  além da regra de listagem acima, `id_entidade` é `NOT NULL` e não existe id
  natural para registrar numa listagem. O `LogAuditoriaResponseDTO` não devolve
  `usuario` — é sempre o logado.

## Paginação

Aplicada em `Anotacao`, `Lembrete` e `LogAuditoria` — as listagens que crescem
sem limite com o uso. `Paciente` e a agenda de `Consulta` continuam retornando
`List`, porque são naturalmente pequenas para um psicólogo autônomo — decisão
revisitada em 2026-08-26, quando a listagem de pacientes deixou de exigir
filtro, e mantida.

Padrão das três camadas:

```java
// repository — Pageable como último parâmetro, retorno Page<>
Page<X> findBy...OrderByCreatedAtDesc(..., Pageable pageable);

// service — map direto, sem stream/toList
return pagina.map(xMapper::toResponseDTO);

// controller — PagedModel no corpo, nunca Page
@GetMapping("/{pacienteId}/recurso")
public ResponseEntity<PagedModel<XResponseDTO>> listar(@PathVariable UUID pacienteId,
        @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.status(HttpStatus.OK).body(new PagedModel<>(servico.listar(pacienteId, pageable)));
}
```

Dois detalhes que não são estéticos:

- **`OrderByCreatedAtDesc` continua no nome do método**, mesmo com
  `Pageable`. `Pageable` sem `sort` respeita a ordenação do nome; `sort` na
  query string sobrescreve. Remover o `OrderBy` mudaria silenciosamente a
  ordem padrão.
- **A resposta é `PagedModel`, nunca `Page`.** Serializar `PageImpl` direto
  produz JSON de estrutura instável entre versões e o Spring Boot emite
  aviso. `new PagedModel<>(pagina)` é o envelope estável.

## Deploy

Render, via Docker (build multi-estágio: Maven compila o `.jar`, roda em
imagem só com JRE). Deploy automático a cada push em `main`. Variáveis de
ambiente (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `JWT_SECRET`)
cadastradas no painel do Render, nunca commitadas.
`server.port=${PORT:8080}` no `application.properties` — o Render injeta
`PORT`, não usa `8080` fixo.

## Divergências conhecidas (código × este documento)

**Nenhuma divergência em aberto no momento.** As cinco levantadas pela suíte de
service/repository foram corrigidas: exclusão física em `deletarPaciente`,
`Anotacao` sem `@GeneratedValue`, migration faltante de
`anotacao.deleted_at_anotacao`, `deletedAt` declarado como `nullable = false` e
`server.jwt.secret` órfão.

Três divergências foram encontradas em 2026-08-26 e corrigidas. A terceira: a
seção "Transação" acima descrevia `@Transactional` como coisa de dois métodos,
enquanto **toda** mutação do projeto grava duas linhas e nenhuma tinha
transação, e os quatro `deletar*` auditavam antes do save. Corrigido nos 13
métodos, com teste de ordem em cada `deletar*`.

As outras duas eram de mapper, e do tipo mais incômodo, em que **este documento
estava certo e o código não cumpria**: a seção de mapper acima já exigia `@Mapping` explícito para nome
divergente, e nem `AnotacaoMapper.toEntity` nem `PacienteMapper.toResponseDTO`
tinham. Resultado: criação de anotação devolvia `500` em produção, e resposta de
paciente omitia telefone e data de nascimento. Detalhe na seção de mapper. Lição
de processo: uma regra escrita aqui não é uma regra verificada — só teste
verifica.

A camada de teste HTTP, ao entrar, pegou outras três — todas corrigidas:

- `AuthController.registrarUsuario` era `void` e montava um `ResponseEntity`
  que descartava: respondia `200` com corpo vazio, nunca `201`. Exatamente a
  regra de controller listada acima. Pego por
  `AuthControllerTest.registrarResponde201`.
- Token malformado, expirado ou assinado com outro segredo fazia o
  `JwtException` subir de dentro do filtro, virando `500`. Pego por
  `SegurancaHttpTest.tokenMalformadoResponde401` e
  `tokenComAssinaturaInvalidaResponde401`.
- Requisição sem token respondia `403`, não `401` — entry point default do
  Spring Security. Pego por `SegurancaHttpTest.semTokenResponde401`.

A suíte está verde (68 testes); mantenha esta seção como o lugar de registrar
qualquer contradição nova entre o código e as regras acima, sempre apontando o
teste que a pega.

**Pendência de produção:** a `V6` cria `anotacao.deleted_at_anotacao`. Se o
banco de produção já recebeu essa coluna por DDL manual, a `V6` falha no
boot. Conferir antes do deploy e, se for o caso, tratar com `repair` ou
marcar a migration como já aplicada.

## Fora de escopo (não sugerir sem o usuário pedir explicitamente)

Financeiro, relatórios, convênios, documentos, multiempresa, chat,
assinaturas, IA, dashboard complexo, roles/múltiplos psicólogos por
clínica, OAuth2/login social.

## Roadmap conhecido (não implementado)

- Frontend React/TypeScript — não iniciado