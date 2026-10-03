# Fatia 3 — O dinheiro — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a sessão realizada passa a carregar valor e baixa; a tela `/financeiro` soma o mês por competência e lista quem deve; a Hoje e a ficha passam a mostrar e a dar baixa.

**Architecture:** o backend ganha a regra do valor dentro do `ConsultaService` (congela ao virar REALIZADA, trava quem tenta apagar dinheiro registrado), um `PagamentoService` para baixa, desfazer, lote e valor avulso, e duas leituras agregadas em SQL (`/financeiro/mes`, `/painel/mes`). O contrato OpenAPI é regerado e o frontend ganha um módulo de dinheiro compartilhado (`telas/financeiro/queries.ts`), a tela Financeiro e as mudanças em Hoje e Ficha.

**Tech Stack:** Java 21, Spring Boot 3.5, JPA/Hibernate 6, MapStruct, Flyway, JUnit 5 + Mockito + Testcontainers · React 19, TypeScript, TanStack Query 5, react-hook-form + zod 4, Radix Dialog, Vitest + Testing Library + MSW, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-01-fatia-3-dinheiro-design.md` — leia a spec inteira antes da sua tarefa. Decisões F1–F20 são do dono e não se renegociam.

## Global Constraints

Toda tarefa obedece a esta seção, além do próprio texto.

- **NÃO faça commit, `git add`, `git stash`, `git push`, `git checkout` nem `git reset`.** O dono comita. Trabalhe na árvore como está (branch `fatia-3/dinheiro`). Ao terminar, reporte a lista exata de arquivos criados/modificados.
- **NENHUM comentário novo em código-fonte — nem em teste.** Nada de `//`, `/* */`, `/** */`, `{/* */}` ou `#` novos. `@DisplayName` e nomes de teste são o lugar da explicação. **Não remova nem edite comentário existente**, exceto os trechos listados nominalmente na sua tarefa (decisão do dono: remover só o que a mudança torna falso, sem reescrever).
- **Não toque em arquivo fora da lista da sua tarefa.** Se precisar, pare e reporte `NEEDS_CONTEXT` dizendo qual e por quê.
- **Cópia nova com acento (F20)**, exatamente como escrita neste plano. Cópia existente sem acento fica intocada.
- **Mensagens de erro da API** exatamente como neste plano (são contrato com o front e com o dono).
- **Isolamento:** todo método novo de service busca o usuário por `securityService.obterUsuarioLogado()` e toda consulta ao banco filtra por `usuario` e `deletedAt IS NULL` da `Consulta`. Registro de outro psicólogo = 404.
- **Toda mutação** termina com `save`/`saveAll` explícito, roda em `@Transactional`, e audita (`auditoriaService.registrar(usuario, AcaoAuditoria.ATUALIZACAO, "Consulta", id)`) **depois** do save.
- **Controller** usa `@RequestBody` de `org.springframework.web.bind.annotation`, retorna `ResponseEntity<X>` explícito.
- **Backend:** rode da pasta `backend/`: `sh ./mvnw -q test -Dtest='NomeDaClasse'` (várias: `-Dtest='A,B'`). A suíte inteira: `sh ./mvnw -q test`. Precisa de Docker (Testcontainers) — já está no ar.
- **Frontend:** o Node global é 9 e quebra tudo. **Todo** comando de node da pasta `frontend/` leva o prefixo `ASDF_NODEJS_VERSION=20.19.1`: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/caminho`, `ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit -p .`.
- **Testes de front navegam por rótulo escrito** (`getByRole`, `getByText`, `getByLabelText`), **nunca** por `className`. MSW responde o contrato real (mesmo corpo, mesmo envelope `{status, mensagem, erros[]}`, mesmos códigos).
- **Valores monetários no JSON** são números com duas casas (`180.00`). No front, formatação só por `emReais()` de `src/ui/dinheiro.ts` (Tarefa 6). `toLocaleString` com moeda insere espaço inseparável (`R$ 150,00` com U+00A0); o normalizador padrão do Testing Library trata isso como espaço, então `getByText('R$ 150,00')` funciona.
- **Ao fim da tarefa**, a suíte da sua ponta inteira tem que estar verde (`sh ./mvnw -q test` ou `npx vitest run` + `tsc`). Se um teste antigo quebrar por causa de um record que ganhou campo, ajuste a chamada do teste — nunca o comportamento.

## Review Focus

1. **Borda do mês no Postgres.** `LocalTime.MAX` tem nanossegundos; o Postgres guarda microssegundos e arredonda `23:59:59.999999999` para a meia-noite seguinte. Toda consulta nova por mês usa **intervalo semiaberto** `[início do mês, início do mês seguinte)`. Teste: sessão em `2026-12-31T23:30` conta em dezembro; sessão em `2027-01-01T00:00` não conta (Tarefa 3).
2. **A mesma sessão com duas pendências na Hoje** (sem anotação e sem baixa) vira UMA linha com as duas frases e o botão "Dar baixa" (Tarefas 4 e 8).
3. **Baixa repetida** (duplo clique, duas abas) não muda `pagoEm` nem audita de novo (Tarefa 2).
4. **Paciente excluído com débito:** aparece em "Quem está devendo" sem link para a ficha, e "Dar baixa em todas" funciona porque o lote vai por ids (Tarefas 3 e 7).
5. **Privacidade no Financeiro** borra nomes, todo R$ (totais inclusive) e o nome dentro do anúncio do lote; chips e contagens ficam nítidos (Tarefa 7).

## Mapa de arquivos

Backend (`backend/src/main/java/com/psychologyapi/psychologyapi/`, abreviado `M/`; testes em `backend/src/test/java/com/psychologyapi/psychologyapi/`, abreviado `T/`):

| Arquivo | Responsabilidade | Tarefa |
|---|---|---|
| `M/exceptions/SessaoComBaixaException.java` (novo) | 409 "desfaça a baixa antes" | 1 |
| `M/exceptions/BaixaInvalidaException.java` (novo) | 409 "só realizada/defina o valor" | 2 |
| `M/service/dinheiro/ValorDaSessao.java` (novo) | regra única do congelamento do valor | 1 |
| `M/service/ConsultaService.java` | congela valor; travas de status, PUT e DELETE | 1 |
| `M/controller/dto/consulta/ConsultaResponseDTO.java` | + `valor`, `pagoEm` | 1 |
| `M/model/Consulta.java` | só remove o comentário que fica falso | 1 |
| `M/controller/common/GlobalExceptionsHandler.java` | handlers 409 novos | 1, 2 |
| `M/service/PagamentoService.java` (novo) | baixa, desfazer, lote, valor | 2 |
| `M/controller/PagamentoController.java` (novo) | rotas de dinheiro sob `/consultas` | 2 |
| `M/controller/dto/consulta/BaixaEmLoteDTO.java`, `BaixaEmLoteResponseDTO.java`, `ValorDaConsultaDTO.java` (novos) | corpo e resposta | 2 |
| `M/repository/ConsultaRepository.java` | `findByIdIn...`, agregações, leituras do mês | 2, 3 |
| `M/repository/projecao/TotaisFinanceirosDoMes.java`, `ContagemDoMes.java` (novos) | projeções das agregações | 3 |
| `M/service/periodo/MesConsultado.java` (novo) | resolve `ano`/`mes` + intervalo semiaberto | 3 |
| `M/service/FinanceiroService.java`, `M/controller/FinanceiroController.java` (novos) | `GET /financeiro/mes` | 3 |
| `M/controller/dto/financeiro/*.java` (novos) | `FinanceiroMesDTO`, `SessaoFinanceiraDTO`, `DevedorDTO` | 3 |
| `M/service/PainelService.java`, `M/controller/PainelController.java` | `GET /painel/mes`; SEM_BAIXA na Hoje | 4 |
| `M/controller/dto/painel/*` | `PainelMesDTO` (novo); `SessaoDoDiaDTO` + campos; `TipoPendencia` + SEM_BAIXA | 4 |
| `M/controller/dto/ficha/SessaoDaFichaDTO.java`, `M/service/FichaService.java` | + `valor`, `pagoEm` | 4 |

Frontend (`frontend/`):

| Arquivo | Responsabilidade | Tarefa |
|---|---|---|
| `contrato-openapi.json`, `src/api/contrato.ts` | regerados | 5 (orquestrador) |
| `vite.config.ts` | proxy de `/financeiro` | 6 |
| `src/ui/dinheiro.ts` (novo) | `emReais`, `mesPorExtenso`, `mesVizinho` | 6 |
| `src/telas/financeiro/queries.ts` (novo) | tipos, leituras e as 4 mutações de dinheiro | 6 |
| `src/telas/hoje/queries.ts` | invalidação do status + tipo `SessaoDoDia` | 6, 8 |
| `src/telas/financeiro/FinanceiroPage.tsx`, `DialogoDeValor.tsx`, `esquema.ts` (novos) | a tela | 7 |
| `src/rotas.tsx`, `src/estilo/estilo.css` | rota e estilo | 7 |
| `src/telas/hoje/fila.ts`, `HojePage.tsx` | `tipos[]`, SEM_BAIXA, pago/desfazer | 8 |
| `src/telas/ficha/queries.ts`, `LinhaDoTempo.tsx` | valor, chip e baixa na linha | 9 |
| `e2e/fluxo-do-dinheiro.spec.ts` (novo), READMEs, `ai/Claude.md` | fluxo real e docs | 10 |

---

### Task 1: O valor congela ao virar REALIZADA, e a baixa trava quem apagaria dinheiro

**Files:**
- Create: `M/service/dinheiro/ValorDaSessao.java`
- Create: `M/exceptions/SessaoComBaixaException.java`
- Modify: `M/service/ConsultaService.java` (`atualizarStatus`, `atualizarConsulta`, `deletarConsulta`)
- Modify: `M/controller/dto/consulta/ConsultaResponseDTO.java`
- Modify: `M/controller/common/GlobalExceptionsHandler.java`
- Modify: `M/model/Consulta.java` (só a remoção abaixo)
- Test: `T/service/ConsultaServiceTest.java` (acrescentar), `T/controller/ConsultaComBaixaHttpTest.java` (criar)

**Interfaces:**
- Produces: `ValorDaSessao.congelarSeRealizada(Consulta)` (estático); `SessaoComBaixaException(String)` com as constantes `AO_MUDAR_STATUS`, `AO_ALTERAR_VALOR`, `AO_EXCLUIR`; `ConsultaResponseDTO(..., UUID serieId, BigDecimal valor, LocalDateTime pagoEm)`.

**Regras exatas (spec §3.1 e §3.3):**
- Ao virar REALIZADA, se `valor == null`: `paciente.valorSessao`; se nulo, `usuario.valorPadraoSessao`; se nulo, fica nulo. Valor já preenchido **nunca** é sobrescrito. Nenhum caminho apaga valor.
- Com `pagoEm != null`: `PATCH /status` para destino ≠ REALIZADA → 409 `AO_MUDAR_STATUS`; `PUT` com `status` não nulo e ≠ REALIZADA → 409 `AO_MUDAR_STATUS`; `DELETE` → 409 `AO_EXCLUIR`. Todas as travas vêm **antes** de qualquer mutação. No `DELETE`, a guarda de anotação existente continua primeiro.
- `cadastrar` e `cadastrarSerie` não mudam.

- [ ] **Step 1: Remova o comentário que a fatia torna falso em `Consulta.java`**

Apague exatamente estas quatro linhas (e nada mais) acima de `@Column(name = "valor_consulta", ...)`:

```java
    // COPIA do valor vigente no agendamento, nao referencia ao paciente:
    // reajustar a sessao de 150 para 200 nao pode reescrever o historico
    // faturado (5.3). Nulo quando a consulta foi agendada sem valor definido,
    // nem no paciente nem no padrao do usuario
```

O Javadoc de `pagoEm` continua verdadeiro — não toque.

- [ ] **Step 2: Escreva os testes de service que falham**

Acrescente ao fim de `T/service/ConsultaServiceTest.java` (o arquivo já tem `@Mock` de `consultaMapper`, `consultaRepository`, `pacienteRepository`, `anotacaoRepository`, `securityService`, `auditoriaService`, `@InjectMocks consultaService`, o campo `logado` e o método `pacienteDoLogado`). Imports novos: `java.math.BigDecimal`, `com.psychologyapi.psychologyapi.controller.dto.consulta.StatusConsultaDTO`, `com.psychologyapi.psychologyapi.exceptions.SessaoComBaixaException`.

```java
    private Consulta consultaDoLogado(Status status, BigDecimal valorDoPaciente) {
        Paciente paciente = pacienteDoLogado(UUID.randomUUID());
        paciente.setValorSessao(valorDoPaciente);
        Consulta consulta = Fixtures.consulta(logado, paciente, quando, status);
        consulta.setId(UUID.randomUUID());
        return consulta;
    }

    private void comConsultaSalvaDevolvida(Consulta consulta) {
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), logado))
                .thenReturn(Optional.of(consulta));
        when(consultaRepository.save(consulta)).thenReturn(consulta);
    }

    @Test
    @DisplayName("atualizarStatus para REALIZADA congela o valor do paciente na consulta")
    void realizadaCongelaValorDoPaciente() {
        Consulta consulta = consultaDoLogado(Status.AGENDADA, new BigDecimal("180.00"));
        comConsultaSalvaDevolvida(consulta);

        consultaService.atualizarStatus(consulta.getId(), new StatusConsultaDTO(Status.REALIZADA));

        assertThat(consulta.getValor()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("sem valor no paciente, REALIZADA congela o valor padrao do psicologo")
    void realizadaUsaValorPadraoDoUsuario() {
        logado.setValorPadraoSessao(new BigDecimal("150.00"));
        Consulta consulta = consultaDoLogado(Status.AGENDADA, null);
        comConsultaSalvaDevolvida(consulta);

        consultaService.atualizarStatus(consulta.getId(), new StatusConsultaDTO(Status.REALIZADA));

        assertThat(consulta.getValor()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("sem valor no paciente nem padrao, REALIZADA fica sem valor")
    void realizadaSemNenhumValorFicaNula() {
        Consulta consulta = consultaDoLogado(Status.AGENDADA, null);
        comConsultaSalvaDevolvida(consulta);

        consultaService.atualizarStatus(consulta.getId(), new StatusConsultaDTO(Status.REALIZADA));

        assertThat(consulta.getValor()).isNull();
    }

    @Test
    @DisplayName("valor ja congelado nao e sobrescrito pelo valor atual do paciente")
    void valorJaCongeladoNaoMuda() {
        Consulta consulta = consultaDoLogado(Status.FALTOU, new BigDecimal("200.00"));
        consulta.setValor(new BigDecimal("150.00"));
        comConsultaSalvaDevolvida(consulta);

        consultaService.atualizarStatus(consulta.getId(), new StatusConsultaDTO(Status.REALIZADA));

        assertThat(consulta.getValor()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("FALTOU nao congela valor")
    void faltouNaoCongela() {
        Consulta consulta = consultaDoLogado(Status.AGENDADA, new BigDecimal("180.00"));
        comConsultaSalvaDevolvida(consulta);

        consultaService.atualizarStatus(consulta.getId(), new StatusConsultaDTO(Status.FALTOU));

        assertThat(consulta.getValor()).isNull();
    }

    @Test
    @DisplayName("sessao com baixa nao sai de REALIZADA pelo PATCH de status: 409 e nada salvo")
    void comBaixaNaoViraFaltou() {
        Consulta consulta = consultaDoLogado(Status.REALIZADA, null);
        consulta.setValor(new BigDecimal("180.00"));
        consulta.setPagoEm(quando.plusHours(1));
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), logado))
                .thenReturn(Optional.of(consulta));

        assertThatThrownBy(() -> consultaService.atualizarStatus(
                consulta.getId(), new StatusConsultaDTO(Status.FALTOU)))
                .isInstanceOf(SessaoComBaixaException.class)
                .hasMessage("Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status.");

        assertThat(consulta.getStatus()).isEqualTo(Status.REALIZADA);
        verify(consultaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("sessao com baixa pode receber REALIZADA de novo sem erro")
    void comBaixaRealizadaDeNovoPassa() {
        Consulta consulta = consultaDoLogado(Status.REALIZADA, null);
        consulta.setValor(new BigDecimal("180.00"));
        consulta.setPagoEm(quando.plusHours(1));
        comConsultaSalvaDevolvida(consulta);

        assertThatCode(() -> consultaService.atualizarStatus(
                consulta.getId(), new StatusConsultaDTO(Status.REALIZADA)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PUT com status CANCELADA numa sessao com baixa: 409 antes de mexer em qualquer campo")
    void putComBaixaNaoCancela() {
        Consulta consulta = consultaDoLogado(Status.REALIZADA, null);
        consulta.setValor(new BigDecimal("180.00"));
        consulta.setPagoEm(quando.plusHours(1));
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), logado))
                .thenReturn(Optional.of(consulta));

        assertThatThrownBy(() -> consultaService.atualizarConsulta(
                consulta.getId(), new ConsultaAtualizacaoDTO(null, Status.CANCELADA)))
                .isInstanceOf(SessaoComBaixaException.class)
                .hasMessage("Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status.");

        verify(consultaMapper, never()).atualizarConsultaApartirDoDTO(any(), any());
        verify(consultaRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT que leva a sessao a REALIZADA congela o valor")
    void putRealizadaCongela() {
        Consulta consulta = consultaDoLogado(Status.AGENDADA, new BigDecimal("180.00"));
        ConsultaAtualizacaoDTO dto = new ConsultaAtualizacaoDTO(null, Status.REALIZADA);
        comConsultaSalvaDevolvida(consulta);
        doAnswer(invocacao -> {
            consulta.setStatus(Status.REALIZADA);
            return null;
        }).when(consultaMapper).atualizarConsultaApartirDoDTO(dto, consulta);

        consultaService.atualizarConsulta(consulta.getId(), dto);

        assertThat(consulta.getValor()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("deletarConsulta com baixa: 409, sem soft delete e sem auditoria")
    void deletarComBaixaRecusa() {
        Consulta consulta = consultaDoLogado(Status.REALIZADA, null);
        consulta.setValor(new BigDecimal("180.00"));
        consulta.setPagoEm(quando.plusHours(1));
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), logado))
                .thenReturn(Optional.of(consulta));

        assertThatThrownBy(() -> consultaService.deletarConsulta(consulta.getId()))
                .isInstanceOf(SessaoComBaixaException.class)
                .hasMessage("Esta sessão tem baixa registrada. Desfaça a baixa antes de excluir a sessão.");

        assertThat(consulta.getDeletedAt()).isNull();
        verify(consultaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }
```

`doAnswer` vem de `org.mockito.Mockito.doAnswer` (import estático novo). Se o `MockitoExtension` acusar `UnnecessaryStubbingException` em algum teste, remova o stub que o teste não usa — não troque para `lenient()`.

- [ ] **Step 3: Rode e confirme que falham**

Run: `sh ./mvnw -q test -Dtest='ConsultaServiceTest'`
Expected: falha de compilação (`SessaoComBaixaException` não existe).

- [ ] **Step 4: Crie a exceção e a regra do valor**

`M/exceptions/SessaoComBaixaException.java`:

```java
package com.psychologyapi.psychologyapi.exceptions;

public class SessaoComBaixaException extends RuntimeException {

    public static final String AO_MUDAR_STATUS =
            "Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status.";
    public static final String AO_ALTERAR_VALOR =
            "Esta sessão já tem baixa. Desfaça a baixa antes de alterar o valor.";
    public static final String AO_EXCLUIR =
            "Esta sessão tem baixa registrada. Desfaça a baixa antes de excluir a sessão.";

    public SessaoComBaixaException(String mensagem) {
        super(mensagem);
    }
}
```

`M/service/dinheiro/ValorDaSessao.java`:

```java
package com.psychologyapi.psychologyapi.service.dinheiro;

import java.math.BigDecimal;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Status;

public final class ValorDaSessao {

    private ValorDaSessao() {
    }

    public static void congelarSeRealizada(Consulta consulta) {
        if (consulta.getStatus() != Status.REALIZADA || consulta.getValor() != null) {
            return;
        }
        BigDecimal doPaciente = consulta.getPaciente().getValorSessao();
        consulta.setValor(doPaciente != null ? doPaciente : consulta.getUsuario().getValorPadraoSessao());
    }
}
```

No `GlobalExceptionsHandler`, ao lado do handler de `SessaoComAnotacaoException` (importe a classe):

```java
    @ExceptionHandler(SessaoComBaixaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO sessaoComBaixa(SessaoComBaixaException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }
```

- [ ] **Step 5: Ligue a regra e as travas no `ConsultaService`**

Imports: `com.psychologyapi.psychologyapi.exceptions.SessaoComBaixaException`, `com.psychologyapi.psychologyapi.service.dinheiro.ValorDaSessao`.

Em `atualizarStatus`, troque a linha `consulta.setStatus(dto.status());` por:

```java
        if (consulta.getPagoEm() != null && dto.status() != Status.REALIZADA) {
            throw new SessaoComBaixaException(SessaoComBaixaException.AO_MUDAR_STATUS);
        }

        consulta.setStatus(dto.status());
        ValorDaSessao.congelarSeRealizada(consulta);
```

(a guarda de CANCELADA existente e o comentário dela ficam onde estão, antes disto).

Em `atualizarConsulta`, troque `consultaMapper.atualizarConsultaApartirDoDTO(dto, consulta);` por:

```java
        if (consulta.getPagoEm() != null && dto.status() != null && dto.status() != Status.REALIZADA) {
            throw new SessaoComBaixaException(SessaoComBaixaException.AO_MUDAR_STATUS);
        }

        consultaMapper.atualizarConsultaApartirDoDTO(dto, consulta);
        ValorDaSessao.congelarSeRealizada(consulta);
```

Em `deletarConsulta`, logo depois do bloco `if (anotacaoRepository.existsByConsultaAndDeletedAtIsNull(consulta)) { ... }`:

```java
        if (consulta.getPagoEm() != null) {
            throw new SessaoComBaixaException(SessaoComBaixaException.AO_EXCLUIR);
        }
```

- [ ] **Step 6: A resposta da consulta passa a carregar o dinheiro**

`ConsultaResponseDTO` vira (o MapStruct mapeia `valor` e `pagoEm` por nome igual — confira depois no `target/generated-sources/annotations/.../ConsultaMapperImpl.java` que `toResponseDTO` copia os dois):

```java
public record ConsultaResponseDTO(
    UUID id,
    UUID pacienteId,
    String pacienteNome,
    LocalDateTime dtConsulta,
    Status status,
    LocalDateTime createdAt,
    UUID serieId,
    BigDecimal valor,
    LocalDateTime pagoEm
) {}
```

Rode `grep -rn "new ConsultaResponseDTO(" backend/src` e acrescente `, null, null` nas chamadas que existirem.

- [ ] **Step 7: Rode os testes de service**

Run: `sh ./mvnw -q test -Dtest='ConsultaServiceTest'`
Expected: PASS.

- [ ] **Step 8: Teste HTTP com o mapper real**

Crie `T/controller/ConsultaComBaixaHttpTest.java` no molde de `ConsultaStatusHttpTest` (mesma anotação `@HttpTest`, mesmos `@Autowired`, token por `tokenService.gerarToken`):

```java
package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

@HttpTest
class ConsultaComBaixaHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String token;
    private Usuario dono;
    private Paciente helena;

    @BeforeEach
    void preparar() {
        dono = usuarioRepository.save(Fixtures.usuario("dona.baixa@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
        Paciente paciente = Fixtures.paciente(dono, "Helena Vaz");
        paciente.setValorSessao(new BigDecimal("180.00"));
        helena = pacienteRepository.save(paciente);
    }

    private Consulta paga() {
        Consulta consulta = Fixtures.consulta(dono, helena, LocalDateTime.parse("2026-12-02T14:00:00"), Status.REALIZADA);
        consulta.setValor(new BigDecimal("180.00"));
        consulta.setPagoEm(LocalDateTime.parse("2026-12-02T15:00:00"));
        return consultaRepository.save(consulta);
    }

    @Test
    @DisplayName("PATCH para REALIZADA devolve o valor congelado do paciente")
    void realizadaDevolveValor() throws Exception {
        Consulta agendada = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-03T14:00:00"), Status.AGENDADA));

        mockMvc.perform(patch("/consultas/" + agendada.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"REALIZADA"}
                         """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valor").value(180.0))
               .andExpect(jsonPath("$.pagoEm").doesNotExist());
    }

    @Test
    @DisplayName("GET da consulta devolve valor e pagoEm")
    void getDevolveDinheiro() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(get("/consultas/" + consulta.getId()).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valor").value(180.0))
               .andExpect(jsonPath("$.pagoEm").value("2026-12-02T15:00:00"));
    }

    @Test
    @DisplayName("PATCH FALTOU numa sessao paga responde 409 com a frase que diz o que fazer")
    void pagaNaoViraFaltou() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"FALTOU"}
                         """))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status."))
               .andExpect(jsonPath("$.erros").isEmpty());
    }

    @Test
    @DisplayName("PUT com status CANCELADA numa sessao paga responde 409")
    void pagaNaoCancelaPeloPut() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(put("/consultas/" + consulta.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"CANCELADA"}
                         """))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status."));
    }

    @Test
    @DisplayName("DELETE numa sessao paga responde 409 e ela continua na agenda")
    void pagaNaoSeExclui() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(delete("/consultas/" + consulta.getId()).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão tem baixa registrada. Desfaça a baixa antes de excluir a sessão."));

        mockMvc.perform(get("/consultas/" + consulta.getId()).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk());
    }
}
```

Run: `sh ./mvnw -q test -Dtest='ConsultaComBaixaHttpTest,ConsultaServiceTest'` → PASS. Se `jsonPath("$.valor").value(180.0)` falhar por tipo (`BigDecimal` × `Double`), troque para `.value(org.hamcrest.Matchers.closeTo(180.0, 0.001))` — não mude a serialização.

- [ ] **Step 9: Suíte inteira do backend**

Run: `sh ./mvnw -q test` → tudo verde. Reporte a contagem (baseline: 316).

- [ ] **Step 10: Não comite.** Reporte os arquivos tocados.

---

### Task 2: Baixa, desfazer, lote e valor avulso

**Files:**
- Create: `M/exceptions/BaixaInvalidaException.java`
- Create: `M/controller/dto/consulta/BaixaEmLoteDTO.java`, `BaixaEmLoteResponseDTO.java`, `ValorDaConsultaDTO.java`
- Create: `M/service/PagamentoService.java`, `M/controller/PagamentoController.java`
- Modify: `M/repository/ConsultaRepository.java` (um método derivado)
- Modify: `M/controller/common/GlobalExceptionsHandler.java` (um handler)
- Test: `T/service/PagamentoServiceTest.java`, `T/controller/PagamentoHttpTest.java` (criar)

**Interfaces:**
- Consumes: `SessaoComBaixaException.AO_ALTERAR_VALOR` (Tarefa 1); `ConsultaResponseDTO` com `valor`/`pagoEm` (Tarefa 1).
- Produces (HTTP, consumido pelo front na Tarefa 6):
  - `PATCH /consultas/{consultaId}/baixa` → 200 `ConsultaResponseDTO`
  - `DELETE /consultas/{consultaId}/baixa` → 200 `ConsultaResponseDTO`
  - `POST /consultas/baixas` `{ "consultaIds": [uuid...] }` → 200 `{ "consultasBaixadas": int }`
  - `PATCH /consultas/{consultaId}/valor` `{ "valor": number }` → 200 `ConsultaResponseDTO`

**Regras exatas (spec §3.1–§3.2):**

| Situação | Resultado |
|---|---|
| baixa em REALIZADA com valor e sem baixa | `pagoEm = LocalDateTime.now(clock)`, save, audita, 200 |
| baixa em sessão já paga | 200 com a consulta inalterada, **sem** save e **sem** auditoria |
| baixa em status ≠ REALIZADA | 409 "Só sessão realizada recebe baixa. Marque a sessão como realizada antes." |
| baixa em REALIZADA sem valor | 409 "Defina o valor antes de dar baixa." |
| desfazer com baixa | `pagoEm = null`, save, audita, 200 |
| desfazer sem baixa | 200 inalterada, sem save, sem auditoria |
| lote | ids distintos; algum não encontrado para o usuário → 404 `ConsultaNaoEncontradaException("Consulta nao encontrada")` e nada gravado; algum não pago e inválido → 409 (mesmas frases) e nada gravado; pagos são pulados; o mesmo `agora` para todos; `saveAll`; audita cada um depois |
| lote vazio / >200 / id nulo | 400 no envelope, campo `consultaIds` (ou `consultaIds[0]` para nulo) |
| valor em REALIZADA sem baixa | `valor = dto.valor()`, save, audita, 200 |
| valor em status ≠ REALIZADA | 409 "Só sessão realizada tem valor a alterar. Marque a sessão como realizada antes." |
| valor em sessão paga | 409 `SessaoComBaixaException.AO_ALTERAR_VALOR` |
| valor ausente / 0 / 3 casas | 400 campo `valor` com as mensagens do DTO |
| qualquer id de outro psicólogo | 404 |

A checagem de "já pago" vem **antes** das outras na baixa (idempotência primeiro).

- [ ] **Step 1: DTOs e exceção**

`BaixaInvalidaException`:

```java
package com.psychologyapi.psychologyapi.exceptions;

public class BaixaInvalidaException extends RuntimeException {

    public static final String SO_REALIZADA_RECEBE_BAIXA =
            "Só sessão realizada recebe baixa. Marque a sessão como realizada antes.";
    public static final String SEM_VALOR =
            "Defina o valor antes de dar baixa.";
    public static final String SO_REALIZADA_TEM_VALOR =
            "Só sessão realizada tem valor a alterar. Marque a sessão como realizada antes.";

    public BaixaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
```

Handler no `GlobalExceptionsHandler` (mesmo molde do `SessaoComBaixaException`, 409, `semCampos`).

`BaixaEmLoteDTO`:

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BaixaEmLoteDTO(
        @NotEmpty(message = "Informe ao menos uma sessão.")
        @Size(max = 200, message = "Informe no máximo 200 sessões por vez.")
        List<@NotNull(message = "Informe o id da sessão.") UUID> consultaIds) {
}
```

`BaixaEmLoteResponseDTO`:

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

public record BaixaEmLoteResponseDTO(int consultasBaixadas) {
}
```

`ValorDaConsultaDTO` — as anotações e o `@Schema` são cópia das de `valorSessao` em `PacienteCadastroDTO` (não invente outras):

```java
package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record ValorDaConsultaDTO(
        @NotNull(message = "Informe o valor da sessão.")
        @DecimalMin(value = "0.01", message = "O valor da sessão deve ser maior que zero.")
        @Digits(integer = 8, fraction = 2, message = "O valor da sessão deve ter no máximo duas casas decimais.")
        @Schema(maximum = "99999999.99", multipleOf = 0.01,
                example = "180.00",
                description = "Valor desta sessão. Maior que zero, no máximo 8 dígitos "
                        + "inteiros e 2 casas decimais.")
        BigDecimal valor) {
}
```

No `ConsultaRepository`, acrescente (import `java.util.Collection`):

```java
    List<Consulta> findByIdInAndUsuarioAndDeletedAtIsNull(Collection<UUID> ids, Usuario usuario);
```

- [ ] **Step 2: Testes de service que falham**

`T/service/PagamentoServiceTest.java` — Mockito puro, construtor manual com relógio fixo (o molde é `PainelServiceTest`):

```java
package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ValorDaConsultaDTO;
import com.psychologyapi.psychologyapi.exceptions.BaixaInvalidaException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.SessaoComBaixaException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 12, 2, 16, 30);

    @Mock private ConsultaRepository consultaRepository;
    @Mock private ConsultaMapper consultaMapper;
    @Mock private SecurityService securityService;
    @Mock private AuditoriaService auditoriaService;

    private PagamentoService pagamentoService;
    private final Usuario logado = Fixtures.usuario("dona.pagamento@example.com");

    @BeforeEach
    void preparar() {
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(AGORA.atZone(fuso).toInstant(), fuso);
        pagamentoService = new PagamentoService(
                consultaRepository, consultaMapper, securityService, auditoriaService, relogio);
    }

    private Consulta consulta(Status status, String valor, LocalDateTime pagoEm) {
        Paciente paciente = Fixtures.paciente(logado, "Bruna Teixeira");
        paciente.setId(UUID.randomUUID());
        Consulta consulta = Fixtures.consulta(logado, paciente, AGORA.minusDays(1), status);
        consulta.setId(UUID.randomUUID());
        consulta.setValor(valor == null ? null : new BigDecimal(valor));
        consulta.setPagoEm(pagoEm);
        return consulta;
    }

    private void encontrada(Consulta consulta) {
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), logado))
                .thenReturn(Optional.of(consulta));
    }

    @Test
    @DisplayName("darBaixa grava o agora do relogio e audita depois do save")
    void darBaixaGravaAgora() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", null);
        encontrada(consulta);
        when(consultaRepository.save(consulta)).thenReturn(consulta);

        pagamentoService.darBaixa(consulta.getId());

        assertThat(consulta.getPagoEm()).isEqualTo(AGORA);
        InOrder ordem = inOrder(consultaRepository, auditoriaService);
        ordem.verify(consultaRepository).save(consulta);
        ordem.verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Consulta", consulta.getId());
    }

    @Test
    @DisplayName("darBaixa numa sessao ja paga nao muda a data, nao salva e nao audita")
    void darBaixaIdempotente() {
        LocalDateTime antes = AGORA.minusDays(3);
        Consulta consulta = consulta(Status.REALIZADA, "180.00", antes);
        encontrada(consulta);

        pagamentoService.darBaixa(consulta.getId());

        assertThat(consulta.getPagoEm()).isEqualTo(antes);
        verify(consultaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("darBaixa em sessao nao realizada: 409 com a frase certa")
    void darBaixaEmAgendada() {
        Consulta consulta = consulta(Status.AGENDADA, "180.00", null);
        encontrada(consulta);

        assertThatThrownBy(() -> pagamentoService.darBaixa(consulta.getId()))
                .isInstanceOf(BaixaInvalidaException.class)
                .hasMessage("Só sessão realizada recebe baixa. Marque a sessão como realizada antes.");
        verify(consultaRepository, never()).save(any());
    }

    @Test
    @DisplayName("darBaixa em realizada sem valor: 409 pedindo o valor")
    void darBaixaSemValor() {
        Consulta consulta = consulta(Status.REALIZADA, null, null);
        encontrada(consulta);

        assertThatThrownBy(() -> pagamentoService.darBaixa(consulta.getId()))
                .isInstanceOf(BaixaInvalidaException.class)
                .hasMessage("Defina o valor antes de dar baixa.");
    }

    @Test
    @DisplayName("darBaixa em consulta de outro psicologo: nao encontrada")
    void darBaixaDeOutro() {
        UUID id = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagamentoService.darBaixa(id))
                .isInstanceOf(ConsultaNaoEncontradaException.class);
    }

    @Test
    @DisplayName("desfazerBaixa limpa pagoEm, salva e audita")
    void desfazer() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", AGORA.minusDays(1));
        encontrada(consulta);
        when(consultaRepository.save(consulta)).thenReturn(consulta);

        pagamentoService.desfazerBaixa(consulta.getId());

        assertThat(consulta.getPagoEm()).isNull();
        verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Consulta", consulta.getId());
    }

    @Test
    @DisplayName("desfazerBaixa sem baixa nao salva nem audita")
    void desfazerSemBaixa() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", null);
        encontrada(consulta);

        pagamentoService.desfazerBaixa(consulta.getId());

        verify(consultaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("lote da baixa nas abertas, pula as pagas, conta ids repetidos uma vez, mesmo agora para todas")
    void loteFeliz() {
        Consulta aberta1 = consulta(Status.REALIZADA, "180.00", null);
        Consulta aberta2 = consulta(Status.REALIZADA, "180.00", null);
        Consulta jaPaga = consulta(Status.REALIZADA, "180.00", AGORA.minusDays(5));
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdInAndUsuarioAndDeletedAtIsNull(anyList(), any()))
                .thenReturn(List.of(aberta1, aberta2, jaPaga));
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var resposta = pagamentoService.darBaixaEmLote(new BaixaEmLoteDTO(List.of(
                aberta1.getId(), aberta2.getId(), jaPaga.getId(), aberta1.getId())));

        assertThat(resposta.consultasBaixadas()).isEqualTo(2);
        assertThat(aberta1.getPagoEm()).isEqualTo(AGORA);
        assertThat(aberta2.getPagoEm()).isEqualTo(AGORA);
        assertThat(jaPaga.getPagoEm()).isEqualTo(AGORA.minusDays(5));
        verify(auditoriaService, times(2)).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("lote com um id que nao e do psicologo: 404 e nada gravado")
    void loteComIdDeOutro() {
        Consulta aberta = consulta(Status.REALIZADA, "180.00", null);
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdInAndUsuarioAndDeletedAtIsNull(anyList(), any()))
                .thenReturn(List.of(aberta));

        assertThatThrownBy(() -> pagamentoService.darBaixaEmLote(
                new BaixaEmLoteDTO(List.of(aberta.getId(), UUID.randomUUID()))))
                .isInstanceOf(ConsultaNaoEncontradaException.class);
        assertThat(aberta.getPagoEm()).isNull();
        verify(consultaRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("lote com uma sessao sem valor: 409 e NENHUMA ganha baixa")
    void loteTudoOuNada() {
        Consulta aberta = consulta(Status.REALIZADA, "180.00", null);
        Consulta semValor = consulta(Status.REALIZADA, null, null);
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdInAndUsuarioAndDeletedAtIsNull(anyList(), any()))
                .thenReturn(List.of(aberta, semValor));

        assertThatThrownBy(() -> pagamentoService.darBaixaEmLote(
                new BaixaEmLoteDTO(List.of(aberta.getId(), semValor.getId()))))
                .isInstanceOf(BaixaInvalidaException.class)
                .hasMessage("Defina o valor antes de dar baixa.");
        assertThat(aberta.getPagoEm()).isNull();
        verify(consultaRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("alterarValor troca o valor de uma realizada sem baixa")
    void alterarValor() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", null);
        encontrada(consulta);
        when(consultaRepository.save(consulta)).thenReturn(consulta);

        pagamentoService.alterarValor(consulta.getId(), new ValorDaConsultaDTO(new BigDecimal("90.00")));

        assertThat(consulta.getValor()).isEqualByComparingTo("90.00");
        verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Consulta", consulta.getId());
    }

    @Test
    @DisplayName("alterarValor numa sessao paga: 409 mandando desfazer a baixa")
    void alterarValorPaga() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", AGORA.minusDays(1));
        encontrada(consulta);

        assertThatThrownBy(() -> pagamentoService.alterarValor(
                consulta.getId(), new ValorDaConsultaDTO(new BigDecimal("90.00"))))
                .isInstanceOf(SessaoComBaixaException.class)
                .hasMessage("Esta sessão já tem baixa. Desfaça a baixa antes de alterar o valor.");
        assertThat(consulta.getValor()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("alterarValor numa sessao agendada: 409")
    void alterarValorAgendada() {
        Consulta consulta = consulta(Status.AGENDADA, null, null);
        encontrada(consulta);

        assertThatThrownBy(() -> pagamentoService.alterarValor(
                consulta.getId(), new ValorDaConsultaDTO(new BigDecimal("90.00"))))
                .isInstanceOf(BaixaInvalidaException.class)
                .hasMessage("Só sessão realizada tem valor a alterar. Marque a sessão como realizada antes.");
    }
}
```

Run: `sh ./mvnw -q test -Dtest='PagamentoServiceTest'` → falha de compilação (classe não existe).

- [ ] **Step 3: Implemente o service**

`M/service/PagamentoService.java`:

```java
package com.psychologyapi.psychologyapi.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ValorDaConsultaDTO;
import com.psychologyapi.psychologyapi.exceptions.BaixaInvalidaException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.SessaoComBaixaException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PagamentoService {

    private final ConsultaRepository consultaRepository;
    private final ConsultaMapper consultaMapper;
    private final SecurityService securityService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    @Transactional
    public ConsultaResponseDTO darBaixa(UUID consultaId) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Consulta consulta = buscar(consultaId, usuario);
        if (consulta.getPagoEm() != null) {
            return consultaMapper.toResponseDTO(consulta);
        }
        exigirQuePodeReceberBaixa(consulta);
        consulta.setPagoEm(LocalDateTime.now(clock));
        return salvarEAuditar(consulta, usuario);
    }

    @Transactional
    public ConsultaResponseDTO desfazerBaixa(UUID consultaId) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Consulta consulta = buscar(consultaId, usuario);
        if (consulta.getPagoEm() == null) {
            return consultaMapper.toResponseDTO(consulta);
        }
        consulta.setPagoEm(null);
        return salvarEAuditar(consulta, usuario);
    }

    @Transactional
    public BaixaEmLoteResponseDTO darBaixaEmLote(BaixaEmLoteDTO dto) {
        Usuario usuario = securityService.obterUsuarioLogado();
        List<UUID> ids = dto.consultaIds().stream().distinct().toList();
        List<Consulta> consultas = consultaRepository.findByIdInAndUsuarioAndDeletedAtIsNull(ids, usuario);
        if (consultas.size() != ids.size()) {
            throw new ConsultaNaoEncontradaException("Consulta nao encontrada");
        }

        List<Consulta> aBaixar = consultas.stream().filter(c -> c.getPagoEm() == null).toList();
        aBaixar.forEach(this::exigirQuePodeReceberBaixa);

        LocalDateTime agora = LocalDateTime.now(clock);
        aBaixar.forEach(c -> c.setPagoEm(agora));
        List<Consulta> salvas = consultaRepository.saveAll(aBaixar);
        salvas.forEach(c -> auditoriaService.registrar(usuario, AcaoAuditoria.ATUALIZACAO, "Consulta", c.getId()));
        return new BaixaEmLoteResponseDTO(salvas.size());
    }

    @Transactional
    public ConsultaResponseDTO alterarValor(UUID consultaId, ValorDaConsultaDTO dto) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Consulta consulta = buscar(consultaId, usuario);
        if (consulta.getStatus() != Status.REALIZADA) {
            throw new BaixaInvalidaException(BaixaInvalidaException.SO_REALIZADA_TEM_VALOR);
        }
        if (consulta.getPagoEm() != null) {
            throw new SessaoComBaixaException(SessaoComBaixaException.AO_ALTERAR_VALOR);
        }
        consulta.setValor(dto.valor());
        return salvarEAuditar(consulta, usuario);
    }

    private Consulta buscar(UUID consultaId, Usuario usuario) {
        return consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consultaId, usuario)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));
    }

    private void exigirQuePodeReceberBaixa(Consulta consulta) {
        if (consulta.getStatus() != Status.REALIZADA) {
            throw new BaixaInvalidaException(BaixaInvalidaException.SO_REALIZADA_RECEBE_BAIXA);
        }
        if (consulta.getValor() == null) {
            throw new BaixaInvalidaException(BaixaInvalidaException.SEM_VALOR);
        }
    }

    private ConsultaResponseDTO salvarEAuditar(Consulta consulta, Usuario usuario) {
        Consulta salva = consultaRepository.save(consulta);
        auditoriaService.registrar(usuario, AcaoAuditoria.ATUALIZACAO, "Consulta", salva.getId());
        return consultaMapper.toResponseDTO(salva);
    }
}
```

Run: `sh ./mvnw -q test -Dtest='PagamentoServiceTest'` → PASS.

- [ ] **Step 4: Controller**

`M/controller/PagamentoController.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ValorDaConsultaDTO;
import com.psychologyapi.psychologyapi.service.PagamentoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/consultas")
@RequiredArgsConstructor
public class PagamentoController {

    private final PagamentoService pagamentoService;

    @PatchMapping("/{consultaId}/baixa")
    public ResponseEntity<ConsultaResponseDTO> darBaixa(@PathVariable UUID consultaId) {
        return ResponseEntity.ok(pagamentoService.darBaixa(consultaId));
    }

    @DeleteMapping("/{consultaId}/baixa")
    public ResponseEntity<ConsultaResponseDTO> desfazerBaixa(@PathVariable UUID consultaId) {
        return ResponseEntity.ok(pagamentoService.desfazerBaixa(consultaId));
    }

    @PostMapping("/baixas")
    public ResponseEntity<BaixaEmLoteResponseDTO> darBaixaEmLote(@RequestBody @Valid BaixaEmLoteDTO dto) {
        return ResponseEntity.ok(pagamentoService.darBaixaEmLote(dto));
    }

    @PatchMapping("/{consultaId}/valor")
    public ResponseEntity<ConsultaResponseDTO> alterarValor(
            @PathVariable UUID consultaId, @RequestBody @Valid ValorDaConsultaDTO dto) {
        return ResponseEntity.ok(pagamentoService.alterarValor(consultaId, dto));
    }
}
```

- [ ] **Step 5: Teste HTTP**

`T/controller/PagamentoHttpTest.java`, `@HttpTest`, mesmo preparo da `ConsultaComBaixaHttpTest` (dono, token, paciente "Bruna Teixeira" com `valorSessao` 180, e um segundo psicólogo com uma consulta REALIZADA com valor). Casos, cada um um `@Test` com `@DisplayName` em português e sem comentário:

1. `PATCH /consultas/{id}/baixa` numa REALIZADA com valor → 200, `$.pagoEm` existe, `$.valor` 180.0.
2. Mesmo PATCH duas vezes → o segundo devolve o **mesmo** `$.pagoEm` do primeiro (capture com `JsonPath.read(resultado.getResponse().getContentAsString(), "$.pagoEm")`).
3. `PATCH .../baixa` numa AGENDADA → 409 com a mensagem exata, `$.erros` vazio.
4. `PATCH .../baixa` numa REALIZADA sem valor → 409 "Defina o valor antes de dar baixa."
5. `DELETE .../baixa` numa paga → 200 e `$.pagoEm` não existe.
6. `POST /consultas/baixas` com duas abertas → 200 `$.consultasBaixadas` 2; depois `GET /consultas/{id}` de cada uma tem `pagoEm`.
7. `POST /consultas/baixas` com `{"consultaIds":[]}` → 400 e `$.erros[0].campo` = `consultaIds`.
8. `POST /consultas/baixas` com um id do outro psicólogo junto de uma aberta do dono → 404, e a aberta do dono continua sem `pagoEm`.
9. `PATCH /consultas/{id}/valor` `{"valor":90.5}` → 200 `$.valor` 90.5.
10. `PATCH .../valor` com `{"valor":0}` → 400 campo `valor` mensagem "O valor da sessão deve ser maior que zero."; com `{"valor":10.123}` → 400 "O valor da sessão deve ter no máximo duas casas decimais."; com `{}` → 400 "Informe o valor da sessão."
11. `PATCH .../valor` numa paga → 409 "Esta sessão já tem baixa. Desfaça a baixa antes de alterar o valor."
12. `PATCH .../baixa` na consulta do outro psicólogo → 404.
13. Sem token → 401.

Run: `sh ./mvnw -q test -Dtest='PagamentoHttpTest,PagamentoServiceTest'` → PASS. Depois `sh ./mvnw -q test` → verde.

- [ ] **Step 6: Não comite.** Reporte os arquivos.

---

### Task 3: `GET /financeiro/mes` — o mês por competência e quem deve

**Files:**
- Create: `M/service/periodo/MesConsultado.java`
- Create: `M/repository/projecao/TotaisFinanceirosDoMes.java`
- Create: `M/controller/dto/financeiro/FinanceiroMesDTO.java`, `SessaoFinanceiraDTO.java`, `DevedorDTO.java`
- Create: `M/service/FinanceiroService.java`, `M/controller/FinanceiroController.java`
- Modify: `M/repository/ConsultaRepository.java` (3 métodos)
- Test: `T/service/periodo/MesConsultadoTest.java`, `T/repository/ConsultaAgregacaoRepositoryTest.java`, `T/controller/FinanceiroMesHttpTest.java` (criar); `T/controller/OpenApiHttpTest.java` (acrescentar 1 teste)

**Interfaces:**
- Produces: `MesConsultado.resolver(Integer ano, Integer mes, Clock clock)` → `record MesConsultado(int ano, int mes, LocalDateTime inicio, LocalDateTime fimExclusivo)`. A Tarefa 4 usa.
- Produces (HTTP): `GET /financeiro/mes?ano&mes` → `FinanceiroMesDTO` (spec §3.5, tabela).

- [ ] **Step 1: `MesConsultado` com teste primeiro**

`T/service/periodo/MesConsultadoTest.java` (JUnit puro):

```java
package com.psychologyapi.psychologyapi.service.periodo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.psychologyapi.psychologyapi.exceptions.PeriodoInvalidoException;

class MesConsultadoTest {

    private final ZoneId fuso = ZoneId.of("America/Sao_Paulo");
    private final Clock dezembro = Clock.fixed(
            LocalDateTime.parse("2026-12-02T10:00:00").atZone(fuso).toInstant(), fuso);

    @Test
    @DisplayName("sem ano nem mes, e o mes corrente do relogio")
    void semParametrosEOMesCorrente() {
        MesConsultado mes = MesConsultado.resolver(null, null, dezembro);
        assertThat(mes.ano()).isEqualTo(2026);
        assertThat(mes.mes()).isEqualTo(12);
    }

    @Test
    @DisplayName("o intervalo e semiaberto: do primeiro dia 00:00 ao primeiro dia do mes seguinte")
    void intervaloSemiaberto() {
        MesConsultado mes = MesConsultado.resolver(2026, 12, dezembro);
        assertThat(mes.inicio()).isEqualTo(LocalDateTime.parse("2026-12-01T00:00:00"));
        assertThat(mes.fimExclusivo()).isEqualTo(LocalDateTime.parse("2027-01-01T00:00:00"));
    }

    @Test
    @DisplayName("so o ano: 400 no campo mes")
    void soAno() {
        assertThatThrownBy(() -> MesConsultado.resolver(2026, null, dezembro))
                .isInstanceOf(PeriodoInvalidoException.class)
                .hasMessage("Informe 'ano' e 'mes' juntos, ou nenhum dos dois.")
                .extracting("campo").isEqualTo("mes");
    }

    @Test
    @DisplayName("so o mes: 400 no campo ano")
    void soMes() {
        assertThatThrownBy(() -> MesConsultado.resolver(null, 5, dezembro))
                .isInstanceOf(PeriodoInvalidoException.class)
                .extracting("campo").isEqualTo("ano");
    }

    @Test
    @DisplayName("mes 13 e mes 0 sao recusados no campo mes")
    void mesForaDaFaixa() {
        assertThatThrownBy(() -> MesConsultado.resolver(2026, 13, dezembro))
                .hasMessage("O mês deve estar entre 1 e 12.").extracting("campo").isEqualTo("mes");
        assertThatThrownBy(() -> MesConsultado.resolver(2026, 0, dezembro))
                .extracting("campo").isEqualTo("mes");
    }

    @Test
    @DisplayName("ano fora de 2000-2100 e recusado no campo ano")
    void anoForaDaFaixa() {
        assertThatThrownBy(() -> MesConsultado.resolver(1999, 1, dezembro))
                .hasMessage("O ano deve estar entre 2000 e 2100.").extracting("campo").isEqualTo("ano");
        assertThatThrownBy(() -> MesConsultado.resolver(2101, 1, dezembro))
                .extracting("campo").isEqualTo("ano");
    }
}
```

`M/service/periodo/MesConsultado.java`:

```java
package com.psychologyapi.psychologyapi.service.periodo;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;

import com.psychologyapi.psychologyapi.exceptions.PeriodoInvalidoException;

public record MesConsultado(int ano, int mes, LocalDateTime inicio, LocalDateTime fimExclusivo) {

    public static MesConsultado resolver(Integer ano, Integer mes, Clock clock) {
        if (ano == null && mes == null) {
            return de(YearMonth.now(clock));
        }
        if (ano == null || mes == null) {
            throw new PeriodoInvalidoException(ano == null ? "ano" : "mes",
                    "Informe 'ano' e 'mes' juntos, ou nenhum dos dois.");
        }
        if (mes < 1 || mes > 12) {
            throw new PeriodoInvalidoException("mes", "O mês deve estar entre 1 e 12.");
        }
        if (ano < 2000 || ano > 2100) {
            throw new PeriodoInvalidoException("ano", "O ano deve estar entre 2000 e 2100.");
        }
        return de(YearMonth.of(ano, mes));
    }

    private static MesConsultado de(YearMonth mes) {
        return new MesConsultado(mes.getYear(), mes.getMonthValue(),
                mes.atDay(1).atStartOfDay(), mes.plusMonths(1).atDay(1).atStartOfDay());
    }
}
```

Run: `sh ./mvnw -q test -Dtest='MesConsultadoTest'` → PASS.

- [ ] **Step 2: Repositório — teste primeiro, contra Postgres real**

Projeção `M/repository/projecao/TotaisFinanceirosDoMes.java`:

```java
package com.psychologyapi.psychologyapi.repository.projecao;

import java.math.BigDecimal;

public interface TotaisFinanceirosDoMes {

    BigDecimal getFaturado();

    BigDecimal getRecebido();

    Long getSemValor();
}
```

Acrescente ao `ConsultaRepository` (imports: `org.springframework.data.jpa.repository.EntityGraph`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`, `com.psychologyapi.psychologyapi.repository.projecao.TotaisFinanceirosDoMes`):

```java
    @Query("""
            SELECT SUM(c.valor) AS faturado,
                   SUM(CASE WHEN c.pagoEm IS NOT NULL THEN c.valor END) AS recebido,
                   COUNT(CASE WHEN c.valor IS NULL THEN 1 END) AS semValor
            FROM Consulta c
            WHERE c.usuario = :usuario
              AND c.status = com.psychologyapi.psychologyapi.model.Status.REALIZADA
              AND c.deletedAt IS NULL
              AND c.dtConsulta >= :inicio
              AND c.dtConsulta < :fimExclusivo
            """)
    TotaisFinanceirosDoMes totaisFinanceirosDoMes(
            @Param("usuario") Usuario usuario,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo);

    @EntityGraph(attributePaths = "paciente")
    List<Consulta> findByUsuarioAndStatusAndDtConsultaGreaterThanEqualAndDtConsultaLessThanAndDeletedAtIsNullOrderByDtConsultaAsc(
            Usuario usuario, Status status, LocalDateTime inicio, LocalDateTime fimExclusivo);

    @EntityGraph(attributePaths = "paciente")
    List<Consulta> findByUsuarioAndStatusAndValorIsNotNullAndPagoEmIsNullAndDeletedAtIsNullOrderByDtConsultaAsc(
            Usuario usuario, Status status);
```

`T/repository/ConsultaAgregacaoRepositoryTest.java` — no molde de `ConsultaRepositoryTest` (`@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)`, `@Import(PostgresContainerConfig.class)`, `TestEntityManager em`). Um método auxiliar para criar consulta com valor e pagoEm:

```java
    private Consulta salvar(Usuario dono, Paciente paciente, String quando, Status status, String valor, String pagoEm) {
        Consulta consulta = Fixtures.consulta(dono, paciente, LocalDateTime.parse(quando), status);
        consulta.setValor(valor == null ? null : new BigDecimal(valor));
        consulta.setPagoEm(pagoEm == null ? null : LocalDateTime.parse(pagoEm));
        return em.persistAndFlush(consulta);
    }
```

Casos (cada um um `@Test`):

1. **Totais por competência**: em dezembro/2026 — REALIZADA 180 paga (pagoEm `2027-01-10T09:00:00`), REALIZADA 180 sem baixa, REALIZADA sem valor, FALTOU com valor 180, CANCELADA com valor 180, REALIZADA 180 com `deletedAt` preenchido, e uma REALIZADA 150 de **novembro**. `totaisFinanceirosDoMes(dono, 2026-12-01T00:00, 2027-01-01T00:00)` → faturado 360, recebido 180, semValor 1 (compare com `isEqualByComparingTo`).
2. **Borda do mês**: REALIZADA 100 em `2026-12-31T23:30:00` conta; REALIZADA 100 em `2027-01-01T00:00:00` não conta → faturado 100.
3. **Mês vazio**: faturado e recebido `null`, semValor 0 (o service converte o nulo — este teste fixa o contrato da query).
4. **Isolamento**: consulta REALIZADA com valor de outro psicólogo no mesmo mês não entra.
5. **Sessões do mês** (`findByUsuarioAndStatusAndDtConsultaGreaterThanEqual...`) devolve só REALIZADAS do intervalo, ordem crescente, e `getPaciente().getNomePaciente()` acessível.
6. **Em aberto** (`findByUsuarioAndStatusAndValorIsNotNull...`) devolve REALIZADA com valor e sem baixa de **qualquer** mês, inclusive de paciente com `deletedAt` preenchido; não devolve paga, sem valor, FALTOU nem consulta com `deletedAt`.

Run: `sh ./mvnw -q test -Dtest='ConsultaAgregacaoRepositoryTest'` → PASS. **Se o Hibernate recusar o `CASE` sem `ELSE` ou a projeção por interface**, troque o `SUM(CASE ... THEN c.valor END)` por `SUM(CASE WHEN c.pagoEm IS NOT NULL THEN c.valor ELSE 0 END)` e reporte a troca; não troque o desenho da consulta por soma em memória.

- [ ] **Step 3: DTOs**

```java
package com.psychologyapi.psychologyapi.controller.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessaoFinanceiraDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        UUID pacienteId,
        String pacienteNome,
        boolean pacienteExcluido,
        BigDecimal valor,
        LocalDateTime pagoEm) {
}
```

```java
package com.psychologyapi.psychologyapi.controller.dto.financeiro;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DevedorDTO(
        UUID pacienteId,
        String pacienteNome,
        boolean pacienteExcluido,
        int quantidadeSessoes,
        BigDecimal total,
        List<UUID> consultaIds) {
}
```

```java
package com.psychologyapi.psychologyapi.controller.dto.financeiro;

import java.math.BigDecimal;
import java.util.List;

public record FinanceiroMesDTO(
        int ano,
        int mes,
        BigDecimal faturado,
        BigDecimal recebido,
        BigDecimal aReceber,
        int sessoesSemValor,
        List<SessaoFinanceiraDTO> sessoes,
        List<DevedorDTO> devendo) {
}
```

- [ ] **Step 4: Service e controller**

`M/service/FinanceiroService.java`:

```java
package com.psychologyapi.psychologyapi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.financeiro.DevedorDTO;
import com.psychologyapi.psychologyapi.controller.dto.financeiro.FinanceiroMesDTO;
import com.psychologyapi.psychologyapi.controller.dto.financeiro.SessaoFinanceiraDTO;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.projecao.TotaisFinanceirosDoMes;
import com.psychologyapi.psychologyapi.service.periodo.MesConsultado;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinanceiroService {

    private final SecurityService securityService;
    private final ConsultaRepository consultaRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FinanceiroMesDTO doMes(Integer ano, Integer mes) {
        MesConsultado periodo = MesConsultado.resolver(ano, mes, clock);
        Usuario usuario = securityService.obterUsuarioLogado();

        TotaisFinanceirosDoMes totais = consultaRepository.totaisFinanceirosDoMes(
                usuario, periodo.inicio(), periodo.fimExclusivo());
        BigDecimal faturado = emReais(totais.getFaturado());
        BigDecimal recebido = emReais(totais.getRecebido());

        List<SessaoFinanceiraDTO> sessoes = consultaRepository
                .findByUsuarioAndStatusAndDtConsultaGreaterThanEqualAndDtConsultaLessThanAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, Status.REALIZADA, periodo.inicio(), periodo.fimExclusivo())
                .stream()
                .map(c -> new SessaoFinanceiraDTO(
                        c.getId(),
                        c.getDtConsulta(),
                        c.getPaciente().getId(),
                        c.getPaciente().getNomePaciente(),
                        c.getPaciente().getDeletedAt() != null,
                        c.getValor() == null ? null : emReais(c.getValor()),
                        c.getPagoEm()))
                .toList();

        return new FinanceiroMesDTO(
                periodo.ano(),
                periodo.mes(),
                faturado,
                recebido,
                faturado.subtract(recebido),
                totais.getSemValor() == null ? 0 : totais.getSemValor().intValue(),
                sessoes,
                devendo(usuario));
    }

    private List<DevedorDTO> devendo(Usuario usuario) {
        Map<UUID, List<Consulta>> porPaciente = new LinkedHashMap<>();
        consultaRepository
                .findByUsuarioAndStatusAndValorIsNotNullAndPagoEmIsNullAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, Status.REALIZADA)
                .forEach(c -> porPaciente.computeIfAbsent(c.getPaciente().getId(), id -> new ArrayList<>()).add(c));

        return porPaciente.values().stream()
                .map(consultas -> {
                    Paciente paciente = consultas.get(0).getPaciente();
                    BigDecimal total = consultas.stream()
                            .map(Consulta::getValor)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DevedorDTO(
                            paciente.getId(),
                            paciente.getNomePaciente(),
                            paciente.getDeletedAt() != null,
                            consultas.size(),
                            emReais(total),
                            consultas.stream().map(Consulta::getId).toList());
                })
                .sorted(Comparator.comparing(DevedorDTO::total).reversed()
                        .thenComparing(DevedorDTO::pacienteNome))
                .toList();
    }

    private static BigDecimal emReais(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }
}
```

`M/controller/FinanceiroController.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.financeiro.FinanceiroMesDTO;
import com.psychologyapi.psychologyapi.service.FinanceiroService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FinanceiroService financeiroService;

    @GetMapping("/mes")
    public ResponseEntity<FinanceiroMesDTO> doMes(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes) {
        return ResponseEntity.ok(financeiroService.doMes(ano, mes));
    }
}
```

- [ ] **Step 5: Teste HTTP**

`T/controller/FinanceiroMesHttpTest.java` com `@HttpTest` e **`@Import(PainelHojeHttpTest.RelogioFixoConfig.class)`** — reaproveite a classe existente (relógio em 2026-12-02T10:00 America/Sao_Paulo). Não crie outro `@TestConfiguration`: classe de configuração diferente sobe outro contexto Spring e dobra o tempo da suíte.

Prepare no `@BeforeEach`: dono com token; "Bruna Teixeira" (valorSessao 180) com, em dezembro/2026, uma REALIZADA 180 paga, uma REALIZADA 180 sem baixa, uma FALTOU 180, e em **novembro** uma REALIZADA 150 sem baixa; "Ivan Prado" com uma REALIZADA sem valor em dezembro; "Clara Bueno" com uma REALIZADA 200 sem baixa em dezembro e depois `deletedAt` preenchido no **paciente** (soft delete do paciente, não da consulta); outro psicólogo com uma REALIZADA 500 sem baixa em dezembro; e um usuário sem nada.

Casos:

1. `?ano=2026&mes=12` → `$.faturado` 560.0, `$.recebido` 180.0, `$.aReceber` 380.0, `$.sessoesSemValor` 1, `$.sessoes.length()` 4 (as REALIZADAS de dezembro: Bruna paga, Bruna aberta, Ivan sem valor, Clara), `$.sessoes[0].dtConsulta` é a mais cedo. Conta: faturado = 180 + 180 + 200 = 560; recebido = 180.
2. `$.devendo` tem dois devedores: Bruna com `quantidadeSessoes` 2, `total` 330.0 (dezembro + novembro) e Clara com `pacienteExcluido` true, `total` 200.0; a ordem é Bruna (330) antes de Clara (200); `$.devendo[0].consultaIds.length()` 2. O outro psicólogo não aparece em nada.
3. Sem parâmetros → `$.ano` 2026, `$.mes` 12.
4. Usuário sem nada → 200, `$.faturado` 0.0, `$.sessoes` vazio, `$.devendo` vazio.
5. `?ano=2026` só → 400, `$.erros[0].campo` "mes", `$.erros[0].erro` "Informe 'ano' e 'mes' juntos, ou nenhum dos dois."
6. `?ano=2026&mes=13` → 400 campo "mes". `?ano=2026&mes=abc` → 400 (handler de type mismatch já existente).
7. Sem token → 401.

Ao preencher valores, use `new BigDecimal("180.00")`. Ajuste os números esperados se a sua montagem diferir, **mas** mantenha os invariantes: recebido + aReceber = faturado; FALTOU não entra; novembro não entra nos totais de dezembro e entra em `devendo`.

Acrescente a `T/controller/OpenApiHttpTest.java` um teste: `$.paths['/financeiro/mes']` existe.

Run: `sh ./mvnw -q test -Dtest='FinanceiroMesHttpTest,ConsultaAgregacaoRepositoryTest,MesConsultadoTest,OpenApiHttpTest'` → PASS. Depois `sh ./mvnw -q test` → verde.

- [ ] **Step 6: Não comite.** Reporte os arquivos.

---

### Task 4: `GET /painel/mes`, SEM_BAIXA na Hoje e dinheiro na ficha

**Files:**
- Create: `M/repository/projecao/ContagemDoMes.java`, `M/controller/dto/painel/PainelMesDTO.java`
- Modify: `M/repository/ConsultaRepository.java` (1 query + 1 remoção de comentário)
- Modify: `M/service/PainelService.java`, `M/controller/PainelController.java`
- Modify: `M/controller/dto/painel/SessaoDoDiaDTO.java`, `TipoPendencia.java`, `PendenciaDoPainelDTO.java`
- Modify: `M/controller/dto/ficha/SessaoDaFichaDTO.java`, `M/service/FichaService.java`
- Test: `T/controller/PainelMesHttpTest.java` (criar); `T/controller/PainelHojeHttpTest.java`, `T/service/PainelServiceTest.java`, `T/controller/FichaDoPacienteHttpTest.java`, `T/controller/OpenApiHttpTest.java` (acrescentar)

**Interfaces:**
- Consumes: `MesConsultado` (Tarefa 3).
- Produces (HTTP): `GET /painel/mes?ano&mes` → `PainelMesDTO(int ano, int mes, long realizadas, long faltas, long canceladas, BigDecimal taxaDeFalta, long pacientesAtivos)`; `SessaoDoDiaDTO(consultaId, dtConsulta, pacienteId, pacienteNome, status, valor, pagoEm)`; `TipoPendencia.SEM_BAIXA`; `SessaoDaFichaDTO(consultaId, dtConsulta, status, anotacoes, valor, pagoEm)`.

- [ ] **Step 1: Remova os trechos de comentário que ficam falsos**

Exatamente estes, sem reescrever o resto:

1. `TipoPendencia.java` — no Javadoc da classe, troque `a Fatia 1 so tinha SEM_STATUS, a Fatia 2 traz SEM_ANOTACAO, e a Fatia 3 vai trazer a de baixa.` por `a Fatia 1 so tinha SEM_STATUS, a Fatia 2 traz SEM_ANOTACAO.` (só some a oração final).
2. `PendenciaDoPainelDTO.java` — no Javadoc, apague ` — "realizada sem baixa" chega na Fatia 3 (spec 9)` (o resto fica: `... O \`tipo\` diz qual das especies e.`).
3. `SessaoDaFichaDTO.java` — apague o parágrafo inteiro `` `valor` e `pago` NAO entram aqui: sao da Fatia 3 (spec 9). Os campos ja existem em Consulta; nao antecipar.`` e a linha ` *` em branco que o precedia.
4. `ConsultaRepository.java` — no Javadoc do método `findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc`, apague ` — a unica pendencia da\n     *  Fatia 1 (spec 4 (1) e 9). As outras duas chegam nas Fatias 2 e 3.`, ficando `/** Sessao que passou da hora e continua AGENDADA. */`.

A constante nova `SEM_BAIXA` entra **sem** Javadoc.

- [ ] **Step 2: SEM_BAIXA e dinheiro na Hoje — testes primeiro**

Em `T/service/PainelServiceTest.java`, acrescente (o molde `consultaDe(...)` já existe; use `setValor`/`setPagoEm` na consulta devolvida):

```java
    @Test
    @DisplayName("realizada hoje com valor e sem baixa gera SEM_BAIXA, e a mesma sessao sem anotacao gera as duas")
    void realizadaSemBaixaESemAnotacao() {
        Consulta semNada = consultaDe("Bruna Teixeira", AGORA.minusHours(2), Status.REALIZADA);
        semNada.setValor(new BigDecimal("180.00"));
        Consulta paga = consultaDe("Ivan Prado", AGORA.minusHours(1), Status.REALIZADA);
        paga.setValor(new BigDecimal("180.00"));
        paga.setPagoEm(AGORA.minusMinutes(30));
        Consulta semValor = consultaDe("Clara Bueno", AGORA.minusMinutes(50), Status.REALIZADA);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(
                any(), any(), any()))
                .thenReturn(List.of(semNada, paga, semValor));
        when(consultaRepository.findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
                any(), any(), any()))
                .thenReturn(List.of());
        when(anotacaoRepository.idsDeConsultasComAnotacao(any(), anyList()))
                .thenReturn(List.of(paga.getId(), semValor.getId()));

        PainelHojeDTO painel = painelService.hoje();

        assertThat(painel.pendencias())
                .extracting(p -> p.pacienteNome() + ":" + p.tipo())
                .containsExactly("Bruna Teixeira:SEM_ANOTACAO", "Bruna Teixeira:SEM_BAIXA");
        assertThat(painel.sessoes())
                .filteredOn(s -> s.pacienteNome().equals("Ivan Prado"))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.valor()).isEqualByComparingTo("180.00");
                    assertThat(s.pagoEm()).isEqualTo(AGORA.minusMinutes(30));
                });
    }
