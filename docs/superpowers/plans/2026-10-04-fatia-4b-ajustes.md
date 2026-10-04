# Fatia 4B — Trilha, expediente e senha — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajustes fica completo: o psicólogo lê a trilha de acesso em frases, define o expediente que molda a grade da Agenda e troca a senha derrubando as outras sessões.

**Architecture:** `GET /auditoria` ganha `pacienteId`, `pacienteNome` e `pacienteExcluido`, resolvidos em lote por página num componente novo (`PacientesDaTrilha`), sempre filtrando pelo usuário logado. `POST /ajustes/senha` confere a senha atual, grava a nova, **apaga** todos os refresh tokens do usuário e emite um novo no cookie. No front, Ajustes ganha as seções Expediente, Trocar senha (diálogo) e Trilha de acesso; a Agenda passa a desenhar as horas a partir do expediente, por uma função pura.

**Tech Stack:** Java 21, Spring Boot 3.5, JPA/Hibernate 6, MapStruct, JUnit 5/Mockito/Testcontainers · React 19, TS, TanStack Query 5, RHF + zod 4, Radix Dialog, Vitest/MSW, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-03-fatia-4-aviso-design.md` — §6.1 (senha), §6.2 (seções 1, 3 e 6), §7.1, §7.2. Decisões A9, A13, A14 e A15 são do dono.

## Global Constraints

- **NÃO faça commit, `git add`, `git stash`, `git checkout`, `git reset`** nem nenhum git que escreva. O dono comita. Reporte a lista exata de arquivos.
- **NENHUM comentário novo** em código, teste, SQL ou CSS (`//`, `/* */`, Javadoc, `{/* */}`, `--`, `#`). Não edite nem remova comentário existente, **exceto** o que a tarefa nomeia.
- **Não rode formatador** (prettier, google-java-format etc.). Estilo da casa à mão: front sem ponto e vírgula, aspas simples.
- **Só os arquivos listados na tarefa.** Precisou de outro → pare e reporte `NEEDS_CONTEXT`.
- **Cópia nova com acento**, exatamente como a spec ou este plano escrevem. Cópia existente sem acento fica intocada.
- **Mensagens de erro da API** exatamente como a spec/este plano.
- **Isolamento:** toda leitura e escrita nova parte de `securityService.obterUsuarioLogado()`; nada de outro psicólogo aparece, nem resolvido por id.
- **Toda mutação de serviço** termina em `save`/update explícito, em `@Transactional`, com auditoria depois.
- **A leitura da trilha continua sem auditoria.**
- **Backend:** de `backend/`, `sh ./mvnw -q test -Dtest='Classe'`; suíte inteira `sh ./mvnw -q test` (baseline **487** verdes). Docker no ar.
- **Frontend:** todo comando de node com `ASDF_NODEJS_VERSION=20.19.1` (Node global é 9). De `frontend/`: `ASDF_NODEJS_VERSION=20.19.1 npx vitest run <caminho>`, `ASDF_NODEJS_VERSION=20.19.1 npx tsc --noEmit -p .` (baseline **213** verdes, tsc limpo).
- **Testes de front por rótulo escrito**, nunca `className`; MSW com o envelope real `{status, mensagem, erros[]}`; UUIDs de verdade.
- **Se um comando for negado pelo sistema de permissões, pare e reporte `BLOCKED` com o comando** — não procure contorno.
- **Não despache subagentes.**

## Decisões técnicas deste plano (não são de negócio; registradas para o revisor)

- **T1 — Trocar senha APAGA os refresh tokens em vez de marcá-los revogados.** `RefreshTokenService.rotacionar` trata token revogado reapresentado como roubo e revoga a cadeia inteira. Se a troca de senha só revogasse, a primeira aba antiga que tentasse renovar derrubaria também o token novo da aba que trocou a senha. Apagados, os tokens antigos caem em "não encontrado" → 401, sem efeito colateral. O apagamento é um `DELETE` JPQL em massa, porque a tabela tem FK de si para si (`substituido_por`) e o Postgres confere essa FK no fim do comando.
- **T2 — O access token (JWT) das outras sessões continua válido até expirar.** A14 fala de refresh tokens; o JWT não tem lista de revogação. As outras abas caem na próxima renovação.
- **T3 — Cópia nova que a spec não escreve** (o revisor confere só a coerência): `Informe a senha atual.` (senha atual em branco), `Carregando a trilha…`, `Não foi possível carregar a trilha. Tente de novo.`
- **T4 — A Agenda espera os ajustes carregarem**; se `GET /ajustes` falhar, desenha a grade padrão (07h–19h) em vez de travar a semana.

## Review Focus

