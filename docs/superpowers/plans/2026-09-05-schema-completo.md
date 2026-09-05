# Schema completo (§5) — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trazer o modelo inteiro da §5 para dentro da migration única e das entidades, antes de qualquer fatia começar, com a suíte do backend verde.

**Architecture:** Uma tarefa por tabela. A migration `V1__20260901.sql` é reescrita em pedaços — não é uma V2: a janela ainda está aberta, nenhum banco de produção existe, e a spec manda o schema entrar de uma vez. Cada tarefa move junto a fatia de migration, a entidade, o repositório, os DTOs afetados e os testes, porque `ddl-auto` não está configurado (padrão `none` com Flyway presente): **o Hibernate não valida schema contra entidade**, então uma coluna removida no banco com a entidade ainda mapeando-a não falha no boot — falha no primeiro `SELECT`, dentro de outro teste, longe da causa.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Lombok, Flyway, JUnit 5, Testcontainers (Postgres), AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-01-redesenho-produto-design.md` — §5 inteira, mais §3 (tabela de decisões) e §7.1 (por que o aviso não tem corpo).

## Global Constraints

- **A migration é editada, não versionada.** Nada de `V2__`. A `V1__20260901.sql` é reescrita no lugar. O comentário no topo dela explica por quê e **continua valendo** — não apague. Isto para no primeiro deploy.
- **A suíte inteira fica verde ao fim de cada tarefa.** São ~170 testes hoje. `./mvnw test` no diretório `backend/`.
- **Isolamento por usuário é invariante.** Toda consulta de repositório filtra pelo usuário dono, direta (`id_usuario`) ou indiretamente (`paciente.usuario`). Nenhuma tarefa pode afrouxar isso; há teste dedicado para várias entidades e ele não pode ser relaxado para passar.
- **Exclusão lógica onde já existe fica.** `deleted_at_*` continua em paciente, consulta, anotação e na tabela que hoje é `lembrete`. `log_auditoria` continua sem, de propósito.
- **Texto de erro em um só registro** (§6.3): frase completa, com acento, dizendo o que fazer. `"Informe o nome do paciente."`, nunca `"campo obrigatorio"`. Vale para toda mensagem que esta tarefa tocar, mesmo as que já existiam erradas.
- **`AvisoDeConsulta` não tem campo de corpo de mensagem** (§5.2/§7.1). Se aparecer um, a garantia de privacidade sai do produto.
- **NÃO COMMITAR.** O dono do repositório commita. Nenhum `git add`, `git commit`, `git stash`, `git checkout` em tarefa nenhuma.

---

## File Structure

**Migration** — `backend/src/main/resources/db/migration/V1__20260901.sql`, reescrita em pedaços pelas tarefas 1 a 6.

**Entidades** (`model/`): `Paciente`, `Consulta`, `Anotacao`, `Usuario` modificadas; `Lembrete` renomeada para `Pendencia`; `AvisoDeConsulta` criada. Enums novos: `CanalAviso`, `Destinatario`, `EstadoAviso`, `Antecedencia`, `BlocoAviso`.

**Repositórios** (`repository/`): `LembreteRepository` → `PendenciaRepository`; `AvisoDeConsultaRepository` criado.

**Camada de serviço/controller**: `LembreteService`/`LembreteController`/`LembreteAcoesController` e os DTOs de lembrete acompanham o rename. `PacienteCadastroDTO` perde o `@NotNull`.

**Testes**: `SchemaMigrationTest` cresce a cada tarefa — é o guarda-costas do schema. Os testes de lembrete acompanham o rename.

Onde o plano diz "renomear", entenda **renomear de verdade** (arquivo, classe, tabela, coluna), não criar cópia e deixar a antiga.

---

## O ciclo de cada tarefa

1. Escrever o teste que falha
2. Rodar e ver falhar **pelo motivo certo** (coluna não existe, não "NullPointerException em outro lugar")
3. Implementar o mínimo
4. `./mvnw test` — a suíte **inteira**, não só o teste novo
5. Relatar. **Não commitar.**

**Rodar teste isolado:** `./mvnw test -Dtest=NomeDaClasseTest` no diretório `backend/`.

**O auxiliar `existeTabela`** é criado na Task 4, dentro de `SchemaMigrationTest`, e reusado pelas Tasks 5 e 6. Se você está executando fora de ordem e ele não existe, o código dele está no Step 1 da Task 4.

**Testcontainers precisa de Docker no ar.** Se o daemon não estiver rodando, os testes de repositório falham com erro de conexão — isso é ambiente, não código. Confirme antes de investigar.

---

### Task 1: `Paciente` — o que sai e o que entra

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__20260901.sql` (bloco `CREATE TABLE paciente`)
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/model/Paciente.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/model/CanalAviso.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/paciente/PacienteCadastroDTO.java`
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/repository/SchemaMigrationTest.java`

**Interfaces:**
- Produces: `CanalAviso { EMAIL, WHATSAPP, NENHUM }`; `Paciente.valorSessao` (`BigDecimal`), `Paciente.consentimentoAvisoEm` (`LocalDateTime`), `Paciente.canalAviso` (`CanalAviso`), `Paciente.tokenDescadastro` (`String`). A Task 5 (`AvisoDeConsulta`) e a Fatia 4 consomem os quatro.

Quatro campos entram (§5.4), um sai (§5.8), e uma validação afrouxa (§5.8).

O **token de descadastro** merece cuidado: §7.1 exige que ele seja opaco — sem relação com o id do paciente — porque link de e-mail vaza em encaminhamento e em log de proxy. Ele nasce com o paciente e nunca muda; revogar consentimento grava `consentimentoAvisoEm = null` e **não** mexe no token, para que um link antigo continue funcionando.

