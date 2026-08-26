# Consulta Recorrente — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agendar uma série de consultas semanais num único pedido, e cancelar as futuras de uma série de uma vez.

**Architecture:** As ocorrências são materializadas na criação como linhas normais em `consulta`, agrupadas por uma coluna nova `id_serie` (UUID, nullable, sem tabela `serie`). Cada ocorrência segue sendo uma consulta comum — todo o CRUD atual continua valendo sem alteração. O horizonte é explícito (`quantidadeSessoes` ou `dtLimite`) e validado antes de gravar; conflito de horário em qualquer ocorrência aborta a série inteira.

**Tech Stack:** Java 21, Spring Boot 3.5.4, Spring Data JPA, Flyway, MapStruct 1.6.3, Lombok, Bean Validation (Hibernate Validator), JUnit 5 + Mockito, Testcontainers (Postgres), MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-25-consulta-recorrente-design.md`

## Global Constraints

Valem para toda tarefa deste plano.

- **Pacote raiz:** `com.psychologyapi.psychologyapi` (sem underline).
- **Campos Java:** `camelCase` puro. **Colunas:** `snake_case`, sempre com `@Column(name = "...")` explícito, mesmo quando a conversão automática resolveria.
- **Migration:** nome `V<n>__<AAAAMMDD>.sql`, em `src/main/resources/db/migration/`. Migration aplicada é imutável — correção é migration nova. Nunca `ALTER TABLE` na mão.
- **Isolamento:** usuário logado sempre via `securityService.obterUsuarioLogado()`, nunca id vindo de DTO. Busca por `findByIdAndUsuario...`, nunca `findById` puro. Registro de outro psicólogo responde `404`, nunca `403`.
- **Soft delete:** métodos de repository têm sufixo `AndDeletedAtIsNull`; **não criar** versões sem o filtro. "Deletar" é `setDeletedAt(now())` + `save(...)`.
- **Mutação sempre termina com `save(...)`/`saveAll(...)` explícito**, e a resposta usa o retorno do save, não a variável original.
- **`@RequestBody`** vem de `org.springframework.web.bind.annotation`, nunca de `io.swagger.v3.oas.annotations.parameters`.
- **Todo método de controller** devolve `ResponseEntity<X>` com `return` explícito.
- **Auditoria:** chamada manual, sem AOP, uma linha por registro afetado.
- **Duração da consulta / janela de conflito:** 50 minutos, constante já existente `ConsultaService.DURACAO_CONSULTA_MINUTOS`.
- **Teto da série:** 2 a 52 ocorrências, nos dois caminhos de horizonte.
- **Rodar os testes:** `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test`. Uma classe só: acrescente `-Dtest=NomeDaClasse`. Precisa de Docker rodando.

## File Structure

**Criar:**

| Arquivo | Responsabilidade |
|---|---|
| `src/main/resources/db/migration/V7__20260825.sql` | Coluna `id_serie` + índice |
| `src/main/java/.../service/serie/OcorrenciasSemanais.java` | Única fonte da regra semanal: dada a primeira data, produz a lista de ocorrências. Função pura, sem Spring |
| `src/main/java/.../controller/dto/consulta/ConsultaRecorrenteCadastroDTO.java` | Entrada do POST |
| `src/main/java/.../controller/dto/consulta/SerieConsultaResponseDTO.java` | Saída do POST |
| `src/main/java/.../controller/dto/consulta/SerieCancelamentoResponseDTO.java` | Saída do DELETE |
| `src/main/java/.../controller/dto/consulta/HorizonteValido.java` | Anotação de constraint de classe |
| `src/main/java/.../controller/dto/consulta/HorizonteValidoValidator.java` | Regra do XOR de horizonte + teto |
| `src/test/java/.../service/serie/OcorrenciasSemanaisTest.java` | Unitário puro da regra semanal |
| `src/test/java/.../controller/dto/consulta/HorizonteValidoValidatorTest.java` | Unitário do validador, com `Validator` de verdade |

**Modificar:**

| Arquivo | O quê |
|---|---|
| `src/main/java/.../model/Consulta.java` | Campo `serieId` |
| `src/main/java/.../repository/ConsultaRepository.java` | `findBySerieIdAndUsuarioAndDeletedAtIsNull` |
| `src/main/java/.../service/ConsultaService.java` | `cadastrarSerie`, `cancelarSerie` e os helpers privados |
| `src/main/java/.../controller/ConsultaController.java` | Os dois endpoints novos |
| `src/test/java/.../repository/ConsultaRepositoryTest.java` | Persistência do `serieId` e o método derivado novo |
| `src/test/java/.../service/ConsultaServiceTest.java` | Cadastro e cancelamento de série |
| `src/test/java/.../support/Fixtures.java` | `consultaDeSerie(...)` |
| `src/test/java/.../controller/ConsultaSerieControllerTest.java` | Camada HTTP (arquivo novo, mas listado aqui porque pertence à suíte existente) |
| `ai/Claude.md`, `README.md` | Documentação |

Por que `OcorrenciasSemanais` é classe própria e não método privado do service: o validador **e** o service precisam da mesma regra — o validador para contar quantas ocorrências um `dtLimite` produz, o service para gerá-las. Duplicar a aritmética semanal em dois lugares é como a série passa a divergir da validação.

---

### Task 1: Coluna `id_serie` e o campo na entidade

**Files:**
- Create: `src/main/resources/db/migration/V7__20260825.sql`
- Modify: `src/main/java/com/psychologyapi/psychologyapi/model/Consulta.java`
- Test: `src/test/java/com/psychologyapi/psychologyapi/repository/ConsultaRepositoryTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `Consulta.getSerieId()` / `setSerieId(UUID)`, coluna `consulta.id_serie`.

- [ ] **Step 1: Write the failing test**

Acrescente ao final de `ConsultaRepositoryTest` (a classe já tem `em` e `consultaRepository` autowired):

