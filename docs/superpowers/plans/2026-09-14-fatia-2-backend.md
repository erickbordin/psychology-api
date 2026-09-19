# Fatia 2 — Backend (A sessão) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ligar na borda HTTP a mudança de modelo de §5.1 — a anotação passa a ser *daquela sessão* — e entregar a ficha de página única de §4③ numa chamada só, com os campos de §5.4 do paciente e a pendência "realizada sem anotação" no painel Hoje.

**Architecture:** Nada de schema novo. A fase do schema (§ "Antes das fatias") já criou `anotacao.id_consulta`, os campos de §5.4 no `paciente` e o cancelamento em cascata de §5.10; esta fatia é quase toda *borda HTTP* — DTO, mapper, service, controller — sobre modelo que já existe e está desligado. O único endpoint novo é `GET /pacientes/{id}/ficha`, que segue a forma de `GET /painel/hoje`: uma tela, uma chamada, agregação no servidor.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Lombok, MapStruct, Flyway, springdoc-openapi 2.8.9, JUnit 5, Testcontainers (Postgres 16), MockMvc, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-01-redesenho-produto-design.md`

---

## Global Constraints

- **Texto de erro num só registro:** frase completa, com acento, dizendo o que fazer. `"Informe o nome do paciente."`, não `"campo obrigatorio"` (§6.3).
- **Todo erro sai no mesmo envelope**, o `ErroRespostaDTO` — `status`, `mensagem`, `erros[]`. 404 e 409 vêm com `erros` vazio e o motivo na mensagem (`GlobalExceptionsHandler`).
- **Isolamento por psicólogo é invariante**, não detalhe: toda consulta ao repositório filtra por `usuario` *e* por `deletedAtIsNull`. Dado de saúde vazando entre psicólogos é o pior defeito possível deste produto.
- **Dado falso é inventado, nunca real** — nenhum nome, telefone, e-mail ou anotação de pessoa real entra em teste ou fixture (LGPD).
- **A migration `V1__20260901.sql` não muda nesta fatia.** Nenhuma tarefa aqui precisa de coluna nova. Se alguma parecer precisar, pare e reporte em vez de editar.
- **Não commitar.** As tarefas terminam com o `git add`/`git commit` escritos para o dono rodar; o implementador não executa `git add`, `git commit`, `git stash` nem `git checkout`.
- **Fuso único** (§5.9): datas trafegam sem offset, no formato `yyyy-MM-dd'T'HH:mm:ss`. `FormatoDeDataConfig` já cuida das bordas de entrada.

---

## O que a varredura pré-voo encontrou (leia antes de começar)

Esta seção existe porque metade da Fatia 2 **já está implementada** e a tarefa mais cara seria reconstruí-la. Verificado em 14/09/2026 contra o código na `main`:

| Item da spec | Situação | Onde |
|---|---|---|
| §5.1 `anotacao.id_consulta` | **Existe no modelo e na migration**, e é `nullable` | `model/Anotacao.java:30`, `V1__20260901.sql:130` |
| §5.1 na borda HTTP | **NÃO existe** — `AnotacaoCadastroDTO` tem um campo só | Tarefa 1 |
| §5.4 campos do paciente | **Existem no modelo** (`valorSessao`, `consentimentoAvisoEm`, `canalAviso`) | `model/Paciente.java:49-57` |
| §5.4 na borda HTTP | **NÃO existem** em nenhum DTO de paciente | Tarefa 2 |
| §5.10 excluir cancela futuras | **Pronto**, com auditoria por consulta cancelada | `service/PacienteService.java:90-110` |
| Busca de paciente por nome | **Pronta**, `GET /pacientes?nome=` | `controller/PacienteController.java:39` |
| Ficha numa chamada | **NÃO existe** — hoje são duas listas correlacionadas no olho | Tarefa 3 |
| Pendência "realizada sem anotação" | **NÃO existe** — o painel só traz "passou da hora e continua AGENDADA" | Tarefa 4 |

**Não refaça nada da coluna "Pronto".** Em particular: não mexa em `deletarPaciente`, que já implementa §5.10 corretamente, incluindo o filtro que preserva o histórico (`REALIZADA`/`FALTOU`/`CANCELADA` não são tocadas).

### Duas decisões de escopo já tomadas, para você não reabrir

**1. `valor` e `pago` não entram na linha do tempo nesta fatia.** §4③ lista os dois, mas a própria spec avisa que a tela de §4 "descreve o estado final, não o da Fatia 1" — e a mesma regra vale aqui: **Fatia 3 é o dinheiro**. A linha do tempo da Fatia 2 carrega data, status e a anotação daquela sessão. Os campos `valor` e `pagoEm` já existem em `Consulta`; deixe-os quietos.

**2. A linha do tempo carrega uma *lista* de anotações por sessão, não uma.** §4③ diz "a anotação daquela sessão" no singular, mas nada no modelo nem na migration impede duas anotações na mesma consulta — `Anotacao.consulta` é `@ManyToOne` sem `unique`. Devolver só uma esconderia silenciosamente conteúdo clínico do prontuário, que é o pior modo de errar num produto com prazo de guarda legal (§7.3). Devolva todas, em ordem de criação.

### O padrão de teste da casa (não invente outro)

Teste de HTTP usa a anotação composta `@HttpTest`, e autentica assim — **não existe** nenhum helper `autenticado()`:

```java
Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.ficha@example.com"));
String tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

