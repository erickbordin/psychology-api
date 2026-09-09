# Fatia 1 — backend: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deixar o backend pronto para as três telas da Fatia 1 (Login, Hoje, Agenda): a validação que §6.3 exige, os dois endpoints de agregação que §5.6 desenha, a mudança de status em um clique, e o contrato OpenAPI de onde o front vai gerar os tipos.

**Architecture:** Nada de tela aqui. Cada tarefa entrega API testável por HTTP, e a suíte fica verde ao fim de cada uma. Duas tarefas de validação vêm primeiro porque são independentes e baratas; os endpoints depois, porque o de conflito e o do painel leem a mesma tabela e é melhor que o segundo já encontre o primeiro pronto.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Lombok, MapStruct, Flyway, springdoc-openapi, JUnit 5, Testcontainers (Postgres), MockMvc, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-01-redesenho-produto-design.md` — §4① (Hoje), §4② (Agenda), §5.6 (endpoints de agregação), §6.1/§6.3 (validação), §9 (o que a Fatia 1 é).

**Estado de onde isto parte:** o schema inteiro da §5 já está no banco e nas entidades (plano `2026-09-05-schema-completo.md`, 5 commits, `f7ba02c..ed5285e`). A suíte está em **196 testes, 0 falhas**.

## Global Constraints

- **A migration `V1__20260901.sql` está FECHADA para reescrita nesta fase.** A fase anterior a editou no lugar porque nada tinha ido a produção; a partir daqui, mudança de schema é `V2__`. Nenhuma tarefa deste plano precisa de schema novo — se você achar que precisa, pare e diga: é sinal de que o plano errou.
- **Servidor é a autoridade, cliente é o atalho** (§6.2). Toda regra que o front vai aplicar em `zod` tem que existir aqui também. O envelope `ErroRespostaDTO { status, mensagem, erros[{campo, erro}] }` já existe e fica.
- **Texto de erro num só registro** (§6.3): frase completa, com acento, dizendo o que fazer. `"Informe o nome do paciente."`, nunca `"campo obrigatorio"`. A fase anterior zerou isso; não reintroduza.
- **Isolamento por usuário é invariante.** Toda query nova filtra pelo psicólogo dono, direta ou por junção. Endpoint novo sem escopo é defeito grave, não esquecimento.
- **Fuso único `America/Sao_Paulo`** (§5.9), já no Dockerfile. `LocalDate.now()`/`LocalDateTime.now()` dão a hora de parede certa. Não introduza `ZonedDateTime`, `Instant` nem `TIMESTAMPTZ`.
- **Falta não é cobrada** (§5.3): agregação de dinheiro soma só `status = REALIZADA`. Nenhuma tarefa deste plano soma dinheiro — se alguma somar, está fora de escopo.
- **`./mvnw clean test`, com `clean`.** Sem ele o build incremental acusa erro de compilação em massa por classe stale. É ambiente, não código.
- **NÃO COMMITAR.** O dono do repositório commita.
- **NÃO tocar em `README.md`, `backend/README.md` nem `frontend/README.md`.**

---

## File Structure

**Validação** (`controller/dto/`): `PacienteCadastroDTO` e `PacienteAtualizacaoDTO` completam §6.3. `UsuarioCadastroDTO` ganha a lista de senhas comuns. Validador novo em `controller/dto/usuario/SenhaForte*`, no molde de `HorizonteValido`/`HorizonteValidoValidator`, que já existem e são o padrão da casa.

**Recursos de dados:** `src/main/resources/senhas-comuns.txt` — versionado no projeto, nunca serviço externo (§6.3).

**Agregação** (§5.6): `controller/PainelController` + `controller/dto/painel/*`; a checagem de conflito entra em `ConsultaController` (a rota é `/consultas/conflito`, o recurso é consulta). Query nova em `ConsultaRepository`.

**Status em um clique** (§4①): `@PatchMapping("/{id}/status")` em `ConsultaController`, com DTO próprio.

**OpenAPI:** `config/OpenApiConfig.java` novo, mais duas linhas em `SecurityConfiguration` e uma propriedade.

---

## O ciclo de cada tarefa

1. Escrever o teste que falha
2. Rodar e ver falhar **pelo motivo certo** (404 na rota nova, não `NullPointerException` noutro lugar)
3. Implementar o mínimo
4. `./mvnw clean test` — a suíte **inteira**
5. Relatar. **Não commitar.**

**Rodar teste isolado:** `./mvnw test -Dtest=NomeDaClasseTest` no diretório `backend/`.
**Testcontainers precisa de Docker no ar.** Erro de conexão é ambiente, não código.
**Para copiar o formato de teste HTTP**, leia `SegurancaHttpTest` e `HttpTest` — o arnês é `@Transactional` de classe, e isso tem consequência: `flush` não acontece dentro da janela do teste, então teste que dependa de violação de constraint no banco não funciona por HTTP (foi achado na fase anterior). Para esses, teste a camada direto.

---

### Task 1: Senha comum é recusada

**Files:**
- Create: `backend/src/main/resources/senhas-comuns.txt`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/usuario/SenhaForte.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/usuario/SenhaForteValidator.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/usuario/UsuarioCadastroDTO.java`
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/controller/AuthControllerTest.java`, e novo `.../dto/SenhaForteValidatorTest.java`

**Interfaces:**
- Produces: anotação `@SenhaForte` aplicável a `String`. Nada consome fora do `UsuarioCadastroDTO`.

A outra metade da regra de senha (§6.3). O mínimo de 10 caracteres já entrou na fase do schema; falta recusar a lista de senhas comuns. **Arquivo versionado no projeto, nunca serviço externo** — a spec é explícita, e mandar senha de usuário para um serviço de terceiros seria trocar um problema por outro pior.

`"senha123456"` tem 11 caracteres e passa no `@Size`. É exatamente o tipo de senha que o mínimo não pega.

- [ ] **Step 1: A lista**

`src/main/resources/senhas-comuns.txt`, uma senha por linha, minúsculas. Comece com estas — são as que aparecem em todo levantamento de senha vazada, adaptadas ao teclado brasileiro:

```
123456
1234567
12345678
123456789
1234567890
senha123
senha1234
senha12345
senha123456
password
password1
password123
qwertyuiop
1q2w3e4r
102030405060
abcd1234
000000000
111111111
brasil123
felipe123
gabriel123
mudar123456
```

Comentário no topo do arquivo dizendo o que é e por que está versionado.

- [ ] **Step 2: O teste que falha**

Em `SenhaForteValidatorTest`:

```java
    @Test
    @DisplayName("senha da lista de comuns e recusada mesmo com tamanho suficiente")
    void senhaComumERecusada() {
        assertThat(validator.isValid("senha123456", null)).isFalse();
        assertThat(validator.isValid("SENHA123456", null))
                .as("comparacao e insensivel a caixa: trocar para maiuscula nao torna a senha forte")
                .isFalse();
    }

    @Test
    @DisplayName("senha fora da lista passa")
    void senhaIncomumPassa() {
        assertThat(validator.isValid("carroca-azul-de-terca", null)).isTrue();
    }

    @Test
    @DisplayName("nulo e vazio nao sao problema deste validador")
    void nuloEVazioPassam() {
        // @NotBlank e @Size ja cobrem os dois; validador que reclama de nulo
        // faz o usuario levar duas mensagens pelo mesmo defeito
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
    }
```

E em `AuthControllerTest`, o teste de ponta a ponta:

```java
    @Test
    @DisplayName("POST /auth/registrar com senha comum responde 400 apontando o campo")
    void registrarComSenhaComumResponde400() throws Exception {
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Marina Aguiar","email":"marina.senhacomum@example.com","senha":"senha123456"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("senha")));
    }
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./mvnw clean test -Dtest=SenhaForteValidatorTest`
Expected: não compila — a classe não existe. Esse é o vermelho certo aqui.

- [ ] **Step 4: A anotação**

```java
package com.psychologyapi.psychologyapi.controller.dto.usuario;

import java.lang.annotation.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Recusa senha que aparece em lista de vazamento (spec 6.3). O tamanho
 *  minimo e outra regra, e fica no @Size — sao defeitos diferentes e o
 *  usuario merece saber qual dos dois cometeu. */