```

(import `java.math.BigDecimal`).

Em `T/controller/PainelHojeHttpTest.java`, acrescente um teste que **cria os próprios dados dentro do método** (não mexa no `@BeforeEach`: os testes existentes contam `pendencias.length() == 2` e `sessoes.length() == 5`): paciente "Bruna Teixeira" com valorSessao 180; consulta REALIZADA hoje `2026-12-02T09:30:00` com valor 180 e sem baixa → `$.pendencias[?(@.tipo == 'SEM_BAIXA')].pacienteNome` contém "Bruna Teixeira"; e `$.sessoes[?(@.pacienteNome == 'Bruna Teixeira')].valor` contém 180.0. Um segundo teste: a mesma montagem com `pagoEm` preenchido → nenhuma SEM_BAIXA de Bruna.

- [ ] **Step 3: Implemente**

`SessaoDoDiaDTO`:

```java
public record SessaoDoDiaDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        UUID pacienteId,
        String pacienteNome,
        Status status,
        BigDecimal valor,
        LocalDateTime pagoEm) {
}
```

`TipoPendencia` ganha `SEM_BAIXA` como última constante (precedida de vírgula depois de `SEM_ANOTACAO`).

`PainelService.hoje()`: no `.map(c -> new SessaoDoDiaDTO(...))` acrescente `c.getValor(), c.getPagoEm()`. Depois do bloco `semAnotacao`, acrescente:

```java
        List<PendenciaDoPainelDTO> semBaixa = realizadasHoje.stream()
                .filter(c -> c.getValor() != null && c.getPagoEm() == null)
                .map(c -> new PendenciaDoPainelDTO(
                        c.getId(), c.getDtConsulta(), c.getPaciente().getNomePaciente(),
                        TipoPendencia.SEM_BAIXA))
                .toList();