```java
    @Test
    @DisplayName("consulta guarda e devolve o id da serie")
    void persisteOSerieId() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));
        UUID serieId = UUID.randomUUID();

        Consulta consulta = Fixtures.consulta(dono, paciente, INICIO_DO_DIA.plusHours(15), Status.AGENDADA);
        consulta.setSerieId(serieId);
        Consulta salva = consultaRepository.save(consulta);
        em.flush();
        em.clear();

        assertThat(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(salva.getId(), dono))
                .get()
                .extracting(Consulta::getSerieId)
                .isEqualTo(serieId);
    }
```

Adicione o import `java.util.UUID` se ainda não estiver na classe.

- [ ] **Step 2: Run test to verify it fails**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaRepositoryTest`
Expected: FAIL na compilação — `cannot find symbol: method setSerieId(UUID)`.

- [ ] **Step 3: Write the migration**

`src/main/resources/db/migration/V7__20260825.sql`:

```sql
ALTER TABLE consulta ADD COLUMN id_serie UUID;

CREATE INDEX idx_consulta_serie ON consulta (id_serie);
```

- [ ] **Step 4: Add the field to the entity**

Em `Consulta.java`, depois de `deletedAt` e antes do `@PrePersist`:

```java
    @Column(name = "id_serie")
    private UUID serieId;
```

Sem `nullable = false` e sem default no `@PrePersist`: consulta avulsa nasce sem série, e é o caso mais comum.

- [ ] **Step 5: Run test to verify it passes**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaRepositoryTest`
Expected: PASS, todos os testes da classe.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V7__20260825.sql src/main/java/com/psychologyapi/psychologyapi/model/Consulta.java src/test/java/com/psychologyapi/psychologyapi/repository/ConsultaRepositoryTest.java
git commit -m 'feat(model): adiciona id_serie na consulta'
```

---

### Task 2: Busca das ocorrências de uma série

**Files:**
- Modify: `src/main/java/com/psychologyapi/psychologyapi/repository/ConsultaRepository.java`
- Modify: `src/test/java/com/psychologyapi/psychologyapi/support/Fixtures.java`
- Test: `src/test/java/com/psychologyapi/psychologyapi/repository/ConsultaRepositoryTest.java`

**Interfaces:**
- Consumes: `Consulta.setSerieId(UUID)` da Task 1.
- Produces: `List<Consulta> ConsultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(UUID serieId, Usuario usuario)` e `Fixtures.consultaDeSerie(Usuario, Paciente, LocalDateTime, UUID)`.

- [ ] **Step 1: Write the fixture helper**

Ao final de `Fixtures.java`, antes do fecha-chaves da classe:

```java
    public static Consulta consultaDeSerie(Usuario dono, Paciente paciente, LocalDateTime dtConsulta, UUID serieId) {
        Consulta consulta = consulta(dono, paciente, dtConsulta, Status.AGENDADA);
        consulta.setSerieId(serieId);
        return consulta;
    }
```

- [ ] **Step 2: Write the failing test**

Ao final de `ConsultaRepositoryTest`:

```java
    @Test
    @DisplayName("busca por serie nao vaza serie de outro psicologo nem consulta apagada")
    void buscaPorSerieRespeitaUsuarioESoftDelete() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));
        UUID serieId = UUID.randomUUID();

        em.persistAndFlush(Fixtures.consultaDeSerie(dono, paciente, INICIO_DO_DIA.plusHours(15), serieId));
        Consulta apagada = em.persistAndFlush(
                Fixtures.consultaDeSerie(dono, paciente, INICIO_DO_DIA.plusHours(15).plusWeeks(1), serieId));
        apagada.setDeletedAt(LocalDateTime.now());
        em.flush();
        em.clear();

        assertThat(consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, dono)).hasSize(1);
        assertThat(consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, intruso)).isEmpty();
    }
```

- [ ] **Step 3: Run test to verify it fails**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaRepositoryTest`
Expected: FAIL na compilação — `cannot find symbol: method findBySerieIdAndUsuarioAndDeletedAtIsNull`.

- [ ] **Step 4: Add the derived method**

Em `ConsultaRepository`, junto dos outros:

```java
    List<Consulta> findBySerieIdAndUsuarioAndDeletedAtIsNull(UUID serieId, Usuario usuario);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaRepositoryTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/repository/ConsultaRepository.java src/test/java/com/psychologyapi/psychologyapi/support/Fixtures.java src/test/java/com/psychologyapi/psychologyapi/repository/ConsultaRepositoryTest.java
git commit -m 'feat(repository): adiciona busca das consultas de uma serie'
```

---

### Task 3: A regra semanal, isolada

