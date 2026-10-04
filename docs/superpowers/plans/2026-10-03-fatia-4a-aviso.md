# Fatia 4A — O aviso — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** o paciente com consentimento recebe o lembrete por e-mail antes da sessão, o psicólogo recebe o resumo de amanhã às 18h, o paciente se descadastra por link, e Ajustes configura o aviso.

**Architecture:** migration V2 acrescenta colunas e a tabela `resumo_diario`. Um orquestrador sem transação (`ServicoDeAvisos.passada(agora)`) cria avisos/resumos por `INSERT ... ON CONFLICT DO NOTHING` (o índice único é a trava) e processa os pendentes um a um num bean transacional (`ProcessadorDeAvisos`), que reivindica por UPDATE condicional e envia por um `Carteiro` (SMTP ou registro). Um `@Scheduled` liga a passada a cada minuto. Consentimento derivado do canal no `PacienteService`; descadastro por rota pública; Ajustes por `GET`/`PUT /ajustes`. Front: caixa de consentimento, página pública de descadastro, tela Ajustes com prévia.

**Tech Stack:** Java 21, Spring Boot 3.5 (+ `spring-boot-starter-mail`), JPA/Hibernate 6, Flyway, Postgres, JUnit 5/Mockito/Testcontainers · React 19, TS, TanStack Query 5, RHF + zod 4, Vitest/MSW, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-03-fatia-4-aviso-design.md` — autoridade. Decisões A1–A16 são do dono.

## Global Constraints

- **NÃO faça commit, `git add`, `git stash`, `git checkout`, `git reset`** nem nenhum git que escreva. O dono comita. Reporte a lista exata de arquivos.
- **NENHUM comentário novo** em código, teste, SQL ou CSS (`//`, `/* */`, Javadoc, `{/* */}`, `--`, `#`). Não edite nem remova comentário existente, **exceto** o que a tarefa nomeia.
- **Não rode formatador** (prettier, google-java-format etc.). Estilo da casa à mão: front sem ponto e vírgula, aspas simples.
- **Só os arquivos listados na tarefa.** Precisou de outro → pare e reporte `NEEDS_CONTEXT`.
- **Cópia nova com acento**, exatamente como a spec escreve. Cópia existente sem acento fica intocada.
- **Mensagens de erro da API** exatamente como a spec/este plano.
- **Nada clínico em e-mail, log ou `erro`.** `erro` grava só o nome simples da exceção (`e.getClass().getSimpleName()`), nunca `getMessage()` (pode conter endereço).
- **Isolamento:** escrita/leitura de dado de psicólogo via `securityService.obterUsuarioLogado()`; registro alheio = 404. O job é processo de sistema e lê todos os psicólogos, mas cada e-mail só leva dado do próprio psicólogo/paciente.
- **Toda mutação de serviço** termina em `save`/update explícito, em `@Transactional`, com auditoria depois.
- **Backend:** de `backend/`, `sh ./mvnw -q test -Dtest='Classe'`; suíte inteira `sh ./mvnw -q test` (baseline **394** verdes). Docker no ar.
- **Frontend:** todo comando de node com `ASDF_NODEJS_VERSION=20.19.1` (Node global é 9). De `frontend/`: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run <caminho>`, `ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit -p .` (baseline **165** verdes, tsc limpo).
- **Testes de front por rótulo escrito**, nunca `className`; MSW com o envelope real `{status, mensagem, erros[]}`; UUIDs de verdade.
- **Se um comando for negado pelo sistema de permissões, pare e reporte `BLOCKED` com o comando** — não procure contorno.
- **Não despache subagentes.**

## Review Focus

1. **Envio duplo**: duas passadas (ou duas instâncias) sobre a mesma sessão geram **um** aviso — o `ON CONFLICT DO NOTHING` e o UPDATE condicional de tentativa (Tarefa 3).
2. **Remarcação**: aviso enviado para 14h; a sessão vai para 16h → nasce aviso novo para 16h; um aviso pendente do horário velho é `CANCELADO` sem enviar (Tarefa 3).
3. **Revogação no meio**: consentimento revogado ou sessão cancelada entre a criação e a nova tentativa → `CANCELADO`, nada sai (Tarefa 3).
4. **Vazamento por log/erro**: falha de SMTP grava só o nome da exceção; o carteiro de registro nunca loga destinatário/assunto/corpo (Tarefas 2 e 3).
5. **Descadastro não revela nada**: `GET /descadastro/{token}` só devolve `ativo`; token inválido é 404 neutro (Tarefa 4).

## Mapa de arquivos

`M/` = `backend/src/main/java/com/psychologyapi/psychologyapi/`; `T/` = `backend/src/test/java/com/psychologyapi/psychologyapi/`; `R/` = `backend/src/main/resources/`.

| Tarefa | Arquivos |
|---|---|
| 1 Schema | `R/db/migration/V2__20261003.sql` (novo); `M/model/Usuario.java`, `Paciente.java`, `AvisoDeConsulta.java`; `M/model/ResumoDiario.java` (novo); `M/repository/AvisoDeConsultaRepository.java`; `M/repository/ResumoDiarioRepository.java` (novo); `T/repository/SchemaMigrationTest.java`, `AvisoDeConsultaRepositoryTest.java`, `ResumoDiarioRepositoryTest.java` (novo) |
| 2 E-mail | `backend/pom.xml`; `R/application.properties`; `M/service/aviso/Mensagem.java`, `Carteiro.java`, `CarteiroSmtp.java`, `CarteiroDeRegistro.java`, `JanelaDeAviso.java`, `RedatorDeEmail.java`; `M/config/CarteiroConfig.java` (todos novos); `T/service/aviso/JanelaDeAvisoTest.java`, `RedatorDeEmailTest.java` (novos) |
| 3 Job | `M/service/aviso/ServicoDeAvisos.java`, `RegistradorDeAvisos.java`, `ProcessadorDeAvisos.java`, `AgendadorDeAvisos.java`; `M/config/AgendamentoConfig.java` (novos); `M/repository/ConsultaRepository.java`, `AvisoDeConsultaRepository.java`, `ResumoDiarioRepository.java`; `T/support/HttpTest.java`, `T/support/CarteiroDeTeste.java` (novo); `T/service/aviso/ServicoDeAvisosTest.java` (novo) |
| 4 Consentimento e descadastro | `M/service/PacienteService.java`; `M/controller/dto/paciente/PacienteResponseDTO.java`; `M/repository/PacienteRepository.java`; `M/service/DescadastroService.java`, `M/controller/DescadastroController.java`, `M/controller/dto/paciente/DescadastroDTO.java` (novos); `M/authsecurity/SecurityConfiguration.java`; `M/controller/dto/ficha/SessaoDaFichaDTO.java`; `M/service/FichaService.java`; `M/repository/AvisoDeConsultaRepository.java`; testes `T/controller/ConsentimentoHttpTest.java`, `DescadastroHttpTest.java` (novos), `T/controller/FichaDoPacienteHttpTest.java` |
| 5 Ajustes API | `M/controller/AjustesController.java`, `M/service/AjustesService.java`, `M/controller/dto/ajustes/AjustesResponseDTO.java`, `AjustesAtualizacaoDTO.java`, `M/exceptions/CampoInvalidoException.java` (novos); `M/controller/common/GlobalExceptionsHandler.java`; `T/controller/AjustesHttpTest.java` (novo); `T/controller/OpenApiHttpTest.java` |
| 6 Contrato | orquestrador: `frontend/contrato-openapi.json`, `frontend/src/api/contrato.ts` |
| 7 Paciente e ficha (front) | `frontend/src/telas/pacientes/esquema.ts`, `esquema.test.ts`, `DialogoDePaciente.tsx`, `DialogoDePaciente.test.tsx`, `queries.ts`; `frontend/src/telas/ficha/FichaPage.tsx`, `FichaPage.test.tsx`, `LinhaDoTempo.tsx`, `queries.ts` |
| 8 Descadastro (front) | `frontend/src/telas/descadastro/DescadastroPage.tsx`, `DescadastroPage.test.tsx`, `queries.ts` (novos); `frontend/src/rotas.tsx`; `frontend/vite.config.ts` |
| 9 Ajustes (front) | `frontend/src/telas/ajustes/AjustesPage.tsx`, `AjustesPage.test.tsx`, `queries.ts`, `esquema.ts`, `previa.ts`, `previa.test.ts` (novos); `frontend/src/rotas.tsx`; `frontend/src/estilo/estilo.css` |
| 10 e2e e docs | `frontend/e2e/fluxo-do-aviso.spec.ts` (novo); `README.md`, `frontend/README.md`, `backend/README.md`, `ai/Claude.md` |

---

### Task 1: Schema V2 e entidades

**Files:** ver mapa.

**Interfaces — Produces:**
- `Usuario`: `String tituloProfissional`, `boolean resumoDiario` (padrão `true`), `Integer expedienteInicio`, `Integer expedienteFim`.
- `Paciente`: `LocalDateTime descadastradoEm`.
- `AvisoDeConsulta`: `LocalDateTime dataHoraConsulta`, `int tentativas`, `LocalDateTime proximaTentativa`.
- `ResumoDiario` (entidade nova), `ResumoDiarioRepository`.

- [ ] **Step 1: Migration** `R/db/migration/V2__20261003.sql` — exatamente (sem nenhum comentário SQL):

```sql
ALTER TABLE usuario ADD COLUMN titulo_profissional_usuario VARCHAR(80);
ALTER TABLE usuario ADD COLUMN resumo_diario_usuario BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE usuario ADD COLUMN expediente_inicio_usuario SMALLINT;
ALTER TABLE usuario ADD COLUMN expediente_fim_usuario SMALLINT;
ALTER TABLE usuario ADD CONSTRAINT chk_expediente_usuario CHECK (
    (expediente_inicio_usuario IS NULL AND expediente_fim_usuario IS NULL)
    OR (expediente_inicio_usuario BETWEEN 0 AND 23
        AND expediente_fim_usuario BETWEEN 1 AND 24
        AND expediente_inicio_usuario < expediente_fim_usuario)
);