```

e troque a montagem de `pendencias` por:

```java
        List<PendenciaDoPainelDTO> pendencias = Stream.of(semStatus, semAnotacao, semBaixa)
                .flatMap(List::stream)
                .sorted(Comparator.comparing(PendenciaDoPainelDTO::dtConsulta))
                .toList();
```

(`sorted` é estável: para a mesma sessão, SEM_ANOTACAO vem antes de SEM_BAIXA.)

Run: `sh ./mvnw -q test -Dtest='PainelServiceTest,PainelHojeHttpTest'` → PASS.

- [ ] **Step 4: `/painel/mes` — teste primeiro**

`T/controller/PainelMesHttpTest.java` com `@HttpTest` e `@Import(PainelHojeHttpTest.RelogioFixoConfig.class)`. Dados em dezembro/2026: Bruna com 2 REALIZADAS e 1 FALTOU; Ivan com 1 REALIZADA e 1 CANCELADA; uma REALIZADA com `deletedAt` (não conta); uma REALIZADA em novembro (não conta); uma REALIZADA de outro psicólogo (não conta). Casos:

1. `?ano=2026&mes=12` → `realizadas` 3, `faltas` 1, `canceladas` 1, `pacientesAtivos` 2, `taxaDeFalta` 0.25.
2. Usuário sem nada → `realizadas` 0, `taxaDeFalta` não existe (nulo), `pacientesAtivos` 0.
3. Só cancelada no mês (crie um usuário só com CANCELADA) → `taxaDeFalta` nulo.
4. Sem parâmetros → `ano` 2026, `mes` 12.
5. `?mes=12` só → 400 campo "ano".

Acrescente a `OpenApiHttpTest`: `$.paths['/painel/mes']` existe.

Projeção `M/repository/projecao/ContagemDoMes.java`:

```java
package com.psychologyapi.psychologyapi.repository.projecao;