@Documented
@Constraint(validatedBy = SenhaForteValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaForte {
    String message() default "Essa senha é fácil de adivinhar. Escolha outra.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

- [ ] **Step 5: O validador**

```java
package com.psychologyapi.psychologyapi.controller.dto.usuario;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    /** Carregada uma vez, na classe: sao poucas centenas de linhas e a lista
     *  nao muda em tempo de execucao. Ler o arquivo a cada cadastro seria
     *  I/O por requisicao sem nenhum ganho. */
    private static final Set<String> COMUNS = carregar();

    private static Set<String> carregar() {
        try (InputStream in = SenhaForteValidator.class.getResourceAsStream("/senhas-comuns.txt")) {
            if (in == null) {
                // falhar alto: o arquivo e versionado, entao sumir significa
                // build quebrado — nao "sem lista, aceita tudo"
                throw new IllegalStateException("senhas-comuns.txt nao encontrado no classpath");
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return r.lines()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .map(l -> l.toLowerCase(java.util.Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext contexto) {
        // nulo e vazio sao problema do @NotBlank; duas mensagens pelo mesmo
        // defeito e ruido
        if (senha == null || senha.isBlank()) {
            return true;
        }
        return !COMUNS.contains(senha.toLowerCase(java.util.Locale.ROOT));
    }
}
```

- [ ] **Step 6: Ligar no DTO**

Acrescente `@SenhaForte` ao campo `senha` de `UsuarioCadastroDTO`, junto do `@NotBlank` e do `@Size` que já estão lá.

- [ ] **Step 7: `./mvnw clean test`**

Expected: verde. Se algum fixture usar uma senha que está na lista, **troque o fixture** — não tire a senha da lista.

- [ ] **Step 8: Relatar. NÃO COMMITAR.**

---

### Task 2: A validação do paciente fecha §6.3

**Files:**
Raiz de `main`: `backend/src/main/java/com/psychologyapi/psychologyapi/`.

- Modify: `controller/dto/paciente/PacienteCadastroDTO.java`
- Modify: `controller/dto/paciente/PacienteAtualizacaoDTO.java`
- Create: `controller/dto/paciente/IdadeMaxima.java` (Step 4)
- Create: `controller/dto/paciente/IdadeMaximaValidator.java` (Step 4)
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/controller/SegurancaHttpTest.java` (ou teste HTTP de paciente dedicado, se preferir criar)
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/controller/dto/paciente/IdadeMaximaValidatorTest.java` (novo — o validador merece teste de unidade, no molde de `dto/consulta/HorizonteValidoValidatorTest`)

**Interfaces:**
- Produces: nada novo em código. Produz o **contrato de validação** que o `zod` do front vai espelhar na fatia do frontend.

A tabela de §6.3, linha por linha, contra o que os dois DTOs têm hoje. O que falta:

| Campo | Regra da §6.3 | Hoje |
|---|---|---|
| Nome | 2 a 100 | só `@NotBlank` |
| E-mail | formato + máx. 100 | **sem `@Email`** no cadastro |
| Telefone | máx. 20, formato BR, opcional | sem nada no cadastro |
| Nascimento | data real, no passado, **idade < 120** | só `@Past` |

O e-mail do paciente é o que mais pesa: §6.1 nota que ele é justamente o campo que vira canal de aviso, e endereço inválido vira falha de envio silenciosa.

**Nome mínimo 2, não 1:** um nome de uma letra é quase sempre engano de digitação, e a spec fixou 2.

- [ ] **Step 1: Os testes que falham**

```java
    @Test
    @DisplayName("e-mail de paciente em formato invalido responde 400 (spec 6.1)")
    void emailDePacienteInvalidoResponde400() throws Exception {
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Helena Vaz","email":"helena-arroba-nada"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("email")));
    }

    @Test
    @DisplayName("nome de uma letra so responde 400: minimo e 2 (spec 6.3)")
    void nomeCurtoDemaisResponde400() throws Exception {
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"H"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("nome")));
    }

    @Test
    @DisplayName("nascimento com idade acima de 120 anos responde 400 (spec 6.3)")
    void idadeAcimaDe120Responde400() throws Exception {
        String muitoAntiga = java.time.LocalDate.now().minusYears(121).toString();
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Helena Vaz\",\"dataNascimento\":\"" + muitoAntiga + "\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dataNascimento")));
    }

    @Test
    @DisplayName("telefone fora do formato brasileiro responde 400 (spec 6.3)")
    void telefoneForaDoFormatoResponde400() throws Exception {
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Helena Vaz","telefone":"abc"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("telefone")));
    }

    @Test
    @DisplayName("cadastro minimo valido continua respondendo 201")
    void cadastroMinimoValidoResponde201() throws Exception {
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Helena Vaz","telefone":"(51) 99811-2345","email":"helena.vaz@example.com"}
                         """))
               .andExpect(status().isCreated());
    }
```

**Como autenticar:** não existe helper `.with(...)` nesta base. `SegurancaHttpTest`
já tem os dois métodos privados que os testes HTTP usam, e eles são o padrão a seguir:

```java
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }
```

Como os testes acima entram em `SegurancaHttpTest`, os dois já estão lá — use-os.
Se você preferir criar um teste de paciente dedicado, copie os dois para a classe nova.

`@HttpTest` é `@Transactional` de classe, então cada teste rola de volta e criar o
mesmo psicólogo em testes diferentes não colide.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw clean test -Dtest=SegurancaHttpTest`
Expected: os quatro primeiros passam **201** onde deveriam dar 400. O último já deve passar — ele existe para pegar excesso de validação, não falta.

- [ ] **Step 3: `PacienteCadastroDTO`**

```java
public record PacienteCadastroDTO(
        @NotBlank(message = "Informe o nome do paciente.")
        @Size(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
        String nome,

        // formato brasileiro, com ou sem mascara: (51) 99811-2345, 51998112345,
        // +55 51 99811-2345. Opcional — quem nao tem telefone cadastra assim mesmo
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres.")
        @Pattern(regexp = "^$|^(\\+55\\s?)?\\(?\\d{2}\\)?[\\s-]?\\d{4,5}[\\s-]?\\d{4}$",
                 message = "Informe um telefone brasileiro válido, com DDD.")
        String telefone,

        // vira canal de aviso (spec 5.4): endereco invalido aqui e falha de
        // envio silenciosa depois
        @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres.")
        @Email(message = "Informe um e-mail em formato válido.")
        String email,

        @Past(message = "A data de nascimento deve estar no passado.")
        @IdadeMaxima(anos = 120, message = "Confira a data de nascimento.")
        LocalDate dataNascimento) {
}
```

O `@Pattern` aceita string vazia (`^$|...`) de propósito: telefone é opcional, e um campo enviado como `""` pelo front não pode virar erro de formato.

- [ ] **Step 4: `@IdadeMaxima`**

Anotação e validador no molde do `@SenhaForte` da Task 1 e do `HorizonteValido` que já existe:

```java
package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.lang.annotation.*;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Idade impossivel e quase sempre erro de digitacao no ano (1925 no lugar
 *  de 1995). @Past sozinho aceita 1825 sem reclamar. */
@Documented
@Constraint(validatedBy = IdadeMaximaValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface IdadeMaxima {
    int anos();
    String message() default "Confira a data de nascimento.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;
import java.time.Period;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IdadeMaximaValidator implements ConstraintValidator<IdadeMaxima, LocalDate> {

    private int anos;

    @Override
    public void initialize(IdadeMaxima anotacao) {
        this.anos = anotacao.anos();
    }

    @Override
    public boolean isValid(LocalDate nascimento, ConstraintValidatorContext contexto) {
        // nulo e valido: a data de nascimento e OPCIONAL por decisao do dono
        // (spec 5.8). Data futura e problema do @Past, nao deste validador
        if (nascimento == null || nascimento.isAfter(LocalDate.now())) {
            return true;
        }
        return Period.between(nascimento, LocalDate.now()).getYears() <= anos;
    }
}
```

- [ ] **Step 5: `PacienteAtualizacaoDTO`**

Ele já tem os `@Size`. Acrescente `@Email` no e-mail, o mesmo `@Pattern` no telefone, e `@Past` + `@IdadeMaxima` no nascimento — **mas não** `@NotBlank` em nada: atualização parcial manda campo ausente como nulo, e exigir presença aqui quebraria o `PUT` parcial que já existe.

- [ ] **Step 6: `./mvnw clean test`**

Expected: verde. Se algum fixture ou teste existente cadastrar paciente com telefone fora do formato ou e-mail inválido, **corrija o dado do teste** — é dado falso, e agora precisa ser falso *e* válido.

- [ ] **Step 7: Relatar. NÃO COMMITAR.**

---

### Task 3: `GET /consultas/conflito` — avisar antes do envio

**Files:**
- Modify: `controller/ConsultaController.java`
- Modify: `service/ConsultaService.java`
- Modify: `repository/ConsultaRepository.java`
- Create: `controller/dto/consulta/ConflitoResponseDTO.java`
- Test: novo `.../controller/ConsultaConflitoHttpTest.java`

**Interfaces:**
- Produces: `GET /consultas/conflito?dtConsulta=<ISO-8601>` → `ConflitoResponseDTO { boolean livre, String pacienteNome, UUID consultaId }`. A tela ② da Fatia 1 consome; o diálogo de consulta do front chama isto no `change` de data e hora.

**Por que este endpoint existe** (§5.6): a API já sabe recusar conflito no envio, com `ConflitoDeHorarioException`. O front, hoje, só descobre apanhando. §6.3 diz que o conflito é **checado antes do envio** — sem uma rota que responda "esse horário está livre?" sem criar nada, a checagem prévia não tem como acontecer.

**Ele diz quem está no horário, não só que está ocupado.** O protótipo aprovado mostra "já está ocupado por Otávio Lins" — um erro genérico obrigaria o psicólogo a ir procurar na agenda quem é.

- [ ] **Step 1: O teste que falha**

```java
    @Test
    @DisplayName("horario livre responde livre=true e sem paciente")
    void horarioLivre() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-01T09:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true))
               .andExpect(jsonPath("$.pacienteNome").doesNotExist());
    }

    @Test
    @DisplayName("horario ocupado responde livre=false e diz QUEM esta nele")
    void horarioOcupadoDizQuem() throws Exception {
        // consulta existente da Helena Vaz em 2026-12-02T14:00, criada no setup
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-02T14:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(false))
               .andExpect(jsonPath("$.pacienteNome").value("Helena Vaz"));
    }

    @Test
    @DisplayName("consulta de OUTRO psicologo no mesmo horario nao gera conflito")
    void agendaDeOutroPsicologoNaoConflita() throws Exception {
        // mesma hora da consulta do outro psicologo, criada no setup
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-03T10:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true));
    }

    @Test
    @DisplayName("consulta cancelada nao ocupa o horario")
    void canceladaNaoOcupa() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-04T11:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true));
    }