ALTER TABLE paciente ADD COLUMN descadastrado_em_paciente TIMESTAMP;

ALTER TABLE aviso_de_consulta ADD COLUMN data_hora_consulta_aviso TIMESTAMP NOT NULL;
ALTER TABLE aviso_de_consulta ADD COLUMN tentativas_aviso SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE aviso_de_consulta ADD COLUMN proxima_tentativa_aviso TIMESTAMP;
ALTER TABLE aviso_de_consulta ADD CONSTRAINT uq_aviso_consulta_destinatario_horario
    UNIQUE (id_consulta, destinatario_aviso, data_hora_consulta_aviso);
CREATE INDEX idx_aviso_pendente ON aviso_de_consulta (estado_aviso, proxima_tentativa_aviso);

CREATE TABLE resumo_diario (
    id_resumo UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    data_resumo DATE NOT NULL,
    estado_resumo VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    tentativas_resumo SMALLINT NOT NULL DEFAULT 0,
    proxima_tentativa_resumo TIMESTAMP,
    enviado_em_resumo TIMESTAMP,
    erro_resumo TEXT,
    created_at_resumo TIMESTAMP NOT NULL,
    CONSTRAINT fk_resumo_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT uq_resumo_usuario_data UNIQUE (id_usuario, data_resumo)
);
CREATE INDEX idx_resumo_pendente ON resumo_diario (estado_resumo, proxima_tentativa_resumo);
```

- [ ] **Step 2: Entidades.** Mapeamento explícito `@Column(name=...)`, sem Javadoc novo:
  - `Usuario`: `@Column(name = "titulo_profissional_usuario", length = 80) private String tituloProfissional;` · `@Column(name = "resumo_diario_usuario", nullable = false) private boolean resumoDiario = true;` · `@Column(name = "expediente_inicio_usuario") private Integer expedienteInicio;` · `@Column(name = "expediente_fim_usuario") private Integer expedienteFim;`
  - `Paciente`: `@Column(name = "descadastrado_em_paciente") private LocalDateTime descadastradoEm;`
  - `AvisoDeConsulta`: `@Column(name = "data_hora_consulta_aviso", nullable = false) private LocalDateTime dataHoraConsulta;` · `@Column(name = "tentativas_aviso", nullable = false) private int tentativas;` · `@Column(name = "proxima_tentativa_aviso") private LocalDateTime proximaTentativa;`
  - `ResumoDiario` (novo, `@Table(name = "resumo_diario", schema = "public")`, `@Data`): `id` (`id_resumo`, `@GeneratedValue(strategy = GenerationType.UUID)`), `@ManyToOne(fetch = LAZY) @JoinColumn(name = "id_usuario", nullable = false) Usuario usuario`, `LocalDate data` (`data_resumo`), `@Enumerated(STRING) EstadoAviso estado` (`estado_resumo`), `int tentativas` (`tentativas_resumo`), `LocalDateTime proximaTentativa` (`proxima_tentativa_resumo`), `LocalDateTime enviadoEm` (`enviado_em_resumo`), `String erro` (`erro_resumo`), `LocalDateTime createdAt` (`created_at_resumo`); `@PrePersist` grava `createdAt = LocalDateTime.now()` e `estado = PENDENTE` se nulo.

- [ ] **Step 3: Repositórios.** Em `AvisoDeConsultaRepository`, **remova os métodos `aVencer` e `reivindicar` com seus Javadocs** (decisão do dono: código morto). O Javadoc da classe (se houver) fica. Crie `ResumoDiarioRepository extends JpaRepository<ResumoDiario, UUID>` vazio por ora (a Tarefa 3 acrescenta métodos).

- [ ] **Step 4: Testes.**
  - `AvisoDeConsultaRepositoryTest`: apague os 4 testes de `aVencer`/`reivindicar` e o que mais dependia deles; os demais ganham `setDataHoraConsulta(...)` onde criam aviso. Acrescente: `@DisplayName("dois avisos para a mesma consulta, destinatario e horario violam o indice unico")` — persiste um, tenta `persistAndFlush` do segundo idêntico e espera `PersistenceException`/`DataIntegrityViolationException` (use `assertThatThrownBy(...).isInstanceOf(Exception.class)` e confira a mensagem contém `uq_aviso_consulta_destinatario_horario`); e `@DisplayName("o mesmo aviso com horario diferente e permitido (remarcacao)")`.
  - `ResumoDiarioRepositoryTest` (novo, molde `ConsultaRepositoryTest` com `@DataJpaTest` + Testcontainers): persiste resumo e lê de volta com estado `PENDENTE` e `createdAt` preenchido; dois resumos do mesmo usuário e data violam `uq_resumo_usuario_data`.
  - `SchemaMigrationTest`: acrescente um teste por grupo — colunas novas de `usuario`, `paciente.descadastrado_em_paciente`, colunas novas de `aviso_de_consulta`, tabela `resumo_diario` — usando os helpers `existeColuna`/`existeTabela` já do arquivo; e `@DisplayName("expediente invertido e recusado pelo banco")` com `JdbcTemplate`/`EntityManager` nativo tentando `UPDATE usuario SET expediente_inicio_usuario = 18, expediente_fim_usuario = 8` e esperando exceção (siga como o arquivo já executa SQL; se não executa, use `em.getEntityManager().createNativeQuery(...)`).

- [ ] **Step 5:** `sh ./mvnw -q test -Dtest='SchemaMigrationTest,AvisoDeConsultaRepositoryTest,ResumoDiarioRepositoryTest'` → verde; depois `sh ./mvnw -q test` → verde. **Não comite.**

---

### Task 2: Carteiro, janela e redação do e-mail (puro, sem job)

**Interfaces — Produces:**
- `record Mensagem(String de, String nomeDoRemetente, String para, String responderPara, String assunto, String corpo)`
- `interface Carteiro { void enviar(Mensagem mensagem); }`
- `final class JanelaDeAviso { static LocalDateTime momento(LocalDateTime dtConsulta, Antecedencia antecedencia) }`
- `@Component RedatorDeEmail(@Value("${app.email.remetente}") String remetente, @Value("${app.frontend.url}") String urlDoFrontend)` com `Mensagem lembrete(Consulta consulta)` e `Mensagem resumo(Usuario usuario, LocalDate dia, List<Consulta> sessoes)`.

- [ ] **Step 1: Dependência e propriedades.** `backend/pom.xml`: acrescente `spring-boot-starter-mail` (sem versão, gerida pelo parent), ao lado dos outros starters. `R/application.properties`, acrescente (sem comentário):

```properties
app.email.remetente=${APP_EMAIL_REMETENTE:lembretes@localhost}
app.frontend.url=${APP_FRONTEND_URL:http://localhost:5173}
app.avisos.habilitado=${APP_AVISOS_HABILITADO:true}
spring.mail.host=${SPRING_MAIL_HOST:}
spring.mail.port=${SPRING_MAIL_PORT:587}
spring.mail.username=${SPRING_MAIL_USERNAME:}
spring.mail.password=${SPRING_MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=${SPRING_MAIL_AUTH:true}
spring.mail.properties.mail.smtp.starttls.enable=${SPRING_MAIL_STARTTLS:true}
```

**Atenção:** com `spring.mail.host` vazio, o Spring Boot **ainda** cria o `JavaMailSender` (a condição é a propriedade existir). Por isso a escolha do carteiro (Step 3) olha se o host **não está em branco**, e não se o bean existe.

- [ ] **Step 2: Testes puros primeiro.** `T/service/aviso/JanelaDeAvisoTest.java`:
  - `H24` de `2026-12-03T14:00` → `2026-12-02T14:00`; `H2` → `2026-12-03T12:00`; `VESPERA` → `2026-12-02T18:00`; `VESPERA` de sessão `2026-12-03T08:00` → `2026-12-02T18:00`.

  `T/service/aviso/RedatorDeEmailTest.java` (constrói `new RedatorDeEmail("lembretes@exemplo.local", "http://app.local")`; fixtures via `Fixtures`): 
  1. Lembrete com blocos `REMARCAR` e `DURACAO`, título desligado, paciente "Helena Vaz" de token `tok-123`, sessão `2026-10-08T14:00` (quinta-feira): `assunto` = `Lembrete de agendamento`; `de` = `lembretes@exemplo.local`; `nomeDoRemetente` = nome do psicólogo; `responderPara` = e-mail do psicólogo; `para` = e-mail da paciente; `corpo` exatamente:
     ```
     Olá, Helena.

     Você tem um horário marcado para quinta-feira, 8 de outubro, às 14:00.

     Se precisar remarcar, é só responder este e-mail.
     Nosso encontro dura 50 minutos.

     Não quer mais receber estes lembretes? Acesse:
     http://app.local/descadastro/tok-123
     ```
     (linhas separadas por `\n`; sem `\n` final extra além do que o teste fixar — escolha uma forma e fixe no teste).
  2. Título ligado com `tituloProfissional` "Psicóloga · CRP 07/12345" → `nomeDoRemetente` = `{nome} · Psicóloga · CRP 07/12345`; título ligado mas em branco → só o nome.
  3. Nenhum bloco ligado → o corpo pula direto da frase do horário para o descadastro, com **uma** linha em branco entre eles.
  4. O corpo e o assunto **nunca** contêm as palavras `psicólog`, `terapia`, `sessão`, `consulta` (case-insensitive) quando o título está desligado.
  5. Resumo de dois pacientes ("Helena Vaz" 09:00, "Rui Sampaio" 14:30) no dia `2026-10-08`: `assunto` = `Sua agenda de amanhã`; `nomeDoRemetente` = `Consultório`; `para` = e-mail do psicólogo; `responderPara` = `null`; corpo:
     ```
     Olá, {primeiro nome do psicólogo}.

     Amanhã, quinta-feira, 8 de outubro, você tem 2 sessões:

     09:00 · Helena Vaz
     14:30 · Rui Sampaio

     Para deixar de receber este resumo, desligue-o em Ajustes.
     ```
     Com uma sessão: `você tem 1 sessão:`.

  Data por extenso: `DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", new Locale("pt", "BR"))` produz `quinta-feira, 8 de outubro` (confira no teste; se o JDK gerar maiúscula ou outra forma, ajuste o formato para chegar exatamente ao texto da spec). Hora: `HH:mm`. Primeiro nome: `nome.trim().split("\\s+")[0]`. Blocos na ordem `REMARCAR`, `CHEGADA`, `DURACAO`, com os textos exatos da spec §4.3.

- [ ] **Step 3: Implemente.** `Mensagem`, `Carteiro`, `JanelaDeAviso` (switch sobre `Antecedencia`: `H24 → minusHours(24)`, `H2 → minusHours(2)`, `VESPERA → dtConsulta.toLocalDate().minusDays(1).atTime(18, 0)`), `RedatorDeEmail` (lê `consulta.getPaciente()`, `consulta.getUsuario()`, `usuario.getBlocosAviso()`, `isTituloNoEmail()`, `getTituloProfissional()`, `paciente.getTokenDescadastro()`).
  - `CarteiroSmtp(JavaMailSender sender)`: monta `SimpleMailMessage` — `setFrom("\"" + nomeDoRemetente + "\" <" + de + ">")` (ou `InternetAddress` com nome pessoal e charset UTF-8 via `MimeMessageHelper` — prefira `MimeMessageHelper(mime, "UTF-8")` com `setFrom(de, nomeDoRemetente)`, `setTo`, `setReplyTo` só se não nulo, `setSubject`, `setText(corpo, false)`); `sender.send(...)`. Exceções sobem.
  - `CarteiroDeRegistro`: `private static final Logger LOG = LoggerFactory.getLogger(...)`; `enviar` faz só `LOG.info("Aviso registrado sem SMTP configurado")` — **sem** nenhum campo da mensagem.
  - `M/config/CarteiroConfig`: `@Bean Carteiro carteiro(@Value("${spring.mail.host:}") String host, ObjectProvider<JavaMailSender> sender)` → `host.isBlank() ? new CarteiroDeRegistro() : new CarteiroSmtp(sender.getObject())`.

- [ ] **Step 4:** `sh ./mvnw -q test -Dtest='JanelaDeAvisoTest,RedatorDeEmailTest'` → verde; suíte inteira verde. **Não comite.**

---

### Task 3: O job — criação, reivindicação, envio, nova tentativa

**Interfaces — Consumes:** Tarefas 1 e 2. **Produces:** `ServicoDeAvisos.passada()` e `passada(LocalDateTime agora)`; `AgendadorDeAvisos`.

Regras exatas: spec §4.1. Resumo: candidatas `AGENDADA`, não excluídas, `agora < dt ≤ agora + 31h`; paciente não excluído, `consentimentoAvisoEm != null`, `canalAviso == EMAIL`, e-mail não vazio; `JanelaDeAviso.momento(dt, usuario.getAntecedencia()) ≤ agora` → registra se novo. Resumo diário a partir das 18:00, para amanhã. Pendentes com `proximaTentativa ≤ agora` são processados. Tentativas: 1ª falha → `+5 min`, 2ª → `+15 min`, 3ª → `FALHOU`.

- [ ] **Step 1: Repositórios.**

`ConsultaRepository` (acrescente):

```java
    @EntityGraph(attributePaths = {"paciente", "usuario"})
    List<Consulta> findByStatusAndDtConsultaGreaterThanAndDtConsultaLessThanEqualAndDeletedAtIsNull(
            Status status, LocalDateTime depoisDe, LocalDateTime ateInclusive);

    @Query("""
            SELECT DISTINCT c.usuario FROM Consulta c
            WHERE c.status = com.psychologyapi.psychologyapi.model.Status.AGENDADA
              AND c.deletedAt IS NULL
              AND c.dtConsulta >= :inicio
              AND c.dtConsulta < :fimExclusivo
              AND c.usuario.resumoDiario = true
            """)
    List<Usuario> usuariosComResumoEntre(
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo);
```

`AvisoDeConsultaRepository` (acrescente; imports de `Modifying`, `Query`, `Param`, `EstadoAviso`, `Destinatario`):

```java
    @Modifying
    @Query(value = """
            INSERT INTO aviso_de_consulta
                (id_aviso, id_consulta, destinatario_aviso, canal_aviso, agendado_para_aviso,
                 estado_aviso, data_hora_consulta_aviso, tentativas_aviso, proxima_tentativa_aviso)
            VALUES (:id, :consultaId, 'PACIENTE', 'EMAIL', :agendadoPara,
                    'PENDENTE', :dataHoraConsulta, 0, :agora)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int registrarSeNovo(@Param("id") UUID id, @Param("consultaId") UUID consultaId,
            @Param("agendadoPara") LocalDateTime agendadoPara,
            @Param("dataHoraConsulta") LocalDateTime dataHoraConsulta,
            @Param("agora") LocalDateTime agora);

    @Query("SELECT a.id FROM AvisoDeConsulta a WHERE a.estado = :estado AND a.proximaTentativa <= :agora")
    List<UUID> idsPendentesAte(@Param("estado") EstadoAviso estado, @Param("agora") LocalDateTime agora);

    @Modifying
    @Query("""
            UPDATE AvisoDeConsulta a SET a.tentativas = a.tentativas + 1
            WHERE a.id = :id AND a.tentativas = :lidas
              AND a.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.PENDENTE
            """)
    int reivindicarTentativa(@Param("id") UUID id, @Param("lidas") int lidas);

    @Modifying
    @Query("UPDATE AvisoDeConsulta a SET a.estado = :estado, a.enviadoEm = :enviadoEm, a.erro = :erro, a.proximaTentativa = :proxima WHERE a.id = :id")
    int fechar(@Param("id") UUID id, @Param("estado") EstadoAviso estado,
            @Param("enviadoEm") LocalDateTime enviadoEm, @Param("erro") String erro,
            @Param("proxima") LocalDateTime proxima);
```

`ResumoDiarioRepository` (acrescente, análogo): `registrarSeNovo(id, usuarioId, data, agora)` nativo em `resumo_diario` (`estado_resumo 'PENDENTE'`, `tentativas_resumo 0`, `proxima_tentativa_resumo :agora`, `created_at_resumo :agora`, `ON CONFLICT DO NOTHING`); `idsPendentesAte(estado, agora)`; `reivindicarTentativa(id, lidas)`; `fechar(id, estado, enviadoEm, erro, proxima)`.

**Armadilha:** UPDATE/INSERT em massa não atualiza a entidade já carregada no contexto de persistência. Regras obrigatórias:
- **Todo** `@Modifying` destes três repositórios usa `@Modifying(clearAutomatically = true, flushAutomatically = true)`.
- Como o `clear` desanexa o que estava carregado, o processador **relê** o aviso com `findById` **depois** de `reivindicarTentativa` e só então navega para `getConsulta()`/`getPaciente()` (navegar a entidade lida antes do clear dá `LazyInitializationException`).
- Estado se fecha sempre por `fechar(...)` — nunca `save()` de entidade carregada antes do UPDATE.
- Nos testes, depois de cada `passada(...)`, leia o resultado com `findById`/`findAll` novos; para mudar uma fixture entre passadas (revogar consentimento, remarcar), **releia** a entidade pelo repositório, altere e `save`.

- [ ] **Step 2: Apoio de teste.** `T/support/CarteiroDeTeste.java`: `@TestConfiguration(proxyBeanMethods = false)` com `@Bean @Primary CarteiroDeTeste carteiroDeTeste()`; a classe `CarteiroDeTeste implements Carteiro` guarda as mensagens numa `List<Mensagem>` sincronizada (`enviadas()`, `limpar()`) e tem `falharNasProximas(int n)` que faz as próximas `n` chamadas lançarem `MailSendException("falha simulada")`. Em `T/support/HttpTest.java`: acrescente `CarteiroDeTeste.class` ao `@Import` e `"app.avisos.habilitado=false"` às `properties` do `@SpringBootTest`. (Isto muda a anotação de todas as classes HTTP de uma vez — a chave de cache de contexto continua única.) **Não toque nos comentários existentes do `HttpTest`.**

- [ ] **Step 3: Testes do job que falham** — `T/service/aviso/ServicoDeAvisosTest.java`, `@HttpTest`, `@Autowired ServicoDeAvisos`, `CarteiroDeTeste`, repositórios; `@BeforeEach` limpa o carteiro. Agora fixo: `AGORA = 2026-12-02T10:00`. Fixtures montadas por repositório (paciente com e-mail, `canalAviso EMAIL`, `consentimentoAvisoEm` preenchido; usuário com `antecedencia H24`). Um `@Test` + `@DisplayName` em português para cada:
  1. Sessão em `2026-12-03T09:00` (dentro de 24h) → `passada(AGORA)` envia **1** e-mail; o aviso fica `ENVIADO`, `tentativas` 1, `dataHoraConsulta` = horário da sessão.
  2. Rodar `passada(AGORA)` **duas vezes** → continua **1** e-mail e **1** linha.
  3. Sessão em `2026-12-04T09:00` (fora de 24h) → nada.
  4. Antecedência `H2` e sessão `2026-12-02T15:00` → nada às 10:00; `passada(2026-12-02T13:00)` envia.
  5. `VESPERA`, sessão `2026-12-03T08:00` → nada às 10:00 do dia 2; envia em `passada(2026-12-02T18:00)`.
  6. Sessão às `2026-12-02T12:00` criada já dentro da janela (A4) → envia logo.
  7. Sem consentimento, canal `NENHUM`, e-mail nulo, paciente excluído, sessão `CANCELADA`, sessão excluída → nenhum e-mail (um teste por caso ou parametrizado).
  8. Remarcação: envia para 09:00; muda `dtConsulta` para `2026-12-03T11:00`; nova passada → 2º e-mail, duas linhas (horários diferentes).
  9. Falha: `falharNasProximas(1)` → aviso `PENDENTE`, `tentativas` 1, `proximaTentativa = AGORA + 5 min`, `erro` = `MailSendException`; `passada(AGORA + 5 min)` envia e fecha `ENVIADO`.
  10. Três falhas: `falharNasProximas(3)` + passadas em `AGORA`, `+5`, `+20` → `FALHOU`, `tentativas` 3, nenhum e-mail registrado.
  11. Revogação no meio: falha na 1ª; revoga o consentimento; passada `+5` → `CANCELADO`, nenhum e-mail.
  12. Remarcação no meio: falha na 1ª; muda o horário da sessão; passada `+5` → o aviso velho `CANCELADO` sem enviar; o novo horário (se na janela) gera e envia o aviso novo.
  13. Resumo: psicólogo com 2 sessões `AGENDADA` em `2026-12-03` → `passada(2026-12-02T17:59)` não manda; `passada(2026-12-02T18:00)` manda 1 resumo com assunto `Sua agenda de amanhã`; segunda passada não manda de novo.
  14. Resumo desligado (`resumoDiario = false`) ou sem sessão amanhã → nada.
  15. `erro` nunca contém o e-mail do paciente.

- [ ] **Step 4: Implemente.**

`ProcessadorDeAvisos` (`@Component`, `@RequiredArgsConstructor`):

```java
    private static final int MAXIMO_DE_TENTATIVAS = 3;

    @Transactional
    public void processarAviso(UUID avisoId, LocalDateTime agora) {
        AvisoDeConsulta aviso = avisoRepository.findById(avisoId).orElse(null);
        if (aviso == null || aviso.getEstado() != EstadoAviso.PENDENTE) {
            return;
        }
        int lidas = aviso.getTentativas();
        if (avisoRepository.reivindicarTentativa(avisoId, lidas) == 0) {
            return;
        }
        int tentativa = lidas + 1;
        aviso = avisoRepository.findById(avisoId).orElseThrow();
        Consulta consulta = aviso.getConsulta();
        if (!aindaDeveSair(aviso, consulta, agora)) {
            avisoRepository.fechar(avisoId, EstadoAviso.CANCELADO, null, null, null);
            return;
        }
        try {
            carteiro.enviar(redator.lembrete(consulta));
            avisoRepository.fechar(avisoId, EstadoAviso.ENVIADO, agora, null, null);
        } catch (RuntimeException e) {
            registrarFalha(avisoId, tentativa, e, agora, avisoRepository::fechar);
        }
    }
```

`aindaDeveSair`: `consulta.getDeletedAt() == null`, `status == AGENDADA`, `dtConsulta.equals(aviso.getDataHoraConsulta())`, `dtConsulta.isAfter(agora)`, paciente não excluído, `consentimentoAvisoEm != null`, `canalAviso == EMAIL`, e-mail não vazio. `registrarFalha`: `erro = e.getClass().getSimpleName()`; `tentativa >= 3` → `FALHOU` com `proxima = null`; senão `PENDENTE` com `proxima = agora.plusMinutes(tentativa == 1 ? 5 : 15)`. (Uma interface funcional local de 5 parâmetros, ou dois métodos privados — sem duplicar a regra entre aviso e resumo.)

`processarResumo(UUID resumoId, LocalDateTime agora)` análogo: deve sair se `usuario.isResumoDiario()`, `agora.toLocalDate().isBefore(resumo.getData())` (passou da meia-noite → `CANCELADO`) e há ≥ 1 sessão `AGENDADA` não excluída no dia (`findByUsuarioAndStatusAndDtConsultaGreaterThanEqualAndDtConsultaLessThanAndDeletedAtIsNullOrderByDtConsultaAsc`, já existente) → `redator.resumo(usuario, dia, sessoes)`.

`ServicoDeAvisos` (`@Service`, **sem** `@Transactional` na classe):

```java
    public void passada() {
        passada(LocalDateTime.now(clock));
    }

    public void passada(LocalDateTime agora) {
        registradora.registrarAvisosNovos(agora);
        registradora.registrarResumos(agora);
        avisoRepository.idsPendentesAte(EstadoAviso.PENDENTE, agora)
                .forEach(id -> isolado(() -> processador.processarAviso(id, agora)));
        resumoRepository.idsPendentesAte(EstadoAviso.PENDENTE, agora)
                .forEach(id -> isolado(() -> processador.processarResumo(id, agora)));
    }
```

`isolado` captura `RuntimeException` e faz `LOG.warn("Falha ao processar aviso {}", id)` com **só o id**. `registradora` é o bean `RegistradorDeAvisos` (`@Component`, métodos `@Transactional public void registrarAvisosNovos(LocalDateTime agora)` e `registrarResumos(LocalDateTime agora)`), injetado no `ServicoDeAvisos` — nunca chamado por `this`: candidatas → `registrarSeNovo(UUID.randomUUID(), consulta.getId(), momento, consulta.getDtConsulta(), agora)`; resumos: se `!agora.toLocalTime().isBefore(LocalTime.of(18, 0))`, `amanha = agora.toLocalDate().plusDays(1)`, para cada `usuariosComResumoEntre(amanha.atStartOfDay(), amanha.plusDays(1).atStartOfDay())` → `registrarSeNovo(...)`.

`AgendadorDeAvisos` (`@Component @ConditionalOnProperty(name = "app.avisos.habilitado", havingValue = "true", matchIfMissing = true)`): `@Scheduled(fixedDelay = 60_000, initialDelay = 60_000) public void executar() { servicoDeAvisos.passada(); }`. `M/config/AgendamentoConfig`: `@Configuration @EnableScheduling @ConditionalOnProperty(` igual `)`.

- [ ] **Step 5:** `sh ./mvnw -q test -Dtest='ServicoDeAvisosTest'` → verde; suíte inteira verde. **Não comite.**

---

### Task 4: Consentimento derivado, descadastro e lembrete na ficha

**Regras exatas:** spec §5.1–§5.3.

- [ ] **Step 1: Testes primeiro.**
  - `T/controller/ConsentimentoHttpTest.java` (`@HttpTest`): `POST /pacientes` com `canalAviso EMAIL` e e-mail → `$.consentimentoAvisoEm` existe; com `NENHUM` → não existe. `PUT` de `NENHUM` para `EMAIL` grava a data; `PUT` mantendo `EMAIL` não muda a data (compare antes/depois); `PUT` para `NENHUM` limpa. Cada mudança gera trilha: `GET /auditoria?entidade=ConsentimentoDoPaciente` tem a linha. `PUT` só com nome não toca o consentimento. `$.descadastradoEm` aparece no response quando preenchido.
  - `T/controller/DescadastroHttpTest.java` (`@HttpTest`, **sem token**): `GET /descadastro/{token}` de paciente autorizado → `{"ativo":true}` e o corpo **não** contém o nome nem o e-mail do paciente; `POST` → `{"ativo":false}`, o paciente fica `canalAviso NENHUM`, `consentimentoAvisoEm` nulo, `descadastradoEm` preenchido, e a trilha do psicólogo tem `DescadastroDoPaciente`; segundo `POST` → 200 `{"ativo":false}` sem segunda linha de trilha; token desconhecido → 404 no envelope; token de paciente excluído → 404.
  - `T/controller/FichaDoPacienteHttpTest.java`: acrescente — aviso `FALHOU` para o horário atual da sessão → `$.linhaDoTempo[i].lembrete` = `FALHOU`; aviso `FALHOU` de horário **antigo** (sessão remarcada) → `lembrete` nulo.

- [ ] **Step 2: Implemente.**
  - `PacienteResponseDTO` ganha `LocalDateTime descadastradoEm` no fim (o mapper mapeia por nome igual; confira o `PacienteMapperImpl` gerado).
  - `PacienteService.cadastrar`: depois do `toEntity` e antes do save, `if (paciente.getCanalAviso() == CanalAviso.EMAIL) paciente.setConsentimentoAvisoEm(LocalDateTime.now());`; depois da auditoria `CRIACAO`, se gravou consentimento, `auditoriaService.registrar(usuario, ATUALIZACAO, "ConsentimentoDoPaciente", id)`.
  - `PacienteService.atualizarPaciente`: guarde `CanalAviso antes = paciente.getCanalAviso()` antes do mapper; depois dele, `boolean ligou = antes != EMAIL && paciente.getCanalAviso() == EMAIL; boolean desligou = antes == EMAIL && paciente.getCanalAviso() != EMAIL;` ligou → data agora; desligou → nulo; após o save e a auditoria `ATUALIZACAO "Paciente"` existente, se `ligou || desligou` audite `"ConsentimentoDoPaciente"`. **Não** altere os Javadocs existentes do método.
  - `PacienteRepository`: `Optional<Paciente> findByTokenDescadastroAndDeletedAtIsNull(String tokenDescadastro);`
  - `DescadastroDTO(boolean ativo)`; `DescadastroService` (`consultar(token)`, `@Transactional descadastrar(token)`; `PacienteNaoEncontradoException("Link invalido")` para 404; ativo = `consentimentoAvisoEm != null && canalAviso == EMAIL`; descadastrar só escreve quando ativo: `canalAviso = NENHUM`, `consentimentoAvisoEm = null`, `descadastradoEm = LocalDateTime.now()`, save, `auditoriaService.registrar(paciente.getUsuario(), ATUALIZACAO, "DescadastroDoPaciente", paciente.getId())`).
  - `DescadastroController` `@RequestMapping("/descadastro")`: `@GetMapping("/{token}")`, `@PostMapping("/{token}")`, `ResponseEntity.ok(...)`.
  - `SecurityConfiguration`: acrescente `.requestMatchers("/descadastro/**").permitAll()` logo depois da linha `.requestMatchers("/auth/**").permitAll()`. Nada mais no arquivo.
  - Ficha: `AvisoDeConsultaRepository.findByConsultaInAndDestinatario(List<Consulta> consultas, Destinatario destinatario)`; em `FichaService`, com a lista de consultas **não vazia** (guarda contra `IN ()`), monte `Map<UUID, EstadoAviso>` com o aviso cujo `dataHoraConsulta` é igual ao `dtConsulta` atual da consulta; `SessaoDaFichaDTO` ganha `EstadoAviso lembrete` no fim; atualize o `new SessaoDaFichaDTO(...)`.

- [ ] **Step 3:** testes focados e suíte inteira verdes. **Não comite.**

---

### Task 5: API de Ajustes

**Regras exatas:** spec §6.1 (só `GET`/`PUT /ajustes`; a senha é do 4B).

- [ ] **Step 1: Testes** `T/controller/AjustesHttpTest.java` (`@HttpTest`): `GET` devolve os padrões de um usuário novo (`antecedencia H24`, `blocosAviso ["REMARCAR"]`, `tituloNoEmail false`, `resumoDiario true`, expediente nulo, `email` do usuário); `PUT` completo persiste e o `GET` seguinte devolve igual; `PUT` com `tituloNoEmail true` e título em branco → 400 campo `tituloProfissional` com `Informe o título profissional para incluí-lo no e-mail.`; título com 81 caracteres → 400; só `expedienteInicio` → 400 campo `expedienteInicio` com `Informe o início e o fim do expediente, ou nenhum dos dois.`; `18`/`8` → 400 campo `expedienteFim` com `O fim do expediente deve ser depois do início.`; `24` no início → 400 (`O início do expediente deve estar entre 0 e 23.`); `0` no fim → 400 (`O fim do expediente deve estar entre 1 e 24.`); nome com 1 caractere → 400; sem `antecedencia` → 400; `valorPadraoSessao 0` → 400 com a mensagem de valor; `PUT` audita `ATUALIZACAO "Ajustes"`; sem token → 401. Acrescente a `OpenApiHttpTest` que `/ajustes` existe.

- [ ] **Step 2: Implemente.**
  - `AjustesResponseDTO(String nome, String email, BigDecimal valorPadraoSessao, Antecedencia antecedencia, Set<BlocoAviso> blocosAviso, boolean tituloNoEmail, String tituloProfissional, boolean resumoDiario, Integer expedienteInicio, Integer expedienteFim)`.
  - `AjustesAtualizacaoDTO` (mesmos campos sem `email`): `nome` `@NotBlank(message = "Informe seu nome.") @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres.")`; `valorPadraoSessao` com `@DecimalMin`/`@Digits`/`@Schema` copiados de `ValorDaConsultaDTO` **sem** `@NotNull`; `antecedencia` `@NotNull(message = "Escolha a antecedência do aviso.")`; `tituloProfissional` `@Size(max = 80, message = "O título deve ter no máximo 80 caracteres.")`; `expedienteInicio` `@Min(value = 0, message = "O início do expediente deve estar entre 0 e 23.") @Max(value = 23, ...)`; `expedienteFim` `@Min(1, "O fim do expediente deve estar entre 1 e 24.") @Max(24, ...)`; `blocosAviso` nulo = conjunto vazio.
  - `CampoInvalidoException(String campo, String mensagem)` + handler 400 no mesmo formato de `PeriodoInvalidoException` (lista com um `ErroCampoDTO`).
  - `AjustesService`: `obter()` e `@Transactional atualizar(dto)`; regras cruzadas no serviço **antes** de mutar: título obrigatório com a caixa ligada (campo `tituloProfissional`); expediente: um só preenchido → campo do que falta; fim ≤ início → campo `expedienteFim`. Persistir via `usuarioRepository.save(usuario)` e auditar `ATUALIZACAO "Ajustes"` com `usuario.getId()`. `tituloProfissional` em branco grava nulo.
  - `AjustesController` `@RequestMapping("/ajustes")` com `GET` e `PUT` (`@RequestBody @Valid`).

- [ ] **Step 3:** testes focados e suíte inteira verdes. **Não comite.**

---

### Task 6: Regerar o contrato (orquestrador)

Mesmo procedimento da Fatia 3: API na **8090** (a 8080 da máquina é de outro servidor) com o JWT de teste do `HttpTest`, `curl` para `frontend/contrato-openapi.json`, `servers.url` normalizado para `http://localhost:8080`, `npm run tipos`, conferir `AjustesResponseDTO`, `AjustesAtualizacaoDTO`, `DescadastroDTO`, `descadastradoEm`, `lembrete`, `/ajustes`, `/descadastro/{token}`. Derrubar a API.

---

### Task 7: Front — caixa de consentimento e marcas na ficha

**Regras e cópia exatas:** spec §5.1 e §5.3.

- [ ] **Step 1:** `esquema.ts` de pacientes: o campo `canalAviso` vira `autorizaAviso: z.boolean()`; a regra condicional passa a ser `v => !v.autorizaAviso || v.email !== ''` com a **mesma** mensagem (`Informe o e-mail do paciente para avisar por e-mail.`, path `email`). `queries.ts` (`useSalvarPaciente`): o corpo manda `canalAviso: valores.autorizaAviso ? 'EMAIL' : 'NENHUM'`. `DialogoDePaciente.tsx`: o bloco do `<select>` "Canal de aviso" sai; entra `<input type="checkbox">` com `<label>` `Paciente autorizou receber lembretes por e-mail`; `defaultValues.autorizaAviso = paciente?.canalAviso === 'EMAIL'`; quando `paciente?.descadastradoEm`, abaixo da caixa um `<p className="sub">` com `Este paciente parou de receber lembretes pelo link do e-mail em {dd/mm/aaaa}. Marque só se ele autorizou de novo.` (`dd/mm/aaaa` via `toLocaleDateString('pt-BR')` de `deApi`). O tipo `Paciente` ganha `descadastradoEm: ... | null`.
- [ ] **Step 2:** Testes: atualize `esquema.test.ts` e `DialogoDePaciente.test.tsx` (os casos de canal viram casos da caixa: marcada sem e-mail recusa antes do envio; desmarcada sem e-mail passa; marcada manda `canalAviso: 'EMAIL'`; desmarcada manda `NENHUM`; edição de paciente `EMAIL` abre marcada; paciente com `descadastradoEm` mostra o aviso). Mantenha as demais asserções.
- [ ] **Step 3:** Ficha: `FichaPage.tsx` — o chip do cabeçalho: autorizado → como hoje; senão, se `descadastradoEm` → `<Chip variante="alerta">Descadastrou-se em {dd/mm}</Chip>`; senão como hoje. `LinhaDoTempo.tsx`/`queries.ts`: `SessaoDaFicha` ganha `lembrete: ... | null`; quando `lembrete === 'FALHOU'`, chip `variante="alerta"` `Lembrete não enviado` no `acoes-linha`. Testes em `FichaPage.test.tsx` para os três estados do chip e o lembrete.
- [ ] **Step 4:** `npx vitest run` e `npx tsc --noEmit -p .` (com o prefixo) verdes. **Não comite.**

---

### Task 8: Front — página pública de descadastro

**Cópia exata:** spec §5.2.

- [ ] **Step 1:** `telas/descadastro/queries.ts`: `useDescadastro(token)` (`GET /descadastro/{token}`, chave `['descadastro', token]`, `retry: false`) e `useConfirmarDescadastro(token)` (`POST`; no sucesso grava `{ativo:false}` no cache da chave). `DescadastroPage.tsx`: `<main className="tela-login">` + cartão (`cartao cartao-login`), `<h1>Lembretes de agendamento</h1>`; estados: carregando `Carregando…`; ativo → texto + `<Botao>` `Parar de receber lembretes` (trava em voo); inativo/depois do POST → `Pronto. Você não vai mais receber lembretes por e-mail.` em `role="status"`; `ErroDaApi` 404 → `Este link não é mais válido.`; outro erro → `Não foi possível carregar. Tente de novo em instantes.` em `role="alert"`.
- [ ] **Step 2:** `rotas.tsx`: `<Route path="/descadastro/:token" element={<DescadastroPage />} />` **fora** da `RotaProtegida`, ao lado de `/entrar`. `vite.config.ts`: acrescente `'/descadastro'` a `CAMINHOS_DA_API` (o `bypass` existente já devolve o `index.html` para navegação de documento).
- [ ] **Step 3:** Testes `DescadastroPage.test.tsx` (monte `<Routes><Route path="/descadastro/:token" .../></Routes>` com `renderizar(..., { rota: '/descadastro/<token>' })`): os quatro estados, o POST enviado ao clicar, nenhuma chamada a rota autenticada. `vitest` e `tsc` verdes. **Não comite.**

---

### Task 9: Front — tela Ajustes (perfil, sessão, aviso, prévia)

**Cópia e estrutura exatas:** spec §6.2, **só** seções 1 (sem o botão `Trocar senha`), 2, 4 e 5. Expediente, senha e trilha são do 4B — não crie placeholders.

- [ ] **Step 1:** `telas/ajustes/queries.ts`: tipo `Ajustes` (container à mão do contrato, nuláveis `| null`), `useAjustes()` (chave `['ajustes']`), `useSalvarAjustes()` (`PUT /ajustes`, `onSuccess` invalida `['ajustes']`). `esquema.ts`: zod do formulário (`nome` 2–100, `valorPadraoSessao` igual ao de pacientes, `antecedencia` enum, `blocosAviso` array, `tituloNoEmail` boolean, `tituloProfissional` máx. 80 e obrigatório com a caixa ligada — mesmas mensagens da spec, `resumoDiario` boolean). O `PUT` manda também `expedienteInicio`/`expedienteFim` **como vieram do `GET`** (o 4B põe os campos na tela).
- [ ] **Step 2:** `previa.ts` — função pura `montarPrevia(dados, exemplo)` que devolve `{ de, assunto, corpo }` com **exatamente** o texto da spec §4.3, exemplo fixo `Ana`, data de amanhã às `14:00` (receba a data por parâmetro para o teste ser determinístico). `previa.test.ts` cobre: blocos ligados/desligados, título ligado/desligado/em branco, assunto fixo, e que o corpo da prévia é idêntico ao do backend para o mesmo caso (use o caso do `RedatorDeEmailTest` 1 como fixture e compare string a string).
- [ ] **Step 3:** `AjustesPage.tsx` com `useFormularioDoProduto`, as seções `Perfil`, `Sessão`, `Aviso de consulta`, `Como o aviso chega`, botão `Salvar ajustes`, anúncio `Ajustes salvos.` em `role="status"`; a prévia lê `watch()` e muda sem salvar; `Título profissional` desabilitado com a caixa desmarcada. `rotas.tsx`: `/ajustes` aponta para `AjustesPage` (sai o `AindaNaoNestaFatia` — remova a função se ficar sem uso). CSS novo no fim de `estilo.css`, só tokens, sem comentário.
- [ ] **Step 4:** `AjustesPage.test.tsx`: carrega e preenche do `GET`; salvar manda o `PUT` com o corpo esperado; erros de campo do servidor pousam no campo; título obrigatório com a caixa ligada (cliente); a prévia muda ao marcar uma frase e ao ligar o título; assunto da prévia é sempre `Lembrete de agendamento`. `vitest` e `tsc` verdes. **Não comite.**

---

### Task 10: e2e do aviso e documentação

- [ ] **Step 1:** `frontend/e2e/fluxo-do-aviso.spec.ts` (molde dos outros, sem comentários): registra e entra; Ajustes → muda antecedência para `2 horas antes`, liga `Chegue com alguns minutos de antecedência.`, salva, vê `Ajustes salvos.`; sai para Hoje e volta a Ajustes → os valores persistiram e a prévia mostra a frase; Pacientes → `+ Novo paciente` com nome, e-mail e a caixa `Paciente autorizou receber lembretes por e-mail` marcada → a ficha mostra `Aviso autorizado`. Rode com a API do orquestrador: `API_URL=http://localhost:8090 ASDF_NODEJS_VERSION=20.19.1 npx playwright test` (os quatro fluxos).
- [ ] **Step 2:** Docs — só o que a fatia torna falso ou novo: tabelas de telas/rotas (Ajustes, `/descadastro/:token`), endpoints novos, variáveis de ambiente novas (`APP_EMAIL_REMETENTE`, `APP_FRONTEND_URL`, `APP_AVISOS_HABILITADO`, `SPRING_MAIL_*`), o job e a trava no `ai/Claude.md` (seção Banco: a V2 existe; migrations agora são duas), contagens de teste atuais, e2e com 4 fluxos. Não toque na linha 1 de `ai/Claude.md`.
- [ ] **Step 3:** `sh ./mvnw -q test`, `npx vitest run`, `npm run build`, e2e — tudo verde. **Não comite.**