- [ ] **Step 1: Escrever o teste que falha**

Em `SchemaMigrationTest`, acrescente:

```java
    @Test
    @DisplayName("paciente tem os campos de aviso e de valor da sessao (spec 5.4)")
    void pacienteTemCamposDeAvisoEValor() {
        assertThat(existeColuna("paciente", "valor_sessao_paciente")).isTrue();
        assertThat(existeColuna("paciente", "consentimento_aviso_em_paciente")).isTrue();
        assertThat(existeColuna("paciente", "canal_aviso_paciente")).isTrue();
        assertThat(existeColuna("paciente", "token_descadastro_paciente")).isTrue();
    }

    @Test
    @DisplayName("observacoes_paciente saiu do schema (spec 5.8, decisao do dono)")
    void observacoesPacienteNaoExisteMais() {
        assertThat(existeColuna("paciente", "observacoes_paciente")).isFalse();
    }

    @Test
    @DisplayName("token de descadastro e unico: link de e-mail nao pode servir a dois pacientes")
    void tokenDescadastroEUnico() {
        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name
                WHERE tc.table_name = 'paciente'
                  AND tc.constraint_type = 'UNIQUE'
                  AND ccu.column_name = 'token_descadastro_paciente'
                """, Integer.class);
        assertThat(total).isEqualTo(1);
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=SchemaMigrationTest`
Expected: os três novos falham — `existeColuna` devolve `false` para os quatro campos novos, `true` para `observacoes_paciente`, e a contagem de constraint dá 0.

- [ ] **Step 3: Reescrever o bloco `paciente` da migration**

Substitua o `CREATE TABLE paciente` inteiro por:

```sql
CREATE TABLE paciente (
    id_paciente UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,

    nm_paciente VARCHAR(100) NOT NULL,
    telefone_paciente VARCHAR(20),
    email_paciente VARCHAR(100),
    dt_nascimento_paciente DATE,
    -- valor padrao desta pessoa; a consulta COPIA no agendamento (5.3), nao
    -- referencia. Nulo = usa o valor padrao do usuario (5.5)
    valor_sessao_paciente NUMERIC(10,2),
    -- consentimento do aviso: data em que o psicologo declarou ter obtido.
    -- Nulo = sem consentimento, e entao nenhum aviso sai para esta pessoa
    consentimento_aviso_em_paciente TIMESTAMP,
    canal_aviso_paciente VARCHAR(20) NOT NULL DEFAULT 'NENHUM',
    -- opaco, sem relacao com o id: link de e-mail vaza em encaminhamento e em
    -- log de proxy (7.1). Estavel por paciente, sobrevive a remarcacao, e
    -- revogar consentimento NAO o troca — link antigo continua funcionando
    token_descadastro_paciente VARCHAR(64) NOT NULL UNIQUE,
    created_at_paciente TIMESTAMP NOT NULL,
    -- exclusao reversivel: o registro sai das consultas da aplicacao, mas
    -- continua no banco e na trilha de auditoria
    deleted_at_paciente TIMESTAMP,

    CONSTRAINT fk_paciente_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario)
);
```

`observacoes_paciente` não aparece: é a decisão do dono em §5.8.

- [ ] **Step 4: Criar o enum `CanalAviso`**

```java
package com.psychologyapi.psychologyapi.model;

/** Por onde o aviso de consulta chega ao paciente. WHATSAPP e da fase 2
 *  (spec 3); existe aqui porque o schema fecha antes das fatias. */
public enum CanalAviso {
    EMAIL,
    WHATSAPP,
    NENHUM
}
```

- [ ] **Step 5: Atualizar a entidade `Paciente`**

Remova o campo `observacoesPaciente` e sua `@Column`. Acrescente:

```java
    @Column(name = "valor_sessao_paciente", nullable = true, precision = 10, scale = 2)
    private BigDecimal valorSessao;

    @Column(name = "consentimento_aviso_em_paciente", nullable = true)
    private LocalDateTime consentimentoAvisoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_aviso_paciente", nullable = false, length = 20)
    private CanalAviso canalAviso;

    @Column(name = "token_descadastro_paciente", nullable = false, unique = true, length = 64)
    private String tokenDescadastro;
```

E no `prePersist` já existente, acrescente as duas linhas de nascimento do registro:

```java
    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.canalAviso == null) {
            this.canalAviso = CanalAviso.NENHUM;
        }
        if (this.tokenDescadastro == null) {
            // 32 bytes de aleatoriedade criptografica em Base64 URL-safe: cabe
            // num link sem escape e nao e adivinhavel a partir do id
            byte[] bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            this.tokenDescadastro = java.util.Base64.getUrlEncoder()
                    .withoutPadding().encodeToString(bytes);
        }
    }
```

Importe `java.math.BigDecimal`, `jakarta.persistence.Enumerated` e `jakarta.persistence.EnumType` se ainda não estiverem.

- [ ] **Step 6: Afrouxar a data de nascimento no DTO**

Decisão do dono (§5.8): a entidade vence, é opcional. Em `PacienteCadastroDTO`, tire o `@NotNull` e corrija as duas mensagens (§6.1 reclama das duas: `"campo obrigatorio"` é o registro amador, e a do `@Past` diz "válida" quando valida "passada"):

```java
public record PacienteCadastroDTO(
        @NotBlank(message = "Informe o nome do paciente.") String nome,
        String telefone,
        String email,
        @Past(message = "A data de nascimento deve estar no passado.") LocalDate dataNascimento) {
}
```

O import de `NotNull` fica sem uso — remova.

- [ ] **Step 7: Rodar a suíte inteira**