```

**O setup da classe.** `ConsultaConflitoHttpTest` é classe nova, então ela precisa
montar o próprio arnês. `@HttpTest` já dá `@Transactional` de classe, logo tudo o
que o setup criar desaparece no fim de cada teste:

```java
@HttpTest
class ConsultaConflitoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String tokenDoDono;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.conflito@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        Paciente helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-02T14:00:00"), Status.AGENDADA));
        consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-04T11:00:00"), Status.CANCELADA));

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.conflito@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        consultaRepository.save(Fixtures.consulta(
                outro, doOutro, LocalDateTime.parse("2026-12-03T10:00:00"), Status.AGENDADA));
    }
```

`Fixtures` (em `support/Fixtures.java`) já tem `usuario`, `paciente` e `consulta` —
não monte entidade à mão.

O terceiro teste é o que importa mais: **conflito é por psicólogo, não global**. Sem ele, a agenda de um psicólogo bloquearia horário na de outro — e o defeito seria descoberto só quando o segundo cliente pagante aparecesse.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw clean test -Dtest=ConsultaConflitoHttpTest`
Expected: **404** nos quatro — a rota não existe.

- [ ] **Step 3: A query**

Em `ConsultaRepository`:

```java
    // NENHUMA query nova. A de faixa que os caminhos de gravacao ja usam serve:
    List<Consulta> findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(
            Usuario usuario, LocalDateTime inicio, LocalDateTime fim);
```