public interface ContagemDoMes {

    Long getRealizadas();

    Long getFaltas();

    Long getCanceladas();

    Long getPacientesAtivos();
}
```

No `ConsultaRepository`:

```java
    @Query("""
            SELECT COUNT(CASE WHEN c.status = com.psychologyapi.psychologyapi.model.Status.REALIZADA THEN 1 END) AS realizadas,
                   COUNT(CASE WHEN c.status = com.psychologyapi.psychologyapi.model.Status.FALTOU THEN 1 END) AS faltas,
                   COUNT(CASE WHEN c.status = com.psychologyapi.psychologyapi.model.Status.CANCELADA THEN 1 END) AS canceladas,
                   COUNT(DISTINCT CASE WHEN c.status = com.psychologyapi.psychologyapi.model.Status.REALIZADA
                                       THEN c.paciente.id END) AS pacientesAtivos
            FROM Consulta c
            WHERE c.usuario = :usuario
              AND c.deletedAt IS NULL
              AND c.dtConsulta >= :inicio
              AND c.dtConsulta < :fimExclusivo
            """)
    ContagemDoMes contagemDoMes(
            @Param("usuario") Usuario usuario,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo);
```

Se o Hibernate recusar `COUNT(DISTINCT CASE ...)`, troque **só** `pacientesAtivos` por uma segunda query `@Query("SELECT COUNT(DISTINCT c.paciente.id) FROM Consulta c WHERE ... AND c.status = ...REALIZADA")` devolvendo `long`, e reporte.

`PainelMesDTO`:

```java
package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.math.BigDecimal;

