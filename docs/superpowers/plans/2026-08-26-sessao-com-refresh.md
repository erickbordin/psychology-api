# Sessão com refresh — plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use `superpowers:subagent-driven-development`
> (recomendado) ou `superpowers:executing-plans` para executar tarefa a tarefa. Os
> passos usam checkbox (`- [ ]`) para acompanhamento.

**Objetivo:** dar à API o que o frontend precisa antes de existir qualquer tela —
renovação de sessão sem deslogar o psicólogo, CORS, e o histórico de consultas de
um paciente.

**Arquitetura:** refresh token opaco de 32 bytes, guardado como SHA-256 na tabela
`refresh_token`, entregue em cookie `HttpOnly` e rotacionado a cada uso; reuso de
token revogado derruba a cadeia inteira daquele usuário. O access token JWT segue
com 15 min e passa a ser renovado silenciosamente.

**Stack:** Java 21, Spring Boot 3.5.4, Spring Security, Spring Data JPA, Flyway,
PostgreSQL, JUnit 5, Mockito, Testcontainers, MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-26-sessao-com-refresh-design.md`

## Restrições globais

- Toda resposta de erro sai no envelope `ErroRespostaDTO`; erro sem campo culpado
  usa o helper `semCampos(HttpStatus, String)` do `GlobalExceptionsHandler`.
- Todo método de service que grava mais de uma linha leva `@Transactional`
  (`org.springframework.transaction.annotation`).
- Toda auditoria vem **depois** do `save`, nunca antes.
- Método de repository que busca dado de paciente filtra por usuário e por
  `deleted_at`; a versão sem filtro não é criada.
- Registro de outro profissional responde `404`, nunca `403`.
- Migration já aplicada é imutável — corrigir significa criar a próxima versão.
- Colunas de data levam o sufixo da entidade: `created_at_refresh_token`.
- Nenhum `System.out`; nenhuma senha, token ou hash em log.
- Rodar a suíte: `sh ./mvnw test` (exige Docker).

---

### Task 1: CORS

**Arquivos:**
- Modificar: `src/main/java/com/psychologyapi/psychologyapi/authsecurity/SecurityConfiguration.java`
- Modificar: `src/main/resources/application.properties`
- Criar teste: `src/test/java/com/psychologyapi/psychologyapi/controller/CorsHttpTest.java`

**Interfaces:**
- Consome: nada.
- Produz: bean `CorsConfigurationSource corsConfigurationSource(List<String> origens)`;
  propriedade `app.cors.origins`.

- [x] **Passo 1: escrever o teste que falha**

`src/test/java/com/psychologyapi/psychologyapi/controller/CorsHttpTest.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * O frontend vive em outra origem. Sem CORS o navegador nem chega a enviar a
 * requisicao real, e o erro aparece no console do cliente, nao no log da API.
 */