mockMvc.perform(get("/pacientes/" + pacienteId + "/ficha")
        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
       .andExpect(status().isOk());
```

`Fixtures` (em `support/Fixtures.java`) já tem `usuario(email)`, `paciente(dono, nome)`, `consulta(dono, paciente, dtConsulta, status)`, `anotacao(dono, paciente, conteudo)` e `pendencia(paciente, descricao)`. **`Fixtures.anotacao` não recebe consulta** — a Tarefa 1 adiciona a variante que recebe.

Quando o teste depende de "agora", fixe o relógio com uma `@TestConfiguration` local, como em `PainelHojeHttpTest.RelogioFixoConfig` — bean chamado `relogioFixo` (nome diferente do `clock` de produção, porque *bean overriding* é desligado por padrão) e `@Primary` para resolver a ambiguidade de tipo.

---

## File Structure

**Tarefa 1 — a anotação se prende à sessão**
- `controller/dto/anotacao/AnotacaoCadastroDTO.java` *(modificar)* — ganha `consultaId`, opcional
- `controller/dto/anotacao/AnotacaoResponseDTO.java` *(modificar)* — ganha `consultaId`
- `mapper/AnotacaoMapper.java` *(modificar)* — ignora `consulta` na entrada, projeta `consulta.id` na saída
- `service/AnotacaoService.java` *(modificar)* — resolve e valida a consulta
- `exceptions/SessaoDeOutroPacienteException.java` *(criar)*
- `controller/common/GlobalExceptionsHandler.java` *(modificar)* — mapeia a nova exceção para 409
- `support/Fixtures.java` *(modificar)* — variante de `anotacao` que recebe consulta
- Teste: `controller/AnotacaoNaSessaoHttpTest.java` *(criar)*

**Tarefa 2 — o paciente expõe valor, canal e consentimento**
- `controller/dto/paciente/PacienteResponseDTO.java` *(modificar)*
- `controller/dto/paciente/PacienteCadastroDTO.java` *(modificar)*
- `controller/dto/paciente/PacienteAtualizacaoDTO.java` *(modificar)*
- `controller/dto/paciente/EmailExigidoPeloCanal.java` + `EmailExigidoPeloCanalValidator.java` *(criar)* — regra condicional de §5.4
- `mapper/PacienteMapper.java` *(modificar)* — projeção dos campos novos e `NullValuePropertyMappingStrategy.IGNORE`
- Teste: `controller/PacienteCamposDaFichaHttpTest.java` *(criar)*

**Tarefa 3 — a ficha numa chamada**
- `controller/dto/ficha/FichaDoPacienteDTO.java`, `SessaoDaFichaDTO.java`, `AnotacaoDaSessaoDTO.java` *(criar)*
- `repository/AnotacaoRepository.java` *(modificar)* — busca por paciente com consulta preenchida
- `service/FichaService.java` *(criar)*
- `controller/FichaController.java` *(criar)*
- Teste: `controller/FichaDoPacienteHttpTest.java` *(criar)*

**Tarefa 4 — "realizada sem anotação" no painel Hoje**
- `controller/dto/painel/TipoPendencia.java` *(criar)*
- `controller/dto/painel/PendenciaDoPainelDTO.java` *(modificar)* — ganha `tipo`
- `service/PainelService.java` *(modificar)*
- Teste: `controller/PainelHojeHttpTest.java` *(modificar)*

---

### Task 1: A anotação se prende à sessão

Esta é a tarefa que a Fatia 2 existe para entregar. §5.1 chama a ligação
`anotacao → consulta` de "causa estrutural da confusão na ficha", e ela está
**implementada no banco e desligada na API**: nenhuma anotação criada hoje
consegue apontar para a sessão a que pertence, porque o DTO de entrada não tem
o campo.

**Files:**
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/anotacao/AnotacaoCadastroDTO.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/anotacao/AnotacaoResponseDTO.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/mapper/AnotacaoMapper.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/service/AnotacaoService.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/exceptions/SessaoDeOutroPacienteException.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/common/GlobalExceptionsHandler.java`
- Modify: `backend/src/test/java/com/psychologyapi/psychologyapi/support/Fixtures.java`
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/controller/AnotacaoNaSessaoHttpTest.java`

**Interfaces:**
- Consumes: `ConsultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(UUID, Usuario)` (já existe), `PacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(UUID, Usuario)` (já existe).
- Produces: `AnotacaoCadastroDTO(String anotacao, UUID consultaId)` e `AnotacaoResponseDTO(UUID id, String conteudo, LocalDate createdAt, UUID pacienteId, UUID consultaId)` — a Tarefa 3 lê o segundo, e o plano do frontend gera tipos a partir dos dois. `Fixtures.anotacaoDaSessao(Usuario, Paciente, Consulta, String)`.

- [ ] **Step 1: Escreva o teste que falha**

Crie `backend/src/test/java/com/psychologyapi/psychologyapi/controller/AnotacaoNaSessaoHttpTest.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * POST /pacientes/{id}/anotacoes com `consultaId` — a anotacao passa a ser
 * DAQUELA SESSAO (spec 5.1). O campo e opcional: anotacao avulsa continua
 * valida, so nao entra na linha do tempo da ficha.
 */
@HttpTest
class AnotacaoNaSessaoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String tokenDoDono;
    private Paciente helena;
    private Consulta sessaoDaHelena;
    private Consulta sessaoDoRui;
    private Consulta sessaoDeOutroPsicologo;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.anotacao@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        Paciente rui = pacienteRepository.save(Fixtures.paciente(dono, "Rui Sampaio"));

        sessaoDaHelena = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-01T09:00:00"), Status.REALIZADA));
        sessaoDoRui = consultaRepository.save(Fixtures.consulta(
                dono, rui, LocalDateTime.parse("2026-12-01T10:00:00"), Status.REALIZADA));

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.anotacao@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        sessaoDeOutroPsicologo = consultaRepository.save(Fixtures.consulta(
                outro, doOutro, LocalDateTime.parse("2026-12-01T11:00:00"), Status.REALIZADA));
    }

    private String corpo(String conteudo, UUID consultaId) {
        String idEmJson = consultaId == null ? "null" : "\"" + consultaId + "\"";
        return "{\"anotacao\":\"" + conteudo + "\",\"consultaId\":" + idEmJson + "}";
    }

    @Test
    @DisplayName("anotacao com consultaId volta presa aquela sessao")
    void anotacaoSePrendeASessao() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Trabalhamos a exposicao gradual.", sessaoDaHelena.getId())))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.consultaId").value(sessaoDaHelena.getId().toString()));
    }

    @Test
    @DisplayName("anotacao avulsa continua valida e volta com consultaId nulo")
    void anotacaoAvulsaContinuaValida() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Liguei fora do horario.", null)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.consultaId").doesNotExist());
    }

    @Test
    @DisplayName("nao prende anotacao a sessao de OUTRO paciente do mesmo psicologo")
    void naoPrendeASessaoDeOutroPaciente() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Anotacao trocada de ficha.", sessaoDoRui.getId())))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem").value(
                       "Esta sessão é de outro paciente. A anotação não pode ser presa a ela."))
               .andExpect(jsonPath("$.erros").isEmpty());
    }

    @Test
    @DisplayName("sessao de outro psicologo responde 404, nao 409 — nem confirma que existe")
    void sessaoDeOutroPsicologoNaoExisteParaMim() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Tentativa de alcancar outra agenda.", sessaoDeOutroPsicologo.getId())))
               .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("consultaId inexistente responde 404")
    void consultaInexistente() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Aponta pro nada.", UUID.randomUUID())))
               .andExpect(status().isNotFound());
    }
}
```

> **Por que 409 para outro paciente e 404 para outro psicólogo.** São
> perguntas diferentes. "Essa sessão é de outro paciente **seu**" é informação
> que o dono pode receber — ele é o controlador dos dois prontuários, e a
> mensagem ajuda a corrigir um clique errado. Já a sessão de outro psicólogo
> **não deve nem ser confirmada como existente**: responder 409 ali diria "esse
> UUID existe no sistema", que é vazamento de dado de saúde por canal lateral.
> 404 é a resposta honesta — para este usuário, aquela consulta não existe.

- [ ] **Step 2: Rode o teste e confirme que falha**

```bash
cd backend && ./mvnw test -Dtest=AnotacaoNaSessaoHttpTest
```

Esperado: FALHA na compilação do teste, porque `consultaId` não existe em
`AnotacaoResponseDTO` — e, se o JSON de entrada for aceito, o campo extra é
ignorado em silêncio e `$.consultaId` não aparece.

- [ ] **Step 3: Adicione `consultaId` aos dois DTOs**

`controller/dto/anotacao/AnotacaoCadastroDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.anotacao;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record AnotacaoCadastroDTO(
        @NotBlank(message = "Informe o conteúdo da anotação.") String anotacao,

        /** A sessao a que esta anotacao pertence (spec 5.1). Opcional: nulo e
         *  anotacao avulsa, que vale mas nao entra na linha do tempo. */
        UUID consultaId
) {}
```

`controller/dto/anotacao/AnotacaoResponseDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.anotacao;

import java.time.LocalDate;
import java.util.UUID;