Ela ja existe em `ConsultaRepository.java:18` — nao a redeclare. Uma query derivada por
horario exato **nao** deve ser criada: nao teria chamador, e ficaria como convite a
alguem usa-la achando que checa conflito.

> **CORRIGIDO durante a execução, 2026-09-08.** Este bloco dizia que a checagem compara o **início exato**, justificando com "o modelo não tem duração (§5.3 não a inclui)". A primeira metade é verdade — não existe coluna de duração. A segunda é falsa: `ConsultaService.java:44` já tem `private static final long DURACAO_CONSULTA_MINUTOS = 50`, três caminhos de gravação já checam conflito por janela de ±50min, e há um predicado privado pronto, `dentroDaJanela`, em `ConsultaService.java:263`.
>
> Se este endpoint respondesse por início exato, ele diria **"livre"** para as 09h30 havendo consulta às 09h00, e o `POST /consultas` recusaria em seguida. O front pergunta, recebe sim, envia e apanha — exatamente a falha que esta rota existe para eliminar, agora com o front confiando numa resposta errada. Pior que não ter a rota.
>
> A regra correta: **reusar a janela e o predicado que já existem**, com a query de faixa `findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull` que os outros três caminhos usam. Não criar query por horário exato. A borda de `dentroDaJanela` é inclusiva (`!isBefore`/`!isAfter`), então 14h00 e 14h50 conflitam; o endpoint de leitura tem que usar a mesma borda do caminho de gravação, seja ela qual for.

- [ ] **Step 4: O DTO**

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.util.UUID;

/** Resposta de GET /consultas/conflito (spec 5.6).
 *
 *  `pacienteNome` e `consultaId` vem nulos quando o horario esta livre. O
 *  nome vai junto de proposito: dizer so "ocupado" obrigaria o psicologo a
 *  ir procurar na agenda quem esta la. */