public record PainelMesDTO(
        int ano,
        int mes,
        long realizadas,
        long faltas,
        long canceladas,
        BigDecimal taxaDeFalta,
        long pacientesAtivos) {
}
```

`PainelService` (imports: `java.math.BigDecimal`, `java.math.RoundingMode`, `PainelMesDTO`, `ContagemDoMes`, `MesConsultado`):

```java
    @Transactional(readOnly = true)
    public PainelMesDTO mes(Integer ano, Integer mes) {
        MesConsultado periodo = MesConsultado.resolver(ano, mes, clock);
        Usuario usuario = securityService.obterUsuarioLogado();
        ContagemDoMes contagem = consultaRepository.contagemDoMes(
                usuario, periodo.inicio(), periodo.fimExclusivo());

        long realizadas = contagem.getRealizadas();
        long faltas = contagem.getFaltas();
        long denominador = realizadas + faltas;
        BigDecimal taxaDeFalta = denominador == 0
                ? null
                : BigDecimal.valueOf(faltas).divide(BigDecimal.valueOf(denominador), 4, RoundingMode.HALF_UP);

        return new PainelMesDTO(periodo.ano(), periodo.mes(), realizadas, faltas,
                contagem.getCanceladas(), taxaDeFalta, contagem.getPacientesAtivos());
    }