1. **Troca de senha com outra aba aberta**: a outra aba tenta renovar com o token antigo → 401 só para ela; o cookie novo da aba que trocou continua renovando (Tarefa 2, teste `abaAntigaNaoDerrubaANova`).
2. **Trilha que aponta para paciente de outro psicólogo**: uma linha de log com `entidadeId` de paciente alheio não resolve nome (Tarefa 1, teste `naoResolvePacienteAlheio`).
3. **Trilha de coisa excluída**: sessão, anotação ou pendência excluídas ainda resolvem o paciente; paciente excluído vem com `pacienteExcluido: true`, nome presente e sem link no front (Tarefas 1 e 6).
4. **Sessão fora do expediente**: sessão às 06h ou às 20h30 na semana exibida estica a grade só até incluí-la; sessão CANCELADA fora do expediente não estica (Tarefa 4).
5. **Expediente até 24h**: `expedienteFim = 24` mostra a linha 23:00 e para; o select de fim mostra `24:00` (Tarefa 4).

## Mapa de arquivos

`M/` = `backend/src/main/java/com/psychologyapi/psychologyapi/`; `T/` = `backend/src/test/java/com/psychologyapi/psychologyapi/`; `F/` = `frontend/src/`.

| Tarefa | Arquivos |
|---|---|
| 1 Trilha (API) | `M/controller/dto/auditoria/LogAuditoriaResponseDTO.java`; `M/mapper/LogAuditoriaMapper.java`; `M/service/AuditoriaService.java`; `M/service/PacientesDaTrilha.java` (novo); `M/repository/PacienteRepository.java`, `ConsultaRepository.java`, `AnotacaoRepository.java`, `PendenciaRepository.java`; `T/service/AuditoriaServiceTest.java`; `T/controller/TrilhaLegivelHttpTest.java` (novo) |
| 2 Senha (API) | `M/controller/dto/ajustes/TrocaDeSenhaDTO.java` (novo); `M/controller/AjustesController.java`; `M/service/AjustesService.java`; `M/service/RefreshTokenService.java`; `M/repository/RefreshTokenRepository.java`; `T/controller/TrocaDeSenhaHttpTest.java` (novo) |
| 3 Contrato | orquestrador: `frontend/contrato-openapi.json`, `F/api/contrato.ts` |
| 4 Expediente (front) | `F/telas/ajustes/esquema.ts`, `queries.ts`, `AjustesPage.tsx`, `AjustesPage.test.tsx`; `F/telas/agenda/grade.ts` (novo), `grade.test.ts` (novo), `AgendaPage.tsx`, `AgendaPage.test.tsx` |
| 5 Senha (front) | `F/telas/ajustes/DialogoDeSenha.tsx` (novo), `DialogoDeSenha.test.tsx` (novo), `senha.ts` (novo), `AjustesPage.tsx`, `AjustesPage.test.tsx` |
| 6 Trilha (front) | `F/telas/ajustes/frases.ts` (novo), `frases.test.ts` (novo), `TrilhaDeAcesso.tsx` (novo), `TrilhaDeAcesso.test.tsx` (novo), `trilha.ts` (novo), `AjustesPage.tsx`; `F/estilo/estilo.css` (fim do arquivo) |
| 7 e2e + docs | `frontend/e2e/fluxo-dos-ajustes.spec.ts` (novo); `README.md`, `backend/README.md`, `frontend/README.md`, `ai/Claude.md` |

Ordem: 1 → 2 → 3 → (4, 5, 6 em sequência, todos mexem em `AjustesPage.tsx`) → 7.

---

### Task 1: Trilha legível na API

**Files:** ver mapa.

**Interfaces:**
- Consumes: `LogAuditoria` (`getId()`, `getEntidade()`, `getEntidadeId()`), `Paciente` (`getId()`, `getNomePaciente()`, `getDeletedAt()`), `securityService.obterUsuarioLogado()`.
- Produces: `LogAuditoriaResponseDTO(UUID id, AcaoAuditoria acao, String entidade, UUID entidadeId, LocalDateTime createdAt, UUID pacienteId, String pacienteNome, Boolean pacienteExcluido)` — os três campos novos **no fim**, nulos para entidades sem paciente. `GET /auditoria` mantém parâmetros, paginação e ordem.

Regra de resolução (spec §7.1): `Paciente`, `FichaDoPaciente`, `ConsentimentoDoPaciente`, `DescadastroDoPaciente` → `entidadeId` é o id do paciente; `Consulta`, `Anotacao`, `Pendencia` → paciente da linha; demais (`Ajustes`, `Senha`, qualquer outra) → nulos. Linhas **excluídas** (soft delete) resolvem igual. Nada de outro usuário resolve.

- [ ] **Step 1: Repositórios** (acrescente, imports de `Collection` e `EntityGraph` onde faltarem):