public record ConflitoResponseDTO(
        boolean livre,
        String pacienteNome,
        UUID consultaId) {

    // NAO se chama `livre()`: um record proibe metodo com o nome de um
    // componente, e `livre` e componente. `javac` recusa com
    // "invalid accessor method in record" — provado, nao suposto
    public static ConflitoResponseDTO horarioLivre() {
        return new ConflitoResponseDTO(true, null, null);
    }
}
```

- [ ] **Step 5: Serviço e rota**

`ConsultaService` é `@Service @RequiredArgsConstructor` e já tem
`private final SecurityService securityService;` e
`private final ConsultaRepository consultaRepository;`. Acrescente:

```java
    /** Responde se o horario esta livre na agenda DESTE psicologo, sem criar
     *  nada (spec 5.6). Conflito e por psicologo: dois profissionais podem
     *  ter paciente as 14h da mesma terca sem que um bloqueie o outro.
     *
     *  Usa a MESMA janela de DURACAO_CONSULTA_MINUTOS que os caminhos de
     *  gravacao, e reusa o predicado `dentroDaJanela` em vez de escrever
     *  outro. O `dentroDaJanela` e redundante com a faixa da query no caso
     *  simples, e e de proposito: e ele que garante que leitura e gravacao
     *  usem literalmente a mesma regra de borda, nao duas aproximacoes que
     *  concordam quase sempre. */
    @Transactional(readOnly = true)
    public ConflitoResponseDTO checarConflito(LocalDateTime dtConsulta) {
        Usuario usuario = securityService.obterUsuarioLogado();

        LocalDateTime inicioJanela = dtConsulta.minusMinutes(DURACAO_CONSULTA_MINUTOS);
        LocalDateTime fimJanela = dtConsulta.plusMinutes(DURACAO_CONSULTA_MINUTOS);

        return consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(
                        usuario, inicioJanela, fimJanela)
                .stream()
                .filter(c -> c.getStatus() != Status.CANCELADA)
                .filter(c -> dentroDaJanela(c.getDtConsulta(), dtConsulta))
                .findFirst()
                .map(c -> new ConflitoResponseDTO(
                        false, c.getPaciente().getNomePaciente(), c.getId()))
                .orElseGet(ConflitoResponseDTO::horarioLivre);
    }
```

`Consulta.paciente` é `FetchType.LAZY`, e `getNomePaciente()` é acessado dentro do método — por isso o `@Transactional(readOnly = true)`. Sem ele, o acesso acontece fora da sessão e vira `LazyInitializationException`.

No `ConsultaController`:

```java
    /** Responde se o horario esta livre, SEM criar nada (spec 5.6). E o que
     *  permite avisar do conflito ANTES do envio (6.3) — sem esta rota, o
     *  front so descobre apanhando no POST. */
    @GetMapping("/conflito")
    public ResponseEntity<ConflitoResponseDTO> conflito(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dtConsulta) {
        return ResponseEntity.ok(consultaService.checarConflito(dtConsulta));
    }
```

**Atenção à ordem das rotas:** `@GetMapping("/{id}")` já existe em `ConsultaController`. Uma rota literal como `/conflito` precisa não ser engolida pelo padrão `/{id}` — o Spring resolve literal antes de variável, então funciona, **mas confirme com o teste** em vez de confiar: se `/conflito` cair no handler de `/{id}`, o sintoma é um 400 de UUID malformado, não um 404.

- [ ] **Step 6: `./mvnw clean test`**

Expected: verde.

- [ ] **Step 7: Relatar. NÃO COMMITAR.**

---

### Task 4: `PATCH /consultas/{id}/status` — Realizada ou Faltou em um clique

**Files:**
- Modify: `controller/ConsultaController.java`
- Modify: `service/ConsultaService.java`
- Create: `controller/dto/consulta/StatusConsultaDTO.java`
- Test: novo `.../controller/ConsultaStatusHttpTest.java`

**Interfaces:**
- Produces: `PATCH /consultas/{id}/status` com corpo `{ "status": "REALIZADA" }` → `ConsultaResponseDTO`. A lista do dia da tela ① consome, em um clique na própria linha.

§4① é explícita: **"Realizada / Faltou em um clique na própria linha (hoje exige abrir diálogo)"**. Hoje só existe `PUT /consultas/{id}`, que troca data e status de uma vez — mandar a data junto para mudar só o status é pedir para o front reenviar dado que ele não está mudando, e é como se apagam campos sem querer.

- [ ] **Step 1: O teste que falha**

```java
    @Test
    @DisplayName("PATCH marca a sessao como realizada sem mexer na data")
    void marcarRealizada() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"REALIZADA"}
                         """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("REALIZADA"))
               .andExpect(jsonPath("$.dtConsulta").value(startsWith("2026-12-02T14:00")));
    }

    @Test
    @DisplayName("PATCH sem status responde 400")
    void statusAusenteResponde400() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("consulta de outro psicologo responde 404, nao 403")
    void consultaDeOutroPsicologoResponde404() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaDeOutroId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"FALTOU"}
                         """))
               .andExpect(status().isNotFound());
    }
```

**O setup da classe.** `ConsultaStatusHttpTest` é nova e os testes acima usam três
campos que o setup precisa preencher — `tokenDoDono`, `consultaId` e
`consultaDeOutroId`. Monte no molde do setup da Task 3 (mesmos `@Autowired`, mesmo
`Fixtures`), guardando os ids:

```java
    private String tokenDoDono;
    private UUID consultaId;
    private UUID consultaDeOutroId;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.status@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        Paciente helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        consultaId = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-02T14:00:00"), Status.AGENDADA)).getId();

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.status@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        consultaDeOutroId = consultaRepository.save(Fixtures.consulta(
                outro, doOutro, LocalDateTime.parse("2026-12-05T09:00:00"), Status.AGENDADA)).getId();
    }