```

`PainelController`:

```java
    @GetMapping("/mes")
    public ResponseEntity<PainelMesDTO> mes(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes) {
        return ResponseEntity.ok(painelService.mes(ano, mes));
    }
```

(import `RequestParam` e `PainelMesDTO`; não adicione Javadoc.)

Run: `sh ./mvnw -q test -Dtest='PainelMesHttpTest,OpenApiHttpTest'` → PASS.

- [ ] **Step 5: Ficha**

`SessaoDaFichaDTO` vira `(UUID consultaId, LocalDateTime dtConsulta, Status status, List<AnotacaoDaSessaoDTO> anotacoes, BigDecimal valor, LocalDateTime pagoEm)`. Em `FichaService`, o `new SessaoDaFichaDTO(...)` ganha `c.getValor(), c.getPagoEm()` no fim. Em `T/controller/FichaDoPacienteHttpTest.java`, acrescente um teste que cria uma REALIZADA com valor 180 e pagoEm e confere `valor` e `pagoEm` na linha dela.

- [ ] **Step 6: Suíte inteira**

Run: `sh ./mvnw -q test` → verde. Reporte a contagem.

- [ ] **Step 7: Não comite.** Reporte os arquivos.

---

### Task 5: Regerar o contrato (orquestrador, sem agente)

**Files:** `frontend/contrato-openapi.json`, `frontend/src/api/contrato.ts`

- [ ] **Step 1:** Suba a API com o código da fatia: `cd backend && ./debug.sh` em segundo plano; espere `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/v3/api-docs` responder 200.
- [ ] **Step 2:** `curl -s http://localhost:8080/v3/api-docs -o frontend/contrato-openapi.json`
- [ ] **Step 3:** Confira: `for s in FinanceiroMesDTO SessaoFinanceiraDTO DevedorDTO PainelMesDTO BaixaEmLoteDTO BaixaEmLoteResponseDTO ValorDaConsultaDTO SEM_BAIXA '/financeiro/mes' '/painel/mes' '/consultas/baixas'; do printf '%-26s %s\n' "$s" "$(grep -c "$s" frontend/contrato-openapi.json)"; done` — todos ≥ 1.
- [ ] **Step 4:** `cd frontend && ASDF_NODEJS_VERSION=20.19.1 npm run tipos && ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit -p .`. Erros de tipo esperados só em `fila.test.ts`/fixtures que montam `SessaoDoDia` com `Required<>` — se houver, ficam para as Tarefas 6 e 8 (registre a lista).
- [ ] **Step 5:** Derrube a API.

---

### Task 6: Fundação de dinheiro no front

**Files:**
- Modify: `frontend/vite.config.ts` (`CAMINHOS_DA_API` ganha `'/financeiro'` — só isso)
- Create: `frontend/src/ui/dinheiro.ts`, `frontend/src/ui/dinheiro.test.ts`
- Create: `frontend/src/telas/financeiro/queries.ts`, `frontend/src/telas/financeiro/queries.test.tsx`
- Modify: `frontend/src/telas/hoje/queries.ts` (só o `onSuccess` de `useMarcarStatus`)

**Interfaces:**
- Produces: `emReais(valor: number): string`; `mesPorExtenso(ano: number, mes: number): string`; `mesVizinho(ano: number, mes: number, passo: -1 | 1): { ano: number; mes: number }`.
- Produces (de `telas/financeiro/queries.ts`): tipos `SessaoFinanceira`, `Devedor`, `FinanceiroDoMes`, `PainelDoMes`; `useFinanceiroDoMes(ano, mes)`, `usePainelDoMes(ano, mes)`; `invalidarDinheiro(cliente: QueryClient): Promise<void>`; mutações `useDarBaixa()` (`mutate(consultaId: string)`), `useDesfazerBaixa()` (`mutate(consultaId: string)`), `useDarBaixaEmLote()` (`mutate(consultaIds: string[])`, devolve `{ consultasBaixadas: number }`), `useAlterarValor()` (`mutateAsync({ consultaId: string; valor: number })`).

- [ ] **Step 1: `dinheiro.ts` com teste primeiro**

`frontend/src/ui/dinheiro.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { emReais, mesPorExtenso, mesVizinho } from './dinheiro'

describe('dinheiro', () => {
  it('formata em reais com duas casas', () => {
    expect(emReais(150).replace(/ /g, ' ')).toBe('R$ 150,00')
    expect(emReais(1234.5).replace(/ /g, ' ')).toBe('R$ 1.234,50')
  })

  it('escreve o mes por extenso', () => {
    expect(mesPorExtenso(2026, 10)).toBe('outubro de 2026')
  })

  it('anda um mes para tras e para frente atravessando o ano', () => {
    expect(mesVizinho(2027, 1, -1)).toEqual({ ano: 2026, mes: 12 })
    expect(mesVizinho(2026, 12, 1)).toEqual({ ano: 2027, mes: 1 })
    expect(mesVizinho(2026, 6, 1)).toEqual({ ano: 2026, mes: 7 })
  })
})
```

`frontend/src/ui/dinheiro.ts`:

```ts
export function emReais(valor: number): string {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function mesPorExtenso(ano: number, mes: number): string {
  return new Date(ano, mes - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
}

export function mesVizinho(ano: number, mes: number, passo: -1 | 1): { ano: number; mes: number } {
  const data = new Date(ano, mes - 1 + passo, 1)
  return { ano: data.getFullYear(), mes: data.getMonth() + 1 }
}
```

Run: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/ui/dinheiro.test.ts` → PASS.

- [ ] **Step 2: `telas/financeiro/queries.ts`**

Os nomes de schema vêm do contrato regerado (Tarefa 5): confira em `src/api/contrato.ts` que existem `FinanceiroMesDTO`, `SessaoFinanceiraDTO`, `DevedorDTO`, `PainelMesDTO`, `BaixaEmLoteResponseDTO`. Container à mão, tipo de cada campo do contrato — padrão da casa (`telas/pacientes/queries.ts`).

```ts
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'

type SessaoDTO = components['schemas']['SessaoFinanceiraDTO']
type DevedorDTO = components['schemas']['DevedorDTO']
type FinanceiroDTO = components['schemas']['FinanceiroMesDTO']
type PainelMesDTO = components['schemas']['PainelMesDTO']
type ConsultaDTO = components['schemas']['ConsultaResponseDTO']

export type SessaoFinanceira = {
  consultaId: NonNullable<SessaoDTO['consultaId']>
  dtConsulta: NonNullable<SessaoDTO['dtConsulta']>
  pacienteId: NonNullable<SessaoDTO['pacienteId']>
  pacienteNome: NonNullable<SessaoDTO['pacienteNome']>
  pacienteExcluido: NonNullable<SessaoDTO['pacienteExcluido']>
  valor: NonNullable<SessaoDTO['valor']> | null
  pagoEm: NonNullable<SessaoDTO['pagoEm']> | null
}

export type Devedor = {
  pacienteId: NonNullable<DevedorDTO['pacienteId']>
  pacienteNome: NonNullable<DevedorDTO['pacienteNome']>
  pacienteExcluido: NonNullable<DevedorDTO['pacienteExcluido']>
  quantidadeSessoes: NonNullable<DevedorDTO['quantidadeSessoes']>
  total: NonNullable<DevedorDTO['total']>
  consultaIds: NonNullable<DevedorDTO['consultaIds']>
}

export type FinanceiroDoMes = {
  ano: NonNullable<FinanceiroDTO['ano']>
  mes: NonNullable<FinanceiroDTO['mes']>
  faturado: NonNullable<FinanceiroDTO['faturado']>
  recebido: NonNullable<FinanceiroDTO['recebido']>
  aReceber: NonNullable<FinanceiroDTO['aReceber']>
  sessoesSemValor: NonNullable<FinanceiroDTO['sessoesSemValor']>
  sessoes: SessaoFinanceira[]
  devendo: Devedor[]
}

export type PainelDoMes = {
  ano: NonNullable<PainelMesDTO['ano']>
  mes: NonNullable<PainelMesDTO['mes']>
  realizadas: NonNullable<PainelMesDTO['realizadas']>
  faltas: NonNullable<PainelMesDTO['faltas']>
  canceladas: NonNullable<PainelMesDTO['canceladas']>
  taxaDeFalta: NonNullable<PainelMesDTO['taxaDeFalta']> | null
  pacientesAtivos: NonNullable<PainelMesDTO['pacientesAtivos']>
}

export type ConsultaComDinheiro = Pick<Required<ConsultaDTO>, 'id' | 'status'>

const parametros = (ano: number, mes: number) => new URLSearchParams({ ano: String(ano), mes: String(mes) })

export function useFinanceiroDoMes(ano: number, mes: number) {
  return useQuery({
    queryKey: ['financeiro', 'mes', ano, mes],
    queryFn: () => api<FinanceiroDoMes>(`/financeiro/mes?${parametros(ano, mes)}`),
  })
}