**Files:**
- Create: `src/main/java/com/psychologyapi/psychologyapi/service/serie/OcorrenciasSemanais.java`
- Test: `src/test/java/com/psychologyapi/psychologyapi/service/serie/OcorrenciasSemanaisTest.java`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `static final int OcorrenciasSemanais.MIN_OCORRENCIAS = 2` e `MAX_OCORRENCIAS = 52`
  - `static List<LocalDateTime> OcorrenciasSemanais.aPartirDe(LocalDateTime primeira, int quantidade)`
  - `static List<LocalDateTime> OcorrenciasSemanais.ate(LocalDateTime primeira, LocalDate limite, int teto)` — devolve **no máximo `teto + 1`** ocorrências, para que quem chama detecte o estouro sem gerar uma lista gigante.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/psychologyapi/psychologyapi/service/serie/OcorrenciasSemanaisTest.java`:

```java
package com.psychologyapi.psychologyapi.service.serie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcorrenciasSemanaisTest {

    private static final LocalDateTime TERCA_15H = LocalDateTime.of(2026, 9, 1, 15, 0);

    @Test
    @DisplayName("aPartirDe gera a quantidade pedida, de 7 em 7 dias, na mesma hora")
    void geraQuantidadePedida() {
        List<LocalDateTime> ocorrencias = OcorrenciasSemanais.aPartirDe(TERCA_15H, 4);

        assertThat(ocorrencias).hasSize(4);
        assertThat(ocorrencias.get(0)).isEqualTo(TERCA_15H);
        assertThat(ocorrencias.get(3)).isEqualTo(TERCA_15H.plusWeeks(3));
        assertThat(ocorrencias).allMatch(dt -> dt.getDayOfWeek() == DayOfWeek.TUESDAY);
        assertThat(ocorrencias).allMatch(dt -> dt.toLocalTime().equals(TERCA_15H.toLocalTime()));
    }

    @Test
    @DisplayName("ate inclui a ocorrencia que cai no proprio dia do limite")
    void incluiOcorrenciaNoDiaDoLimite() {
        LocalDate limite = TERCA_15H.plusWeeks(2).toLocalDate();

        List<LocalDateTime> ocorrencias = OcorrenciasSemanais.ate(TERCA_15H, limite, 52);

        assertThat(ocorrencias).containsExactly(TERCA_15H, TERCA_15H.plusWeeks(1), TERCA_15H.plusWeeks(2));
    }

    @Test
    @DisplayName("ate nao passa do limite")
    void naoPassaDoLimite() {
        LocalDate limite = TERCA_15H.plusWeeks(2).minusDays(1).toLocalDate();

        List<LocalDateTime> ocorrencias = OcorrenciasSemanais.ate(TERCA_15H, limite, 52);

        assertThat(ocorrencias).containsExactly(TERCA_15H, TERCA_15H.plusWeeks(1));
    }

    @Test
    @DisplayName("ate para em teto + 1, para quem chama detectar o estouro sem gerar lista gigante")
    void paraEmTetoMaisUm() {
        LocalDate limiteDistante = TERCA_15H.plusYears(5).toLocalDate();

        List<LocalDateTime> ocorrencias = OcorrenciasSemanais.ate(TERCA_15H, limiteDistante, 52);

        assertThat(ocorrencias).hasSize(53);
    }

    @Test
    @DisplayName("os limites da serie sao 2 e 52")
    void limitesDaSerie() {
        assertThat(OcorrenciasSemanais.MIN_OCORRENCIAS).isEqualTo(2);
        assertThat(OcorrenciasSemanais.MAX_OCORRENCIAS).isEqualTo(52);
    }

    @Test
    @DisplayName("ate devolve lista vazia quando o limite e anterior a primeira ocorrencia")
    void limiteAnteriorDevolveVazio() {
        LocalDate limite = TERCA_15H.minusDays(1).toLocalDate();

        assertThat(OcorrenciasSemanais.ate(TERCA_15H, limite, 52)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=OcorrenciasSemanaisTest`
Expected: FAIL na compilação — `package com.psychologyapi.psychologyapi.service.serie does not exist`.

- [ ] **Step 3: Write the implementation**

`src/main/java/com/psychologyapi/psychologyapi/service/serie/OcorrenciasSemanais.java`:

```java
package com.psychologyapi.psychologyapi.service.serie;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fonte unica da regra semanal da consulta recorrente.
 *
 * Existe como classe propria porque duas camadas precisam da mesma aritmetica:
 * o validador do DTO, para contar quantas ocorrencias um dtLimite produz, e o
 * service, para gerar as ocorrencias. Duplicar a regra e como a serie passaria a
 * divergir da validacao.
 *
 * plusWeeks preserva a hora local de proposito: "toda terca as 15h" continua as
 * 15h mesmo que o horario de verao volte.
 */
public final class OcorrenciasSemanais {

    /** Limites da serie. Moram aqui, e nao no validador do DTO, porque o service
     *  tambem precisa deles: service importando classe de validacao de DTO seria
     *  inversao de camada. */
    public static final int MIN_OCORRENCIAS = 2;
    public static final int MAX_OCORRENCIAS = 52;

    private OcorrenciasSemanais() {
    }

    public static List<LocalDateTime> aPartirDe(LocalDateTime primeira, int quantidade) {
        List<LocalDateTime> ocorrencias = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            ocorrencias.add(primeira.plusWeeks(i));
        }
        return List.copyOf(ocorrencias);
    }

    /**
     * Para em teto + 1 ocorrencias: quem chama so precisa saber que estourou, e
     * um dtLimite digitado errado nao vira uma lista de milhares de datas.
     */
    public static List<LocalDateTime> ate(LocalDateTime primeira, LocalDate limite, int teto) {
        LocalDateTime fim = limite.atTime(LocalTime.MAX);
        List<LocalDateTime> ocorrencias = new ArrayList<>();
        LocalDateTime atual = primeira;
        while (!atual.isAfter(fim) && ocorrencias.size() <= teto) {
            ocorrencias.add(atual);
            atual = atual.plusWeeks(1);
        }
        return List.copyOf(ocorrencias);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=OcorrenciasSemanaisTest`
Expected: PASS, 6 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/service/serie/OcorrenciasSemanais.java src/test/java/com/psychologyapi/psychologyapi/service/serie/OcorrenciasSemanaisTest.java
git commit -m 'feat(serie): isola a regra de recorrencia semanal'
```

---

### Task 4: Entrada validada — DTOs e a constraint do horizonte

**Files:**
- Create: `src/main/java/com/psychologyapi/psychologyapi/controller/dto/consulta/ConsultaRecorrenteCadastroDTO.java`
- Create: `src/main/java/com/psychologyapi/psychologyapi/controller/dto/consulta/SerieConsultaResponseDTO.java`
- Create: `src/main/java/com/psychologyapi/psychologyapi/controller/dto/consulta/SerieCancelamentoResponseDTO.java`
- Create: `src/main/java/com/psychologyapi/psychologyapi/controller/dto/consulta/HorizonteValido.java`
- Create: `src/main/java/com/psychologyapi/psychologyapi/controller/dto/consulta/HorizonteValidoValidator.java`
- Test: `src/test/java/com/psychologyapi/psychologyapi/controller/dto/consulta/HorizonteValidoValidatorTest.java`

**Interfaces:**
- Consumes: `OcorrenciasSemanais.ate(...)` da Task 3.
- Produces:
  - `ConsultaRecorrenteCadastroDTO(UUID pacienteId, LocalDateTime dtPrimeiraConsulta, Integer quantidadeSessoes, LocalDate dtLimite)`
  - `SerieConsultaResponseDTO(UUID serieId, int quantidade, List<ConsultaResponseDTO> consultas)`
  - `SerieCancelamentoResponseDTO(UUID serieId, int ocorrenciasRemovidas)`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/psychologyapi/psychologyapi/controller/dto/consulta/HorizonteValidoValidatorTest.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class HorizonteValidoValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private static final LocalDateTime PRIMEIRA = LocalDateTime.now().plusDays(7).withHour(15).withMinute(0)
            .withSecond(0).withNano(0);

    @BeforeAll
    static void abrirValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        factory.close();
    }

    private Set<ConstraintViolation<ConsultaRecorrenteCadastroDTO>> validar(Integer quantidade, LocalDate limite) {
        return validator.validate(
                new ConsultaRecorrenteCadastroDTO(UUID.randomUUID(), PRIMEIRA, quantidade, limite));
    }

    @Test
    @DisplayName("so quantidadeSessoes e valido")
    void apenasQuantidade() {
        assertThat(validar(10, null)).isEmpty();
    }

    @Test
    @DisplayName("so dtLimite e valido")
    void apenasLimite() {
        assertThat(validar(null, PRIMEIRA.plusWeeks(9).toLocalDate())).isEmpty();
    }

    @Test
    @DisplayName("os dois horizontes juntos e invalido, e o erro aponta o campo quantidadeSessoes")
    void osDoisJuntos() {
        Set<ConstraintViolation<ConsultaRecorrenteCadastroDTO>> violacoes =
                validar(10, PRIMEIRA.plusWeeks(9).toLocalDate());

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("quantidadeSessoes");
    }

    @Test
    @DisplayName("nenhum horizonte e invalido")
    void nenhumHorizonte() {
        assertThat(validar(null, null)).hasSize(1);
    }

    @Test
    @DisplayName("quantidade menor que 2 e invalida: serie de uma sessao e consulta avulsa")
    void quantidadeAbaixoDoMinimo() {
        assertThat(validar(1, null)).hasSize(1);
    }

    @Test
    @DisplayName("quantidade acima de 52 e invalida")
    void quantidadeAcimaDoTeto() {
        assertThat(validar(53, null)).hasSize(1);
    }

    @Test
    @DisplayName("dtLimite que estoura o teto e rejeitado, nao truncado")
    void limiteQueEstouraOTeto() {
        assertThat(validar(null, PRIMEIRA.plusYears(5).toLocalDate())).hasSize(1);
    }

    @Test
    @DisplayName("dtLimite que cabe uma ocorrencia so e invalido")
    void limiteQueCabeUmaOcorrencia() {
        assertThat(validar(null, PRIMEIRA.plusDays(3).toLocalDate())).hasSize(1);
    }

    @Test
    @DisplayName("dtLimite anterior a primeira consulta e invalido")
    void limiteAnteriorAPrimeira() {
        assertThat(validar(null, PRIMEIRA.minusDays(1).toLocalDate())).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=HorizonteValidoValidatorTest`
Expected: FAIL na compilação — `cannot find symbol: class ConsultaRecorrenteCadastroDTO`.

- [ ] **Step 3: Write the annotation**

`HorizonteValido.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = HorizonteValidoValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HorizonteValido {

    String message() default "horizonte da serie invalido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

- [ ] **Step 4: Write the validator**

`HorizonteValidoValidator.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais.MAX_OCORRENCIAS;
import static com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais.MIN_OCORRENCIAS;

import com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * O horizonte da serie e um XOR entre dois campos, o que anotacao de campo nao
 * expressa, e o teto do dtLimite depende de dtPrimeiraConsulta. Por isso a
 * constraint e de classe.
 *
 * A violacao e anexada ao path de quantidadeSessoes de proposito: constraint de
 * classe sem addPropertyNode produz violacao global, e o GlobalExceptionsHandler
 * so le getFieldErrors() — a resposta 400 sairia com a lista "erros" vazia.
 */
public class HorizonteValidoValidator implements ConstraintValidator<HorizonteValido, ConsultaRecorrenteCadastroDTO> {

    private static final String CAMPO = "quantidadeSessoes";

    @Override
    public boolean isValid(ConsultaRecorrenteCadastroDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        Integer quantidade = dto.quantidadeSessoes();
        LocalDate limite = dto.dtLimite();

        if ((quantidade == null) == (limite == null)) {
            return erro(context, "informe exatamente um entre quantidadeSessoes e dtLimite");
        }

        if (quantidade != null) {
            if (quantidade < MIN_OCORRENCIAS || quantidade > MAX_OCORRENCIAS) {
                return erro(context, "quantidadeSessoes deve estar entre " + MIN_OCORRENCIAS + " e " + MAX_OCORRENCIAS);
            }
            return true;
        }

        LocalDateTime primeira = dto.dtPrimeiraConsulta();
        if (primeira == null) {
            return true; // o @NotNull do campo ja reporta esse caso
        }

        int ocorrencias = OcorrenciasSemanais.ate(primeira, limite, MAX_OCORRENCIAS).size();
        if (ocorrencias < MIN_OCORRENCIAS || ocorrencias > MAX_OCORRENCIAS) {
            return erro(context, "dtLimite deve caber entre " + MIN_OCORRENCIAS + " e " + MAX_OCORRENCIAS
                    + " sessoes semanais a partir de dtPrimeiraConsulta");
        }
        return true;
    }

    private boolean erro(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem)
                .addPropertyNode(CAMPO)
                .addConstraintViolation();
        return false;
    }
}
```

- [ ] **Step 5: Write the three DTOs**

`ConsultaRecorrenteCadastroDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

@HorizonteValido
public record ConsultaRecorrenteCadastroDTO(

        @NotNull(message = "O paciente é obrigatório.") UUID pacienteId,

        @NotNull(message = "A data da primeira consulta é obrigatória.")
        @Future(message = "A data da primeira consulta deve estar no futuro.")
        LocalDateTime dtPrimeiraConsulta,

        Integer quantidadeSessoes,

        LocalDate dtLimite

) {}
```

`SerieConsultaResponseDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.util.List;
import java.util.UUID;

public record SerieConsultaResponseDTO(

        UUID serieId,
        int quantidade,
        List<ConsultaResponseDTO> consultas

) {}
```

`SerieCancelamentoResponseDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.util.UUID;

public record SerieCancelamentoResponseDTO(

        UUID serieId,
        int ocorrenciasRemovidas

) {}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=HorizonteValidoValidatorTest`
Expected: PASS, 9 testes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/controller/dto/consulta/ src/test/java/com/psychologyapi/psychologyapi/controller/dto/consulta/
git commit -m 'feat(dto): cria a entrada e as saidas da consulta recorrente'
```

---

### Task 5: Cadastro da série no service

**Files:**
- Modify: `src/main/java/com/psychologyapi/psychologyapi/service/ConsultaService.java`
- Test: `src/test/java/com/psychologyapi/psychologyapi/service/ConsultaServiceTest.java`

**Interfaces:**
- Consumes: `OcorrenciasSemanais` e suas constantes `MIN_OCORRENCIAS`/`MAX_OCORRENCIAS` (Task 3), os DTOs (Task 4), `Consulta.setSerieId` (Task 1).
- Produces: `SerieConsultaResponseDTO ConsultaService.cadastrarSerie(ConsultaRecorrenteCadastroDTO dto)`.

- [ ] **Step 1: Write the failing tests**

Acrescente a `ConsultaServiceTest`. A classe já tem os `@Mock` de `consultaRepository`, `consultaMapper`, `pacienteRepository`, `securityService` e `auditoriaService`, e o `@InjectMocks consultaService` — reuse os campos existentes e siga o estilo de helper da classe.

```java
    @Test
    @DisplayName("cadastrarSerie gera as ocorrencias semanais com o mesmo serieId")
    void cadastrarSerieGeraOcorrencias() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        ConsultaRecorrenteCadastroDTO dto =
                new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 3, null);

        SerieConsultaResponseDTO resposta = consultaService.cadastrarSerie(dto);

        ArgumentCaptor<List<Consulta>> captor = ArgumentCaptor.forClass(List.class);
        verify(consultaRepository).saveAll(captor.capture());
        List<Consulta> salvas = captor.getValue();

        assertThat(salvas).hasSize(3);
        assertThat(salvas).extracting(Consulta::getDtConsulta)
                .containsExactly(primeira, primeira.plusWeeks(1), primeira.plusWeeks(2));
        assertThat(salvas).extracting(Consulta::getSerieId).containsOnly(salvas.get(0).getSerieId());
        assertThat(salvas.get(0).getSerieId()).isNotNull();
        assertThat(salvas).allSatisfy(c -> {
            assertThat(c.getUsuario()).isSameAs(logado);
            assertThat(c.getPaciente()).isSameAs(paciente);
        });
        assertThat(resposta.quantidade()).isEqualTo(3);
        assertThat(resposta.serieId()).isEqualTo(salvas.get(0).getSerieId());
    }

    @Test
    @DisplayName("cadastrarSerie audita CRIACAO uma vez por ocorrencia")
    void cadastrarSerieAuditaCadaOcorrencia() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        consultaService.cadastrarSerie(new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 4, null));

        verify(auditoriaService, times(4)).registrar(eq(logado), eq(AcaoAuditoria.CRIACAO), eq("Consulta"), any());
    }

    @Test
    @DisplayName("cadastrarSerie aborta a serie inteira quando uma ocorrencia conflita")
    void cadastrarSerieAbortaEmConflito() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        Consulta jaAgendada = Fixtures.consulta(logado, paciente, primeira.plusWeeks(2), Status.AGENDADA);
        jaAgendada.setId(UUID.randomUUID());

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(jaAgendada));

        ConsultaRecorrenteCadastroDTO dto =
                new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 4, null);

        assertThatThrownBy(() -> consultaService.cadastrarSerie(dto))
                .isInstanceOf(ConflitoDeHorarioException.class)
                .hasMessageContaining(primeira.plusWeeks(2).toString());

        verify(consultaRepository, never()).saveAll(anyList());
        verify(consultaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cadastrarSerie ignora consulta CANCELADA na checagem de conflito")
    void cadastrarSerieIgnoraCancelada() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        Consulta cancelada = Fixtures.consulta(logado, paciente, primeira, Status.CANCELADA);
        cancelada.setId(UUID.randomUUID());

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(cancelada));
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        consultaService.cadastrarSerie(new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 2, null));

        verify(consultaRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("cadastrarSerie com paciente de outro psicologo responde 404 e nao grava nada")
    void cadastrarSerieComPacienteDeOutroUsuario() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado)).thenReturn(Optional.empty());

        ConsultaRecorrenteCadastroDTO dto =
                new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 4, null);

        assertThatThrownBy(() -> consultaService.cadastrarSerie(dto))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(consultaRepository, never()).saveAll(anyList());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cadastrarSerie por dtLimite gera ate a data limite")
    void cadastrarSeriePorDtLimite() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        SerieConsultaResponseDTO resposta = consultaService.cadastrarSerie(
                new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, null,
                        primeira.plusWeeks(3).toLocalDate()));

        assertThat(resposta.quantidade()).isEqualTo(4);
    }