```

O terceiro é o teste de isolamento, e **404 é deliberado, não 403**: responder 403 confirmaria que aquele id existe na base de outro psicólogo. É o padrão que esta base já usa (`SegurancaHttpTest.pacienteDeOutroPsicologoResponde404`) — siga-o.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw clean test -Dtest=ConsultaStatusHttpTest`
Expected: 404/405 nos três — a rota não existe.

- [ ] **Step 3: O DTO**

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import com.psychologyapi.psychologyapi.model.Status;
import jakarta.validation.constraints.NotNull;

/** Corpo de PATCH /consultas/{id}/status — so o status, de proposito.
 *  Mandar a data junto para trocar so o status faz o front reenviar dado
 *  que ele nao esta mudando, e e assim que campo se apaga sem querer. */
public record StatusConsultaDTO(
        @NotNull(message = "Informe o status da consulta.") Status status) {
}
```

- [ ] **Step 4: Serviço e rota**

No serviço: buscar por `findByIdAndUsuarioAndDeletedAtIsNull` (isolamento), lançar `ConsultaNaoEncontradaException` se vazio, trocar o status, salvar, registrar auditoria **do mesmo jeito que os outros métodos da classe fazem** — leia `atualizarConsulta` e siga o padrão, inclusive a chamada ao `AuditoriaService`.

No controller, `@PatchMapping("/{id}/status")` com `@Valid @RequestBody StatusConsultaDTO`.

- [ ] **Step 5: `./mvnw clean test`**

Expected: verde.

- [ ] **Step 6: Relatar. NÃO COMMITAR.**

---

### Task 5: `GET /painel/hoje` — a tela ① inteira numa chamada

**Files:**
- Create: `controller/PainelController.java`
- Create: `service/PainelService.java`
- Create: `controller/dto/painel/PainelHojeDTO.java`, `SessaoDoDiaDTO.java`, `PendenciaDoPainelDTO.java`
- Modify: `repository/ConsultaRepository.java`
- Test: novo `.../controller/PainelHojeHttpTest.java`

**Interfaces:**
- Produces: `GET /painel/hoje` → `PainelHojeDTO { SessaoDoDiaDTO proxima, List<SessaoDoDiaDTO> sessoes, List<PendenciaDoPainelDTO> pendencias }`. A tela ① inteira consome.

**Por que um endpoint e não três** (§5.6): hoje todo número do painel é `.length` de lista baixada para o navegador — "Pacientes cadastrados" conta a página que veio, não o total. Uma chamada devolve a tela.

**O que entra nas pendências nesta fatia, e só isto.** §9 avisa: *"O painel Hoje cresce por fatia. Na Fatia 1 ele traz sessão sem status; a pendência 'realizada sem anotação' chega na Fatia 2 e 'realizada sem baixa' na Fatia 3. A tela de §4 descreve o estado final, não o da Fatia 1 — o plano de implementação não deve tentar construir as três de uma vez."**

Então: **pendência aqui é sessão cuja hora já passou e que continua `AGENDADA`.** Nada mais. Não invente as outras duas.

- [ ] **Step 1: Os DTOs**

```java
package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.time.LocalDateTime;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.Status;

public record SessaoDoDiaDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        UUID pacienteId,
        String pacienteNome,
        Status status) {
}
```

```java
package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.time.LocalDateTime;
import java.util.UUID;

/** Sessao cuja hora ja passou e que continua AGENDADA (spec 4 (1)).
 *  As outras duas pendencias da tela final — realizada sem anotacao, e
 *  realizada sem baixa — sao das Fatias 2 e 3. Nao antecipar. */
public record PendenciaDoPainelDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        String pacienteNome) {
}
```

```java
package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.util.List;

/** A tela (1) inteira numa chamada (spec 5.6).
 *
 *  `proxima` e nula quando nao ha mais sessao hoje — inclusive no dia 1 do
 *  psicologo, que e um estado que a tela precisa saber desenhar.
 *
 *  NAO devolve "quanto falta" em minutos de proposito: seria um numero que
 *  nasce velho e envelhece na tela. O cliente calcula a partir de
 *  `dtConsulta`, que e um fato. */
public record PainelHojeDTO(
        SessaoDoDiaDTO proxima,
        List<SessaoDoDiaDTO> sessoes,
        List<PendenciaDoPainelDTO> pendencias) {
}
```

- [ ] **Step 2: Os testes que falham**

```java
    @Test
    @DisplayName("painel devolve as sessoes de hoje em ordem de horario")
    void sessoesDoDiaEmOrdem() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.sessoes.length()").value(3))
               .andExpect(jsonPath("$.sessoes[0].pacienteNome").value("Helena Vaz"))
               .andExpect(jsonPath("$.sessoes[2].pacienteNome").value("Clara Bueno"));
    }

    @Test
    @DisplayName("proxima e a primeira sessao que ainda nao comecou")
    void proximaEAPrimeiraQueNaoComecou() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.proxima.pacienteNome").value("Rui Sampaio"));
    }

    @Test
    @DisplayName("pendencia e sessao que passou da hora e continua agendada")
    void pendenciaESessaoPassadaSemStatus() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias.length()").value(1))
               .andExpect(jsonPath("$.pendencias[0].pacienteNome").value("Helena Vaz"));
    }

    @Test
    @DisplayName("agenda de outro psicologo nao aparece no painel")
    void painelNaoVazaEntrePsicologos() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.sessoes[*].pacienteNome")
                       .value(not(hasItem("Paciente do outro psicologo"))));
    }

    @Test
    @DisplayName("dia 1 do psicologo: tudo vazio, proxima nula, e 200 — nao 404")
    void diaUmRespondeVazio() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDeQuemNaoTemNada))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.proxima").doesNotExist())
               .andExpect(jsonPath("$.sessoes").isEmpty())
               .andExpect(jsonPath("$.pendencias").isEmpty());
    }