Run: `./mvnw test`
Expected: tudo verde.

Atenção ao que pode quebrar aqui, e por quê: algum teste de paciente pode enviar cadastro **sem** data de nascimento esperando **400**. Esse teste agora está errado — a regra mudou por decisão do dono. Corrija o teste para esperar **201**, e não o contrário. Se algum teste referenciar `observacoesPaciente`, remova a referência.

- [ ] **Step 8: Relatar. NÃO COMMITAR.**

---

### Task 2: `Consulta` ganha dinheiro

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__20260901.sql` (bloco `CREATE TABLE consulta`)
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/model/Consulta.java`
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/repository/SchemaMigrationTest.java`

**Interfaces:**
- Produces: `Consulta.valor` (`BigDecimal`), `Consulta.pagoEm` (`LocalDateTime`). A Fatia 3 inteira consome os dois.

Dois campos (§5.3). O que pesa aqui não é o schema, é a regra que vem junto — e ela **não** se implementa nesta tarefa, só se documenta para não ser esquecida.

**`valor` é cópia, não referência.** Se a consulta apontasse para o valor do paciente, reajustar de R$150 para R$200 reescreveria o histórico faturado inteiro. Snapshot no agendamento.

**`pagoEm = null` não significa "a receber" sozinho.** Só significa isso quando `status = REALIZADA` — falta não é cobrada (§5.3, decisão do dono). Qualquer agregação que filtre só por `pagoEm` conta falta como dívida. Isso é da Fatia 3; aqui vai só o comentário na entidade, para quem chegar lá.

- [ ] **Step 1: Escrever o teste que falha**

```java
    @Test
    @DisplayName("consulta tem valor e pago_em (spec 5.3)")
    void consultaTemDinheiro() {
        assertThat(existeColuna("consulta", "valor_consulta")).isTrue();
        assertThat(existeColuna("consulta", "pago_em_consulta")).isTrue();
    }

    @Test
    @DisplayName("pago_em e anulavel: consulta nasce sem baixa")
    void pagoEmEAnulavel() {
        assertThat(colunaEAnulavel("consulta", "pago_em_consulta")).isTrue();
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=SchemaMigrationTest`
Expected: os dois falham por coluna inexistente.

- [ ] **Step 3: Acrescentar as colunas na migration**

No `CREATE TABLE consulta`, depois de `status_consulta`:

```sql
    -- COPIA do valor vigente no agendamento, nao referencia ao paciente:
    -- reajustar a sessao de 150 para 200 nao pode reescrever o historico
    -- faturado (5.3). Nulo em consulta antiga, anterior a esta regra
    valor_consulta NUMERIC(10,2),
    -- data da baixa. Nulo NAO significa "a receber" sozinho: so significa
    -- isso quando status_consulta = 'REALIZADA', porque falta nao e cobrada
    -- (5.3, decisao do dono). Agregacao que filtre so por esta coluna conta
    -- falta como divida
    pago_em_consulta TIMESTAMP,
```

- [ ] **Step 4: Acrescentar os campos na entidade**

```java
    @Column(name = "valor_consulta", nullable = true, precision = 10, scale = 2)
    private BigDecimal valor;

    /** Data da baixa. Nulo so significa "a receber" quando o status e
     *  REALIZADA — falta nao e cobrada (spec 5.3). Quem somar dinheiro
     *  filtrando so por este campo conta falta como divida. */
    @Column(name = "pago_em_consulta", nullable = true)
    private LocalDateTime pagoEm;
```

Importe `java.math.BigDecimal`.

- [ ] **Step 5: Rodar a suíte inteira**

Run: `./mvnw test`
Expected: tudo verde. Nada consome os campos ainda, então nenhum teste existente deve reagir.

- [ ] **Step 6: Relatar. NÃO COMMITAR.**

---

### Task 3: `Anotacao` liga à `Consulta`

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__20260901.sql` (bloco `CREATE TABLE anotacao`)
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/model/Anotacao.java`
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/repository/SchemaMigrationTest.java`

**Interfaces:**
- Produces: `Anotacao.consulta` (`Consulta`, nullable). A Fatia 2 monta a linha do tempo da ficha em cima disto.

Uma coluna, e é a causa estrutural do problema que originou o redesenho (§5.1).

Hoje a anotação liga a `Usuario` + `Paciente`, e `createdAt` é **quando foi digitada**, não quando a sessão aconteceu. Quem registrou a terça na quarta de manhã quebra a correlação: a ficha não consegue dizer qual anotação é de qual encontro.

**Nullable de propósito.** Anotação avulsa ("liguei fora do horário") continua válida; só não entra na linha do tempo.

- [ ] **Step 1: Escrever o teste que falha**

```java
    @Test
    @DisplayName("anotacao aponta para a consulta, e o vinculo e opcional (spec 5.1)")
    void anotacaoLigaAConsulta() {
        assertThat(existeColuna("anotacao", "id_consulta")).isTrue();
        assertThat(colunaEAnulavel("anotacao", "id_consulta")).isTrue();
    }

    @Test
    @DisplayName("o vinculo anotacao->consulta e chave estrangeira de verdade")
    void anotacaoTemFkParaConsulta() {
        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_name = 'anotacao'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name = 'fk_anotacao_consulta'
                """, Integer.class);
        assertThat(total).isEqualTo(1);
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=SchemaMigrationTest`
Expected: coluna e constraint inexistentes.

- [ ] **Step 3: Migration**

No `CREATE TABLE anotacao`, depois de `id_paciente`:

```sql
    -- a anotacao passa a ser DAQUELA SESSAO, nao do paciente em geral (5.1).
    -- Anulavel de proposito: anotacao avulsa ("liguei fora do horario")
    -- continua valida, so nao entra na linha do tempo da ficha
    id_consulta UUID,
```

E, junto das outras constraints da tabela:

```sql
    CONSTRAINT fk_anotacao_consulta
        FOREIGN KEY (id_consulta)
        REFERENCES consulta(id_consulta),
```

Acrescente também o índice, porque a ficha vai buscar anotação por consulta a cada abertura:

```sql
CREATE INDEX idx_anotacao_consulta ON anotacao (id_consulta);
```

- [ ] **Step 4: Entidade**

```java
    /** A sessao a que esta anotacao pertence. Nulo = anotacao avulsa: vale,
     *  mas nao entra na linha do tempo da ficha (spec 5.1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = true)
    private Consulta consulta;
```

- [ ] **Step 5: Rodar a suíte inteira**

Run: `./mvnw test`
Expected: verde. `AnotacaoCadastroDTO` **não** ganha o campo nesta tarefa — quem preenche o vínculo é a Fatia 2, junto com a tela que o produz.

- [ ] **Step 6: Relatar. NÃO COMMITAR.**

---

### Task 4: `Lembrete` vira `Pendencia`

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__20260901.sql` (bloco `CREATE TABLE lembrete`)
- Rename: `model/Lembrete.java` → `model/Pendencia.java`
- Rename: `repository/LembreteRepository.java` → `repository/PendenciaRepository.java`
- Rename: `service/LembreteService.java` → `service/PendenciaService.java`
- Rename: `controller/LembreteController.java` → `controller/PendenciaController.java`
- Rename: `controller/LembreteAcoesController.java` → `controller/PendenciaAcoesController.java`
- Rename: `controller/dto/lembrete/LembreteCadastroDTO.java` → `controller/dto/pendencia/PendenciaCadastroDTO.java`
- Rename: `controller/dto/lembrete/LembreteResponseDTO.java` → `controller/dto/pendencia/PendenciaResponseDTO.java`
- Rename: `mapper/LembreteMapper.java` → `mapper/PendenciaMapper.java`
- Rename: `exceptions/LembreteNaoEncontradoException.java` → `exceptions/PendenciaNaoEncontradaException.java`
- Modify: `controller/common/GlobalExceptionsHandler.java` (a exceção renomeada)
- Rename os testes correspondentes: `LembreteRepositoryTest`, `LembreteServiceTest`, e o que houver em `Fixtures.java`

**Interfaces:**
- Produces: `Pendencia` (entidade), `PendenciaRepository` com `findByIdAndPaciente_UsuarioAndDeletedAtIsNull(UUID, Usuario)` e `findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(Paciente, Usuario, Pageable)` — **mesmas assinaturas**, só o tipo muda.

Uma entidade fazendo dois trabalhos que não se parecem é a raiz do "lembrete não lembra nada" (§5.2). O que existe hoje — descrição, concluído, sem data, do paciente — **é** pendência clínica. Serve, fica, e só troca de nome. O que não existe é o aviso agendado, e ele é a Task 5.

**Isto é rename, não redesenho.** Nenhum campo entra, nenhum sai, nenhuma regra muda.

**Não acrescente `id_usuario` a esta tabela.** O isolamento já é correto por `paciente.usuario`, existe teste que prova, e a §5.2 não pede a coluna. Denormalizar aqui só criaria um segundo lugar para o isolamento discordar de si mesmo.

**A URL da API muda junto?** Sim: `/pacientes/{id}/lembretes` vira `/pacientes/{id}/pendencias`. Nenhum cliente em produção existe, e o front vai ser reescrito do zero — carregar o nome velho na URL seria carregar para sempre a confusão que esta tarefa desfaz.

- [ ] **Step 1: Escrever o teste que falha**

```java
    @Test
    @DisplayName("a tabela chama-se pendencia; lembrete nao existe mais (spec 5.2)")
    void lembreteViroupendencia() {
        assertThat(existeTabela("pendencia")).isTrue();
        assertThat(existeTabela("lembrete")).isFalse();
    }

    @Test
    @DisplayName("pendencia mantem exclusao logica anulavel")
    void pendenciaTemSoftDelete() {
        assertThat(existeColuna("pendencia", "deleted_at_pendencia")).isTrue();
        assertThat(colunaEAnulavel("pendencia", "deleted_at_pendencia")).isTrue();
    }
```

Acrescente o auxiliar, ao lado de `existeColuna`:

```java
    private boolean existeTabela(String tabela) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tabela);
        return total != null && total > 0;
    }