```java
// PacienteRepository
List<Paciente> findByIdInAndUsuario(Collection<UUID> ids, Usuario usuario);

// ConsultaRepository
@EntityGraph(attributePaths = "paciente")
List<Consulta> findByIdInAndUsuario(Collection<UUID> ids, Usuario usuario);

// AnotacaoRepository
@EntityGraph(attributePaths = "paciente")
List<Anotacao> findByIdInAndUsuario(Collection<UUID> ids, Usuario usuario);

// PendenciaRepository (Pendencia não tem usuario; o dono vem pelo paciente)
@EntityGraph(attributePaths = "paciente")
List<Pendencia> findByIdInAndPaciente_Usuario(Collection<UUID> ids, Usuario usuario);
```

Confira o nome do campo de usuário em `Consulta` e `Anotacao` (é `usuario` nas duas hoje) antes de usar.

- [ ] **Step 2: Testes que falham** — `T/controller/TrilhaLegivelHttpTest.java` (`@HttpTest`, MockMvc, token de `TokenService` como em `ConsentimentoHttpTest`). Fixtures pela API (`POST /pacientes`, `GET /pacientes/{id}/ficha` ou o endpoint que audita `FichaDoPaciente`, `POST /consultas`, `POST /anotacoes`, `POST /pendencias` — leia os controllers para os corpos) ou por repositório quando a API não servir. Um `@Test` + `@DisplayName` em português para cada:
  1. `pacienteResolvidoPorTipo`: depois de cadastrar paciente "Helena Vaz", abrir a ficha, agendar uma sessão, registrar anotação e criar pendência dela, `GET /auditoria?size=50` → toda linha de `Paciente`, `FichaDoPaciente`, `Consulta`, `Anotacao`, `Pendencia` tem `pacienteId` = id dela, `pacienteNome` = `Helena Vaz`, `pacienteExcluido` = `false`.
  2. `ajustesSemPaciente`: depois de `PUT /ajustes`, a linha `Ajustes` tem os três campos nulos (`jsonPath(...).doesNotExist()` ou `isEmpty()` — confira como o Jackson do projeto serializa nulo; o teste aceita ausente ou `null`).
  3. `pacienteExcluidoSegueComNome`: `DELETE /pacientes/{id}` → as linhas dela vêm com `pacienteExcluido: true` e `pacienteNome` presente.
  4. `sessaoExcluidaResolve`: sessão excluída (`DELETE /consultas/{id}`) → a linha `Consulta` de `EXCLUSAO` resolve o paciente.
  5. `naoResolvePacienteAlheio`: psicóloga B tem paciente P; grave por `LogAuditoriaRepository` uma linha **da psicóloga A** com `entidade = "Paciente"` e `entidadeId = P.id` → no `GET /auditoria` de A, a linha vem com os três campos nulos.
  6. `filtroPorEntidadeContinua`: `?entidade=Consulta` só traz `Consulta`, já resolvidas.
  7. `leituraNaoAudita`: contar linhas de A, `GET /auditoria` duas vezes, a contagem não muda.

- [ ] **Step 3: Rode** `sh ./mvnw -q test -Dtest='TrilhaLegivelHttpTest'` → falha (campos ausentes).

- [ ] **Step 4: Implemente.**

`M/service/PacientesDaTrilha.java`:

```java
package com.psychologyapi.psychologyapi.service;

@Component
@RequiredArgsConstructor
public class PacientesDaTrilha {

    private static final Set<String> DO_PROPRIO_PACIENTE =
            Set.of("Paciente", "FichaDoPaciente", "ConsentimentoDoPaciente", "DescadastroDoPaciente");

    private final PacienteRepository pacienteRepository;
    private final ConsultaRepository consultaRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final PendenciaRepository pendenciaRepository;

    public Map<UUID, Paciente> porLinha(Usuario usuario, List<LogAuditoria> linhas) {
        Map<String, Set<UUID>> idsPorEntidade = new HashMap<>();
        linhas.stream()
                .filter(linha -> linha.getEntidadeId() != null)
                .forEach(linha -> idsPorEntidade
                        .computeIfAbsent(linha.getEntidade(), e -> new HashSet<>())
                        .add(linha.getEntidadeId()));

        Map<String, Map<UUID, Paciente>> resolvidos = new HashMap<>();

        Set<UUID> diretos = new HashSet<>();
        DO_PROPRIO_PACIENTE.forEach(e -> diretos.addAll(idsPorEntidade.getOrDefault(e, Set.of())));
        Map<UUID, Paciente> pacientes = diretos.isEmpty() ? Map.of()
                : pacienteRepository.findByIdInAndUsuario(diretos, usuario).stream()
                        .collect(Collectors.toMap(Paciente::getId, p -> p));
        DO_PROPRIO_PACIENTE.forEach(e -> resolvidos.put(e, pacientes));

        resolvidos.put("Consulta", pacientesDe(idsPorEntidade.get("Consulta"),
                ids -> consultaRepository.findByIdInAndUsuario(ids, usuario), Consulta::getId, Consulta::getPaciente));
        resolvidos.put("Anotacao", pacientesDe(idsPorEntidade.get("Anotacao"),
                ids -> anotacaoRepository.findByIdInAndUsuario(ids, usuario), Anotacao::getId, Anotacao::getPaciente));
        resolvidos.put("Pendencia", pacientesDe(idsPorEntidade.get("Pendencia"),
                ids -> pendenciaRepository.findByIdInAndPaciente_Usuario(ids, usuario), Pendencia::getId,
                Pendencia::getPaciente));

        Map<UUID, Paciente> porLinha = new HashMap<>();
        linhas.forEach(linha -> {
            if (linha.getEntidadeId() == null) {
                return;
            }
            Paciente paciente = resolvidos.getOrDefault(linha.getEntidade(), Map.of()).get(linha.getEntidadeId());
            if (paciente != null) {
                porLinha.put(linha.getId(), paciente);
            }
        });
        return porLinha;
    }

    private <E> Map<UUID, Paciente> pacientesDe(Set<UUID> ids, Function<Set<UUID>, List<E>> buscar,
            Function<E, UUID> id, Function<E, Paciente> paciente) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return buscar.apply(ids).stream().collect(Collectors.toMap(id, paciente));
    }
}
```