```

O último é o estado vazio que o protótipo desenhou e que a §9 chama de "o dia 1 do psicólogo pagante". **200 com listas vazias, nunca 404** — não há nada de errado em não ter sessão hoje.

**Os dois tokens.** Os testes usam `tokenDoDono` (o psicólogo com agenda) e
`tokenDeQuemNaoTemNada` (o do dia 1, sem paciente nem consulta). Ambos são campos
preenchidos no `@BeforeEach`, no molde do setup da Task 3 — o segundo é só
`usuarioRepository.save(Fixtures.usuario("dia.um@example.com"))` e o bearer dele,
sem criar mais nada. É justamente a ausência de dados que o último teste prova.

**O setup precisa fixar as horas em relação a `LocalDateTime.now()`**, não em horários absolutos: crie as sessões com `now().minusHours(2)` (passada, ainda `AGENDADA` → vira pendência), `now().plusHours(1)` e `now().plusHours(3)`. Teste que só passa em certa hora do dia é pior que nenhum.

Cuidado com a borda: se `now()` for perto da meia-noite, `now().plusHours(3)` cai no dia seguinte e o teste falha por motivo bobo. **Trate isso** — ou fixe o relógio com um `Clock` injetável, ou pule o teste fora de uma janela segura dizendo por quê. Diga no relatório o que escolheu.

- [ ] **Step 3: Rodar e ver falhar**

Run: `./mvnw clean test -Dtest=PainelHojeHttpTest`
Expected: **404** — a rota não existe.

- [ ] **Step 4: A query das pendências**

Em `ConsultaRepository`:

```java
    /** Sessao que passou da hora e continua AGENDADA — a unica pendencia da
     *  Fatia 1 (spec 4 (1) e 9). As outras duas chegam nas Fatias 2 e 3. */
    List<Consulta> findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
            Usuario usuario, Status status, LocalDateTime limite);
```

As sessões do dia reusam `findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull`, que já existe — **mas ela não ordena**. Acrescente uma variante ordenada por `dtConsulta` em vez de ordenar em memória; a tela mostra o dia em ordem e a ordenação é trabalho do banco.

- [ ] **Step 5: O serviço**

Mesmo formato dos outros serviços desta base — `@Service @RequiredArgsConstructor`, com `SecurityService` e `ConsultaRepository` como campos `private final`:

```java
    /** A tela (1) inteira numa chamada (spec 5.6).
     *
     *  readOnly porque monta DTO a partir de Consulta.paciente, que e LAZY:
     *  sem transacao o acesso ao nome vira LazyInitializationException. */
    @Transactional(readOnly = true)
    public PainelHojeDTO hoje() {
        Usuario usuario = securityService.obterUsuarioLogado();

        // com o fuso unico da spec 5.9 fixado no Dockerfile, "hoje" aqui e o
        // mesmo dia que o psicologo ve no relogio dele
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicioDoDia = agora.toLocalDate().atStartOfDay();
        LocalDateTime fimDoDia = agora.toLocalDate().atTime(LocalTime.MAX);

        List<SessaoDoDiaDTO> sessoes = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, inicioDoDia, fimDoDia)
                .stream()
                // cancelada nao e compromisso: nao ocupa a linha do dia
                .filter(c -> c.getStatus() != Status.CANCELADA)
                .map(c -> new SessaoDoDiaDTO(
                        c.getId(),
                        c.getDtConsulta(),
                        c.getPaciente().getId(),
                        c.getPaciente().getNomePaciente(),
                        c.getStatus()))
                .toList();

        // a proxima e a primeira que ainda nao comecou E continua agendada:
        // sessao ja marcada como realizada nao volta a ser "a proxima"
        SessaoDoDiaDTO proxima = sessoes.stream()
                .filter(s -> s.dtConsulta().isAfter(agora) && s.status() == Status.AGENDADA)
                .findFirst()
                .orElse(null);

        List<PendenciaDoPainelDTO> pendencias = consultaRepository
                .findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, Status.AGENDADA, agora)
                .stream()
                .map(c -> new PendenciaDoPainelDTO(
                        c.getId(), c.getDtConsulta(), c.getPaciente().getNomePaciente()))
                .toList();

        return new PainelHojeDTO(proxima, sessoes, pendencias);
    }
```

**Duas coisas que valem reparar no código acima**, porque são fáceis de errar:

`proxima` sai da lista **já filtrada** do dia, não de uma consulta nova ao banco — uma ida a menos, e garante que as duas partes da tela nunca discordem entre si.

A query de pendência **não** limita ao dia de hoje, de propósito: sessão de anteontem que ficou sem status continua pendente. Limitar a hoje faria a pendência sumir sozinha na virada da meia-noite, que é exatamente o comportamento que a faixa existe para impedir.

- [ ] **Step 6: O controller**

```java
@RestController
@RequestMapping("/painel")
public class PainelController {

    // injecao pelo mesmo padrao dos outros controllers desta base — leia um
    // deles antes de escrever