```

E **corrija os testes existentes** `colunasDeSoftDeleteExistemNoSchema` e `colunasDeSoftDeleteSaoAnulaveis`, que hoje afirmam `lembrete`/`deleted_at_lembrete` — passam a afirmar `pendencia`/`deleted_at_pendencia`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=SchemaMigrationTest`
Expected: `existeTabela("pendencia")` é `false`, `existeTabela("lembrete")` é `true` — exatamente invertido.

- [ ] **Step 3: Migration**

Substitua o `CREATE TABLE lembrete` por:

```sql
-- Pendencia clinica do paciente: sem data, sem envio, sem destinatario. E o
-- que a antiga tabela "lembrete" de fato era. O aviso agendado — a outra
-- metade do que "lembrete" tentava ser — e a tabela aviso_de_consulta (5.2)
CREATE TABLE pendencia (
    id_pendencia UUID PRIMARY KEY,
    id_paciente UUID NOT NULL,

    descricao TEXT NOT NULL,
    concluido BOOLEAN NOT NULL DEFAULT false,
    created_at_pendencia TIMESTAMP NOT NULL,
    deleted_at_pendencia TIMESTAMP,

    CONSTRAINT fk_pendencia_paciente
        FOREIGN KEY (id_paciente)
        REFERENCES paciente(id_paciente)
);
```