```

Imports novos necessários na classe de teste: `org.mockito.ArgumentCaptor`, `static org.mockito.ArgumentMatchers.anyList`, `com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO`, `com.psychologyapi.psychologyapi.controller.dto.consulta.SerieConsultaResponseDTO`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaServiceTest`
Expected: FAIL na compilação — `cannot find symbol: method cadastrarSerie`.

- [ ] **Step 3: Write the implementation**

Em `ConsultaService`, acrescente os imports e o método. Note o `@Transactional` — **padrão novo no projeto**, necessário porque são N inserts mais N linhas de auditoria com requisito de tudo-ou-nada:

```java
    @Transactional
    public SerieConsultaResponseDTO cadastrarSerie(ConsultaRecorrenteCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(dto.pacienteId(), usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        List<LocalDateTime> ocorrencias = dto.quantidadeSessoes() != null
                ? OcorrenciasSemanais.aPartirDe(dto.dtPrimeiraConsulta(), dto.quantidadeSessoes())
                : OcorrenciasSemanais.ate(dto.dtPrimeiraConsulta(), dto.dtLimite(),
                        OcorrenciasSemanais.MAX_OCORRENCIAS);

        validarConflitoDaSerie(usuarioLogado, ocorrencias);

        UUID serieId = UUID.randomUUID();
        List<Consulta> novas = ocorrencias.stream()
                .map(dtConsulta -> {
                    Consulta consulta = new Consulta();
                    consulta.setUsuario(usuarioLogado);
                    consulta.setPaciente(paciente);
                    consulta.setDtConsulta(dtConsulta);
                    consulta.setSerieId(serieId);
                    return consulta;
                })
                .toList();

        List<Consulta> salvas = consultaRepository.saveAll(novas);
        salvas.forEach(consulta -> auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Consulta",
                consulta.getId()));

        return new SerieConsultaResponseDTO(serieId, salvas.size(),
                salvas.stream().map(consultaMapper::toResponseDTO).toList());
    }

    /**
     * Uma query no intervalo inteiro da serie, e a janela de 50 min avaliada em
     * memoria: uma query por ocorrencia seriam ate 52 round trips.
     */
    private void validarConflitoDaSerie(Usuario usuario, List<LocalDateTime> ocorrencias) {
        LocalDateTime inicioJanela = ocorrencias.get(0).minusMinutes(DURACAO_CONSULTA_MINUTOS);
        LocalDateTime fimJanela = ocorrencias.get(ocorrencias.size() - 1).plusMinutes(DURACAO_CONSULTA_MINUTOS);

        List<Consulta> existentes = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuario, inicioJanela, fimJanela)
                .stream()
                .filter(consulta -> consulta.getStatus() != Status.CANCELADA)
                .toList();

        String conflitantes = ocorrencias.stream()
                .filter(ocorrencia -> existentes.stream()
                        .anyMatch(existente -> dentroDaJanela(existente.getDtConsulta(), ocorrencia)))
                .map(LocalDateTime::toString)
                .collect(Collectors.joining(", "));

        if (!conflitantes.isEmpty()) {
            throw new ConflitoDeHorarioException("Conflito de horario em: " + conflitantes);
        }
    }

    private boolean dentroDaJanela(LocalDateTime existente, LocalDateTime nova) {
        return !existente.isBefore(nova.minusMinutes(DURACAO_CONSULTA_MINUTOS))
                && !existente.isAfter(nova.plusMinutes(DURACAO_CONSULTA_MINUTOS));
    }
```