Confira em `LogAuditoria` se `entidadeId` pode ser nulo; o `return` acima cobre os dois casos.

`LogAuditoriaMapper`: troque o método por um de duas fontes (o de uma fonte só sai; o único chamador é o `AuditoriaService`):

```java
@Mapping(target = "id", source = "log.id")
@Mapping(target = "acao", source = "log.acao")
@Mapping(target = "entidade", source = "log.entidade")
@Mapping(target = "entidadeId", source = "log.entidadeId")
@Mapping(target = "createdAt", source = "log.createdAt")
@Mapping(target = "pacienteId", source = "paciente.id")
@Mapping(target = "pacienteNome", source = "paciente.nomePaciente")
@Mapping(target = "pacienteExcluido", expression = "java(paciente == null ? null : paciente.getDeletedAt() != null)")
LogAuditoriaResponseDTO toResponseDTO(LogAuditoria log, Paciente paciente);
```

Confira no `LogAuditoriaMapperImpl` gerado que `paciente` nulo dá os três nulos sem NPE.

`AuditoriaService.listar`: injete `PacientesDaTrilha`; troque só o `return`:

```java
Map<UUID, Paciente> pacientes = pacientesDaTrilha.porLinha(usuarioLogado, logs.getContent());
return logs.map(log -> logAuditoriaMapper.toResponseDTO(log, pacientes.get(log.getId())));
```

Não toque no Javadoc existente do método.

`T/service/AuditoriaServiceTest.java`: acrescente `@Mock PacientesDaTrilha pacientesDaTrilha` (com `when(...porLinha(any(), any())).thenReturn(Map.of())`) e troque os `when(logAuditoriaMapper.toResponseDTO(log))` por `toResponseDTO(log, null)`. Nada mais.

- [ ] **Step 5:** `sh ./mvnw -q test -Dtest='TrilhaLegivelHttpTest,AuditoriaServiceTest,AuditoriaControllerTest,FichaAuditoriaHttpTest'` verde; suíte inteira verde. **Não comite.**

---

### Task 2: Trocar senha na API

**Files:** ver mapa.

**Interfaces:**
- Consumes: `CampoInvalidoException(String campo, String mensagem)` (4A), `CookieRefreshToken.emitir(String)`, `RefreshTokenService.emitir(Usuario)`, `PasswordEncoder` (bean BCrypt de `SecurityConfiguration`), `@SenhaForte`.
- Produces: `POST /ajustes/senha` `{senhaAtual, novaSenha}` → `204` + `Set-Cookie: refresh_token=...; Path=/auth; HttpOnly`; `RefreshTokenService.apagarTodosDe(Usuario)`; trilha `ATUALIZACAO "Senha"` com o id do usuário.