public record AnotacaoResponseDTO(

        UUID id,
        String conteudo,
        LocalDate createdAt,
        UUID pacienteId,
        UUID consultaId

) {}
```

- [ ] **Step 4: Ensine o mapper**

Em `mapper/AnotacaoMapper.java`, acrescente `consulta` à lista de `ignore` da
entrada e projete `consulta.id` na saída:

```java
    @Mapping(target = "conteudo", source = "anotacao")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "paciente", ignore = true)
    @Mapping(target = "consulta", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Anotacao toEntity(AnotacaoCadastroDTO dto);

    /**
     * `consulta.id` e projecao de propriedade aninhada: o MapStruct gera o
     * null-check sozinho, entao anotacao avulsa sai com consultaId nulo em vez
     * de estourar NPE. O `ignore` na entrada e obrigatorio — sem ele o
     * MapStruct tentaria casar `consultaId` (UUID) com `consulta` (entidade) e
     * o build quebra em "Can't map property".
     */
    @Mapping(target = "pacienteId", source = "paciente.id")
    @Mapping(target = "consultaId", source = "consulta.id")
    AnotacaoResponseDTO toResponseDTO(Anotacao anotacao);
```

- [ ] **Step 5: Crie a exceção e mapeie para 409**

`exceptions/SessaoDeOutroPacienteException.java`:

```java
package com.psychologyapi.psychologyapi.exceptions;

/** A consulta existe e e do psicologo logado, mas pertence a outro paciente:
 *  prender a anotacao a ela misturaria dois prontuarios (spec 5.1). */
public class SessaoDeOutroPacienteException extends RuntimeException {

    public SessaoDeOutroPacienteException(String mensagem) {
        super(mensagem);
    }
}
```

Em `controller/common/GlobalExceptionsHandler.java`, ao lado do handler de
`TransicaoDeStatusInvalidaException` (mesmo status, mesma forma):

```java
    @ExceptionHandler(SessaoDeOutroPacienteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO sessaoDeOutroPaciente(SessaoDeOutroPacienteException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }
```

Acrescente o import correspondente no topo do arquivo.

- [ ] **Step 6: Resolva a consulta no service**

Em `service/AnotacaoService.java`, injete o `ConsultaRepository` e resolva
dentro de `cadastrar`, **antes** do `save`:

```java
        private final ConsultaRepository consultaRepository;
```

```java
        @Transactional
        public AnotacaoResponseDTO cadastrar(UUID pacienteId, AnotacaoCadastroDTO dto) {

                Usuario usuarioLogado = securityService.obterUsuarioLogado();
                Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
                Anotacao anotacao = anotacaoMapper.toEntity(dto);
                anotacao.setPaciente(paciente);
                anotacao.setUsuario(usuarioLogado);

                if (dto.consultaId() != null) {
                        // o filtro por usuario e o que faz a sessao de outro
                        // psicologo responder 404: para este usuario ela nao existe
                        Consulta consulta = consultaRepository
                                        .findByIdAndUsuarioAndDeletedAtIsNull(dto.consultaId(), usuarioLogado)
                                        .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));
                        if (!consulta.getPaciente().getId().equals(paciente.getId())) {
                                throw new SessaoDeOutroPacienteException(
                                                "Esta sessão é de outro paciente. A anotação não pode ser presa a ela.");
                        }
                        anotacao.setConsulta(consulta);
                }

                Anotacao anotacaoSalva = anotacaoRepository.save(anotacao);
                auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Anotacao", anotacaoSalva.getId());
                return anotacaoMapper.toResponseDTO(anotacaoSalva);
        }
```

Imports novos: `com.psychologyapi.psychologyapi.model.Consulta`,
`com.psychologyapi.psychologyapi.repository.ConsultaRepository`,
`com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException`,
`com.psychologyapi.psychologyapi.exceptions.SessaoDeOutroPacienteException`.

- [ ] **Step 7: Acrescente a fixture que a Tarefa 3 vai usar**

Em `support/Fixtures.java`, ao lado de `anotacao`:

```java
    public static Anotacao anotacaoDaSessao(Usuario dono, Paciente paciente, Consulta consulta, String conteudo) {
        Anotacao anotacao = anotacao(dono, paciente, conteudo);
        anotacao.setConsulta(consulta);
        return anotacao;
    }
```

- [ ] **Step 8: Rode o teste e confirme que passa**

```bash
cd backend && ./mvnw test -Dtest=AnotacaoNaSessaoHttpTest
```

Esperado: 5 testes, todos verdes.

- [ ] **Step 9: Rode a suíte inteira**

```bash
cd backend && ./mvnw test
```

Esperado: tudo verde. Atenção especial a testes que já exercitam
`POST /pacientes/{id}/anotacoes` — o campo novo é opcional, então nenhum
corpo antigo deve quebrar. Se algum quebrar, o campo não ficou opcional.

- [ ] **Step 10: Entregue o commit ao dono (não execute)**

```bash
git add backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/anotacao/ \
        backend/src/main/java/com/psychologyapi/psychologyapi/mapper/AnotacaoMapper.java \
        backend/src/main/java/com/psychologyapi/psychologyapi/service/AnotacaoService.java \
        backend/src/main/java/com/psychologyapi/psychologyapi/exceptions/SessaoDeOutroPacienteException.java \
        backend/src/main/java/com/psychologyapi/psychologyapi/controller/common/GlobalExceptionsHandler.java \
        backend/src/test/java/com/psychologyapi/psychologyapi/support/Fixtures.java \
        backend/src/test/java/com/psychologyapi/psychologyapi/controller/AnotacaoNaSessaoHttpTest.java
git commit -m "feat(anotacao): a anotacao passa a ser daquela sessao, nao do paciente em geral"
```

---

### Task 2: O paciente expõe valor, canal e consentimento

§4③ manda o cabeçalho da ficha mostrar "nome, idade, contato, **valor de
sessão, estado do consentimento**". Os três campos de §5.4 existem na entidade
desde a fase do schema e **nenhum DTO de paciente os menciona** — a API grava
`NENHUM` em `canalAviso` pelo `@PrePersist` e nunca mais deixa ninguém ver nem
mudar.

> **Esta tarefa arruma a dívida I6 no caminho, e isso é obrigatório, não
> oportunismo.** Hoje `atualizarPacienteAPartirDoDto` usa a estratégia padrão
> do MapStruct, `SET_TO_NULL`: campo ausente no PUT **apaga** o que estava
> salvo. Enquanto os campos eram nome/telefone/e-mail/nascimento, isso era uma
> dívida anotada. No instante em que `valorSessao` entra no PUT, editar o
> telefone de um paciente passa a **zerar o valor da sessão dele em silêncio** —
> perda de dado, não inconsistência de contrato. A correção é alinhar o mapper
> com o que o próprio DTO já declara em comentário desde agosto: *"atualizacao
> e parcial"*. Campo ausente passa a significar "não mexa".
>
> **Consequência a registrar:** com `IGNORE`, não há mais como *limpar* um
> campo via PUT (mandar `null` em telefone não apaga o telefone). Limpar campo
> passa a exigir mecanismo próprio, que nenhuma tela desta fatia pede. Isso vai
> para o laudo como item em aberto, não para o código como improviso.

**Files:**
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/paciente/PacienteResponseDTO.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/paciente/PacienteCadastroDTO.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/paciente/PacienteAtualizacaoDTO.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/paciente/EmailExigidoPeloCanal.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/paciente/EmailExigidoPeloCanalValidator.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/mapper/PacienteMapper.java`
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/controller/PacienteCamposDaFichaHttpTest.java`

**Interfaces:**
- Consumes: `Paciente.getValorSessao()` → `BigDecimal`, `Paciente.getCanalAviso()` → `CanalAviso`, `Paciente.getConsentimentoAvisoEm()` → `LocalDateTime` (todos já existem).
- Produces: `PacienteResponseDTO(UUID idPaciente, UUID idUsuario, String nome, String email, String telefone, LocalDate dataNascimento, BigDecimal valorSessao, CanalAviso canalAviso, LocalDateTime consentimentoAvisoEm, LocalDateTime createdAt)` — a Tarefa 3 embute este DTO inteiro no cabeçalho da ficha.

- [ ] **Step 1: Escreva o teste que falha**

Crie `backend/src/test/java/com/psychologyapi/psychologyapi/controller/PacienteCamposDaFichaHttpTest.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * Os tres campos de 5.4 na borda HTTP — valor de sessao, canal de aviso e
 * estado do consentimento — que o cabecalho da ficha (spec 4 (3)) precisa
 * mostrar. E a regra condicional: canal EMAIL exige e-mail.
 */