- [ ] **Step 4: Renomear a entidade**

`model/Pendencia.java`, com `@Table(name = "pendencia", schema = "public")`, `@Column(name = "id_pendencia")`, `created_at_pendencia`, `deleted_at_pendencia`. Os campos `descricao` e `concluido` não mudam de nome de coluna.

Acrescente o javadoc que evita a próxima confusão:

```java
/** Pendencia clinica do paciente — sem data, sem envio (spec 5.2). Nao
 *  confundir com AvisoDeConsulta, que e mensagem agendada presa a uma
 *  Consulta. Uma entidade so, tentando ser as duas, foi a raiz do
 *  "lembrete nao lembra nada". */
```

Em `Paciente`, o `@OneToMany(mappedBy = "paciente") private List<Lembrete> lembretes;` vira `private List<Pendencia> pendencias;`.

- [ ] **Step 5: Renomear o resto da pilha**

Repositório, serviço, os dois controllers, os dois DTOs (pacote `dto/pendencia/`), o mapper e a exceção. Registre a exceção renomeada no `GlobalExceptionsHandler`.

Mecânico, com dois cuidados:
- as assinaturas do repositório continuam iguais (`findByIdAndPaciente_UsuarioAndDeletedAtIsNull`, `findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc`) — só o tipo muda. Derivação de query do Spring Data lê o **nome do método**; mudar uma letra além do tipo quebra em tempo de execução, não de compilação.
- o caminho `@RequestMapping` passa a `pendencias`.

- [ ] **Step 6: Renomear os testes**

`LembreteRepositoryTest` → `PendenciaRepositoryTest`, `LembreteServiceTest` → `PendenciaServiceTest`, e o que houver em `Fixtures.java`. **O teste de isolamento entre usuários fica** — é ele que prova que a §5.2 não afrouxou nada.

- [ ] **Step 7: Rodar a suíte inteira**

Run: `./mvnw test`
Expected: verde, com a mesma contagem de testes de antes (nenhum some no rename).

Se algum teste HTTP bater em `/lembretes`, atualize a URL. Se sobrar qualquer ocorrência da palavra, ache com:

```bash
grep -rn --include=*.java -i "lembrete" backend/src
```

Espere zero. Se sobrar em comentário histórico explicando a mudança, tudo bem — em código, não.

- [ ] **Step 8: Relatar. NÃO COMMITAR.**

---

### Task 5: `AvisoDeConsulta` nasce

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__20260901.sql`
- Create: `model/AvisoDeConsulta.java`, `model/Destinatario.java`, `model/EstadoAviso.java`
- Create: `repository/AvisoDeConsultaRepository.java`
- Test: `SchemaMigrationTest.java`, e novo `repository/AvisoDeConsultaRepositoryTest.java`

**Interfaces:**
- Produces: `AvisoDeConsulta` com `consulta`, `destinatario`, `canal`, `agendadoPara`, `enviadoEm`, `estado`, `erro`; `Destinatario { PSICOLOGO, PACIENTE }`; `EstadoAviso { PENDENTE, ENVIADO, FALHOU, CANCELADO }`; `AvisoDeConsultaRepository.reivindicar(UUID)`. A Fatia 4 consome tudo.

A mensagem agendada que a §5.2 separou da pendência. **Sem endpoint, sem serviço, sem job** — isso é Fatia 4. Aqui nasce só o modelo, porque a spec manda o schema fechar antes das fatias.

**Nenhum campo de corpo de mensagem, e isso não é esquecimento** (§5.2/§7.1): a mensagem é derivada da `Consulta` no envio, a partir dos blocos fixos que o psicólogo escolheu (Task 6). Guardar corpo aqui abriria a porta para texto arbitrário chegar ao paciente — foi exatamente a trava `[escopo]` que o protótipo levou na Fase 0.5.

A **trava contra envio duplo** (§5.7) entra já no repositório, porque é barata agora e cara de descobrir depois: com duas instâncias no ar, as duas varrem e o paciente recebe dois e-mails.

- [ ] **Step 1: Escrever o teste que falha**

Em `SchemaMigrationTest`:

```java
    @Test
    @DisplayName("aviso_de_consulta existe, presa a uma consulta (spec 5.2)")
    void avisoDeConsultaExiste() {
        assertThat(existeTabela("aviso_de_consulta")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "id_consulta")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "destinatario_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "canal_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "agendado_para_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "enviado_em_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "estado_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "erro_aviso")).isTrue();
    }

    @Test
    @DisplayName("o aviso NAO guarda corpo de mensagem (spec 5.2 e 7.1)")
    void avisoNaoTemCorpoDeMensagem() {
        List<String> colunas = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'aviso_de_consulta'",
                String.class);
        assertThat(colunas)
                .as("corpo guardado aqui deixa texto arbitrario chegar ao paciente")
                .noneMatch(c -> c.contains("corpo") || c.contains("mensagem") || c.contains("texto"));
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=SchemaMigrationTest`
Expected: `existeTabela("aviso_de_consulta")` é `false`. O segundo teste passa por vacuidade (lista vazia) — normal; ele é uma trava contra o futuro, não uma prova de hoje.

- [ ] **Step 3: Migration**

```sql
-- Mensagem agendada, presa a uma Consulta (5.2). A outra metade do que a
-- antiga tabela "lembrete" tentava ser.
--
-- NAO TEM COLUNA DE CORPO, de proposito: a mensagem e derivada da Consulta no
-- envio, a partir dos blocos fixos que o psicologo escolheu (5.5). Guardar
-- corpo aqui abriria a porta para texto arbitrario chegar ao paciente, o que
-- a 7.1 proibe — foi a unica trava de escopo do prototipo
CREATE TABLE aviso_de_consulta (
    id_aviso UUID PRIMARY KEY,
    id_consulta UUID NOT NULL,

    destinatario_aviso VARCHAR(20) NOT NULL,
    canal_aviso VARCHAR(20) NOT NULL,
    agendado_para_aviso TIMESTAMP NOT NULL,
    enviado_em_aviso TIMESTAMP,
    estado_aviso VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    -- motivo da falha, quando estado = FALHOU. Diagnostico, nunca conteudo
    erro_aviso TEXT,

    CONSTRAINT fk_aviso_consulta
        FOREIGN KEY (id_consulta)
        REFERENCES consulta(id_consulta)
);