Imports a acrescentar em `ConsultaService`:

```java
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieConsultaResponseDTO;
import com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais;
```

`dentroDaJanela` usa comparação inclusiva nas duas pontas, igual ao `BETWEEN` que o `cadastrar` avulso já faz — inclusive tratando "exatamente 50 minutos de distância" como conflito. Manter a mesma regra vale mais que corrigi-la aqui.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaServiceTest`
Expected: PASS, os 10 testes que já existiam mais os 6 novos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/service/ConsultaService.java src/test/java/com/psychologyapi/psychologyapi/service/ConsultaServiceTest.java
git commit -m 'feat(consulta): cadastra serie recorrente com validacao tudo-ou-nada'
```

---

### Task 6: Cancelamento da série no service

**Files:**
- Modify: `src/main/java/com/psychologyapi/psychologyapi/service/ConsultaService.java`
- Test: `src/test/java/com/psychologyapi/psychologyapi/service/ConsultaServiceTest.java`

**Interfaces:**
- Consumes: `findBySerieIdAndUsuarioAndDeletedAtIsNull` (Task 2), `SerieCancelamentoResponseDTO` (Task 4).
- Produces: `SerieCancelamentoResponseDTO ConsultaService.cancelarSerie(UUID serieId)`.

- [ ] **Step 1: Write the failing tests**