@HttpTest
class PacienteCamposDaFichaHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;

    private String token;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.campos@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
    }

    private String cadastrar(String corpo) throws Exception {
        return mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
               .andExpect(status().isCreated())
               .andReturn().getResponse().getContentAsString();
    }

    private String idDe(String json) {
        // o corpo e pequeno e a chave e unica: extrair por recorte evita
        // arrastar ObjectMapper para um teste que so precisa do id
        int i = json.indexOf("\"idPaciente\":\"") + 14;
        return json.substring(i, json.indexOf('"', i));
    }

    @Test
    @DisplayName("cadastro aceita valor de sessao e devolve nos tres campos da ficha")
    void cadastroAceitaEDevolveOsCamposDaFicha() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","valorSessao":180.00,"canalAviso":"NENHUM"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.valorSessao").value(180.00))
               .andExpect(jsonPath("$.canalAviso").value("NENHUM"))
               .andExpect(jsonPath("$.consentimentoAvisoEm").doesNotExist());
    }

    @Test
    @DisplayName("valor de sessao zero ou negativo e recusado com o campo culpado")
    void valorDeSessaoPrecisaSerPositivo() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","valorSessao":0}
                                """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("valorSessao"))
               .andExpect(jsonPath("$.erros[0].erro").value("O valor da sessão deve ser maior que zero."));
    }

    @Test
    @DisplayName("valor com mais de duas casas e recusado")
    void valorDeSessaoTemDuasCasas() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","valorSessao":180.005}
                                """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("valorSessao"));
    }

    @Test
    @DisplayName("canal EMAIL sem e-mail e recusado — 5.4, validacao condicional")
    void canalEmailExigeEmail() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","canalAviso":"EMAIL"}
                                """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("email"))
               .andExpect(jsonPath("$.erros[0].erro").value(
                       "Informe o e-mail do paciente para avisar por e-mail."));
    }

    @Test
    @DisplayName("canal EMAIL com e-mail passa")
    void canalEmailComEmailPassa() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","email":"helena@example.com","canalAviso":"EMAIL"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.canalAviso").value("EMAIL"));
    }

    @Test
    @DisplayName("canal NENHUM sem e-mail passa — e-mail continua opcional")
    void emailContinuaOpcionalSemCanalEmail() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz"}
                                """))
               .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("I6: editar o telefone NAO apaga o valor da sessao")
    void putParcialNaoApagaOQueNaoVeio() throws Exception {
        String id = idDe(cadastrar("""
                {"nome":"Helena Vaz","telefone":"51998112345","valorSessao":180.00}
                """));

        mockMvc.perform(put("/pacientes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telefone":"51999998888"}
                                """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.telefone").value("51999998888"))
               .andExpect(jsonPath("$.valorSessao").value(180.00))
               .andExpect(jsonPath("$.nome").value("Helena Vaz"));

        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.valorSessao").value(180.00))
               .andExpect(jsonPath("$.nome").value("Helena Vaz"));
    }
}
```

- [ ] **Step 2: Rode o teste e confirme que falha**

```bash
cd backend && ./mvnw test -Dtest=PacienteCamposDaFichaHttpTest
```

Esperado: FALHA — `valorSessao` e `canalAviso` não existem em nenhum DTO, e o
teste de I6 falha porque o PUT parcial hoje apaga `nome` e `valorSessao`.

- [ ] **Step 3: Acrescente os campos aos três DTOs**

`PacienteResponseDTO.java` — a ordem importa para a legibilidade do contrato
gerado, com `createdAt` ficando por último:

```java
package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.CanalAviso;

public record PacienteResponseDTO(

    UUID idPaciente,
    UUID idUsuario,
    String nome,
    String email,
    String telefone,
    LocalDate dataNascimento,

    /** Padrao que a consulta copia (spec 5.4). Nulo = psicologo ainda nao
     *  definiu valor para este paciente. */
    BigDecimal valorSessao,
    CanalAviso canalAviso,

    /** Quando o paciente consentiu em receber aviso. Nulo = nao consentiu, e
     *  entao nenhum aviso e agendado (invariante de 8.4). So de leitura aqui:
     *  consentir e um ato, nao um campo de formulario — a acao chega na
     *  Fatia 4. */
    LocalDateTime consentimentoAvisoEm,

    LocalDateTime createdAt

) {}
```

Em `PacienteCadastroDTO.java`, acrescente a anotação de classe e os dois campos
graváveis (mantenha os quatro campos existentes exatamente como estão):

```java
@EmailExigidoPeloCanal
public record PacienteCadastroDTO(
        // ... nome, telefone, email, dataNascimento sem alteracao ...

        // "> 0, 2 casas" (spec 6.3). @DecimalMin com inclusive=false e o
        // "maior que zero"; @Digits e as duas casas. Nao precisa de validador
        // proprio — ao contrario de @SenhaForte e @TelefoneBR, o Jakarta
        // expressa esta regra inteira.
        @DecimalMin(value = "0", inclusive = false, message = "O valor da sessão deve ser maior que zero.")
        @Digits(integer = 8, fraction = 2, message = "O valor da sessão deve ter no máximo duas casas decimais.")
        BigDecimal valorSessao,

        CanalAviso canalAviso) {
}
```

Imports novos: `java.math.BigDecimal`,
`jakarta.validation.constraints.DecimalMin`,
`jakarta.validation.constraints.Digits`,
`com.psychologyapi.psychologyapi.model.CanalAviso`.

Repita os dois campos, com as mesmas anotações e a mesma `@EmailExigidoPeloCanal`
na classe, em `PacienteAtualizacaoDTO.java`.

- [ ] **Step 4: Crie a validação condicional de §5.4**

`EmailExigidoPeloCanal.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Spec 5.4: "E-mail continua opcional — exceto quando o canal e EMAIL, ai e
 * obrigatorio. Validacao condicional, nao @NotNull cego."
 *
 * Vai na CLASSE, e nao no campo, porque a regra le dois campos ao mesmo tempo:
 * constraint de campo nao enxerga o vizinho.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailExigidoPeloCanalValidator.class)
public @interface EmailExigidoPeloCanal {

    String message() default "Informe o e-mail do paciente para avisar por e-mail.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

`EmailExigidoPeloCanalValidator.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.paciente;

import com.psychologyapi.psychologyapi.model.CanalAviso;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Aceita os dois records de paciente sem interface comum: o sealed-por-contrato
 * aqui seria cerimonia: um `instanceof` por tipo e menor e mais obvio do que
 * uma interface criada so para dois records de DTO se parecerem.
 *
 * O erro e reportado NO CAMPO `email`, e nao na classe, porque o envelope de
 * erro da casa entrega "campo culpado" e o formulario poe o foco nele — erro
 * de classe chegaria sem campo e o foco nao teria onde pousar.
 */
public class EmailExigidoPeloCanalValidator implements ConstraintValidator<EmailExigidoPeloCanal, Object> {

    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext context) {
        CanalAviso canal;
        String email;

        if (dto instanceof PacienteCadastroDTO cadastro) {
            canal = cadastro.canalAviso();
            email = cadastro.email();
        } else if (dto instanceof PacienteAtualizacaoDTO atualizacao) {
            canal = atualizacao.canalAviso();
            email = atualizacao.email();
        } else {
            return true;
        }

        if (canal != CanalAviso.EMAIL) {
            return true;
        }
        if (email != null && !email.isBlank()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
               .addPropertyNode("email")
               .addConstraintViolation();
        return false;
    }
}
```

> **Nota sobre o PUT parcial.** Num PUT que omite `canalAviso`, `canal` chega
> nulo e a regra passa — correto: o cliente não está mudando o canal. Mudar o
> canal para `EMAIL` sem e-mail no mesmo corpo continua sendo recusado, que é
> o caso que a regra existe para pegar.

- [ ] **Step 5: Ensine o mapper, e conserte o I6 no mesmo movimento**

Em `mapper/PacienteMapper.java`: a projeção dos campos novos é automática
(mesmo nome nos dois lados), então só o `toEntity` precisa parar de ignorar
`valorSessao` e `canalAviso`. E o método de atualização ganha a estratégia:

```java
    @Mapping(target = "nomePaciente", source = "nome")
    @Mapping(target = "emailPaciente", source = "email")
    @Mapping(target = "telefonePaciente", source = "telefone")
    @Mapping(target = "dataNascimentoPaciente", source = "dataNascimento")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "consentimentoAvisoEm", ignore = true)
    @Mapping(target = "tokenDescadastro", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "anotacoes", ignore = true)
    @Mapping(target = "consultas", ignore = true)
    @Mapping(target = "pendencias", ignore = true)
    Paciente toEntity(PacienteCadastroDTO dto);
```

(`valorSessao` e `canalAviso` saem da lista de `ignore`; `consentimentoAvisoEm`
continua ignorado, porque consentir não é campo de formulário.)

```java
    /**
     * NullValuePropertyMappingStrategy.IGNORE: campo ausente no PUT nao mexe no
     * que esta salvo. Sem isto — comportamento ate 14/09/2026, a divida I6 — a
     * estrategia padrao SET_TO_NULL fazia um PUT que so mudava o telefone
     * apagar nome, e-mail, nascimento e valor da sessao. Com `valorSessao` no
     * corpo, aquilo deixava de ser contrato torto e virava perda de dado.
     *
     * Em troca, mandar null nao limpa mais campo nenhum: limpar passa a exigir
     * mecanismo proprio, que nenhuma tela desta fatia pede.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "nomePaciente", source = "dto.nome")
    @Mapping(target = "telefonePaciente", source = "dto.telefone")
    @Mapping(target = "emailPaciente", source = "dto.email")
    @Mapping(target = "dataNascimentoPaciente", source = "dto.dataNascimento")
    void atualizarPacienteAPartirDoDto(PacienteAtualizacaoDTO dto, @MappingTarget Paciente paciente);
```

Imports novos: `org.mapstruct.BeanMapping`,
`org.mapstruct.NullValuePropertyMappingStrategy`.

- [ ] **Step 6: Rode o teste e confirme que passa**

```bash
cd backend && ./mvnw test -Dtest=PacienteCamposDaFichaHttpTest
```

Esperado: 7 testes verdes.

- [ ] **Step 7: Rode a suíte inteira, com atenção ao PUT**

```bash
cd backend && ./mvnw test
```

Esperado: tudo verde. Se algum teste antigo **esperava** que o PUT apagasse
campo omitido, ele documentava a dívida I6 — pare e reporte em vez de
"consertar" o teste: essa é uma mudança de contrato que o dono precisa ver.

- [ ] **Step 8: Entregue o commit ao dono (não execute)**

```bash
git add backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/paciente/ \
        backend/src/main/java/com/psychologyapi/psychologyapi/mapper/PacienteMapper.java \
        backend/src/test/java/com/psychologyapi/psychologyapi/controller/PacienteCamposDaFichaHttpTest.java
git commit -m "feat(paciente): valor, canal e consentimento na borda HTTP, e o PUT para de apagar campo omitido"
```

---

### Task 3: A ficha numa chamada

§4③: a ficha "perde as quatro abas e vira uma página", com cabeçalho, **linha
do tempo de sessões — uma linha por encontro, com a anotação daquela sessão ali
dentro** — e pendências ao lado. O texto da spec diz por que: as abas
*Consultas* e *Anotações* "são a mesma realidade partida em duas e
correlacionada no olho pela data".

Servir isso como três chamadas separadas recriaria o defeito no cliente: quem
correlaciona vira o front, pela data, que é exatamente o que §5.1 acabou de
tornar desnecessário. A ficha segue a forma de `GET /painel/hoje` — **uma tela,
uma chamada, junção no servidor**.

**Files:**
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/ficha/FichaDoPacienteDTO.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/ficha/SessaoDaFichaDTO.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/ficha/AnotacaoDaSessaoDTO.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/repository/AnotacaoRepository.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/service/FichaService.java`
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/FichaController.java`
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/controller/FichaDoPacienteHttpTest.java`

**Interfaces:**
- Consumes: `PacienteMapper.toResponseDTO(Paciente)` → `PacienteResponseDTO` com os campos da Tarefa 2; `PendenciaMapper.toResponseDTO(Pendencia)` → `PendenciaResponseDTO(UUID id, String descricao, Boolean concluido, LocalDateTime createdAt)`; `ConsultaRepository.findByPacienteAndUsuarioAndDeletedAtIsNull(Paciente, Usuario)`; `Fixtures.anotacaoDaSessao(...)` da Tarefa 1.
- Produces: `GET /pacientes/{pacienteId}/ficha` → `FichaDoPacienteDTO(PacienteResponseDTO paciente, List<SessaoDaFichaDTO> linhaDoTempo, List<PendenciaResponseDTO> pendencias)`, com `SessaoDaFichaDTO(UUID consultaId, LocalDateTime dtConsulta, Status status, List<AnotacaoDaSessaoDTO> anotacoes)` e `AnotacaoDaSessaoDTO(UUID anotacaoId, String conteudo, LocalDateTime createdAt)`. O plano do frontend gera os tipos daqui.

- [ ] **Step 1: Escreva o teste que falha**

Crie `backend/src/test/java/com/psychologyapi/psychologyapi/controller/FichaDoPacienteHttpTest.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.PendenciaRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * GET /pacientes/{id}/ficha — a pagina unica de spec 4 (3) numa chamada.
 *
 * A linha do tempo vem do MAIS RECENTE para o mais antigo: a ficha e aberta
 * para saber o que aconteceu na ultima sessao, nao na primeira.
 */
@HttpTest
class FichaDoPacienteHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;
    @Autowired private AnotacaoRepository anotacaoRepository;
    @Autowired private PendenciaRepository pendenciaRepository;

    private String tokenDoDono;
    private String tokenDoOutro;
    private Paciente helena;
    private Paciente semNada;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.ficha@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        semNada = pacienteRepository.save(Fixtures.paciente(dono, "Paciente Recem Cadastrado"));

        Consulta primeira = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-11-18T09:00:00"), Status.REALIZADA));
        Consulta segunda = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-11-25T09:00:00"), Status.REALIZADA));
        consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-02T09:00:00"), Status.AGENDADA));

        anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, helena, primeira, "Primeiro encontro, mapeamos a queixa."));
        anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, helena, segunda, "Retomamos o combinado da semana."));
        anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, helena, segunda, "Complemento escrito depois da sessao."));
        // avulsa: vale, mas NAO entra na linha do tempo (spec 5.1)
        anotacaoRepository.save(Fixtures.anotacao(dono, helena, "Liguei fora do horario."));

        pendenciaRepository.save(Fixtures.pendencia(helena, "Enviar recibo de novembro"));

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.ficha@example.com"));
        tokenDoOutro = "Bearer " + tokenService.gerarToken(outro.getEmailUsuario());
    }

    @Test
    @DisplayName("cabecalho traz os campos que a spec 4 (3) manda mostrar")
    void cabecalhoDaFicha() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.paciente.nome").value("Helena Vaz"))
               .andExpect(jsonPath("$.paciente.canalAviso").value("NENHUM"));
    }

    @Test
    @DisplayName("linha do tempo vem do encontro mais recente para o mais antigo")
    void linhaDoTempoDoMaisRecenteParaOMaisAntigo() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.linhaDoTempo.length()").value(3))
               .andExpect(jsonPath("$.linhaDoTempo[0].dtConsulta").value("2026-12-02T09:00:00"))
               .andExpect(jsonPath("$.linhaDoTempo[2].dtConsulta").value("2026-11-18T09:00:00"));
    }

    @Test
    @DisplayName("a anotacao da sessao vem DENTRO da linha daquela sessao")
    void anotacaoVemDentroDaSessao() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               // [0] e a de 02/12, ainda agendada e sem anotacao
               .andExpect(jsonPath("$.linhaDoTempo[0].anotacoes").isEmpty())
               // [1] e a de 25/11, com DUAS anotacoes. Afirmamos que as duas
               // estao la, e NAO em que ordem: `createdAt` e gravado por
               // LocalDateTime.now() no @PrePersist, e dois saves no mesmo
               // instante empatariam — teste que depende de desempate vira
               // intermitente na integracao
               .andExpect(jsonPath("$.linhaDoTempo[1].anotacoes.length()").value(2))
               .andExpect(jsonPath("$.linhaDoTempo[1].anotacoes[*].conteudo")
                       .value(hasItem("Retomamos o combinado da semana.")))
               .andExpect(jsonPath("$.linhaDoTempo[1].anotacoes[*].conteudo")
                       .value(hasItem("Complemento escrito depois da sessao.")))
               // [2] e a de 18/11, com uma
               .andExpect(jsonPath("$.linhaDoTempo[2].anotacoes.length()").value(1));
    }

    @Test
    @DisplayName("anotacao avulsa nao entra na linha do tempo")
    void avulsaNaoEntraNaLinhaDoTempo() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.linhaDoTempo[*].anotacoes[*].conteudo")
                       .value(not(hasItem("Liguei fora do horario."))));
    }

    @Test
    @DisplayName("pendencias do paciente vem junto, na mesma chamada")
    void pendenciasVemJunto() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias.length()").value(1))
               .andExpect(jsonPath("$.pendencias[0].descricao").value("Enviar recibo de novembro"));
    }

    @Test
    @DisplayName("paciente recem cadastrado abre a ficha vazia, com 200 — nao 404")
    void fichaVaziaAbre() throws Exception {
        mockMvc.perform(get("/pacientes/" + semNada.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.linhaDoTempo").isEmpty())
               .andExpect(jsonPath("$.pendencias").isEmpty());
    }

    @Test
    @DisplayName("ficha de paciente de outro psicologo responde 404")
    void fichaNaoVazaEntrePsicologos() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoOutro))
               .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("paciente inexistente responde 404")
    void pacienteInexistente() throws Exception {
        mockMvc.perform(get("/pacientes/" + UUID.randomUUID() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Rode o teste e confirme que falha**

```bash
cd backend && ./mvnw test -Dtest=FichaDoPacienteHttpTest
```

Esperado: FALHA — a rota `/pacientes/{id}/ficha` não existe, então tudo
responde 404 (inclusive os testes que esperam 200).

- [ ] **Step 3: Crie os três DTOs**

`controller/dto/ficha/AnotacaoDaSessaoDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.ficha;

import java.time.LocalDateTime;
import java.util.UUID;

/** A anotacao como ela aparece DENTRO da linha da sessao (spec 4 (3)).
 *  Sem pacienteId nem consultaId: a posicao na arvore ja diz os dois. */
public record AnotacaoDaSessaoDTO(
        UUID anotacaoId,
        String conteudo,
        LocalDateTime createdAt) {
}
```

`controller/dto/ficha/SessaoDaFichaDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.ficha;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.Status;

/**
 * Uma linha da linha do tempo: um encontro, com o que foi escrito nele.
 *
 * `anotacoes` e lista, e nao um campo unico, porque nada no modelo impede duas
 * anotacoes na mesma consulta — `Anotacao.consulta` e @ManyToOne sem unique.
 * Devolver so uma esconderia conteudo de prontuario em silencio, que e o pior
 * jeito de errar num registro com prazo de guarda legal (spec 7.3).
 *
 * `valor` e `pago` NAO entram aqui: sao da Fatia 3 (spec 9). Os campos ja
 * existem em Consulta; nao antecipar.
 */
public record SessaoDaFichaDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        Status status,
        List<AnotacaoDaSessaoDTO> anotacoes) {
}
```

`controller/dto/ficha/FichaDoPacienteDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.ficha;

import java.util.List;

import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaResponseDTO;

/** A tela (3) inteira numa chamada, como o painel de spec 5.6 faz com a (1). */
public record FichaDoPacienteDTO(
        PacienteResponseDTO paciente,
        List<SessaoDaFichaDTO> linhaDoTempo,
        List<PendenciaResponseDTO> pendencias) {
}
```

- [ ] **Step 4: Acrescente a busca de anotações com sessão**

Em `repository/AnotacaoRepository.java`:

```java
    /** As anotacoes do paciente que estao presas a alguma sessao — as avulsas
     *  ficam de fora, porque nao entram na linha do tempo (spec 5.1). Uma
     *  consulta so para todas elas: o alternativo seria uma por sessao, que e
     *  N+1 numa tela que abre a cada atendimento. */
    List<Anotacao> findByPacienteAndUsuarioAndConsultaIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(
            Paciente paciente, Usuario usuario);
```

Imports necessários no arquivo: `java.util.List` (confirme que já está lá).

- [ ] **Step 5: Crie o service**

`service/FichaService.java`:

```java
package com.psychologyapi.psychologyapi.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.ficha.AnotacaoDaSessaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.ficha.FichaDoPacienteDTO;
import com.psychologyapi.psychologyapi.controller.dto.ficha.SessaoDaFichaDTO;
import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.PacienteMapper;
import com.psychologyapi.psychologyapi.mapper.PendenciaMapper;
import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.PendenciaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FichaService {

    private final SecurityService securityService;
    private final PacienteRepository pacienteRepository;
    private final ConsultaRepository consultaRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final PendenciaRepository pendenciaRepository;
    private final PacienteMapper pacienteMapper;
    private final PendenciaMapper pendenciaMapper;
    private final AuditoriaService auditoriaService;

    /**
     * readOnly pelo mesmo motivo de PainelService.hoje(): o DTO e montado a
     * partir de associacoes LAZY, e sem transacao o acesso vira
     * LazyInitializationException.
     *
     * Tres consultas ao banco, nao N+1: uma de sessoes, uma de anotacoes com
     * sessao, uma de pendencias. A juncao acontece em memoria sobre um mapa
     * por consultaId — volume de autonomo e pequeno, mas uma query por linha
     * da ficha seria erro de graca.
     */
    @Transactional(readOnly = true)
    public FichaDoPacienteDTO doPaciente(UUID pacienteId) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuario)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        Map<UUID, List<AnotacaoDaSessaoDTO>> anotacoesPorSessao = anotacaoRepository
                .findByPacienteAndUsuarioAndConsultaIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(paciente, usuario)
                .stream()
                .collect(Collectors.groupingBy(
                        (Anotacao a) -> a.getConsulta().getId(),
                        Collectors.mapping(
                                a -> new AnotacaoDaSessaoDTO(a.getId(), a.getConteudo(), a.getCreatedAt()),
                                Collectors.toList())));

        List<SessaoDaFichaDTO> linhaDoTempo = consultaRepository
                .findByPacienteAndUsuarioAndDeletedAtIsNull(paciente, usuario)
                .stream()
                // do encontro mais recente para o mais antigo: a ficha e aberta
                // para saber o que aconteceu na ultima sessao
                .sorted(Comparator.comparing(c -> c.getDtConsulta(), Comparator.reverseOrder()))
                .map(c -> new SessaoDaFichaDTO(
                        c.getId(),
                        c.getDtConsulta(),
                        c.getStatus(),
                        anotacoesPorSessao.getOrDefault(c.getId(), List.of())))
                .toList();

        List<PendenciaResponseDTO> pendencias = pendenciaRepository
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(
                        paciente, usuario, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .map(pendenciaMapper::toResponseDTO)
                .toList();

        // abrir ficha e acesso a prontuario: registrar e exigencia, nao enfeite
        auditoriaService.registrar(usuario, AcaoAuditoria.VISUALIZACAO, "Paciente", paciente.getId());

        return new FichaDoPacienteDTO(
                pacienteMapper.toResponseDTO(paciente), linhaDoTempo, pendencias);
    }
}
```

- [ ] **Step 6: Crie o controller**

`controller/FichaController.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.ficha.FichaDoPacienteDTO;
import com.psychologyapi.psychologyapi.service.FichaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class FichaController {

    private final FichaService fichaService;

    @GetMapping("/{pacienteId}/ficha")
    public ResponseEntity<FichaDoPacienteDTO> ficha(@PathVariable UUID pacienteId) {
        return ResponseEntity.status(HttpStatus.OK).body(fichaService.doPaciente(pacienteId));
    }
}
```

> A rota fica em controller próprio, e não dentro de `PacienteController`,
> pelo mesmo motivo que `ConsultaDoPacienteController` e `PendenciaController`
> já existem separados: a casa agrupa por recurso servido, não por prefixo de
> URL. Confirme no `SecurityConfiguration` que `/pacientes/**` já exige
> autenticação — exige, e nenhuma regra nova é necessária.

- [ ] **Step 7: Rode o teste e confirme que passa**

```bash
cd backend && ./mvnw test -Dtest=FichaDoPacienteHttpTest
```

Esperado: 8 testes verdes.

- [ ] **Step 8: Rode a suíte inteira**

```bash
cd backend && ./mvnw test
```

- [ ] **Step 9: Entregue o commit ao dono (não execute)**

```bash
git add backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/ficha/ \
        backend/src/main/java/com/psychologyapi/psychologyapi/controller/FichaController.java \
        backend/src/main/java/com/psychologyapi/psychologyapi/service/FichaService.java \
        backend/src/main/java/com/psychologyapi/psychologyapi/repository/AnotacaoRepository.java \
        backend/src/test/java/com/psychologyapi/psychologyapi/controller/FichaDoPacienteHttpTest.java
git commit -m "feat(ficha): a pagina unica do paciente numa chamada, com a anotacao dentro da sessao"
```

---

### Task 4: "Realizada sem anotação" entra no painel Hoje

§9 é explícito sobre o ritmo: *"O painel Hoje cresce por fatia. Na Fatia 1 ele
traz sessão sem status; a pendência 'realizada sem anotação' chega na Fatia 2 e
'realizada sem baixa' na Fatia 3."* O `PendenciaDoPainelDTO` até carrega um
comentário dizendo isso.

Com duas espécies de pendência na mesma lista, o cliente precisa saber qual é
qual — a frase que a tela escreve é diferente ("marque o que aconteceu" contra
"registre a anotação"), e o clique leva a lugares diferentes. Por isso entra um
discriminador, e não uma segunda lista: a tela mostra **uma** fila de
pendências.

**Files:**
- Create: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/painel/TipoPendencia.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/painel/PendenciaDoPainelDTO.java`
- Modify: `backend/src/main/java/com/psychologyapi/psychologyapi/service/PainelService.java`
- Test: `backend/src/test/java/com/psychologyapi/psychologyapi/controller/PainelHojeHttpTest.java`

**Interfaces:**
- Consumes: `AnotacaoRepository.findByPacienteAndUsuarioAndConsultaIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc` **não** serve aqui (é por paciente); esta tarefa acrescenta um método próprio ao `AnotacaoRepository`.
- Produces: `PendenciaDoPainelDTO(UUID consultaId, LocalDateTime dtConsulta, String pacienteNome, TipoPendencia tipo)` com `TipoPendencia { SEM_STATUS, SEM_ANOTACAO }`. **Mudança de contrato:** o frontend da Fatia 1 já consome este DTO; o campo novo é aditivo, então nada quebra, mas os tipos gerados precisam ser regerados no plano do frontend.

- [ ] **Step 1: Escreva o teste que falha**

Em `controller/PainelHojeHttpTest.java`, acrescente ao `preparar()` — **depois**
das três consultas que já existem, sem mexer nelas:

```java
        // 09:00 ja passou e foi marcada REALIZADA, mas ninguem escreveu nada:
        // pendencia da Fatia 2 (spec 9)
        Paciente teodoro = pacienteRepository.save(Fixtures.paciente(dono, "Teodoro Alencar"));
        consultaRepository.save(Fixtures.consulta(
                dono, teodoro, LocalDateTime.parse("2026-12-02T09:00:00"), Status.REALIZADA));

        // 07:00 tambem foi REALIZADA, e esta anotada: NAO e pendencia
        Paciente nadia = pacienteRepository.save(Fixtures.paciente(dono, "Nadia Furtado"));
        Consulta anotada = consultaRepository.save(Fixtures.consulta(
                dono, nadia, LocalDateTime.parse("2026-12-02T07:00:00"), Status.REALIZADA));
        anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, nadia, anotada, "Sessao registrada no mesmo dia."));
```

Declare o campo `@Autowired private AnotacaoRepository anotacaoRepository;` e
importe `Consulta` e `AnotacaoRepository`.

Depois acrescente os testes:

```java
    @Test
    @DisplayName("sessao realizada sem anotacao vira pendencia — a da Fatia 2")
    void realizadaSemAnotacaoEPendencia() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias[?(@.tipo == 'SEM_ANOTACAO')].pacienteNome")
                       .value(hasItem("Teodoro Alencar")));
    }

    @Test
    @DisplayName("sessao realizada JA anotada nao vira pendencia")
    void realizadaAnotadaNaoEPendencia() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias[*].pacienteNome")
                       .value(not(hasItem("Nadia Furtado"))));
    }

    @Test
    @DisplayName("a pendencia da Fatia 1 continua, agora marcada SEM_STATUS")
    void pendenciaDeStatusContinuaEGanhaTipo() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias[?(@.tipo == 'SEM_STATUS')].pacienteNome")
                       .value(hasItem("Helena Vaz")));
    }
```

O teste `pendenciaESessaoPassadaSemStatus`, que afirma
`$.pendencias.length()` igual a 1, passa a esperar **2** — uma de cada tipo.
Ajuste o número e o `@DisplayName` para dizer o que ele prova agora.

- [ ] **Step 2: Rode o teste e confirme que falha**

```bash
cd backend && ./mvnw test -Dtest=PainelHojeHttpTest
```

Esperado: FALHA — não existe `tipo` no JSON, e Teodoro não aparece em
pendência nenhuma.

- [ ] **Step 3: Crie o discriminador**

`controller/dto/painel/TipoPendencia.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.painel;

/**
 * Que espécie de pendencia e esta. O painel cresce por fatia (spec 9): a
 * Fatia 1 so tinha SEM_STATUS, a Fatia 2 traz SEM_ANOTACAO, e a Fatia 3 vai
 * trazer a de baixa. O tipo existe porque a tela escreve uma frase diferente
 * e leva a um lugar diferente em cada caso.
 */
public enum TipoPendencia {

    /** A hora passou e a sessao continua AGENDADA: ninguem disse o que houve. */
    SEM_STATUS,

    /** A sessao foi dada como REALIZADA e nao tem anotacao presa a ela. */
    SEM_ANOTACAO
}
```

E em `PendenciaDoPainelDTO.java`:

```java
package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma linha da fila de pendencias da tela (1). O `tipo` diz qual das especies
 *  e — "realizada sem baixa" chega na Fatia 3 (spec 9). */
public record PendenciaDoPainelDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        String pacienteNome,
        TipoPendencia tipo) {
}
```

- [ ] **Step 4: Acrescente a busca ao repositório**

Em `repository/AnotacaoRepository.java`:

```java
    /** Os ids das consultas, entre as informadas, que ja tem anotacao. Uma
     *  consulta so para o dia inteiro, em vez de uma por sessao. */
    @Query("""
            SELECT DISTINCT a.consulta.id FROM Anotacao a
            WHERE a.consulta.id IN :consultaIds AND a.deletedAt IS NULL
            """)
    List<UUID> idsDeConsultasComAnotacao(@Param("consultaIds") List<UUID> consultaIds);
```

Imports: `org.springframework.data.jpa.repository.Query`,
`org.springframework.data.repository.query.Param`, `java.util.UUID`.

- [ ] **Step 5: Some a pendência nova no service**

Em `service/PainelService.java`, injete `AnotacaoRepository` e substitua o
trecho que monta `pendencias`:

```java
        List<PendenciaDoPainelDTO> semStatus = consultaRepository
                .findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, Status.AGENDADA, agora)
                .stream()
                .map(c -> new PendenciaDoPainelDTO(
                        c.getId(), c.getDtConsulta(), c.getPaciente().getNomePaciente(),
                        TipoPendencia.SEM_STATUS))
                .toList();

        // "realizada sem anotacao" (spec 9, Fatia 2). So as de HOJE: a fila do
        // painel e do dia, e varrer o historico inteiro transformaria a tela
        // numa lista de tudo que nunca foi escrito.
        List<Consulta> realizadasHoje = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, inicioDoDia, fimDoDia)
                .stream()
                .filter(c -> c.getStatus() == Status.REALIZADA)
                .toList();

        List<UUID> jaAnotadas = realizadasHoje.isEmpty()
                ? List.of()
                : anotacaoRepository.idsDeConsultasComAnotacao(
                        realizadasHoje.stream().map(Consulta::getId).toList());

        List<PendenciaDoPainelDTO> semAnotacao = realizadasHoje.stream()
                .filter(c -> !jaAnotadas.contains(c.getId()))
                .map(c -> new PendenciaDoPainelDTO(
                        c.getId(), c.getDtConsulta(), c.getPaciente().getNomePaciente(),
                        TipoPendencia.SEM_ANOTACAO))
                .toList();

        List<PendenciaDoPainelDTO> pendencias = Stream.concat(semStatus.stream(), semAnotacao.stream())
                .sorted(Comparator.comparing(PendenciaDoPainelDTO::dtConsulta))
                .toList();
```

Imports novos: `java.util.Comparator`, `java.util.UUID`,
`java.util.stream.Stream`, `com.psychologyapi.psychologyapi.model.Consulta`,
`com.psychologyapi.psychologyapi.controller.dto.painel.TipoPendencia`,
`com.psychologyapi.psychologyapi.repository.AnotacaoRepository`.

> A guarda `realizadasHoje.isEmpty()` não é paranoia: `IN ()` com lista vazia
> é SQL inválido em Postgres, e o dia 1 do psicólogo — que já tem teste —
> cairia exatamente nesse caminho.

- [ ] **Step 6: Rode o teste e confirme que passa**

```bash
cd backend && ./mvnw test -Dtest=PainelHojeHttpTest
```

- [ ] **Step 7: Rode a suíte inteira**

```bash
cd backend && ./mvnw test
```

- [ ] **Step 8: Entregue o commit ao dono (não execute)**

```bash
git add backend/src/main/java/com/psychologyapi/psychologyapi/controller/dto/painel/ \
        backend/src/main/java/com/psychologyapi/psychologyapi/service/PainelService.java \
        backend/src/main/java/com/psychologyapi/psychologyapi/repository/AnotacaoRepository.java \
        backend/src/test/java/com/psychologyapi/psychologyapi/controller/PainelHojeHttpTest.java
git commit -m "feat(painel): realizada sem anotacao entra na fila de pendencias do dia"
```

---

## Fora do escopo desta fatia, de propósito

- **`valor` e `pago` na linha do tempo** — Fatia 3 (§9). Os campos existem em `Consulta` desde a fase do schema.
- **Consentimento como ação** (`POST /pacientes/{id}/consentimento`) — Fatia 4. Aqui ele é só de leitura.
- **As 18 rotas de escrita que declaram `200` no contrato OpenAPI** enquanto a API responde 201/204 — dívida registrada no laudo da Fatia 1, decisão do dono. Nenhum endpoint novo desta fatia acrescenta um caso: `GET /pacientes/{id}/ficha` responde 200 e o springdoc infere 200.
- ~~**Limpar campo via PUT** — consequência declarada da correção do I6 na Tarefa 2. Nenhuma tela desta fatia pede.~~ **Revertido em 18/09/2026 — ver abaixo.**

---

## O que a revisão do conjunto mudou (18 de setembro de 2026)

Esta fatia foi revisada por inteiro depois de entregue, no mesmo rito da Fatia 1.
A suíte saiu de 296 para 316 testes. Três decisões do dono mudaram o que este
plano tinha escrito — quem for planejar a Fatia 3 deve ler **daqui**, não das
seções acima, nos pontos abaixo.

**A premissa da linha riscada caiu dentro da própria fatia.** "Nenhuma tela desta
fatia pede" era verdade quando o plano foi escrito e deixou de ser quando o
formulário de paciente ficou pronto: ele manda o campo vazio no PUT, e o
servidor respondia 200 sem limpar nada. A regra agora é **branco limpa, ausente
não toca**, valendo para `email` e `telefone`. O `NullValuePropertyMappingStrategy.IGNORE`
da Tarefa 2 **continua valendo e é o que protege o resto** — o que mudou é que
existe uma forma explícita de pedir a limpeza, e ela não é o nulo. Detalhe do
raciocínio de produto em §5.4 do spec.

**A validação condicional do canal saiu do bean para o serviço.** Enquanto morou
no `EmailExigidoPeloCanalValidator`, ela só via o corpo: `{"canalAviso":"EMAIL"}`
sozinho levava 400 mesmo com e-mail salvo. Agora é avaliada sobre o estado
efetivo (corpo ∪ entidade) no caminho da atualização. O validador de bean fica
**só no cadastro**, onde ver apenas o corpo é o certo.

**`DELETE /consultas/{id}` passou a ser recusado quando a sessão tem anotação
viva** (409). Excluir a sessão não apagava a anotação, mas a tirava da ficha —
prontuário invisível, em silêncio. Raciocínio completo em §7.3 do spec.

**Uma armadilha técnica que vale para toda anotação de contrato daqui para
frente.** O F1 da revisão (o contrato publicava `minimum: 0` num campo que a API
recusa em zero) não fecha do jeito óbvio: `@Schema(exclusiveMinimum = true)` é a
forma do **OpenAPI 3.0** e o serializador 3.1 a descarta sem erro;
`exclusiveMinimumValue` não serve porque seu default é `0`, o que torna zero
indistinguível de "não informado"; e `@Schema(minimum = ...)` explícito é
**sobreposto** pelo valor que o springdoc deriva do `@DecimalMin`. O que funciona
é fazer a própria constraint Jakarta dizer a regra na forma inclusiva —
`@DecimalMin("0.01")`, que com `@Digits(fraction = 2)` recusa exatamente o mesmo
conjunto que `> 0`. Contrato e validação passam a concordar por construção, sem
anotação paralela para alguém dessincronizar depois.