export function usePainelDoMes(ano: number, mes: number) {
  return useQuery({
    queryKey: ['painel', 'mes', ano, mes],
    queryFn: () => api<PainelDoMes>(`/painel/mes?${parametros(ano, mes)}`),
  })
}

export async function invalidarDinheiro(cliente: QueryClient) {
  await Promise.all([
    cliente.invalidateQueries({ queryKey: ['financeiro'] }),
    cliente.invalidateQueries({ queryKey: ['painel'] }),
    cliente.invalidateQueries({ queryKey: ['ficha'] }),
  ])
}

export function useDarBaixa() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (consultaId: string) =>
      api<ConsultaComDinheiro>(`/consultas/${consultaId}/baixa`, { method: 'PATCH' }),
    onSuccess: () => invalidarDinheiro(cliente),
  })
}

export function useDesfazerBaixa() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (consultaId: string) =>
      api<ConsultaComDinheiro>(`/consultas/${consultaId}/baixa`, { method: 'DELETE' }),
    onSuccess: () => invalidarDinheiro(cliente),
  })
}

export function useDarBaixaEmLote() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (consultaIds: string[]) =>
      api<{ consultasBaixadas: number }>('/consultas/baixas', {
        method: 'POST',
        body: JSON.stringify({ consultaIds }),
      }),
    onSuccess: () => invalidarDinheiro(cliente),
  })
}

export function useAlterarValor() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ consultaId, valor }: { consultaId: string; valor: number }) =>
      api<ConsultaComDinheiro>(`/consultas/${consultaId}/valor`, {
        method: 'PATCH',
        body: JSON.stringify({ valor }),
      }),
    onSuccess: () => invalidarDinheiro(cliente),
  })
}
```

Se algum `NonNullable<X['campo']>` não compilar porque o gerador nomeou diferente (ex.: `aReceber`), **leia o nome no `contrato.ts`** e use o nome real; não invente.

- [ ] **Step 3: Teste das mutações**

`frontend/src/telas/financeiro/queries.test.tsx` com `renderHook` de `@testing-library/react`, wrapper `QueryClientProvider` com `clienteDeTeste()` (de `src/teste/renderizar.tsx`) e `vi.spyOn(cliente, 'invalidateQueries')`:

1. `useDarBaixa().mutateAsync('id-1')` faz `PATCH /consultas/id-1/baixa` sem corpo (MSW captura `request.method` e `await request.text()` vazio) e chama `invalidateQueries` com `{ queryKey: ['financeiro'] }`, `{ queryKey: ['painel'] }` e `{ queryKey: ['ficha'] }`.
2. `useDesfazerBaixa` usa `DELETE` na mesma rota.
3. `useDarBaixaEmLote().mutateAsync(['a','b'])` faz `POST /consultas/baixas` com corpo `{"consultaIds":["a","b"]}` e devolve `{ consultasBaixadas: 2 }`.
4. `useAlterarValor().mutateAsync({ consultaId: 'id-1', valor: 90.5 })` faz `PATCH /consultas/id-1/valor` com `{"valor":90.5}`.
5. `useFinanceiroDoMes(2026, 12)` chama `/financeiro/mes?ano=2026&mes=12` (MSW lê `new URL(request.url).searchParams`).

Use UUIDs de verdade nos ids dos fixtures (convenção da casa), não `'id-1'` — os exemplos acima são só a forma.

- [ ] **Step 4: A troca de status passa a derrubar o dinheiro**

Em `telas/hoje/queries.ts`, `useMarcarStatus`: troque **só** a linha `onSuccess: () => cliente.invalidateQueries({ queryKey: CHAVE_DO_PAINEL }),` por `onSuccess: () => invalidarDinheiro(cliente),` (import de `../financeiro/queries`). O comentário acima da linha fica. `['painel']` cobre `['painel','hoje']`.

- [ ] **Step 5: Vite**

Em `vite.config.ts`, `CAMINHOS_DA_API` ganha `'/financeiro'` no fim da lista. Não mexa no comentário.

- [ ] **Step 6: Verificação**

Run: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run` e `ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit -p .`. Se o `tsc` acusar só os erros de `SessaoDoDia` registrados na Tarefa 5 (fixtures sem `valor`/`pagoEm`), reporte-os como esperados — a Tarefa 8 resolve. Qualquer outro erro é seu.

- [ ] **Step 7: Não comite.** Reporte os arquivos.

---

### Task 7: A tela Financeiro

**Files:**
- Create: `frontend/src/telas/financeiro/FinanceiroPage.tsx`, `DialogoDeValor.tsx`, `esquema.ts`
- Create: `frontend/src/telas/financeiro/FinanceiroPage.test.tsx`, `DialogoDeValor.test.tsx`
- Modify: `frontend/src/rotas.tsx` (rota `/financeiro` aponta para `FinanceiroPage`; `AindaNaoNestaFatia` continua para `/ajustes`)
- Modify: `frontend/src/estilo/estilo.css` (bloco novo no fim, só tokens)

**Interfaces:**
- Consumes: tudo de `telas/financeiro/queries.ts` e `ui/dinheiro.ts` (Tarefa 6); `usePrivacidade`; `Botao`, `Chip`, `EstadoVazio`, `Campo`, `ResumoDeErros`, `useFormularioDoProduto`.

**Estrutura da tela (de cima para baixo) — siga literalmente, a cópia é com acento:**

```
<div className="tela">
  <header className="cabecalho-dia">
    <div><h1>Financeiro</h1><p className="sub">{mesPorExtenso(ano, mes)}</p></div>
    <div className="acoes-cabecalho-dia">
      <Botao variante="secundario" className="mini">Mês anterior</Botao>
      <Botao variante="secundario" className="mini">Próximo mês</Botao>
    </div>
  </header>
  <p className="sub" role="status">  ← sempre no DOM: "Carregando…" enquanto carrega; depois o anúncio do lote; senão vazio
  erro de carga: <p className="anuncio" role="alert">Não foi possível carregar o financeiro. Tente de novo.</p>
  <section aria-label="O mês"> <h2 className="titulo-secao">O mês</h2> <dl className="indicadores cartao">
     Realizadas · Faltas · Canceladas · Taxa de falta · Pacientes ativos   (cada um <div className="indicador"><dt>…</dt><dd>…</dd></div>)
  <section aria-label="Totais"> <h2 className="titulo-secao">Totais</h2> <dl className="indicadores cartao">
     Faturado · Recebido · A receber  (dd com <Valor>)
     + <p className="sub">N sessões sem valor definido, fora dos totais</p> (singular: "1 sessão sem valor definido, fora dos totais"; some quando 0)
  <section aria-label="Quem está devendo"> <h2 className="titulo-secao">Quem está devendo</h2>
     vazio: <EstadoVazio titulo="Ninguém devendo" apoio="Sessões realizadas sem baixa aparecem aqui." />
     senão <ul className="lista-sessoes cartao"> uma <li className="linha-pendencia" aria-label={`Débito de ${nome}`}> por devedor:
        <span><Nome>{nome}</Nome>{excluido && ' (paciente excluído)'} · {n} {n === 1 ? 'sessão' : 'sessões'} · <Valor>{emReais(total)}</Valor></span>
        <span className="acoes-linha">
          <Botao className="mini" disabled={lote em voo para este devedor}>Dar baixa em todas ({n})</Botao>
          {!excluido && <Link className="botao secundario mini" to={`/pacientes/${pacienteId}`}>Abrir a ficha</Link>}
        </span>
        erro do lote para este devedor: <p className="anuncio" role="alert">{mensagem do servidor}</p>
  <section aria-label="Sessões do mês"> <h2 className="titulo-secao">Sessões do mês</h2>
     vazio: <EstadoVazio titulo={`Nenhuma sessão realizada em ${mesPorExtenso(ano, mes)}.`} />
     senão <ul className="lista-sessoes cartao"> uma <li className="linha-pendencia" aria-label={`Sessão de ${nome}`}>:
        <span><span className="hora">{dd/mm · HH:mm}</span> <Nome>{nome}</Nome>{valor != null && <> · <Valor>{emReais(valor)}</Valor></>}</span>
        <span className="acoes-linha">
          chip:  valor == null → <Chip>Sem valor definido</Chip>
                 pagoEm       → <Chip variante="realizada">Pago</Chip>
                 senão        → <Chip variante="alerta">A receber</Chip>
          ações: valor == null → <Botao variante="secundario" className="mini">Definir valor</Botao>
                 pagoEm       → <Botao variante="secundario" className="mini">Desfazer baixa</Botao>
                 senão        → <Botao className="mini">Dar baixa</Botao> + <Botao variante="secundario" className="mini">Alterar valor</Botao>
        </span>
        erro de baixa/desfazer desta linha: <p className="anuncio" role="alert">{mensagem}</p>
  {editando && <DialogoDeValor sessao={editando} aoFechar={() => setEditando(null)} />}
</div>
```

Regras de comportamento:
- O mês começa no mês corrente do relógio (`new Date()`), vive em `useState`; os botões usam `mesVizinho`.
- `Nome` e `Valor` são componentes **fora** de `FinanceiroPage` (mesma razão da Hoje): `<span data-borrado={privacidade ? 'true' : undefined}>`. Contagens do painel, a porcentagem e os chips **não** borram.
- Taxa de falta: `Math.round(taxaDeFalta * 100) + '%'`; nula → `—`.
- Data/hora da linha: `deApi(dtConsulta)` formatado `toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })` + ` · ` + `toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })`.
- Lote: `lote.mutate(devedor.consultaIds, { onSuccess: (r) => setAnuncio({ quantidade: r.consultasBaixadas, nome: devedor.pacienteNome }) })`. O anúncio renderiza dentro do `role="status"`: `{q} {q === 1 ? 'sessão' : 'sessões'} de <Nome>{nome}</Nome> com baixa.` Trocar de mês limpa o anúncio.
- Erro de linha: compare `mutation.variables` com o id da linha (molde de `HojePage`); `ErroDaApi` → `erro.mensagem`, senão "Não foi possível completar a ação. Tente de novo."
- Botão de ação fica `disabled` enquanto a mutação daquela linha está em voo.
- Diálogo com **montagem condicional** (molde de `FichaPage`/`PacientesPage`).

**`esquema.ts`:**

```ts
import { z } from 'zod'

export const esquemaDoValor = z.object({
  valor: z
    .string()
    .refine((v) => v.trim() !== '', 'Informe o valor da sessão.')
    .refine(
      (v) => v.trim() === '' || /^\d+(\.\d{1,2})?$/.test(v.trim()),
      'O valor da sessão deve ter no máximo duas casas decimais.',
    )
    .refine((v) => v.trim() === '' || Number(v) > 0, 'O valor da sessão deve ser maior que zero.'),
})

export type DadosDoValor = z.infer<typeof esquemaDoValor>
```

**`DialogoDeValor.tsx`** — molde de `DialogoDePaciente.tsx` (Radix `Dialog.Root/Portal/Overlay className="mascara"/Content className="dialogo" aria-describedby={undefined}`, `dialogo-cabecalho`, `dialogo-corpo`, `dialogo-rodape`, `ResumoDeErros`, alerta de erro sem campo). Props `{ sessao: SessaoFinanceira; aoFechar: () => void }`. Título `sessao.valor == null ? 'Definir valor da sessão' : 'Alterar valor da sessão'`. Um `Campo` `rotulo="Valor da sessão" obrigatorio type="number" step="0.01"`, `defaultValues: { valor: sessao.valor != null ? String(sessao.valor) : '' }`. Rodapé: `<Botao variante="secundario" type="button" onClick={aoFechar}>Cancelar</Botao>` e `<Botao type="submit" disabled={f.enviando}>Salvar</Botao>`. Enviar: `await alterar.mutateAsync({ consultaId: sessao.consultaId, valor: Number(valores.valor) }); aoFechar()`.

**CSS** (fim de `estilo.css`, sem comentário, só tokens existentes):

```css
.indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: var(--gap-4); margin: 0 0 var(--gap-3); }
.indicador { display: flex; flex-direction: column; gap: var(--gap-1); margin: 0; }
.indicador dt { font-size: var(--t-segundo-tam); line-height: var(--t-segundo-alt); color: var(--tinta-2); }
.indicador dd { margin: 0; font-size: var(--t-secao-tam); line-height: var(--t-secao-alt); font-weight: var(--peso-forte); font-variant-numeric: tabular-nums; color: var(--tinta); }
```

- [ ] **Step 1: Testes que falham**

`FinanceiroPage.test.tsx` — importa `'../../estilo/estilo.css'` (para medir o borrão, como `HojePage.test.tsx`), usa `renderizar`, `servidor.use(http.get('/financeiro/mes', ...), http.get('/painel/mes', ...))`, UUIDs de verdade, fixture com: Bruna (2 sessões abertas, total 360, não excluída), Clara (1 sessão, 200, `pacienteExcluido: true`), sessões do mês com uma paga, uma a receber e uma sem valor, `sessoesSemValor: 1`, painel com `realizadas: 3, faltas: 1, canceladas: 0, taxaDeFalta: 0.25, pacientesAtivos: 2`. Fixe a data com `vi.useFakeTimers({ toFake: ['Date'] }); vi.setSystemTime(new Date(2026, 11, 15))` e `vi.useRealTimers()` no fim de cada teste que usar (ou em `afterEach`). Casos:

