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
para Postgres local (`localhost:5432`, banco `psychologydb`, usuário/senha
`postgres`/`postgres`).

Sem essas variáveis de ambiente definidas, a aplicação **não sobe** —
`PlaceholderResolutionException` no `TokenService`.

## Testes

```bash
sh ./mvnw test
```

Duas camadas, ambas em `src/test/java/com/psychologyapi/psychologyapi`:

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

Requisitos: Docker rodando (a imagem `postgres:16-alpine` é baixada uma vez).
O `pom.xml` fixa `api.version=1.44` no surefire — o docker-java negocia 1.32
por padrão e daemons Docker 25+ recusam. Em daemon mais antigo, sobrescreva
com `-Ddocker.api.version=...`.

Nenhuma das camadas depende do arquivo de ambiente local nem do Postgres da
máquina: o container do teste é independente do banco de desenvolvimento.

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
- `TokenService` — gera/valida JWT via `Jwts.builder()`/`Jwts.parser()`
  (API do jjwt 0.12.x — `subject()`, `issuedAt()`, `expiration()`,
  `signWith()`, sem prefixo `set`). Token carrega só o `email` como
  subject + `iat`/`exp` padrão.
- `JwtCustomtAuthenticationFilter` (`OncePerRequestFilter`) — lê
  `Authorization: Bearer ...` em toda requisição, valida token, popula
  `SecurityContextHolder`
- `SecurityConfiguration` — `SecurityFilterChain` com CSRF desabilitado,
  sessão `STATELESS`, `/auth/**` público, resto autenticado
- `SecurityService.obterUsuarioLogado()` — ponto único de acesso ao usuário
  logado a partir do contexto; lança `IllegalStateException` (não retorna
  `null`) se a autenticação não for um `CustomAuthentication` — sinal de
  bug de configuração, não caso de uso normal

`CustomUserDetailsService` existe no código mas **não é usado** —
resíduo de uma abordagem alternativa descartada (`DaoAuthenticationProvider`).
Avaliar remoção.

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
  telefone/email/observações opcionais (`nullable = true`).
- **`Consulta`**: `Status` enum = `AGENDADA, REALIZADA, CANCELADA, FALTOU`.
  Validação de conflito de horário (janela de 50 min ao redor de
  `dtConsulta`, ignorando `CANCELADA`) roda em `cadastrar` e
  `atualizarConsulta` — na atualização, exclui a própria consulta da
  comparação e só roda se `dtConsulta` vier preenchido no DTO (permite
  update parcial). Soft delete ativo — a janela de conflito ignora consulta
  soft-deletada (o filtro está no próprio
  `findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull`, com o
  `AndDeletedAtIsNull` **depois** do `Between`, que já consome os dois
  parâmetros de intervalo). Agenda do dia usa `BETWEEN`
  (`LocalDate.now().atStartOfDay()` até `.atTime(LocalTime.MAX)`), não
  igualdade — `dtConsulta` é `LocalDateTime`.
- **`Anotacao`**: sem `PUT` (registro histórico de sessão, não editável).
  Soft delete ativo. Campo da entidade chama-se `anotacao`, DTO usa
  `conteudo` — precisa de `@Mapping` explícito no mapper.
- **`Lembrete`**: sem `Usuario` direto (só via `Paciente`). Campo
  `concluido` (`Boolean`, default `false` no `@PrePersist`). Sem `PUT`
  genérico — endpoint dedicado `PATCH /lembretes/{id}/concluir`. Soft delete
  ativo — no nome do método de listagem o `AndDeletedAtIsNull` vem **antes** do
  `OrderBy`
  (`findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc`);
  depois do `OrderBy` não parseia.
- **`LogAuditoria`**: append-only (só `save`, nunca update/delete). Campos:
  `usuario`, `acao` (`CRIACAO/VISUALIZACAO/EXCLUSAO`), `entidade` (String
  livre, ex. `"Anotacao"`), `entidadeId`. Chamado manualmente (sem AOP, por
  escolha deliberada de manter explícito) em `AnotacaoService` nos 3
  pontos de acesso — em `listarPorPaciente`, loga o **paciente** visitado,
  não cada anotação individual da lista. Auditoria hoje só cobre
  `Anotacao`; `Paciente` ainda não está coberto.

## Deploy

Render, via Docker (build multi-estágio: Maven compila o `.jar`, roda em
imagem só com JRE). Deploy automático a cada push em `main`. Variáveis de
ambiente (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `JWT_SECRET`)
cadastradas no painel do Render, nunca commitadas.
`server.port=${PORT:8080}` no `application.properties` — o Render injeta
`PORT`, não usa `8080` fixo.

## Divergências conhecidas (código × este documento)

Divergências ainda em aberto entre o código e as regras acima. Três outras
(exclusão física em `deletarPaciente`, `Anotacao` sem `@GeneratedValue` e a
migration faltante de `anotacao.deleted_at_anotacao`) foram corrigidas e a
suíte está verde.

**Pendência de produção:** a `V6` cria `anotacao.deleted_at_anotacao`. Se o
banco de produção já recebeu essa coluna por DDL manual, a `V6` falha no
boot. Conferir antes do deploy e, se for o caso, tratar com `repair` ou
marcar a migration como já aplicada.

1. **`deletedAt` declarado como `nullable = false`** em `Paciente` e
   `Anotacao`, contra a regra ("nasce `null`"). Hoje é inofensivo em runtime
   (`ddl-auto` desligado, e o Hibernate delega a checagem à Bean Validation),
   mas contradiz o schema real, onde a coluna é anulável. `Consulta` e
   `Lembrete` estão corretos.

2. **`server.jwt.secret` no `application.properties` não é lido por
   ninguém.** O `TokenService` usa `@Value("${JWT_SECRET}")`, ou seja, lê a
   variável de ambiente direto. A propriedade é resíduo.

## Fora de escopo (não sugerir sem o usuário pedir explicitamente)

Financeiro, relatórios, convênios, documentos, multiempresa, chat,
assinaturas, IA, dashboard complexo, roles/múltiplos psicólogos por
clínica, OAuth2/login social.

## Roadmap conhecido (não implementado)

- Auditoria estendida além de `Anotacao` (`Paciente`, no mínimo)
- Paginação em listagens de `Anotacao`/`Lembrete` (não crítico até haver
  uso real com histórico longo)
- Consulta recorrente (agendamento semanal automático)
- Frontend React/TypeScript — não iniciado
- `CustomUserDetailsService` órfão — decidir remover ou dar uso