Regras (spec §6.1 + T1):
- Validação de bean primeiro: `senhaAtual` `@NotBlank(message = "Informe a senha atual.")`; `novaSenha` com **as mesmas três anotações e mensagens** de `UsuarioCadastroDTO.senha` (`@NotBlank(message = "Informe uma senha.")`, `@Size(min = 10, message = "A senha precisa de pelo menos 10 caracteres.")`, `@SenhaForte`) — copie do arquivo, não daqui, se divergirem.
- No serviço, nesta ordem e **antes de qualquer mutação**: senha atual não confere → `CampoInvalidoException("senhaAtual", "A senha atual não confere.")`; nova igual à atual (`passwordEncoder.matches(novaSenha, hashAtual)`) → `CampoInvalidoException("novaSenha", "A nova senha precisa ser diferente da atual.")`.
- Sucesso: `usuario.setSenhaUsuario(passwordEncoder.encode(novaSenha))`, `usuarioRepository.save`, `refreshTokenService.apagarTodosDe(usuario)`, `refreshTokenService.emitir(usuario)`, auditoria `ATUALIZACAO "Senha"` com `usuario.getId()`, tudo no mesmo `@Transactional`.

- [ ] **Step 1: Testes que falham** — `T/controller/TrocaDeSenhaHttpTest.java` (`@HttpTest`). Use o molde de `RefreshHttpTest`/`AuthControllerTest` para registrar (`POST /auth/registrar`), entrar (`POST /auth/login`) e ler o cookie `refresh_token` da resposta. Senha inicial forte, ex. `Cavalo-Azul-Pulando-42`; nova, ex. `Montanha-Verde-Cantando-77`. Um teste por caso:
  1. `trocaDevolve204ECookieNovo`: `204`; `Set-Cookie` com `refresh_token=`, `HttpOnly`, `Path=/auth`; valor diferente do cookie do login.
  2. `cookieAntigoMorre`: o cookie do login em `POST /auth/refresh` → `401`.
  3. `abaAntigaNaoDerrubaANova`: depois do 2, o cookie novo em `POST /auth/refresh` → `200`. **Este é o teste da decisão T1.**
  4. `outraSessaoMorre`: dois logins (duas "abas"); troca com o access token da primeira; o cookie da segunda → `401`.
  5. `entraComANova`: `POST /auth/login` com a nova → `200`; com a antiga → `401`.
  6. `senhaAtualErrada`: `400`, erro no campo `senhaAtual` com `A senha atual não confere.`; login com a antiga ainda funciona; o cookie do login ainda renova (`200`).
  7. `novaIgualAAtual`: `400` no campo `novaSenha` com `A nova senha precisa ser diferente da atual.`
  8. `novaCurta` (`curta123`) → `400` `novaSenha` com `A senha precisa de pelo menos 10 caracteres.`; `novaComum` (pegue uma senha com ≥ 10 caracteres de `R/senhas-comuns.txt`) → `400` `novaSenha` com a mensagem de `@SenhaForte`.
  9. `senhaAtualEmBranco` → `400` `senhaAtual` com `Informe a senha atual.`
  10. `semToken` → `401`.
  11. `trilhaRegistra`: `GET /auditoria?entidade=Senha` tem 1 linha `ATUALIZACAO`; depois de uma troca recusada, continua 1.
  12. `cadeiaRotacionadaNaoQuebraOApagar`: login, `POST /auth/refresh` duas vezes (cria tokens revogados com `substituido_por`), então troca a senha → `204` (o `DELETE` em massa não viola a FK de si para si).

- [ ] **Step 2: Rode** `sh ./mvnw -q test -Dtest='TrocaDeSenhaHttpTest'` → falha (404 na rota).

- [ ] **Step 3: Implemente.**