1. Título "Financeiro" e "dezembro de 2026"; as duas requisições vão com `ano=2026&mes=12`.
2. Região "O mês": `Realizadas` 3, `Taxa de falta` "25%", `Pacientes ativos` 2 (`expect(within(regiao).getByText('Taxa de falta').parentElement).toHaveTextContent('Taxa de falta25%')`).
3. Taxa nula → "—".
4. Região "Totais": "R$ 560,00" etc. e "1 sessão sem valor definido, fora dos totais"; com `sessoesSemValor: 2` → "2 sessões ..."; com 0 → o texto não existe.
5. Região "Quem está devendo": a linha de Clara tem "(paciente excluído)" e **não** tem link "Abrir a ficha"; a de Bruna tem o link com `href` `/pacientes/<id>`.
6. "Dar baixa em todas (2)" faz `POST /consultas/baixas` com os dois ids de Bruna e o `role="status"` passa a ter "2 sessões de Bruna Teixeira com baixa."
7. Lote com 409 do servidor → `role="alert"` com a mensagem dentro da linha de Bruna.
8. Ninguém devendo → "Ninguém devendo" e "Sessões realizadas sem baixa aparecem aqui."
9. Região "Sessões do mês": linha paga tem chip "Pago" e botão "Desfazer baixa" (DELETE ao clicar); linha a receber tem "A receber", "Dar baixa" (PATCH sem corpo) e "Alterar valor"; linha sem valor tem "Sem valor definido" e "Definir valor", e **não** tem "Dar baixa".
10. Mês sem sessões → "Nenhuma sessão realizada em dezembro de 2026."
11. "Mês anterior" a partir de janeiro/2027 (setSystemTime em 2027-01-15) pede `ano=2026&mes=12` e mostra "dezembro de 2026"; "Próximo mês" de dezembro/2026 pede `ano=2027&mes=1`.
12. Privacidade ligada (`renderizar(..., { privacidadeLigada: true })`): o nome "Bruna Teixeira" e o valor "R$ 560,00" têm `getComputedStyle(el).filter === 'blur(6px) saturate(0.7)'`; o chip "Pago" e o "25%" não.
13. Erro em `GET /financeiro/mes` (500 no envelope) → "Não foi possível carregar o financeiro. Tente de novo."
14. "Alterar valor" abre o diálogo "Alterar valor da sessão" com o valor atual preenchido.

`DialogoDeValor.test.tsx`:

1. Salvar vazio → "Informe o valor da sessão." e nenhuma requisição.
2. "10.123" → "O valor da sessão deve ter no máximo duas casas decimais."; "0" → "O valor da sessão deve ser maior que zero."
3. "90.5" → `PATCH /consultas/<id>/valor` com `{"valor":90.5}` e `aoFechar` chamado.
4. 409 do servidor sem campo → `role="alert"` com a mensagem; 400 com `erros:[{campo:'valor', erro:'...'}]` → a mensagem do servidor no campo.
5. "Cancelar" chama `aoFechar` sem requisição.

Run: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/financeiro` → falha (componentes não existem).

- [ ] **Step 2: Implemente** `esquema.ts`, `DialogoDeValor.tsx`, `FinanceiroPage.tsx`, a rota e o CSS.

- [ ] **Step 3: Verificação**

Run: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run` (tudo) e `ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit -p .` → verdes (exceto os erros de `SessaoDoDia` herdados, se ainda existirem — reporte).

- [ ] **Step 4: Não comite.** Reporte os arquivos.

---

### Task 8: Hoje — duas pendências na mesma linha, baixa e desfazer

**Files:**
- Modify: `frontend/src/telas/hoje/queries.ts` (tipo `SessaoDoDia`)
- Modify: `frontend/src/telas/hoje/fila.ts`, `fila.test.ts`
- Modify: `frontend/src/telas/hoje/HojePage.tsx`, `HojePage.test.tsx`

**Interfaces:**
- Consumes: `useDarBaixa`, `useDesfazerBaixa` (Tarefa 6); `TipoPendencia` com `SEM_BAIXA` (contrato).
- Produces: `ItemDaFila.tipos: PendenciaDoPainel['tipo'][]` (substitui `tipo`).

- [ ] **Step 1: Tipo `SessaoDoDia` honesto**

Em `hoje/queries.ts`, troque `export type SessaoDoDia = Required<components['schemas']['SessaoDoDiaDTO']>` por um container à mão (o comentário acima dele fala de `Required<>` e continua valendo para `PendenciaDoPainel` — não toque):

```ts
type SessaoDoDiaDTO = components['schemas']['SessaoDoDiaDTO']

export type SessaoDoDia = {
  consultaId: NonNullable<SessaoDoDiaDTO['consultaId']>
  dtConsulta: NonNullable<SessaoDoDiaDTO['dtConsulta']>
  pacienteId: NonNullable<SessaoDoDiaDTO['pacienteId']>
  pacienteNome: NonNullable<SessaoDoDiaDTO['pacienteNome']>
  status: NonNullable<SessaoDoDiaDTO['status']>
  valor: NonNullable<SessaoDoDiaDTO['valor']> | null
  pagoEm: NonNullable<SessaoDoDiaDTO['pagoEm']> | null
}
```

- [ ] **Step 2: `reconciliar` com `tipos[]` — teste primeiro**

Em `fila.test.ts`: o helper `sessao()` ganha `valor: null, pagoEm: null` nos padrões; troque toda asserção sobre `.tipo` por `.tipos`. Acrescente:

```ts
  it('a mesma sessao com duas pendencias vira UMA linha com as duas, na ordem recebida', () => {
    const r = reconciliar(
      [sessao({ consultaId: 'a', status: 'REALIZADA' })],
      [
        pendencia({ consultaId: 'a', tipo: 'SEM_ANOTACAO' }),
        pendencia({ consultaId: 'a', tipo: 'SEM_BAIXA' }),
      ],
    )
    expect(r.fila).toHaveLength(1)
    expect(r.fila[0].tipos).toEqual(['SEM_ANOTACAO', 'SEM_BAIXA'])
    expect(r.resolvidas).toHaveLength(0)
  })

  it('sessao agendada sem pendencia entra com tipos vazio', () => {
    const r = reconciliar([sessao({ consultaId: 'a', status: 'AGENDADA' })], [])
    expect(r.fila[0].tipos).toEqual([])
  })
```

Implementação em `fila.ts`: `ItemDaFila` troca `tipo: PendenciaDoPainel['tipo'] | null` por `tipos: PendenciaDoPainel['tipo'][]`; o laço de sessões cria com `tipos: []`; o laço de pendências faz, se já existe item para o `consultaId`, `item.tipos.push(p.tipo)` quando ainda não estiver lá; senão cria o item como hoje, com `tipos: [p.tipo]`.

Run: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/hoje/fila.test.ts` → PASS.

- [ ] **Step 3: HojePage — testes primeiro**

Acrescente a `HojePage.test.tsx` (as fixtures existentes de sessão ganham `valor: null, pagoEm: null` só onde o `tsc` exigir):

1. Pendência `SEM_BAIXA` sozinha → a linha mostra "dê baixa no pagamento" e o botão "Dar baixa"; clicar faz `PATCH /consultas/<id>/baixa`.
2. Mesma `consultaId` com `SEM_ANOTACAO` e `SEM_BAIXA` (sessão REALIZADA no array de sessões) → **uma** linha na região "Precisa de voce" contendo "registre a anotacao · dê baixa no pagamento".
3. Sessão REALIZADA com `pagoEm` e sem pendência → na região "Sessoes de hoje", a linha tem o chip "Pago" e o botão "Desfazer baixa"; clicar faz `DELETE /consultas/<id>/baixa`.
4. "Dar baixa" com 409 do servidor → `role="alert"` com a mensagem dentro da linha.
5. A tela não mostra valor em R$ em lugar nenhum (`expect(screen.queryByText(/R\$/)).toBeNull()` com uma sessão de valor 180 na fixture).
6. Os testes existentes continuam verdes sem mudar a asserção.

- [ ] **Step 4: Implemente em `HojePage.tsx`**

- `frasePendente` ganha `case 'SEM_BAIXA': return 'dê baixa no pagamento'`.
- `LinhaDaFila`: a frase vira `{item.tipos.length > 0 && <span className="acao-pedida"> · {item.tipos.map(frasePendente).join(' · ')}</span>}`. Quando `item.tipos.includes('SEM_BAIXA')`, acrescenta `<Botao className="mini" onClick={aoDarBaixa}>Dar baixa</Botao>` antes de "Abrir a ficha". O erro mostrado na linha passa a ser o de marcar status **ou** o da baixa daquela consulta (mesmo texto de fallback de hoje).
- Lista "Sessoes de hoje": a `<li className="linha-resolvida">` mantém hora, nome e o chip de status; os itens à direita passam para `<span className="acoes-linha">` contendo o chip de status, e, quando `s.pagoEm`, `<Chip variante="realizada">Pago</Chip>` e `<Botao variante="secundario" className="mini" onClick=...>Desfazer baixa</Botao>`, mais o alerta de erro do desfazer daquela linha.
- `useDarBaixa`/`useDesfazerBaixa` vêm de `../financeiro/queries`. Nenhum valor em R$ na tela.

Run: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/hoje` → PASS; depois `npx vitest run` e `npx tsc --noEmit -p .` (com o prefixo) → verdes.

- [ ] **Step 5: Não comite.** Reporte os arquivos.

---

### Task 9: Ficha — valor, chip e baixa na linha do tempo

**Files:**
- Modify: `frontend/src/telas/ficha/queries.ts` (tipo `SessaoDaFicha`)
- Modify: `frontend/src/telas/ficha/LinhaDoTempo.tsx`
- Modify: `frontend/src/telas/ficha/FichaPage.test.tsx`

**Interfaces:**
- Consumes: `emReais` (Tarefa 6), `useDarBaixa`, `useDesfazerBaixa` (Tarefa 6).

- [ ] **Step 1: Tipo**

`SessaoDaFicha` ganha:

```ts
  valor: NonNullable<SessaoDTO['valor']> | null
  pagoEm: NonNullable<SessaoDTO['pagoEm']> | null
```

- [ ] **Step 2: Testes primeiro** (em `FichaPage.test.tsx`; a fixture `FICHA` ganha `valor`/`pagoEm` em cada sessão — `null` nas existentes, salvo onde o caso pedir):

1. REALIZADA com `valor: 180, pagoEm: null` → a linha tem "R$ 180,00", chip "A receber" e botão "Dar baixa"; clicar faz `PATCH /consultas/<id>/baixa`.
2. REALIZADA paga → chip "Pago" e "Desfazer baixa" (DELETE).
3. REALIZADA sem valor → chip "Sem valor definido", sem botão de baixa.
4. FALTOU → chip "Não cobrada"; AGENDADA → nenhum desses chips nem botões.
5. Baixa com 409 → `role="alert"` com a mensagem na linha.
6. A caixa de registro de anotação continua aparecendo para REALIZADA sem anotação (teste existente verde).

- [ ] **Step 3: Implemente em `LinhaDoTempo.tsx`**

Dentro do `<div className="acoes-linha">` de cada sessão, depois do chip de status:

- `s.status === 'REALIZADA' && s.valor != null` → `<span>{emReais(s.valor)}</span>`, `s.pagoEm ? <Chip variante="realizada">Pago</Chip> : <Chip variante="alerta">A receber</Chip>`, e `s.pagoEm ? <Botao variante="secundario" className="mini">Desfazer baixa</Botao> : <Botao className="mini">Dar baixa</Botao>`.
- `s.status === 'REALIZADA' && s.valor == null` → `<Chip>Sem valor definido</Chip>`.
- `s.status === 'FALTOU'` → `<Chip>Não cobrada</Chip>`.

Erro de baixa/desfazer daquela sessão: `<p className="anuncio" role="alert">` logo abaixo do `div`, mesmo padrão de comparação por `variables`. A ficha **não** borra nada sob privacidade.

Run: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run src/telas/ficha` → PASS; `npx vitest run` e `npx tsc --noEmit -p .` → verdes e **sem** erros herdados.

- [ ] **Step 4: Não comite.** Reporte os arquivos.

---

### Task 10: Fluxo real do dinheiro (e2e) e documentação

**Files:**
- Create: `frontend/e2e/fluxo-do-dinheiro.spec.ts`
- Modify: `README.md` (raiz), `frontend/README.md`, `backend/README.md` (se listar endpoints), `ai/Claude.md`

- [ ] **Step 1: O e2e** — molde de `e2e/fluxo-da-sessao.spec.ts` (registrar pela API com e-mail único, entrar pela tela, navegar por rótulo escrito). Sem comentários no arquivo. Passos:
  1. Pacientes → "+ Novo paciente" → Nome "Paciente Dinheiro", "Valor da sessao" `150` → Salvar.
  2. Agenda → célula de hoje às `07:00` → selecionar o paciente → Salvar.
  3. Hoje → na linha `Sessao de Paciente Dinheiro`, "Realizada" → a linha passa a mostrar "dê baixa no pagamento".
  4. Financeiro (navegação) → região "Totais" contém "R$ 150,00" em Faturado e em A receber; região "Quem está devendo" tem "Paciente Dinheiro".
  5. "Dar baixa em todas (1)" → `role="status"` contém "1 sessão de Paciente Dinheiro com baixa."; "Ninguém devendo" aparece; Recebido mostra "R$ 150,00".
  6. Região "Sessões do mês" → "Desfazer baixa" → "Paciente Dinheiro" volta para "Quem está devendo".

  Pré-requisito: API no ar em 8080 com o código da fatia (`cd backend && ./debug.sh` em segundo plano) — o Playwright sobe só o Vite. Rode `cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx playwright test` (os três fluxos). Derrube a API ao fim.

- [ ] **Step 2: Documentação** — só o que a fatia torna falso, sem reescrever o resto:
  - `README.md` raiz: tabela de telas ganha `Financeiro | /financeiro | o mês: presença, faturado/recebido/a receber, quem deve, baixa e valor da sessão`; a frase "O que **não** existe, de propósito: financeiro, ..." perde "financeiro" e ganha "gateway de pagamento, recibo, NFS-e".
  - `frontend/README.md`: tabela de telas (`/financeiro` deixa de ser placeholder), tabela de endpoints (as 6 rotas novas e quem chama), lista do proxy (`/financeiro`), chaves de invalidação (`['financeiro']`, `['painel']` prefixo, `['ficha']` prefixo), contagem de testes e o terceiro e2e na tabela.
  - `ai/Claude.md`: em "Fora de escopo", tire "Financeiro"; na regra de `Consulta`, acrescente um parágrafo com F5 (valor congela ao virar REALIZADA), F8/F18 (travas da baixa) e as rotas de pagamento; na lista de "Erros / exceções", `SessaoComBaixaException` e `BaixaInvalidaException` → 409.
  - Não mexa em mais nada desses arquivos.

- [ ] **Step 3: Verificação final**

Run: `cd backend && sh ./mvnw -q test`; `cd frontend && ASDF_NODEJS_VERSION=20.19.1 npx vitest run && ASDF_NODEJS_VERSION=20.19.1 npm run build`; os três e2e. Reporte números.

- [ ] **Step 4: Não comite.** Reporte os arquivos.