```java
    @Test
    @DisplayName("cancelarSerie apaga so as ocorrencias futuras, por soft delete, e audita cada uma")
    void cancelarSerieApagaSoAsFuturas() {
        UUID serieId = UUID.randomUUID();
        Paciente paciente = Fixtures.paciente(logado, "Ana");

        Consulta passada = Fixtures.consulta(logado, paciente, LocalDateTime.now().minusWeeks(1), Status.REALIZADA);
        passada.setId(UUID.randomUUID());
        passada.setSerieId(serieId);
        Consulta futura = Fixtures.consulta(logado, paciente, LocalDateTime.now().plusWeeks(1), Status.AGENDADA);
        futura.setId(UUID.randomUUID());
        futura.setSerieId(serieId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, logado))
                .thenReturn(List.of(passada, futura));

        SerieCancelamentoResponseDTO resposta = consultaService.cancelarSerie(serieId);

        assertThat(resposta.ocorrenciasRemovidas()).isEqualTo(1);
        assertThat(resposta.serieId()).isEqualTo(serieId);
        assertThat(futura.getDeletedAt()).isNotNull();
        assertThat(passada.getDeletedAt()).isNull();
        verify(consultaRepository, never()).delete(any());
        verify(auditoriaService, times(1)).registrar(logado, AcaoAuditoria.EXCLUSAO, "Consulta", futura.getId());
        verify(auditoriaService, never()).registrar(any(), any(), any(), eq(passada.getId()));
    }

    @Test
    @DisplayName("cancelarSerie de serie sem ocorrencia futura devolve contagem zero, nao 404")
    void cancelarSerieSemFuturasDevolveZero() {
        UUID serieId = UUID.randomUUID();
        Paciente paciente = Fixtures.paciente(logado, "Ana");

        Consulta passada = Fixtures.consulta(logado, paciente, LocalDateTime.now().minusWeeks(1), Status.REALIZADA);
        passada.setId(UUID.randomUUID());
        passada.setSerieId(serieId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, logado))
                .thenReturn(List.of(passada));

        SerieCancelamentoResponseDTO resposta = consultaService.cancelarSerie(serieId);

        assertThat(resposta.ocorrenciasRemovidas()).isZero();
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancelarSerie de serie inexistente ou de outro psicologo responde 404")
    void cancelarSerieInexistente() {
        UUID serieId = UUID.randomUUID();

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, logado)).thenReturn(List.of());

        assertThatThrownBy(() -> consultaService.cancelarSerie(serieId))
                .isInstanceOf(ConsultaNaoEncontradaException.class);

        verify(consultaRepository, never()).saveAll(anyList());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }
```