`RefreshTokenRepository`:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("DELETE FROM RefreshToken r WHERE r.usuario = :usuario")
int apagarTodosDe(@Param("usuario") Usuario usuario);
```

`RefreshTokenService`:

```java
@Transactional
public void apagarTodosDe(Usuario usuario) {
    refreshTokenRepository.apagarTodosDe(usuario);
}
```

`TrocaDeSenhaDTO` (record em `controller/dto/ajustes/`, campos `senhaAtual`, `novaSenha`, anotações acima; importe `SenhaForte` de `controller/dto/usuario`).

`AjustesService.trocarSenha(TrocaDeSenhaDTO dto)` → `String` (o token em claro) com as regras acima; injete `PasswordEncoder` e `RefreshTokenService`. Constante `ENTIDADE_SENHA = "Senha"`.

`AjustesController`: injete `CookieRefreshToken`;

```java
@PostMapping("/senha")
public ResponseEntity<Void> trocarSenha(@RequestBody @Valid TrocaDeSenhaDTO dto) {
    String token = ajustesService.trocarSenha(dto);
    return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cookieRefreshToken.emitir(token).toString())
            .build();
}
```

Nada muda em `SecurityConfiguration`: `/ajustes/**` já exige autenticação (confira).

- [ ] **Step 4:** `sh ./mvnw -q test -Dtest='TrocaDeSenhaHttpTest,RefreshHttpTest,AuthControllerTest,AjustesHttpTest'` verde; suíte inteira verde. **Não comite.**

---

### Task 3: Regerar o contrato (orquestrador)

Mesmo procedimento da 4A: API na **8090** com o JWT de teste do `HttpTest`, `curl http://localhost:8090/v3/api-docs` cru para `frontend/contrato-openapi.json`, troca de `http://localhost:8090` por `http://localhost:8080`, `ASDF_NODEJS_VERSION=20.19.1 npm run -s tipos`, conferir `TrocaDeSenhaDTO`, `/ajustes/senha` e os três campos novos de `LogAuditoriaResponseDTO`; `tsc` limpo; derrubar a API.

---

### Task 4: Expediente em Ajustes e na Agenda

**Files:** ver mapa.

**Interfaces:**
- Consumes: `useAjustes()`/`Ajustes` (`expedienteInicio`, `expedienteFim`: `number | null`), `useConsultasDaSemana`, `deApi`.
- Produces: `horasDaGrade(inicio: number | null, fim: number | null, horasOcupadas: number[]): number[]` em `F/telas/agenda/grade.ts`; `useSalvarAjustes()` **sem parâmetro** (o expediente vem do formulário).

Regras (spec §6.2 seção 3, §7.2): selects `Início` e `Fim`, hora cheia, rótulo `HH:00`; primeira opção `Sem expediente` (valor vazio). `Início`: `00:00`…`23:00`; `Fim`: `01:00`…`24:00`. Apoio: `A agenda mostra só estas horas. Sem expediente, mostra das 07h às 19h.` Seção `<section aria-label="Expediente" className="cartao ajustes-secao">` com `<h2>Expediente</h2>`, **entre** `Sessão` e `Aviso de consulta`. Validação do cliente com as mensagens do servidor: um só preenchido → no campo que falta `Informe o início e o fim do expediente, ou nenhum dos dois.`; fim ≤ início → em `expedienteFim` `O fim do expediente deve ser depois do início.` Erro do servidor em `expedienteInicio`/`expedienteFim` agora pousa no campo (o alerta genérico da 4A deixa de receber esses dois).

- [ ] **Step 1: `grade.test.ts`** (falha):

```ts
import { describe, expect, it } from 'vitest'

import { horasDaGrade } from './grade'

describe('horasDaGrade', () => {
  it('sem expediente mostra das 07h as 19h', () => {
    expect(horasDaGrade(null, null, [])).toEqual([7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19])
  })
  it('expediente de 8 a 18 vai ate a linha das 17h', () => {
    expect(horasDaGrade(8, 18, [])).toEqual([8, 9, 10, 11, 12, 13, 14, 15, 16, 17])
  })
  it('expediente ate 24 termina na linha das 23h', () => {
    expect(horasDaGrade(20, 24, [])).toEqual([20, 21, 22, 23])
  })
  it('sessao antes do inicio estica a grade para cima', () => {
    expect(horasDaGrade(8, 12, [6])).toEqual([6, 7, 8, 9, 10, 11])
  })
  it('sessao depois do fim estica a grade para baixo', () => {
    expect(horasDaGrade(8, 12, [20])).toEqual([8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20])
  })
  it('sessao dentro do expediente nao muda nada', () => {
    expect(horasDaGrade(8, 12, [9])).toEqual([8, 9, 10, 11])
  })
})
```

- [ ] **Step 2: `grade.ts`:**

```ts
export function horasDaGrade(inicio: number | null, fim: number | null, horasOcupadas: number[]): number[] {
  const de = inicio !== null && fim !== null ? inicio : 7
  const ate = inicio !== null && fim !== null ? fim : 20
  const primeira = Math.min(de, ...horasOcupadas)
  const depoisDaUltima = Math.max(ate, ...horasOcupadas.map((h) => h + 1))
  return Array.from({ length: depoisDaUltima - primeira }, (_, i) => primeira + i)
}
```

- [ ] **Step 3: `AgendaPage`:** `useAjustes()` junto da busca da semana; enquanto **qualquer um** carrega, `Carregando a semana…` (cópia existente); erro da semana → alerta existente; erro dos ajustes → grade padrão (T4). Troque `HORAS` por:

```ts
const horasOcupadas = consultas
  .filter((c) => c.status !== 'CANCELADA')
  .map((c) => deApi(c.dtConsulta).getHours())
const horas = horasDaGrade(ajustes?.expedienteInicio ?? null, ajustes?.expedienteFim ?? null, horasOcupadas)
```

Remova a constante `HORAS` e o comentário dela vai junto (é o único comentário que esta tarefa remove). Nenhuma outra mudança na grade (`role="grid"`, `th scope`, rótulos).

`AgendaPage.test.tsx`: acrescente handler MSW de `GET /ajustes` (ajustes padrão com expediente nulo) nos testes existentes que precisarem, e casos novos: expediente 8–18 → existe `rowheader` `08:00` e `17:00`, não existe `07:00` nem `18:00`; sessão AGENDADA às 06:00 com expediente 8–18 → existe `06:00`; sessão CANCELADA às 06:00 → não existe `06:00`; `GET /ajustes` com 500 → a grade aparece com `07:00` e `19:00`.

- [ ] **Step 4: Ajustes.** `esquema.ts`: `expedienteInicio` e `expedienteFim` como `z.string()` (`''` = sem) com o `superRefine` das duas regras acima nos paths certos. `valoresDoFormulario`: número → `String(n)`, nulo → `''`. `queries.ts`: `useSalvarAjustes()` sem parâmetro; corpo manda `expedienteInicio: valores.expedienteInicio === '' ? null : Number(valores.expedienteInicio)` (idem fim). `AjustesPage.tsx`: a seção nova; os selects com `<label>` ligado ao `id`. Testes em `AjustesPage.test.tsx`: abre com os valores do GET nos selects; mudar para 08:00–18:00 e salvar manda `expedienteInicio: 8, expedienteFim: 18`; `Sem expediente` nos dois manda `null, null`; só início → mensagem no `Fim`; fim ≤ início → mensagem no `Fim`; erro do servidor em `expedienteInicio` pousa no campo `Início`. Ajuste os testes da 4A que esperavam o expediente "ecoado do GET" para a nova forma (o valor agora vem do formulário).

- [ ] **Step 5:** `npx vitest run src/telas/agenda src/telas/ajustes`, suíte inteira e `tsc` verdes. **Não comite.**

---

### Task 5: Trocar senha no front

**Files:** ver mapa.

**Interfaces:**
- Consumes: `POST /ajustes/senha` (204), `api()` de `F/api/cliente.ts` (já manda `credentials: 'include'`, então o `Set-Cookie` vale), `ErroDaApi` com `erros[]` por campo, `useFormularioDoProduto`.
- Produces: `DialogoDeSenha({ aberto, aoFechar, aoTrocar })`; `useTrocarSenha()` em `senha.ts`.

Cópia (spec §6.2 seção 1): no `Perfil`, botão `Trocar senha` (`type="button"`, depois do `E-mail`). Diálogo Radix com título `Trocar senha`, campos `Senha atual` e `Nova senha` (`type="password"`, `autoComplete` `current-password` / `new-password`), botões `Salvar` e `Cancelar`. Sucesso: fecha o diálogo e a página anuncia `Senha trocada. As outras sessões foram encerradas.` no mesmo `<p className="sub" role="status">` que já anuncia `Ajustes salvos.` (um texto por vez: o último evento ganha). Validação do cliente: `Senha atual` vazia → `Informe a senha atual.`; `Nova senha` vazia → `Informe uma senha.`; menos de 10 → `A senha precisa de pelo menos 10 caracteres.` Erros do servidor por campo pousam no campo do diálogo (`senhaAtual`, `novaSenha`); sem campo → alerta dentro do diálogo com a `mensagem` do envelope. Botão `Salvar` trava em voo. `Cancelar`/Esc fecham sem chamar a API e limpam os campos.

- [ ] **Step 1: Testes que falham** (`DialogoDeSenha.test.tsx` e casos em `AjustesPage.test.tsx`): abre pelo botão; envia `{senhaAtual, novaSenha}`; 204 → diálogo fecha e `Senha trocada. As outras sessões foram encerradas.` aparece; 400 `senhaAtual` → `A senha atual não confere.` sob `Senha atual`; 400 `novaSenha` → sob `Nova senha`; validações do cliente sem chamar a API; `Cancelar` não chama a API; duplo clique em `Salvar` manda um POST.
- [ ] **Step 2: Implemente** `senha.ts` (`useTrocarSenha` com `useMutation`, `api<void>('/ajustes/senha', { method: 'POST', body })`), `DialogoDeSenha.tsx` (molde de `DialogoDePaciente.tsx`: `Dialog.Root`/`Portal`/`Overlay className="mascara"`/`Content className="dialogo"`), e a ligação em `AjustesPage.tsx`. Os campos de senha nunca vão para o cache do TanStack Query nem para log.
- [ ] **Step 3:** suíte inteira e `tsc` verdes. **Não comite.**

---

### Task 6: Trilha de acesso no front

**Files:** ver mapa.

**Interfaces:**
- Consumes: `GET /auditoria?page&size=20&entidade` → `PagedModel` `{ content: LinhaDaTrilha[], page: { number, totalPages } }` (confira a forma no contrato); `usePrivacidade()`; `Link` do react-router.
- Produces: `fraseDaTrilha(acao: string, entidade: string): string` em `frases.ts`; `useTrilha(pagina, entidade)` em `trilha.ts`; `TrilhaDeAcesso` (componente).

Cópia exata: tabela de frases da spec §7.1 (copie as 9 linhas **da spec**); fora da tabela → `` `${acao.toLowerCase()} · ${entidade}` ``. Seção `<section aria-label="Trilha de acesso" className="cartao ajustes-secao">` com `<h2>Trilha de acesso</h2>`, **depois do formulário** (fora do `<form>`; não é campo). Filtro `<select>` rotulado `Tipo`: `Tudo` (sem `entidade`) · `Fichas` (`FichaDoPaciente`) · `Pacientes` (`Paciente`) · `Sessões` (`Consulta`) · `Anotações` (`Anotacao`) · `Ajustes` (`Ajustes`); trocar o filtro volta à página 0. Lista `<ul aria-label="Trilha de acesso">`; cada `<li>`: `{dd/mm HH:mm} · {frase}` e, havendo `pacienteNome`, ` · ` + nome — `<Link to={`/pacientes/${pacienteId}`}>` quando `pacienteExcluido` é falso, texto simples quando verdadeiro; o nome vai num `<span data-borrado>` sob o modo privacidade (molde de `HojePage.tsx` `Nome`). Data via `deApi` + `toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })` sem vírgula (confira e normalize para `dd/mm HH:mm`). Paginação: botões `Mais recentes` (página anterior, desabilitado na 0) e `Mais antigos` (próxima, desabilitado na última). Vazio: `Nenhum registro ainda.`; carregando: `Carregando a trilha…`; erro: `Não foi possível carregar a trilha. Tente de novo.` em `role="alert"`.

- [ ] **Step 1: `frases.test.ts`** (falha): as 9 linhas da tabela, uma asserção por célula preenchida (ex. `fraseDaTrilha('VISUALIZACAO', 'FichaDoPaciente')` → `Abriu a ficha`; `('ATUALIZACAO', 'Senha')` → `Trocou a senha`) e o fallback (`('CRIACAO', 'Ajustes')` → `criacao · Ajustes`).
- [ ] **Step 2: `frases.ts`** — um `Record<string, Partial<Record<string, string>>>` por entidade, e a função.
- [ ] **Step 3: `TrilhaDeAcesso.test.tsx`** (falha): renderiza linhas com frase e nome; nome é link para a ficha quando não excluído; excluído sem link; linha `Ajustes` sem nome; filtro `Sessões` pede `entidade=Consulta` e volta à página 0; `Mais antigos` pede `page=1`; `Mais recentes` desabilitado na primeira página e `Mais antigos` na última; vazio; erro; com privacidade ligada o nome tem `data-borrado` e a frase não.
- [ ] **Step 4: Implemente** `trilha.ts` (tipo `LinhaDaTrilha` à mão do contrato, nuláveis `| null`; `useTrilha` com chave `['trilha', pagina, entidade ?? 'tudo']` e `placeholderData: keepPreviousData` para não piscar), `TrilhaDeAcesso.tsx`, a ligação em `AjustesPage.tsx`, e CSS da lista no fim de `F/estilo/estilo.css` (só tokens). A mutação de `PUT /ajustes` e a de senha invalidam `['trilha']` no sucesso.
- [ ] **Step 5:** suíte inteira e `tsc` verdes. **Não comite.**

---

### Task 7: e2e dos ajustes e documentação

- [ ] **Step 1:** `frontend/e2e/fluxo-dos-ajustes.spec.ts` (molde dos outros, e-mail único por execução, sem comentários, sem espera fixa): registra e entra; Ajustes → `Início` `08:00`, `Fim` `18:00`, `Salvar ajustes`, vê `Ajustes salvos.`; Agenda → existe o cabeçalho de linha `08:00` e `17:00`, não existe `07:00` nem `18:00`; Ajustes → a `Trilha de acesso` mostra `Alterou os ajustes`; `Trocar senha` → `Senha atual`, `Nova senha`, `Salvar` → `Senha trocada. As outras sessões foram encerradas.`; sai (ache o controle de sair em `F/ui/Layout.tsx`) e entra com a senha nova → chega em Hoje. Rode com a API do orquestrador: `API_URL=http://localhost:8090 ASDF_NODEJS_VERSION=20.19.1 npx playwright test` (os **cinco** fluxos).
- [ ] **Step 2: Docs** — só o que a 4B torna falso ou novo: `POST /ajustes/senha` e os campos novos de `/auditoria` nas tabelas de endpoints; seções novas de Ajustes e a grade da Agenda pelo expediente nas tabelas de telas; contagens de teste reais; e2e com 5 fluxos. Não toque na linha 1 de `ai/Claude.md`.
- [ ] **Step 3:** e2e, `npx vitest run`, `npm run build`, e **por último** `sh ./mvnw -q test` (recompila `target/` com a API no ar) — tudo verde. **Não comite.**