    /** A tela (1) inteira numa chamada (spec 5.6). */
    @GetMapping("/hoje")
    public ResponseEntity<PainelHojeDTO> hoje() {
        return ResponseEntity.ok(painelService.hoje());
    }
}
```

- [ ] **Step 7: `./mvnw clean test`**

Expected: verde.

- [ ] **Step 8: Relatar. NÃO COMMITAR.**

---

### Task 6: O contrato OpenAPI, de onde o front gera os tipos

**Files:**
- Create: `config/OpenApiConfig.java`
- Modify: `authsecurity/SecurityConfiguration.java`
- Modify: `src/main/resources/application.properties`
- Test: novo `.../controller/OpenApiHttpTest.java`

**Interfaces:**
- Produces: `GET /v3/api-docs` servindo o contrato em JSON. A fatia do frontend gera os tipos TypeScript a partir dele — é o que §9 quer dizer com "tipos gerados do OpenAPI".

`springdoc-openapi-starter-webmvc-ui` **já é dependência** do projeto (`pom.xml`), e nunca foi configurado nem exposto: `SecurityConfiguration` fecha tudo que não é `/auth/**` com `anyRequest().authenticated()`, então `/v3/api-docs` responde 401 hoje. Tipo gerado de contrato é o que impede o front e a API divergirem em silêncio — mas só se o contrato for alcançável.

**Isto expõe a superfície da API, e isso é uma decisão, não um detalhe.** O contrato revela caminhos e formatos — não dado de paciente, mas é informação que ajuda quem estiver atacando. Num produto com prontuário clínico, o certo é **desligar em produção**. Aqui ele fica ligado por padrão e desligável por variável de ambiente, e o desligamento entra no portão da Fatia 5 (§9).

- [ ] **Step 1: O teste que falha**

```java
    @Test
    @DisplayName("o contrato OpenAPI e alcancavel sem autenticacao")
    void contratoEPublico() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    @DisplayName("o contrato descreve os endpoints da Fatia 1")
    void contratoDescreveAFatia1() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(jsonPath("$.paths['/painel/hoje']").exists())
               .andExpect(jsonPath("$.paths['/consultas/conflito']").exists())
               .andExpect(jsonPath("$.paths['/auth/login']").exists());
    }

    @Test
    @DisplayName("expor o contrato nao abre nenhuma rota de dado")
    void contratoNaoAbreRotaDeDado() throws Exception {
        // sem token: /pacientes continua fechado
        mockMvc.perform(get("/pacientes"))
               .andExpect(status().isUnauthorized());
    }
```

O terceiro teste é o que impede esta tarefa de virar um buraco: mexer em `SecurityConfiguration` para liberar o contrato é exatamente o tipo de mudança que libera mais do que se pretendia.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw clean test -Dtest=OpenApiHttpTest`
Expected: **401** nos dois primeiros; o terceiro já passa.

- [ ] **Step 3: Liberar só o contrato**

Em `SecurityConfiguration`, a linha de autorização vira:

```java
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**").permitAll()
                    // contrato OpenAPI: o front gera os tipos a partir dele, e
                    // tipo gerado e o que impede front e API divergirem em
                    // silencio. Nao expoe dado — so caminhos e formatos.
                    // DESLIGAR EM PRODUCAO pela propriedade abaixo: num produto
                    // com prontuario, a superficie da API e informacao que
                    // ajuda quem ataca (portao da Fatia 5, spec 9)
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated())
```

- [ ] **Step 4: A propriedade**

Em `application.properties`:

```properties
# contrato OpenAPI: ligado em desenvolvimento, DESLIGADO em producao.
# O deploy da Fatia 5 tem que setar SPRINGDOC_ENABLED=false
springdoc.api-docs.enabled=${SPRINGDOC_ENABLED:true}
springdoc.swagger-ui.enabled=${SPRINGDOC_ENABLED:true}
```

- [ ] **Step 5: A identificação do contrato**

Sem isto o contrato sai com o título genérico do Spring, e o pacote de tipos gerado no front herda esse nome.

```java
package com.psychologyapi.psychologyapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    /** Identifica o contrato de onde o front gera os tipos (spec 9). */
    @Bean
    public OpenAPI contrato() {
        return new OpenAPI().info(new Info()
                .title("Psychology API")
                .version("v1")
                .description("API do consultorio: agenda, pacientes, prontuario e financeiro."));
    }
}
```

- [ ] **Step 6: `./mvnw clean test`**

Expected: verde.

- [ ] **Step 7: Relatar. NÃO COMMITAR.**

Inclua no relatório **o caminho exato do contrato** e o comando para baixá-lo com a aplicação no ar — a próxima fatia vai precisar disso para gerar os tipos.

---

## Verificação final da fase

- [ ] `./mvnw clean test` inteiro, verde, com a contagem
- [ ] `grep -rn "campo obrigatorio" backend/src` — zero
- [ ] Nenhuma migration nova: `ls backend/src/main/resources/db/migration/` mostra só `V1__20260901.sql`
- [ ] `grep -rn "ZonedDateTime\|OffsetDateTime\|Instant\|TIMESTAMPTZ" backend/src/main` — zero (§5.9)
- [ ] Toda query nova em `ConsultaRepository` tem `Usuario` na assinatura
- [ ] Com a aplicação no ar, `GET /v3/api-docs` responde 200 e lista `/painel/hoje` e `/consultas/conflito`

## O que este plano NÃO faz

- Qualquer coisa de frontend — é o plano irmão
- `GET /painel/mes` e `GET /financeiro/mes` — Fatia 3
- **A regra de "valor da sessão > 0, com 2 casas" da §6.3** — `valorSessao` está no `Paciente` desde a fase do schema, mas não aparece em DTO nenhum ainda. Quem o expõe é o formulário de paciente, que é da Fatia 2; validar um campo que a API não recebe seria código sem caminho até ele
- As pendências "realizada sem anotação" (Fatia 2) e "realizada sem baixa" (Fatia 3)
- Remarcar arrastando (§4②) — é interação de UI, não API
- Valor na consulta, baixa, e qualquer soma de dinheiro — Fatia 3
- `AvisoDeConsulta`: serviço, job ou envio — Fatia 4
- Backup, exportação, purga, Termo de Uso — Fatia 5, e dependem de fonte primária e de advogado (§10)