Import novo: `com.psychologyapi.psychologyapi.controller.dto.consulta.SerieCancelamentoResponseDTO`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaServiceTest`
Expected: FAIL na compilação — `cannot find symbol: method cancelarSerie`.

- [ ] **Step 3: Write the implementation**

```java
    @Transactional
    public SerieCancelamentoResponseDTO cancelarSerie(UUID serieId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        List<Consulta> daSerie = consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, usuarioLogado);
        if (daSerie.isEmpty()) {
            throw new ConsultaNaoEncontradaException("Serie nao encontrada");
        }

        LocalDateTime agora = LocalDateTime.now();
        List<Consulta> futuras = daSerie.stream()
                .filter(consulta -> !consulta.getDtConsulta().isBefore(agora))
                .toList();

        futuras.forEach(consulta -> consulta.setDeletedAt(agora));
        consultaRepository.saveAll(futuras);
        futuras.forEach(consulta -> auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Consulta",
                consulta.getId()));

        return new SerieCancelamentoResponseDTO(serieId, futuras.size());
    }
```

Import novo: `com.psychologyapi.psychologyapi.controller.dto.consulta.SerieCancelamentoResponseDTO`.

Consequência aceita, decorrente de o repository não ter método sem filtro de soft delete (e não deve ter): cancelar duas vezes uma série **inteiramente futura** devolve `200` na primeira e `404` na segunda, porque na segunda todas as ocorrências já estão soft-deletadas e a série deixou de existir para a API — igual a qualquer outro registro apagado.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaServiceTest`
Expected: PASS, 19 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/service/ConsultaService.java src/test/java/com/psychologyapi/psychologyapi/service/ConsultaServiceTest.java
git commit -m 'feat(consulta): cancela as ocorrencias futuras de uma serie'
```

---

### Task 7: Os dois endpoints

**Files:**
- Modify: `src/main/java/com/psychologyapi/psychologyapi/controller/ConsultaController.java`
- Test: `src/test/java/com/psychologyapi/psychologyapi/controller/ConsultaSerieControllerTest.java` (criar)

**Interfaces:**
- Consumes: `cadastrarSerie` (Task 5), `cancelarSerie` (Task 6), `HttpTest` (`support/HttpTest`, já existente).
- Produces: `POST /consultas/recorrentes`, `DELETE /consultas/series/{serieId}`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/psychologyapi/psychologyapi/controller/ConsultaSerieControllerTest.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class ConsultaSerieControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private ConsultaRepository consultaRepository;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }

    private LocalDateTime primeiraOcorrencia() {
        return LocalDateTime.now().plusDays(7).withHour(15).withMinute(0).withSecond(0).withNano(0);
    }

    private String corpoPorQuantidade(UUID pacienteId, LocalDateTime primeira, int quantidade) {
        return """
                {"pacienteId":"%s","dtPrimeiraConsulta":"%s","quantidadeSessoes":%d}
                """.formatted(pacienteId, primeira.format(ISO), quantidade);
    }

    @Test
    @DisplayName("POST /consultas/recorrentes devolve 201 com o serieId e as consultas")
    void cadastraSerie() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPorQuantidade(paciente.getId(), primeira, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serieId").isNotEmpty())
                .andExpect(jsonPath("$.quantidade").value(3))
                .andExpect(jsonPath("$.consultas.length()").value(3))
                .andExpect(jsonPath("$.consultas[0].status").value("AGENDADA"));
    }

    @Test
    @DisplayName("POST /consultas/recorrentes devolve 409 com as datas em conflito e nao cria nada")
    void conflitoAbortaASerie() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        consultaRepository.save(Fixtures.consulta(dono, paciente, primeira.plusWeeks(2), Status.AGENDADA));
        long antes = consultaRepository.count();

        mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPorQuantidade(paciente.getId(), primeira, 4)))
                .andExpect(status().isConflict());

        assertThat(consultaRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("POST /consultas/recorrentes com os dois horizontes devolve 400 com a lista de erros preenchida")
    void doisHorizontesJuntos() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        String corpo = """
                {"pacienteId":"%s","dtPrimeiraConsulta":"%s","quantidadeSessoes":4,"dtLimite":"%s"}
                """.formatted(paciente.getId(), primeira.format(ISO), primeira.plusWeeks(9).toLocalDate());

        mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(1))
                .andExpect(jsonPath("$.erros[0].campo").value("quantidadeSessoes"));
    }

    @Test
    @DisplayName("POST /consultas/recorrentes sem horizonte nenhum devolve 400")
    void nenhumHorizonte() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        String corpo = """
                {"pacienteId":"%s","dtPrimeiraConsulta":"%s"}
                """.formatted(paciente.getId(), primeira.format(ISO));

        mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("quantidadeSessoes"));
    }

    @Test
    @DisplayName("DELETE /consultas/series/{id} cancela as futuras e devolve a contagem")
    void cancelaSerie() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        UUID serieId = UUID.randomUUID();

        consultaRepository.save(Fixtures.consultaDeSerie(dono, paciente, LocalDateTime.now().minusWeeks(1), serieId));
        consultaRepository.save(Fixtures.consultaDeSerie(dono, paciente, LocalDateTime.now().plusWeeks(1), serieId));
        consultaRepository.save(Fixtures.consultaDeSerie(dono, paciente, LocalDateTime.now().plusWeeks(2), serieId));

        mockMvc.perform(delete("/consultas/series/" + serieId)
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serieId").value(serieId.toString()))
                .andExpect(jsonPath("$.ocorrenciasRemovidas").value(2));
    }

    @Test
    @DisplayName("DELETE /consultas/series/{id} de serie de outro psicologo devolve 404")
    void cancelaSerieDeOutroPsicologo() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        UUID serieId = UUID.randomUUID();

        consultaRepository.save(Fixtures.consultaDeSerie(dono, paciente, LocalDateTime.now().plusWeeks(1), serieId));

        mockMvc.perform(delete("/consultas/series/" + serieId)
                .header(HttpHeaders.AUTHORIZATION, bearerDe(intruso)))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaSerieControllerTest`