@HttpTest
class CorsHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("preflight de origem permitida responde com os cabecalhos de CORS")
    void preflightDeOrigemPermitida() throws Exception {
        mockMvc.perform(options("/pacientes")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("preflight de origem desconhecida e recusado")
    void preflightDeOrigemDesconhecida() throws Exception {
        mockMvc.perform(options("/pacientes")
                .header(HttpHeaders.ORIGIN, "http://malicioso.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
```

- [x] **Passo 2: rodar e ver falhar**

Run: `sh ./mvnw test -Dtest=CorsHttpTest`
Esperado: FALHA — sem cabeçalho `Access-Control-Allow-Origin`.

- [x] **Passo 3: adicionar a propriedade**

Ao fim de `src/main/resources/application.properties`:

```properties
app.cors.origins=${APP_CORS_ORIGINS:http://localhost:5173}
```

- [x] **Passo 4: registrar o bean e ligar na cadeia**

Em `SecurityConfiguration.java`, adicionar os imports e o bean:

```java
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

    /**
     * Origens vem de variavel de ambiente e nunca sao "*": o proprio spec de CORS
     * proibe curinga junto de credenciais, e o cookie de refresh depende delas.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origins}") List<String> origens) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
```

Alterar a assinatura de `securityFilterChain` para receber o bean e ligá-lo:

```java
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtCustomtAuthenticationFilter jwtCustomtAuthenticationFilter,
            ErroRespostaAuthenticationEntryPoint erroRespostaAuthenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource) throws Exception{
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csfr -> csfr.disable())
```

(o restante da cadeia fica como está)

- [x] **Passo 5: rodar e ver passar**

Run: `sh ./mvnw test -Dtest=CorsHttpTest`
Esperado: PASSA, 2 testes.

- [x] **Passo 6: commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/authsecurity/SecurityConfiguration.java \
        src/main/resources/application.properties \
        src/test/java/com/psychologyapi/psychologyapi/controller/CorsHttpTest.java
git commit -m "feat(api): habilita CORS com credenciais para a origem do frontend"
```

---

### Task 2: tabela, entidade e repository do refresh token

**Arquivos:**
- Criar: `src/main/resources/db/migration/V8__20260826.sql`
- Criar: `src/main/java/com/psychologyapi/psychologyapi/model/RefreshToken.java`
- Criar: `src/main/java/com/psychologyapi/psychologyapi/repository/RefreshTokenRepository.java`
- Modificar: `src/test/java/com/psychologyapi/psychologyapi/support/Fixtures.java`
- Criar teste: `src/test/java/com/psychologyapi/psychologyapi/repository/RefreshTokenRepositoryTest.java`

**Interfaces:**
- Consome: nada.
- Produz: entidade `RefreshToken` com `getId()`, `getUsuario()`, `getHashToken()`,
  `getCreatedAt()`, `getExpiresAt()`, `getRevokedAt()`, `getSubstituidoPor()` e os
  setters correspondentes (Lombok `@Data`); repository com
  `findByHashToken(String)`, `findByUsuarioAndRevokedAtIsNull(Usuario)`,
  `deleteByUsuarioAndExpiresAtBefore(Usuario, LocalDateTime)`;
  `Fixtures.refreshToken(Usuario, String, LocalDateTime)`.

- [x] **Passo 1: escrever a migration**

`src/main/resources/db/migration/V8__20260826.sql`:

```sql
CREATE TABLE refresh_token (
    id_refresh_token          UUID        NOT NULL PRIMARY KEY,
    id_usuario                UUID        NOT NULL REFERENCES usuario (id_usuario),
    hash_token                VARCHAR(64) NOT NULL UNIQUE,
    created_at_refresh_token  TIMESTAMP   NOT NULL,
    expires_at_refresh_token  TIMESTAMP   NOT NULL,
    revoked_at_refresh_token  TIMESTAMP,
    substituido_por           UUID        REFERENCES refresh_token (id_refresh_token)
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (id_usuario);
```

- [x] **Passo 2: escrever o teste que falha**

Primeiro acrescentar a fixture ao fim de `Fixtures.java` (antes da chave final):

```java
    public static RefreshToken refreshToken(Usuario dono, String hashToken, LocalDateTime expiraEm) {
        RefreshToken token = new RefreshToken();
        token.setUsuario(dono);
        token.setHashToken(hashToken);
        token.setExpiresAt(expiraEm);
        return token;
    }
```

com o import `com.psychologyapi.psychologyapi.model.RefreshToken`.

`src/test/java/com/psychologyapi/psychologyapi/repository/RefreshTokenRepositoryTest.java`:

```java
package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * O lookup por hash e o caminho quente da renovacao, e a limpeza de expirados
 * apaga linha de verdade — as duas coisas so se provam contra um banco real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class RefreshTokenRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("encontra o token pelo hash")
    void encontraPeloHash() {
        Usuario dono = em.persistFlushFind(Fixtures.usuario("dono@teste.com"));
        em.persistFlushFind(Fixtures.refreshToken(dono, "a".repeat(64), LocalDateTime.now().plusDays(14)));

        Optional<RefreshToken> encontrado = refreshTokenRepository.findByHashToken("a".repeat(64));

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getUsuario().getId()).isEqualTo(dono.getId());
    }

    @Test
    @DisplayName("lista apenas os tokens vivos do usuario")
    void listaApenasOsVivos() {
        Usuario dono = em.persistFlushFind(Fixtures.usuario("dono@teste.com"));
        em.persistFlushFind(Fixtures.refreshToken(dono, "b".repeat(64), LocalDateTime.now().plusDays(14)));

        RefreshToken revogado = Fixtures.refreshToken(dono, "c".repeat(64), LocalDateTime.now().plusDays(14));
        revogado.setRevokedAt(LocalDateTime.now());
        em.persistFlushFind(revogado);

        assertThat(refreshTokenRepository.findByUsuarioAndRevokedAtIsNull(dono))
                .extracting(RefreshToken::getHashToken)
                .containsExactly("b".repeat(64));
    }

    @Test
    @DisplayName("apaga so os expirados do usuario informado")
    void apagaSoOsExpiradosDoUsuario() {
        Usuario dono = em.persistFlushFind(Fixtures.usuario("dono@teste.com"));
        Usuario outro = em.persistFlushFind(Fixtures.usuario("outro@teste.com"));

        em.persistFlushFind(Fixtures.refreshToken(dono, "d".repeat(64), LocalDateTime.now().minusDays(1)));
        em.persistFlushFind(Fixtures.refreshToken(dono, "e".repeat(64), LocalDateTime.now().plusDays(14)));
        em.persistFlushFind(Fixtures.refreshToken(outro, "f".repeat(64), LocalDateTime.now().minusDays(1)));

        refreshTokenRepository.deleteByUsuarioAndExpiresAtBefore(dono, LocalDateTime.now());
        em.flush();

        assertThat(refreshTokenRepository.findByHashToken("d".repeat(64))).isEmpty();
        assertThat(refreshTokenRepository.findByHashToken("e".repeat(64))).isPresent();
        assertThat(refreshTokenRepository.findByHashToken("f".repeat(64))).isPresent();
    }
}
```

- [x] **Passo 3: rodar e ver falhar**

Run: `sh ./mvnw test -Dtest=RefreshTokenRepositoryTest`
Esperado: FALHA na compilação — `RefreshToken` e `RefreshTokenRepository` não existem.

- [x] **Passo 4: criar a entidade**

`src/main/java/com/psychologyapi/psychologyapi/model/RefreshToken.java`:

```java
package com.psychologyapi.psychologyapi.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Credencial de renovacao de sessao. Guarda o SHA-256 do token, nunca o token:
 * um dump do banco vazado nao vira conjunto de sessoes ativas.
 *
 * Unica tabela do projeto sem deleted_at, de proposito: revokedAt ja cobre o
 * encerramento logico, e token vencido e lixo de infraestrutura, nao prontuario.
 */
@Entity
@Table(name = "refresh_token", schema = "public")
@Data
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_refresh_token", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    @Column(name = "created_at_refresh_token", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at_refresh_token", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at_refresh_token")
    private LocalDateTime revokedAt;

    /** Proximo elo da cadeia de rotacao. UUID solto, e nao @ManyToOne, para nao
     *  criar auto-relacionamento de entidade por um dado que so se le em auditoria. */
    @Column(name = "substituido_por")
    private UUID substituidoPor;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
```

- [x] **Passo 5: criar o repository**

`src/main/java/com/psychologyapi/psychologyapi/repository/RefreshTokenRepository.java`:

```java
package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByHashToken(String hashToken);

    List<RefreshToken> findByUsuarioAndRevokedAtIsNull(Usuario usuario);

    void deleteByUsuarioAndExpiresAtBefore(Usuario usuario, LocalDateTime momento);

}
```

- [x] **Passo 6: rodar e ver passar**

Run: `sh ./mvnw test -Dtest=RefreshTokenRepositoryTest,SchemaMigrationTest`
Esperado: PASSA. O `SchemaMigrationTest` entra junto porque a `V8` muda o schema.

- [x] **Passo 7: commit**

```bash
git add src/main/resources/db/migration/V8__20260826.sql \
        src/main/java/com/psychologyapi/psychologyapi/model/RefreshToken.java \
        src/main/java/com/psychologyapi/psychologyapi/repository/RefreshTokenRepository.java \
        src/test/java/com/psychologyapi/psychologyapi/support/Fixtures.java \
        src/test/java/com/psychologyapi/psychologyapi/repository/RefreshTokenRepositoryTest.java
git commit -m "feat(api): cria a tabela e o mapeamento do refresh token"
```

---

### Task 3: emissão do token — geração aleatória e hash

**Arquivos:**
- Criar: `src/main/java/com/psychologyapi/psychologyapi/service/RefreshEmitido.java`
- Criar: `src/main/java/com/psychologyapi/psychologyapi/service/RefreshTokenService.java`
- Modificar: `src/main/resources/application.properties`
- Criar teste: `src/test/java/com/psychologyapi/psychologyapi/service/RefreshTokenServiceTest.java`

**Interfaces:**
- Consome: `RefreshTokenRepository` (Task 2).
- Produz: `record RefreshEmitido(String tokenEmClaro, RefreshToken registro)`;
  `RefreshTokenService.emitir(Usuario) -> RefreshEmitido`;
  `RefreshTokenService.hashDe(String) -> String` (estático, visível no pacote).

- [x] **Passo 1: escrever o teste que falha**

`src/test/java/com/psychologyapi/psychologyapi/service/RefreshTokenServiceTest.java`:

```java
package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setEmailUsuario("ana@teste.com");
        return usuario;
    }

    private void comExpiracaoDe(int dias) {
        ReflectionTestUtils.setField(refreshTokenService, "expiracaoDias", dias);
    }

    @Test
    @DisplayName("emite guardando o hash, nunca o token em claro")
    void emiteGuardandoOHash() {
        comExpiracaoDe(14);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshEmitido emitido = refreshTokenService.emitir(usuario());

        assertThat(emitido.tokenEmClaro()).isNotBlank();
        assertThat(emitido.registro().getHashToken())
                .isEqualTo(RefreshTokenService.hashDe(emitido.tokenEmClaro()))
                .hasSize(64)
                .isNotEqualTo(emitido.tokenEmClaro());
    }

    @Test
    @DisplayName("dois tokens emitidos nunca coincidem")
    void doisTokensNaoCoincidem() {
        comExpiracaoDe(14);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        String primeiro = refreshTokenService.emitir(usuario()).tokenEmClaro();
        String segundo = refreshTokenService.emitir(usuario()).tokenEmClaro();

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("a expiracao respeita a janela configurada")
    void expiracaoRespeitaAJanela() {
        comExpiracaoDe(14);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshToken registro = refreshTokenService.emitir(usuario()).registro();

        assertThat(registro.getExpiresAt()).isAfter(java.time.LocalDateTime.now().plusDays(13));
        assertThat(registro.getExpiresAt()).isBefore(java.time.LocalDateTime.now().plusDays(15));
    }
}
```

- [x] **Passo 2: rodar e ver falhar**

Run: `sh ./mvnw test -Dtest=RefreshTokenServiceTest`
Esperado: FALHA na compilação — `RefreshTokenService` e `RefreshEmitido` não existem.

- [x] **Passo 3: criar o record**

`src/main/java/com/psychologyapi/psychologyapi/service/RefreshEmitido.java`:

```java
package com.psychologyapi.psychologyapi.service;

import com.psychologyapi.psychologyapi.model.RefreshToken;

/**
 * O token em claro so existe nesta viagem: dali em diante vive no cookie do
 * navegador e, do lado do servidor, apenas como hash na linha persistida.
 */
public record RefreshEmitido(String tokenEmClaro, RefreshToken registro) {
}
```

- [x] **Passo 4: criar o service com emissão e hash**

`src/main/java/com/psychologyapi/psychologyapi/service/RefreshTokenService.java`:

```java
package com.psychologyapi.psychologyapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int BYTES_DO_TOKEN = 32;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh.expiracao-dias}")
    private int expiracaoDias;

    public RefreshEmitido emitir(Usuario usuario) {
        String tokenEmClaro = gerarTokenEmClaro();

        RefreshToken registro = new RefreshToken();
        registro.setUsuario(usuario);
        registro.setHashToken(hashDe(tokenEmClaro));
        registro.setExpiresAt(LocalDateTime.now().plusDays(expiracaoDias));

        return new RefreshEmitido(tokenEmClaro, refreshTokenRepository.save(registro));
    }

    private String gerarTokenEmClaro() {
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, e nao BCrypt como na senha: a validacao precisa ENCONTRAR a linha a
     * partir do token apresentado, e hash com salt por linha obrigaria a varrer a
     * tabela inteira. O que justifica BCrypt na senha — entropia baixa, ataque de
     * dicionario — nao se aplica a um valor aleatorio de 256 bits.
     */
    static String hashDe(String tokenEmClaro) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tokenEmClaro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

}
```

- [x] **Passo 5: adicionar a propriedade**

Em `src/main/resources/application.properties`:

```properties
app.refresh.expiracao-dias=${APP_REFRESH_EXPIRACAO_DIAS:14}
```

- [x] **Passo 6: rodar e ver passar**

Run: `sh ./mvnw test -Dtest=RefreshTokenServiceTest`
Esperado: PASSA, 3 testes.

- [x] **Passo 7: commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/service/RefreshEmitido.java \
        src/main/java/com/psychologyapi/psychologyapi/service/RefreshTokenService.java \
        src/main/resources/application.properties \
        src/test/java/com/psychologyapi/psychologyapi/service/RefreshTokenServiceTest.java
git commit -m "feat(api): emite refresh token aleatorio guardando apenas o hash"
```

---

### Task 4: rotação, detecção de reuso e revogação

**Arquivos:**
- Criar: `src/main/java/com/psychologyapi/psychologyapi/exceptions/SessaoInvalidaException.java`
- Modificar: `src/main/java/com/psychologyapi/psychologyapi/service/RefreshTokenService.java`
- Modificar: `src/main/java/com/psychologyapi/psychologyapi/controller/common/GlobalExceptionsHandler.java`
- Modificar: `src/test/java/com/psychologyapi/psychologyapi/service/RefreshTokenServiceTest.java`

**Interfaces:**
- Consome: `RefreshEmitido`, `RefreshTokenService.emitir` e `hashDe` (Task 3).
- Produz: `rotacionar(String) -> RefreshEmitido`, `revogar(String) -> void`,
  `limparExpiradosDe(Usuario) -> void`; `SessaoInvalidaException` mapeada para `401`.

- [x] **Passo 1: escrever os testes que falham**

Acrescentar a `RefreshTokenServiceTest` (e os imports
`static org.assertj.core.api.Assertions.assertThatThrownBy`,
`static org.mockito.Mockito.verify`, `java.time.LocalDateTime`, `java.util.List`,
`java.util.Optional`, `com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException`):

```java
    private RefreshToken registroVivo(Usuario dono, String tokenEmClaro) {
        RefreshToken registro = new RefreshToken();
        registro.setUsuario(dono);
        registro.setHashToken(RefreshTokenService.hashDe(tokenEmClaro));
        registro.setExpiresAt(LocalDateTime.now().plusDays(14));
        return registro;
    }

    @Test
    @DisplayName("rotacionar revoga o token apresentado e encadeia o proximo")
    void rotacionarRevogaEEncadeia() {
        comExpiracaoDe(14);
        Usuario dono = usuario();
        RefreshToken atual = registroVivo(dono, "token-atual");
        when(refreshTokenRepository.findByHashToken(RefreshTokenService.hashDe("token-atual")))
                .thenReturn(Optional.of(atual));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshEmitido proximo = refreshTokenService.rotacionar("token-atual");

        assertThat(atual.getRevokedAt()).isNotNull();
        assertThat(proximo.tokenEmClaro()).isNotEqualTo("token-atual");
        verify(refreshTokenRepository).save(atual);
    }

    @Test
    @DisplayName("token desconhecido nao renova")
    void tokenDesconhecidoNaoRenova() {
        when(refreshTokenRepository.findByHashToken(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotacionar("nao-existe"))
                .isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    @DisplayName("token expirado nao renova")
    void tokenExpiradoNaoRenova() {
        Usuario dono = usuario();
        RefreshToken vencido = registroVivo(dono, "token-vencido");
        vencido.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByHashToken(RefreshTokenService.hashDe("token-vencido")))
                .thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> refreshTokenService.rotacionar("token-vencido"))
                .isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    @DisplayName("token ja revogado apresentado de novo derruba a cadeia inteira do usuario")
    void reusoDerrubaACadeia() {
        Usuario dono = usuario();
        RefreshToken revogado = registroVivo(dono, "token-roubado");
        revogado.setRevokedAt(LocalDateTime.now().minusMinutes(5));
        RefreshToken vivo = registroVivo(dono, "token-legitimo");

        when(refreshTokenRepository.findByHashToken(RefreshTokenService.hashDe("token-roubado")))
                .thenReturn(Optional.of(revogado));
        when(refreshTokenRepository.findByUsuarioAndRevokedAtIsNull(dono)).thenReturn(List.of(vivo));

        assertThatThrownBy(() -> refreshTokenService.rotacionar("token-roubado"))
                .isInstanceOf(SessaoInvalidaException.class);

        assertThat(vivo.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).saveAll(List.of(vivo));
    }

    @Test
    @DisplayName("revogar sem cookie nao estoura")
    void revogarSemCookieNaoEstoura() {
        refreshTokenService.revogar(null);
        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any());
    }
```

- [x] **Passo 2: rodar e ver falhar**

Run: `sh ./mvnw test -Dtest=RefreshTokenServiceTest`
Esperado: FALHA na compilação — `rotacionar`, `revogar` e `SessaoInvalidaException` não existem.

- [x] **Passo 3: criar a exceção**

`src/main/java/com/psychologyapi/psychologyapi/exceptions/SessaoInvalidaException.java`:

```java
package com.psychologyapi.psychologyapi.exceptions;

/**
 * Refresh ausente, desconhecido, expirado, revogado ou reapresentado.
 *
 * A mensagem e a mesma para todos os casos de proposito: distinguir "nunca
 * existiu" de "ja foi usado" so ajudaria quem esta sondando a API.
 */
public class SessaoInvalidaException extends RuntimeException {

    public SessaoInvalidaException(String mensagem) {
        super(mensagem);
    }

}
```

- [x] **Passo 4: implementar rotação, revogação e limpeza**

Acrescentar a `RefreshTokenService` (com os imports `java.util.List` e
`com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException`, mais
`org.springframework.transaction.annotation.Transactional`):

```java
    private static final String MENSAGEM_SESSAO_INVALIDA = "Sessão inválida. Entre novamente.";

    /**
     * Rotaciona a cada uso. Um token ja revogado apresentado de novo significa que
     * duas partes tem a mesma credencial — a legitima e uma copia. Revogar a cadeia
     * inteira derruba as duas, e so o dono consegue voltar, porque so ele tem a senha.
     */
    @Transactional
    public RefreshEmitido rotacionar(String tokenEmClaro) {
        RefreshToken atual = refreshTokenRepository.findByHashToken(hashDe(tokenEmClaro))
                .orElseThrow(() -> new SessaoInvalidaException(MENSAGEM_SESSAO_INVALIDA));

        if (atual.getRevokedAt() != null) {
            revogarCadeiaDe(atual.getUsuario());
            throw new SessaoInvalidaException(MENSAGEM_SESSAO_INVALIDA);
        }

        if (atual.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new SessaoInvalidaException(MENSAGEM_SESSAO_INVALIDA);
        }

        RefreshEmitido proximo = emitir(atual.getUsuario());
        atual.setRevokedAt(LocalDateTime.now());
        atual.setSubstituidoPor(proximo.registro().getId());
        refreshTokenRepository.save(atual);

        return proximo;
    }

    @Transactional
    public void revogar(String tokenEmClaro) {
        if (tokenEmClaro == null || tokenEmClaro.isBlank()) {
            return;
        }
        refreshTokenRepository.findByHashToken(hashDe(tokenEmClaro))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void limparExpiradosDe(Usuario usuario) {
        refreshTokenRepository.deleteByUsuarioAndExpiresAtBefore(usuario, LocalDateTime.now());
    }

    private void revogarCadeiaDe(Usuario usuario) {
        LocalDateTime agora = LocalDateTime.now();
        List<RefreshToken> vivos = refreshTokenRepository.findByUsuarioAndRevokedAtIsNull(usuario);
        vivos.forEach(token -> token.setRevokedAt(agora));
        refreshTokenRepository.saveAll(vivos);
    }
```

- [x] **Passo 5: mapear a exceção para 401**

Em `GlobalExceptionsHandler.java`, com o import
`com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException`, acrescentar
logo depois do handler de `BadCredentialsException`:

```java
    @ExceptionHandler(SessaoInvalidaException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErroRespostaDTO handleSessaoInvalida(SessaoInvalidaException e) {
        return semCampos(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
```

- [x] **Passo 6: rodar e ver passar**

Run: `sh ./mvnw test -Dtest=RefreshTokenServiceTest`
Esperado: PASSA, 8 testes.

- [x] **Passo 7: commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/exceptions/SessaoInvalidaException.java \
        src/main/java/com/psychologyapi/psychologyapi/service/RefreshTokenService.java \
        src/main/java/com/psychologyapi/psychologyapi/controller/common/GlobalExceptionsHandler.java \
        src/test/java/com/psychologyapi/psychologyapi/service/RefreshTokenServiceTest.java
git commit -m "feat(api): rotaciona o refresh token e derruba a cadeia ao detectar reuso"
```

---

### Task 5: o cookie

**Arquivos:**
- Criar: `src/main/java/com/psychologyapi/psychologyapi/authsecurity/CookieRefreshToken.java`
- Modificar: `src/main/resources/application.properties`
- Criar teste: `src/test/java/com/psychologyapi/psychologyapi/authsecurity/CookieRefreshTokenTest.java`

**Interfaces:**
- Consome: nada.
- Produz: `CookieRefreshToken.NOME` (constante `"refresh_token"`),
  `emitir(String) -> ResponseCookie`, `expirar() -> ResponseCookie`.

- [x] **Passo 1: escrever o teste que falha**

`src/test/java/com/psychologyapi/psychologyapi/authsecurity/CookieRefreshTokenTest.java`:

```java
package com.psychologyapi.psychologyapi.authsecurity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class CookieRefreshTokenTest {

    private final CookieRefreshToken cookie = new CookieRefreshToken(true, "None", 14);

    @Test
    @DisplayName("o cookie emitido e httpOnly, seguro e restrito a /auth")
    void cookieEmitidoTemOsAtributosDeProtecao() {
        ResponseCookie emitido = cookie.emitir("token-em-claro");

        assertThat(emitido.getName()).isEqualTo(CookieRefreshToken.NOME);
        assertThat(emitido.getValue()).isEqualTo("token-em-claro");
        assertThat(emitido.isHttpOnly()).isTrue();
        assertThat(emitido.isSecure()).isTrue();
        assertThat(emitido.getSameSite()).isEqualTo("None");
        assertThat(emitido.getPath()).isEqualTo("/auth");
        assertThat(emitido.getMaxAge()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("o cookie de expiracao zera valor e prazo")
    void cookieDeExpiracaoZera() {
        ResponseCookie expirado = cookie.expirar();

        assertThat(expirado.getValue()).isEmpty();
        assertThat(expirado.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(expirado.getPath()).isEqualTo("/auth");
    }
}
```

- [x] **Passo 2: rodar e ver falhar**

Run: `sh ./mvnw test -Dtest=CookieRefreshTokenTest`
Esperado: FALHA na compilação — `CookieRefreshToken` não existe.

- [x] **Passo 3: criar o componente**

`src/main/java/com/psychologyapi/psychologyapi/authsecurity/CookieRefreshToken.java`:

```java
package com.psychologyapi.psychologyapi.authsecurity;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * O refresh viaja em cookie HttpOnly porque e a unica barreira que JavaScript nao
 * contorna — e o dado atras dele e anotacao de sessao de paciente.
 *
 * Path=/auth restringe o envio aos tres endpoints que precisam do cookie: nenhuma
 * chamada a /pacientes ou /consultas carrega a credencial de renovacao junto.
 */
@Component
public class CookieRefreshToken {

    public static final String NOME = "refresh_token";
    private static final String CAMINHO = "/auth";

    private final boolean seguro;
    private final String sameSite;
    private final int expiracaoDias;

    public CookieRefreshToken(
            @Value("${app.refresh.cookie-seguro}") boolean seguro,
            @Value("${app.refresh.cookie-samesite}") String sameSite,
            @Value("${app.refresh.expiracao-dias}") int expiracaoDias) {
        this.seguro = seguro;
        this.sameSite = sameSite;
        this.expiracaoDias = expiracaoDias;
    }

    public ResponseCookie emitir(String tokenEmClaro) {
        return base(tokenEmClaro).maxAge(Duration.ofDays(expiracaoDias)).build();
    }

    public ResponseCookie expirar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(seguro)
                .sameSite(sameSite)
                .path(CAMINHO);
    }

}
```

- [x] **Passo 4: adicionar as propriedades**

Em `src/main/resources/application.properties`:

```properties
app.refresh.cookie-seguro=${APP_REFRESH_COOKIE_SEGURO:true}
app.refresh.cookie-samesite=${APP_REFRESH_COOKIE_SAMESITE:None}
```

- [x] **Passo 5: rodar e ver passar**

Run: `sh ./mvnw test -Dtest=CookieRefreshTokenTest`
Esperado: PASSA, 2 testes.

- [x] **Passo 6: commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/authsecurity/CookieRefreshToken.java \
        src/main/resources/application.properties \
        src/test/java/com/psychologyapi/psychologyapi/authsecurity/CookieRefreshTokenTest.java
git commit -m "feat(api): monta o cookie httpOnly do refresh token"
```

---

### Task 6: login com cookie, `POST /auth/refresh` e `POST /auth/logout`

**Arquivos:**
- Modificar: `src/main/java/com/psychologyapi/psychologyapi/controller/AuthController.java`
- Criar teste: `src/test/java/com/psychologyapi/psychologyapi/controller/RefreshHttpTest.java`

**Interfaces:**
- Consome: `RefreshTokenService.emitir/rotacionar/revogar/limparExpiradosDe` (Tasks 3-4),
  `CookieRefreshToken.emitir/expirar` e `CookieRefreshToken.NOME` (Task 5),
  `TokenService.gerarToken(String)`, `CustomAuthentication.getUsuario()`.
- Produz: `POST /auth/refresh` e `POST /auth/logout`; login passa a responder com
  `Set-Cookie`.

- [x] **Passo 1: escrever o teste que falha**

`src/test/java/com/psychologyapi/psychologyapi/controller/RefreshHttpTest.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * A renovacao so existe sobre HTTP: cookie, rotacao e o 401 da cadeia de
 * seguranca nao aparecem em nenhum teste de service.
 */
@HttpTest
class RefreshHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private void psicologo(String email, String senha) {
        Usuario usuario = Fixtures.usuario(email);
        usuario.setSenhaUsuario(passwordEncoder.encode(senha));
        usuarioRepository.save(usuario);
    }

    private MvcResult login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @DisplayName("login devolve o refresh em cookie httpOnly")
    void loginDevolveCookieHttpOnly() throws Exception {
        psicologo("ana@teste.com", "senhaforte123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"ana@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieRefreshToken.NOME))
                .andExpect(cookie().httpOnly(CookieRefreshToken.NOME, true))
                .andExpect(cookie().path(CookieRefreshToken.NOME, "/auth"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("refresh devolve access novo e rotaciona o cookie")
    void refreshRotaciona() throws Exception {
        psicologo("ana@teste.com", "senhaforte123");
        Cookie primeiro = login("ana@teste.com", "senhaforte123").getResponse().getCookie(CookieRefreshToken.NOME);

        MvcResult renovado = mockMvc.perform(post("/auth/refresh").cookie(primeiro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists(CookieRefreshToken.NOME))
                .andReturn();

        Cookie segundo = renovado.getResponse().getCookie(CookieRefreshToken.NOME);
        assertThat(segundo.getValue()).isNotEqualTo(primeiro.getValue());
    }

    @Test
    @DisplayName("refresh sem cookie responde 401 no envelope")
    void refreshSemCookieResponde401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.erros.length()").value(0));
    }

    @Test
    @DisplayName("o mesmo refresh usado duas vezes responde 401 na segunda")
    void reusoResponde401() throws Exception {
        psicologo("ana@teste.com", "senhaforte123");
        Cookie primeiro = login("ana@teste.com", "senhaforte123").getResponse().getCookie(CookieRefreshToken.NOME);

        mockMvc.perform(post("/auth/refresh").cookie(primeiro)).andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh").cookie(primeiro))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout responde 204 e o cookie deixa de servir")
    void logoutRevoga() throws Exception {
        psicologo("ana@teste.com", "senhaforte123");
        Cookie cookieDoLogin = login("ana@teste.com", "senhaforte123").getResponse().getCookie(CookieRefreshToken.NOME);

        mockMvc.perform(post("/auth/logout").cookie(cookieDoLogin))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").cookie(cookieDoLogin))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout sem cookie tambem responde 204")
    void logoutSemCookieResponde204() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
    }
}
```

- [x] **Passo 2: rodar e ver falhar**

Run: `sh ./mvnw test -Dtest=RefreshHttpTest`
Esperado: FALHA — não há cookie no login e `/auth/refresh` responde 404.

- [x] **Passo 3: reescrever o AuthController**

Substituir o conteúdo de `AuthController.java` por:

```java
package com.psychologyapi.psychologyapi.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.authsecurity.CustomAuthentication;
import com.psychologyapi.psychologyapi.controller.dto.TokenResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioLoginDTO;
import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.service.RefreshEmitido;
import com.psychologyapi.psychologyapi.service.RefreshTokenService;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.service.UsuarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final CookieRefreshToken cookieRefreshToken;

    @PostMapping("/registrar")
    public ResponseEntity<UsuarioResponseDTO> registrarUsuario(@RequestBody @Valid UsuarioCadastroDTO dto){
        UsuarioResponseDTO usuario = usuarioService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> logarUsuario(@RequestBody UsuarioLoginDTO dto){
        var authenticationToken = new UsernamePasswordAuthenticationToken(dto.emailUsuario(), dto.senha());
        var authenticate = (CustomAuthentication) authenticationManager.authenticate(authenticationToken);
        Usuario usuario = authenticate.getUsuario();

        refreshTokenService.limparExpiradosDe(usuario);
        RefreshEmitido emitido = refreshTokenService.emitir(usuario);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieRefreshToken.emitir(emitido.tokenEmClaro()).toString())
                .body(new TokenResponseDTO(tokenService.gerarToken(usuario.getEmailUsuario())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDTO> renovar(
            @CookieValue(name = CookieRefreshToken.NOME, required = false) String refreshToken){

        if (refreshToken == null) {
            throw new SessaoInvalidaException("Sessão inválida. Entre novamente.");
        }

        RefreshEmitido emitido = refreshTokenService.rotacionar(refreshToken);
        String acesso = tokenService.gerarToken(emitido.registro().getUsuario().getEmailUsuario());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieRefreshToken.emitir(emitido.tokenEmClaro()).toString())
                .body(new TokenResponseDTO(acesso));
    }

    /**
     * Depende do cookie, nao do access token: expirar o acesso nao pode impedir
     * alguem de encerrar a propria sessao. Idempotente — sem cookie, 204 igual.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> sair(
            @CookieValue(name = CookieRefreshToken.NOME, required = false) String refreshToken){

        refreshTokenService.revogar(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefreshToken.expirar().toString())
                .build();
    }

}
```

- [x] **Passo 4: rodar e ver passar**

Run: `sh ./mvnw test -Dtest=RefreshHttpTest`
Esperado: PASSA, 6 testes.

- [x] **Passo 5: rodar a suíte inteira**

Run: `sh ./mvnw test`
Esperado: BUILD SUCCESS. `AuthControllerTest` continua verde — o corpo do login não mudou.

- [x] **Passo 6: commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/controller/AuthController.java \
        src/test/java/com/psychologyapi/psychologyapi/controller/RefreshHttpTest.java
git commit -m "feat(api): renova e encerra sessao por cookie httpOnly"
```

---

### Task 7: `GET /pacientes/{pacienteId}/consultas`

**Arquivos:**
- Modificar: `src/main/java/com/psychologyapi/psychologyapi/repository/ConsultaRepository.java`
- Modificar: `src/main/java/com/psychologyapi/psychologyapi/service/ConsultaService.java`
- Criar: `src/main/java/com/psychologyapi/psychologyapi/controller/ConsultaDoPacienteController.java`
- Modificar: `src/test/java/com/psychologyapi/psychologyapi/repository/ConsultaRepositoryTest.java`
- Criar teste: `src/test/java/com/psychologyapi/psychologyapi/controller/ConsultaDoPacienteHttpTest.java`

**Interfaces:**
- Consome: `PacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull`,
  `ConsultaMapper.toResponseDTO`, `AuditoriaService.registrar`.
- Produz: `ConsultaRepository.findByPacienteAndUsuarioAndDeletedAtIsNullOrderByDtConsultaDesc(Paciente, Usuario, Pageable)`;
  `ConsultaService.listarPorPaciente(UUID, Pageable) -> Page<ConsultaResponseDTO>`.

- [x] **Passo 1: escrever o teste de repository que falha**

Acrescentar a `ConsultaRepositoryTest` (imports `org.springframework.data.domain.PageRequest`
e `com.psychologyapi.psychologyapi.model.Status` se ainda não houver):

```java
    @Test
    @DisplayName("historico do paciente ignora consulta de outro paciente e soft-deletada")
    void historicoDoPacienteFiltraDonoEDeletadas() {
        Usuario dono = em.persistFlushFind(Fixtures.usuario("dono@teste.com"));
        Paciente alvo = em.persistFlushFind(Fixtures.paciente(dono, "Alvo"));
        Paciente outro = em.persistFlushFind(Fixtures.paciente(dono, "Outro"));

        em.persistFlushFind(Fixtures.consulta(dono, alvo,
                LocalDateTime.of(2026, 8, 20, 9, 0), Status.REALIZADA));
        em.persistFlushFind(Fixtures.consulta(dono, outro,
                LocalDateTime.of(2026, 8, 21, 9, 0), Status.AGENDADA));

        Consulta apagada = Fixtures.consulta(dono, alvo,
                LocalDateTime.of(2026, 8, 22, 9, 0), Status.AGENDADA);
        apagada.setDeletedAt(LocalDateTime.now());
        em.persistFlushFind(apagada);

        assertThat(consultaRepository
                .findByPacienteAndUsuarioAndDeletedAtIsNullOrderByDtConsultaDesc(alvo, dono, PageRequest.of(0, 20))
                .getContent())
                .extracting(Consulta::getDtConsulta)
                .containsExactly(LocalDateTime.of(2026, 8, 20, 9, 0));
    }
```

- [x] **Passo 2: rodar e ver falhar**

Run: `sh ./mvnw test -Dtest=ConsultaRepositoryTest`
Esperado: FALHA na compilação — o método derivado não existe.

- [x] **Passo 3: adicionar o método derivado**

Em `ConsultaRepository.java`, com os imports `org.springframework.data.domain.Page`,
`org.springframework.data.domain.Pageable` e
`com.psychologyapi.psychologyapi.model.Paciente`:

```java
    Page<Consulta> findByPacienteAndUsuarioAndDeletedAtIsNullOrderByDtConsultaDesc(
            Paciente paciente, Usuario usuario, Pageable pageable);
```

- [x] **Passo 4: rodar e ver passar**

Run: `sh ./mvnw test -Dtest=ConsultaRepositoryTest`
Esperado: PASSA.

- [x] **Passo 5: escrever o teste HTTP que falha**

`src/test/java/com/psychologyapi/psychologyapi/controller/ConsultaDoPacienteHttpTest.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
class ConsultaDoPacienteHttpTest {

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

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }

    @Test
    @DisplayName("lista o historico do paciente paginado, mais recente primeiro")
    void listaHistoricoPaginado() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        consultaRepository.save(Fixtures.consulta(dono, paciente,
                LocalDateTime.of(2026, 8, 12, 9, 0), Status.REALIZADA));
        consultaRepository.save(Fixtures.consulta(dono, paciente,
                LocalDateTime.of(2026, 8, 19, 9, 0), Status.REALIZADA));

        mockMvc.perform(get("/pacientes/" + paciente.getId() + "/consultas")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].dtConsulta")
                        .value(org.hamcrest.Matchers.startsWith("2026-08-19T09:00")))
                .andExpect(jsonPath("$.content[0].pacienteNome").value("Ana"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("paciente de outro psicologo responde 404")
    void pacienteDeOutroResponde404() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));

        mockMvc.perform(get("/pacientes/" + paciente.getId() + "/consultas")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(intruso)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("paciente inexistente responde 404")
    void pacienteInexistenteResponde404() throws Exception {
        Usuario dono = psicologo("dono@teste.com");

        mockMvc.perform(get("/pacientes/" + UUID.randomUUID() + "/consultas")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isNotFound());
    }
}
```

- [x] **Passo 6: rodar e ver falhar**

Run: `sh ./mvnw test -Dtest=ConsultaDoPacienteHttpTest`
Esperado: FALHA — a rota responde 404 porque não existe controller.

- [x] **Passo 7: implementar service e controller**

Em `ConsultaService.java`, com os imports `org.springframework.data.domain.Page` e
`org.springframework.data.domain.Pageable`:

```java
    /**
     * Historico de atendimento do paciente. Paginado porque cresce sem limite com
     * o tempo, e auditado como VISUALIZACAO do paciente — e acesso ao historico de
     * alguem identificado, nao uma listagem generica.
     */
    public Page<ConsultaResponseDTO> listarPorPaciente(UUID pacienteId, Pageable pageable) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        Page<Consulta> consultas = consultaRepository
                .findByPacienteAndUsuarioAndDeletedAtIsNullOrderByDtConsultaDesc(paciente, usuarioLogado, pageable);

        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.VISUALIZACAO, "Paciente", paciente.getId());

        return consultas.map(consultaMapper::toResponseDTO);
    }
```

`src/main/java/com/psychologyapi/psychologyapi/controller/ConsultaDoPacienteController.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.service.ConsultaService;

import lombok.RequiredArgsConstructor;

/**
 * Sub-recurso sob paciente, como anotacao e lembrete: a URL e a fonte da verdade
 * do pacienteId. Fica fora do ConsultaController porque a raiz e /pacientes.
 */
@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class ConsultaDoPacienteController {

    private final ConsultaService consultaService;

    @GetMapping("/{pacienteId}/consultas")
    public ResponseEntity<PagedModel<ConsultaResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ConsultaResponseDTO> consultas = consultaService.listarPorPaciente(pacienteId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(new PagedModel<>(consultas));
    }

}
```

- [x] **Passo 8: rodar e ver passar**

Run: `sh ./mvnw test -Dtest=ConsultaDoPacienteHttpTest`
Esperado: PASSA, 3 testes.

- [x] **Passo 9: commit**

```bash
git add src/main/java/com/psychologyapi/psychologyapi/repository/ConsultaRepository.java \
        src/main/java/com/psychologyapi/psychologyapi/service/ConsultaService.java \
        src/main/java/com/psychologyapi/psychologyapi/controller/ConsultaDoPacienteController.java \
        src/test/java/com/psychologyapi/psychologyapi/repository/ConsultaRepositoryTest.java \
        src/test/java/com/psychologyapi/psychologyapi/controller/ConsultaDoPacienteHttpTest.java
git commit -m "feat(api): lista o historico de consultas de um paciente"
```

---

### Task 8: teste de composição da sessão

**Arquivos:**
- Criar teste: `src/test/java/com/psychologyapi/psychologyapi/controller/FluxoDeSessaoHttpTest.java`

**Interfaces:**
- Consome: tudo das Tasks 1-7.
- Produz: nada de produção.

- [x] **Passo 1: escrever o teste**

Nenhum teste por endpoint prova que login, chamada autenticada e renovação
**encaixam** — foi exatamente assim que o bug do `AnotacaoMapper` passou
despercebido.

`src/test/java/com/psychologyapi/psychologyapi/controller/FluxoDeSessaoHttpTest.java`:

```java
package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class FluxoDeSessaoHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String tokenDe(MvcResult resultado) throws Exception {
        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return corpo.get("token").asText();
    }

    @Test
    @DisplayName("registrar, logar, usar o token, renovar e usar o token novo")
    void fluxoCompletoDeSessao() throws Exception {
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana Sessao\",\"email\":\"sessao@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"sessao@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String primeiroAcesso = tokenDe(login);
        Cookie refresh = login.getResponse().getCookie(CookieRefreshToken.NOME);

        mockMvc.perform(get("/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + primeiroAcesso))
                .andExpect(status().isOk());

        MvcResult renovacao = mockMvc.perform(post("/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andReturn();

        String segundoAcesso = tokenDe(renovacao);

        mockMvc.perform(get("/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + segundoAcesso))
                .andExpect(status().isOk());
    }
}
```

- [x] **Passo 2: rodar**

Run: `sh ./mvnw test -Dtest=FluxoDeSessaoHttpTest`
Esperado: PASSA. Se falhar, o problema está na fiação entre as tarefas anteriores,
que é justamente o que este teste existe para encontrar — corrija a causa, não o teste.

- [x] **Passo 3: rodar a suíte inteira**

Run: `sh ./mvnw test`
Esperado: BUILD SUCCESS.

- [x] **Passo 4: commit**

```bash
git add src/test/java/com/psychologyapi/psychologyapi/controller/FluxoDeSessaoHttpTest.java
git commit -m "test(api): cobre a composicao de login, uso e renovacao de sessao"
```

---

### Task 9: documentação

**Arquivos:**
- Modificar: `README.md`
- Modificar: `ai/Claude.md`

**Interfaces:**
- Consome: tudo. Produz: nada de código.

- [x] **Passo 1: atualizar a tabela de rotas do README**

Na seção `## API`, acrescentar as três linhas à tabela, logo após a linha de
`/auth/login`:

```markdown
| `POST` | `/auth/refresh` | Renova o access token a partir do cookie de refresh |
| `POST` | `/auth/logout` | Revoga o refresh corrente e limpa o cookie |
```

e, junto das rotas de paciente:

```markdown
| `GET` | `/pacientes/{pacienteId}/consultas` | Histórico de consultas do paciente (paginado) |
```

- [x] **Passo 2: documentar a sessão no README**

Na seção `### JWT stateless com provider próprio`, acrescentar ao final:

```markdown
A validade de 15 min só é viável porque existe renovação: `POST /auth/refresh`
troca um refresh token opaco, guardado como SHA-256 em `refresh_token` e entregue
em cookie `HttpOnly`, por um access token novo. A rotação é a cada uso — um
refresh reapresentado depois de revogado revoga a cadeia inteira daquele usuário,
porque duas partes com a mesma credencial significa uma cópia roubada.

Detalhes e alternativas rejeitadas em
`docs/superpowers/specs/2026-08-26-sessao-com-refresh-design.md`.
```

- [x] **Passo 3: registrar as variáveis de ambiente no README**

Na seção `## Deploy`, acrescentar ao parágrafo das variáveis de ambiente:

```markdown
O frontend acrescenta quatro variáveis: `APP_CORS_ORIGINS` (a origem do
frontend, separada por vírgula se houver mais de uma — nunca `*`, que o próprio
spec de CORS proíbe combinar com credenciais), `APP_REFRESH_EXPIRACAO_DIAS`
(padrão 14), `APP_REFRESH_COOKIE_SEGURO` e `APP_REFRESH_COOKIE_SAMESITE`
(`true` e `None` em produção, onde API e frontend têm origens distintas).
```

- [x] **Passo 4: registrar a exceção de soft delete no `ai/Claude.md`**

Na seção que descreve o soft delete, acrescentar:

```markdown
`refresh_token` é a única tabela sem `deleted_at`, de propósito:
`revoked_at_refresh_token` já cobre o encerramento lógico, e token vencido é lixo
de infraestrutura, não prontuário. A limpeza física acontece no login, sem job.
```

- [x] **Passo 5: commit**

```bash
git add README.md ai/Claude.md
git commit -m "docs: documenta a sessao com refresh, o CORS e o historico do paciente"
```

---

## Cobertura da spec

| Requisito da spec | Task |
|---|---|
| Refresh opaco persistido | 2, 3 |
| Guarda o hash (SHA-256, não BCrypt) | 3 |
| Rotação a cada uso com detecção de reuso | 4 |
| Cookie httpOnly, access no corpo | 5, 6 |
| Access token segue com 15 min | — (inalterado, por decisão) |
| Migration `V8` com sufixo de coluna e sem cascade | 2 |
| Sem `deleted_at`, com justificativa documentada | 2, 9 |
| `POST /auth/refresh` | 6 |
| `POST /auth/logout` idempotente | 6 |
| Limpeza de expirados no login | 4 (método), 6 (chamada) |
| `GET /pacientes/{id}/consultas` paginado e auditado | 7 |
| Erros no envelope único | 4 (handler), 6 e 7 (asserções) |
| CORS por variável, com credenciais, nunca `*` | 1 |
| Transação onde grava mais de uma linha | 4 |
| Isolamento por usuário, 404 e não 403 | 7 |
| Teste de composição da sessão | 8 |