-- o job varre exatamente por este par (5.7)
CREATE INDEX idx_aviso_varredura ON aviso_de_consulta (estado_aviso, agendado_para_aviso);
```

- [ ] **Step 4: Os dois enums**

```java
package com.psychologyapi.psychologyapi.model;

/** Quem recebe o aviso. O psicologo pediu os dois lados (spec 3). */
public enum Destinatario {
    PSICOLOGO,
    PACIENTE
}
```

```java
package com.psychologyapi.psychologyapi.model;

/** Ciclo de vida do aviso. PENDENTE e o unico estado que o job reivindica. */
public enum EstadoAviso {
    PENDENTE,
    ENVIADO,
    FALHOU,
    CANCELADO
}
```

- [ ] **Step 5: A entidade**

```java
package com.psychologyapi.psychologyapi.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

/** Mensagem agendada, presa a uma Consulta (spec 5.2).
 *
 *  NAO TEM CAMPO DE CORPO, de proposito: a mensagem e montada no envio a
 *  partir da Consulta e dos blocos fixos do Usuario (5.5). Acrescentar um
 *  campo de texto livre aqui remove a garantia de "zero conteudo clinico"
 *  da 7.1 — o assunto e o corpo chegam na tela de bloqueio do celular do
 *  paciente, e ele nao escolheu isso. */
@Entity
@Table(name = "aviso_de_consulta", schema = "public")
@Data
public class AvisoDeConsulta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_aviso", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = false)
    private Consulta consulta;

    @Enumerated(EnumType.STRING)
    @Column(name = "destinatario_aviso", nullable = false, length = 20)
    private Destinatario destinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_aviso", nullable = false, length = 20)
    private CanalAviso canal;

    @Column(name = "agendado_para_aviso", nullable = false)
    private LocalDateTime agendadoPara;

    @Column(name = "enviado_em_aviso")
    private LocalDateTime enviadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_aviso", nullable = false, length = 20)
    private EstadoAviso estado;

    /** Motivo da falha. Diagnostico — nunca conteudo de mensagem. */
    @Column(name = "erro_aviso")
    private String erro;

    @PrePersist
    private void prePersist() {
        if (this.estado == null) {
            this.estado = EstadoAviso.PENDENTE;
        }
    }
}
```

- [ ] **Step 6: O repositório, com a trava**

```java
package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.psychologyapi.psychologyapi.model.AvisoDeConsulta;

public interface AvisoDeConsultaRepository extends JpaRepository<AvisoDeConsulta, UUID> {

    @Query("""
           SELECT a FROM AvisoDeConsulta a
           WHERE a.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.PENDENTE
             AND a.agendadoPara <= :agora
           """)
    List<AvisoDeConsulta> aVencer(@Param("agora") LocalDateTime agora);

    /**
     * Reivindica o aviso antes de enviar. Devolve 1 se este processo ganhou a
     * linha, 0 se outro ja tinha ganhado.
     *
     * Com duas instancias no ar, as duas varrem e o paciente recebe dois
     * e-mails (spec 5.7). O UPDATE condicional resolve: so envia quem afetou
     * uma linha. Barato agora, caro de descobrir depois.
     */
    @Modifying
    @Query("""
           UPDATE AvisoDeConsulta a
              SET a.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.ENVIADO,
                  a.enviadoEm = :agora
            WHERE a.id = :id
              AND a.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.PENDENTE
           """)
    int reivindicar(@Param("id") UUID id, @Param("agora") LocalDateTime agora);
}
```

- [ ] **Step 7: Testar a trava de verdade**

`AvisoDeConsultaRepositoryTest`, no molde dos outros testes de repositório (`@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)`, `@Import(PostgresContainerConfig.class)`). Leia `LogAuditoriaRepositoryTest` para copiar o formato exato de setup e de criação de fixtures.

O teste que importa, com a montagem inteira (dado inventado, nunca de pessoa
real — `Usuario`, `Paciente` dele, `Consulta` dos dois, e o aviso apontando
para ela):

```java
    private AvisoDeConsulta avisoPendenteSalvo() {
        Usuario usuario = new Usuario();
        usuario.setNomeUsuario("Marina Aguiar");
        usuario.setEmailUsuario("marina.aguiar@example.com");
        usuario.setSenhaUsuario("hash-irrelevante-para-este-teste");
        usuario = usuarioRepository.save(usuario);

        Paciente paciente = new Paciente();
        paciente.setUsuario(usuario);
        paciente.setNomePaciente("Helena Vaz");
        paciente = pacienteRepository.save(paciente);

        Consulta consulta = new Consulta();
        consulta.setUsuario(usuario);
        consulta.setPaciente(paciente);
        consulta.setDtConsulta(LocalDateTime.now().plusDays(1));
        consulta = consultaRepository.save(consulta);

        AvisoDeConsulta aviso = new AvisoDeConsulta();
        aviso.setConsulta(consulta);
        aviso.setDestinatario(Destinatario.PACIENTE);
        aviso.setCanal(CanalAviso.EMAIL);
        aviso.setAgendadoPara(LocalDateTime.now().minusMinutes(1)); // ja vencido
        return repository.save(aviso);
    }

    @Test
    @DisplayName("reivindicar duas vezes so vence uma: paciente nao recebe dois e-mails")
    void reivindicarEIdempotente() {
        AvisoDeConsulta aviso = avisoPendenteSalvo();

        int primeira = repository.reivindicar(aviso.getId(), LocalDateTime.now());
        int segunda  = repository.reivindicar(aviso.getId(), LocalDateTime.now());

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isZero();
    }

    @Test
    @DisplayName("aVencer traz o que ja passou da hora e ignora o que nao chegou")
    void aVencerRespeitaAHora() {
        avisoPendenteSalvo(); // agendado para um minuto atras

        assertThat(repository.aVencer(LocalDateTime.now())).hasSize(1);
        assertThat(repository.aVencer(LocalDateTime.now().minusHours(1))).isEmpty();
    }