Expected: FAIL — `404` nas rotas novas, porque os endpoints ainda não existem.

- [ ] **Step 3: Write the endpoints**

Em `ConsultaController`, dentro da classe (que já tem `@RequestMapping("/consultas")`):

```java
    @PostMapping("/recorrentes")
    public ResponseEntity<SerieConsultaResponseDTO> cadastrarSerie(
            @RequestBody @Valid ConsultaRecorrenteCadastroDTO dto) {
        SerieConsultaResponseDTO serie = consultaService.cadastrarSerie(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(serie);
    }

    @DeleteMapping("/series/{serieId}")
    public ResponseEntity<SerieCancelamentoResponseDTO> cancelarSerie(@PathVariable UUID serieId) {
        SerieCancelamentoResponseDTO cancelamento = consultaService.cancelarSerie(serieId);
        return ResponseEntity.status(HttpStatus.OK).body(cancelamento);
    }
```

Imports a acrescentar:

```java
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieCancelamentoResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieConsultaResponseDTO;
```

Confira que `@RequestBody` vem de `org.springframework.web.bind.annotation` e que `jakarta.validation.Valid` está importado — a classe já usa os dois no `cadastrar` avulso.

Este `DELETE` devolve `200` com corpo, divergindo dos outros `DELETE` da API, que devolvem `204` vazio. Deliberado: quantas sessões foram canceladas é a informação que o psicólogo precisa ver.

- [ ] **Step 4: Run test to verify it passes**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test -Dtest=ConsultaSerieControllerTest`
Expected: PASS, 6 testes.

- [ ] **Step 5: Run the whole suite**

Run: `ASDF_JAVA_VERSION=temurin-21.0.10+7.0.LTS sh ./mvnw test`
Expected: PASS, tudo verde. Nenhum teste anterior deve ter quebrado — a coluna nova é nullable e nenhum fluxo existente foi alterado.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/controller/ConsultaController.java src/test/java/com/psychologyapi/psychologyapi/controller/ConsultaSerieControllerTest.java
git commit -m 'feat(controller): expoe o cadastro e o cancelamento de serie recorrente'
```

---

### Task 8: Documentação

**Files:**
- Modify: `ai/Claude.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: tudo das tarefas anteriores.
- Produces: nada de código.

- [ ] **Step 1: Update `ai/Claude.md`**

Quatro pontos:

1. Na lista de migrations, acrescente:
   `- \`V7__20260825.sql\` — \`consulta.id_serie\` + índice \`idx_consulta_serie\``
2. Na entrada de **`Consulta`** em "Entidades e regras específicas", acrescente ao
   final: série recorrente materializada na criação, agrupada por `serieId`
   (nullable, sem tabela `serie`); ocorrência é consulta comum e o CRUD normal
   vale para ela; horizonte explícito de 2 a 52 ocorrências; conflito em qualquer
   ocorrência aborta a série inteira com `409`; `DELETE /consultas/series/{id}`
   apaga só as futuras.
3. Em "Convenções de código", uma subseção nova **Transação**: `@Transactional`
   aparece só em `cadastrarSerie` e `cancelarSerie`, onde há N inserts mais N
   linhas de auditoria com requisito de tudo-ou-nada. Os outros services fazem um
   `save` só e não precisam.
4. Em "Roadmap conhecido (não implementado)", remova a linha da consulta
   recorrente — sobra só o frontend.

- [ ] **Step 2: Update `README.md`**

Três pontos:

1. Na tabela de rotas, depois das linhas de `/consultas/{id}`:
   ```
   | `POST` | `/consultas/recorrentes` | Agenda uma série semanal (2 a 52 sessões) |
   | `DELETE` | `/consultas/series/{serieId}` | Cancela as ocorrências futuras da série |
   ```
2. Na tabela de "Modelo de domínio", na linha de `Consulta`, acrescente: série
   recorrente materializada na criação e agrupada por `serieId`; conflito em
   qualquer ocorrência aborta a série inteira.
3. Em "Roadmap", remova a consulta recorrente.

- [ ] **Step 3: Verify the docs match the code**

Run: `grep -n "id_serie\|recorrente\|V7" ai/Claude.md README.md`
Expected: as menções acrescentadas aparecem, e nenhuma linha de roadmap ainda promete consulta recorrente.

- [ ] **Step 4: Commit**

```bash
git add ai/Claude.md README.md
git commit -m 'docs: documenta a consulta recorrente'
```

---

## Notas de execução

- **Ordem importa.** Task 3 antes de 4 (o validador usa `OcorrenciasSemanais`), 4 antes de 5 e 6 (o service usa os DTOs e a constante do teto), 1 e 2 antes de 5 e 6.
- **Nenhuma tarefa deixa a suíte vermelha ao terminar.** Se terminar vermelha, pare: é sinal de que o passo anterior não foi completado.
- **A migration `V7` é imutável depois de aplicada.** Se ela subir errada em algum banco, a correção é `V8`, nunca editar a `V7`.