```

Os quatro repositórios (`usuarioRepository`, `pacienteRepository`, `consultaRepository`, `repository`) entram por `@Autowired`, como nos outros testes de repositório.

`@Modifying` com `@DataJpaTest` tem uma armadilha: o contexto de persistência guarda a entidade da primeira chamada, e a segunda pode ler do cache em vez do banco, devolvendo o resultado errado. Se acontecer, acrescente `@Modifying(clearAutomatically = true, flushAutomatically = true)`. Não conserte "ajustando a asserção" — a asserção é o ponto do teste.

- [ ] **Step 8: Rodar a suíte inteira**

Run: `./mvnw test`
Expected: verde.

- [ ] **Step 9: Relatar. NÃO COMMITAR.**

---

### Task 6: `Usuario` ganha preferências

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__20260901.sql`
- Modify: `model/Usuario.java`
- Create: `model/Antecedencia.java`, `model/BlocoAviso.java`
- Modify: `controller/dto/usuario/UsuarioCadastroDTO.java`
- Test: `SchemaMigrationTest.java`

**Interfaces:**
- Produces: `Usuario.valorPadraoSessao` (`BigDecimal`), `Usuario.antecedencia` (`Antecedencia`), `Usuario.tituloNoEmail` (`boolean`), `Usuario.blocosAviso` (`Set<BlocoAviso>`); `Antecedencia { H24, H2, VESPERA }`; `BlocoAviso { REMARCAR, CHEGADA, DURACAO }`. A Fatia 4 monta o e-mail com isto; a Fatia 1 usa `valorPadraoSessao`.

As preferências de §5.5, mais a **senha com mínimo** — que a spec põe na Fatia 1 mas é barata aqui, e §6.1 a classifica como **a única falha de segurança** da análise: hoje `UsuarioCadastroDTO.senha` tem só `@NotBlank`, e a senha `"a"` é aceita num produto pago que guarda anotação clínica de terceiros.

**Os blocos são a escolha, não o texto** (§5.5). Guardar quais frases estão ligadas — e não a frase montada — faz com que corrigir uma frase mal escrita conserte os avisos que ainda não saíram, em vez de congelar a versão velha em cada um. Vão numa tabela filha, não numa coluna com vírgulas: acrescentar um bloco novo depois não deve exigir parsing.

- [ ] **Step 1: Escrever o teste que falha**

```java
    @Test
    @DisplayName("usuario tem as preferencias de aviso e de valor (spec 5.5)")
    void usuarioTemPreferencias() {
        assertThat(existeColuna("usuario", "valor_padrao_sessao_usuario")).isTrue();
        assertThat(existeColuna("usuario", "antecedencia_aviso_usuario")).isTrue();
        assertThat(existeColuna("usuario", "titulo_no_email_usuario")).isTrue();
    }

    @Test
    @DisplayName("os blocos do aviso ficam em tabela filha, nao em coluna com virgulas")
    void blocosDoAvisoEmTabelaFilha() {
        assertThat(existeTabela("usuario_bloco_aviso")).isTrue();
        assertThat(existeColuna("usuario_bloco_aviso", "bloco")).isTrue();
    }
```

E, em `AuthControllerTest` (ou onde o cadastro de usuário for exercido por HTTP — ache com `grep -rn "UsuarioCadastroDTO" backend/src/test`):

```java
    @Test
    @DisplayName("senha curta e recusada com 400 (spec 6.1: unica falha de seguranca da analise)")
    void senhaCurtaERecusada() throws Exception {
        // cadastro valido em tudo, menos a senha
        mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Marina Aguiar","email":"marina@example.com","senha":"a"}
                         """))
               .andExpect(status().isBadRequest());
    }
```

Confirme o caminho real do endpoint e o formato do corpo lendo `AuthController` antes de escrever — não presuma `/auth/cadastro`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=SchemaMigrationTest` e o teste de cadastro.
Expected: colunas inexistentes; e a senha `"a"` devolvendo **201** em vez de 400 — que é exatamente o defeito que a §6.1 descreve.

- [ ] **Step 3: Migration**

No `CREATE TABLE usuario`, antes de `created_at_usuario`:

```sql
    -- valor padrao que a consulta COPIA no agendamento (5.3). Nulo ate o
    -- psicologo configurar
    valor_padrao_sessao_usuario NUMERIC(10,2),
    antecedencia_aviso_usuario VARCHAR(20) NOT NULL DEFAULT 'H24',
    -- desligado por padrao: ligado, o remetente revela que a mensagem e de um
    -- psicologo, e quem vir a caixa de entrada do paciente descobre que ele
    -- faz terapia (7.1). E escolha dele, ciente do que expoe
    titulo_no_email_usuario BOOLEAN NOT NULL DEFAULT false,
```

E, depois da tabela:

```sql
-- QUAIS frases opcionais do aviso estao ligadas — a escolha, nunca o texto
-- (5.5). Guardar o texto congelaria a versao velha em cada aviso; guardando a
-- escolha, corrigir uma frase mal escrita conserta os avisos que ainda nao
-- sairam. Tabela filha, e nao coluna com virgulas, para que acrescentar um
-- bloco novo nao exija parsing
CREATE TABLE usuario_bloco_aviso (
    id_usuario UUID NOT NULL,
    bloco VARCHAR(30) NOT NULL,

    PRIMARY KEY (id_usuario, bloco),

    CONSTRAINT fk_bloco_aviso_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario)
);
```

- [ ] **Step 4: Os dois enums**

```java
package com.psychologyapi.psychologyapi.model;

/** Quanto antes o aviso sai. Nomes comecam por letra porque identificador
 *  Java nao comeca por digito. */
public enum Antecedencia {
    H24,
    H2,
    VESPERA
}
```

```java
package com.psychologyapi.psychologyapi.model;

/** As frases opcionais do corpo do aviso (spec 4.5). Duas outras frases sao
 *  obrigatorias e nao aparecem aqui: nao se desligam.
 *
 *  O texto de cada bloco NAO mora no banco — mora no codigo que monta o
 *  e-mail. Aqui fica so a escolha do psicologo. */
public enum BlocoAviso {
    REMARCAR,
    CHEGADA,
    DURACAO
}
```

- [ ] **Step 5: A entidade**

Em `Usuario`, acrescente:

```java
    @Column(name = "valor_padrao_sessao_usuario", nullable = true, precision = 10, scale = 2)
    private BigDecimal valorPadraoSessao;

    @Enumerated(EnumType.STRING)
    @Column(name = "antecedencia_aviso_usuario", nullable = false, length = 20)
    private Antecedencia antecedencia;

    @Column(name = "titulo_no_email_usuario", nullable = false)
    private boolean tituloNoEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "usuario_bloco_aviso",
            joinColumns = @JoinColumn(name = "id_usuario"))
    @Column(name = "bloco", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<BlocoAviso> blocosAviso = new HashSet<>();
```

E no `prePersist`, o padrão que a tela do protótipo já mostrava — só `REMARCAR` ligada:

```java
    @PrePersist
    private void prePersist(){
        this.createdAt = LocalDateTime.now();
        if (this.antecedencia == null) {
            this.antecedencia = Antecedencia.H24;
        }
        if (this.blocosAviso == null || this.blocosAviso.isEmpty()) {
            this.blocosAviso = new HashSet<>(Set.of(BlocoAviso.REMARCAR));
        }
    }
```

`EAGER` aqui é deliberado: são no máximo três linhas por usuário, sempre lidas junto do usuário, e `LAZY` numa `@ElementCollection` rende `LazyInitializationException` fora da transação — custo real, benefício zero.

- [ ] **Step 6: A senha ganha mínimo**

Em `UsuarioCadastroDTO`:

```java
        @NotBlank(message = "Informe uma senha.")
        @Size(min = 10, message = "A senha precisa de pelo menos 10 caracteres.")
        String senha
```

Importe `jakarta.validation.constraints.Size`.

**Só o mínimo entra aqui.** A §6.3 também pede recusa de lista de senhas comuns, com arquivo versionado no projeto. Isso é da Fatia 1, tem arquivo de dados junto, e não cabe numa tarefa de schema — **registre no relatório que ficou de fora**, para não sumir.

Corrija também as mensagens vizinhas neste DTO que estiverem no registro velho (`"campo obrigatorio"`), pela constraint global de texto.

- [ ] **Step 7: Rodar a suíte inteira**

Run: `./mvnw test`
Expected: verde.

O que provavelmente quebra, e é conserto legítimo: testes que cadastram usuário com senha curta (`"123"`, `"senha"`) passam a levar 400. **Alongue a senha do fixture** — não relaxe o `@Size`. Se um `Fixtures.java` centraliza isso, um lugar só resolve.

- [ ] **Step 8: Relatar. NÃO COMMITAR.**

---

## Verificação final da fase

Depois da Task 6, antes de entregar:

- [ ] `./mvnw test` inteiro, verde, contando quantos testes rodaram
- [ ] `grep -rn --include=*.java -i "lembrete" backend/src` — zero em código
- [ ] `grep -rn "observacoes" backend/src` — zero
- [ ] `grep -rn "campo obrigatorio" backend/src` — zero (registro velho de texto)
- [ ] A migration lida de cima a baixo, como quem chega hoje: as tabelas contam o modelo da §5 inteiro, e os comentários explicam **por que**, não o que
- [ ] Nenhuma coluna de corpo de mensagem em `aviso_de_consulta`

## O que este plano NÃO faz

- Endpoint, serviço ou job de `AvisoDeConsulta` — Fatia 4
- Endpoints de agregação (`/painel/hoje`, `/painel/mes`, `/financeiro/mes`, `/consultas/conflito`) — Fatia 1 e 3
- Lista de senhas comuns — Fatia 1
- Preencher `Anotacao.consulta` a partir de alguma tela — Fatia 2
- Qualquer coisa de frontend
- Filtrar agregação por `status = REALIZADA` — não há agregação ainda; a regra está na §5.3 e é da Fatia 3
