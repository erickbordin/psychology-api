# Sessão com refresh — design

**Data:** 2026-08-26
**Status:** aprovado, aguardando plano de implementação
**Escopo:** `psychology-api` (backend). Existe para destravar o `psychology-web`,
que será construído em repositório separado — ver a spec do frontend lá.

## Objetivo

O access token vale 15 min e não há como renová-lo. Um psicólogo escrevendo
anotação de sessão por vinte minutos é deslogado no meio, e o texto se perde.

E um frontend servido de outra origem não consegue sequer chamar a API: não há
configuração de CORS no projeto.

Esta spec cobre as três coisas que a API precisa antes que exista qualquer tela:
renovação de sessão, CORS e o histórico de consultas de um paciente.

## As cinco decisões que moldam o resto

### 1. Refresh opaco e persistido, não JWT

O refresh token é um valor aleatório de 32 bytes, sem significado próprio, com
uma linha correspondente em `refresh_token`.

Rejeitado — **refresh como um segundo JWT, stateless**: dispensa tabela e
migration, mas não pode ser revogado. Vazou, vale até expirar, e "sair" vira
apagar o cookie do lado do cliente enquanto o token segue válido. Num projeto que
mantém trilha append-only de todo acesso a dado de paciente, uma sessão que não
se consegue encerrar contradiz o resto.

Consequência aceita: todo refresh custa uma consulta ao banco. É o preço da
revogação, e é uma consulta por 15 min de uso, não por requisição.

### 2. A tabela guarda o hash, nunca o token

Grava-se `sha256(token)`. O valor em claro existe só no cookie do navegador.

Rejeitado — **guardar o token em claro**: um dump do banco vazado viraria um
conjunto de sessões ativas. Mesmo raciocínio que já se aplica a `senha_usuario`.

**SHA-256 e não BCrypt**, ao contrário da senha: a validação precisa *encontrar* a
linha a partir do token apresentado, e um hash com salt por linha obrigaria a
varrer a tabela inteira comparando uma a uma. SHA-256 é determinístico e
indexável. O que justifica BCrypt na senha — entropia baixa, ataque de
dicionário — não se aplica a um valor aleatório de 256 bits, onde não há
dicionário a percorrer.

### 3. Rotação a cada uso, com detecção de reuso

Cada `POST /auth/refresh` revoga o token apresentado e emite outro, encadeando
os dois por `substituido_por`.

Um token **já revogado** apresentado de novo significa que duas partes têm a
mesma credencial — a cópia legítima e uma cópia roubada. A resposta é revogar
**toda a cadeia daquele usuário** e responder `401`: as duas partes perdem a
sessão, e o dono recupera a dele com uma senha que o atacante não tem.

Rejeitado — **refresh de uso ilimitado até expirar**: mais simples, e um token
capturado passa a valer pelo prazo inteiro sem deixar rastro. A rotação é o que
transforma roubo silencioso em sessão derrubada.

### 4. Refresh em cookie httpOnly, access token no corpo

O refresh vai em `Set-Cookie: refresh_token=…; HttpOnly; Secure; SameSite=None;
Path=/auth`. O access token continua no corpo da resposta, como hoje, e o cliente
o mantém **em memória**.

Rejeitado — **os dois no corpo, guardados pelo cliente**: mais simples de
implementar e de testar, e dispensa CORS com credenciais. Mas qualquer XSS no
frontend lê os dois, e o dado atrás deles é anotação de sessão de paciente.
`HttpOnly` é a única barreira que JavaScript não contorna.

`Path=/auth` restringe o envio do cookie aos três endpoints que precisam dele —
nenhuma chamada a `/pacientes` ou `/consultas` carrega a credencial de renovação.

### 5. O access token continua com 15 min

Rejeitado — **subir a validade para horas** e não construir refresh nenhum:
resolveria o sintoma com uma linha. Mas um token stateless de vida longa não pode
ser revogado, que é exatamente o problema da decisão 1, só que pior.

Com renovação silenciosa, a janela curta volta a ser uma proteção em vez de um
defeito de usabilidade.

## Modelo de dados

Migration `V8__20260826.sql`:

```sql
CREATE TABLE refresh_token (
    id_refresh_token          UUID         NOT NULL PRIMARY KEY,
    id_usuario                UUID         NOT NULL REFERENCES usuario (id_usuario),
    hash_token                VARCHAR(64)  NOT NULL UNIQUE,
    created_at_refresh_token  TIMESTAMP    NOT NULL,
    expires_at_refresh_token  TIMESTAMP    NOT NULL,
    revoked_at_refresh_token  TIMESTAMP,
    substituido_por           UUID         REFERENCES refresh_token (id_refresh_token)
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (id_usuario);
```

- `hash_token` é o hexadecimal do SHA-256 — 64 caracteres, tamanho fixo. O
  `UNIQUE` dá o índice do lookup e, de quebra, impede colisão de emissão.
- Colunas de data levam o sufixo da entidade (`created_at_refresh_token`), como
  em toda tabela do projeto.
- Sem `ON DELETE CASCADE`, como todas as FKs do projeto.
- **Sem `deleted_at`.** Esta é a primeira tabela do projeto com exclusão física, e
  é deliberado: `revoked_at_refresh_token` já cobre o encerramento lógico, e token expirado não
  é histórico clínico — é lixo de infraestrutura. A regra de soft delete existe
  para proteger prontuário, não credencial vencida.

## Contrato

### `POST /auth/login` — mudança

Corpo e status inalterados (`200`, `TokenResponseDTO`). Passa a acompanhar
`Set-Cookie` com o refresh recém-emitido.

No mesmo pedido, apaga fisicamente os refresh tokens **expirados daquele
usuário**. É a limpeza mais barata possível: acontece no momento em que já se
está escrevendo na tabela, sem job, sem scheduler — o projeto não tem nenhum, e
introduzir um para isso seria desproporcional.

### `POST /auth/refresh` → `200`

Lê o cookie. Sem cookie, com token desconhecido, expirado ou revogado: `401` no
envelope padrão. Válido: revoga o atual, emite o próximo, devolve
`TokenResponseDTO` com access novo e `Set-Cookie` com o refresh novo.

Reuso de token revogado: revoga a cadeia inteira do usuário e responde `401`.

### `POST /auth/logout` → `204`

Revoga o refresh do cookie e devolve um `Set-Cookie` vencido. Sem cookie,
responde `204` do mesmo jeito — logout é idempotente, e distinguir "não havia
sessão" de "havia" só entregaria informação a quem está sondando.

Depende do cookie, não do access token: expirar o access não pode impedir alguém
de encerrar a própria sessão.

### `GET /pacientes/{pacienteId}/consultas` → `200`

O histórico de atendimento de um paciente, paginado (`PagedModel`), mais recente
primeiro. Sub-recurso sob paciente, como anotações e lembretes — a URL é a fonte
da verdade do `pacienteId`.

Existe porque `GET /consultas` filtra por intervalo de datas e não por paciente:
a ficha do paciente não tem como montar o histórico sem varrer um intervalo
arbitrário e filtrar no cliente, o que quebra com o uso e mente na contagem.

Paginado pelo mesmo critério das outras listagens: cresce sem limite com o tempo.

Registra `VISUALIZACAO` de `Paciente` na trilha, seguindo o que
`AnotacaoService.listarPorPaciente` já faz — é acesso ao histórico de um
paciente identificado, não uma listagem genérica.

### Erros

Todos no envelope único (`ErroRespostaDTO`), como o resto da API desde a
padronização de 2026-08-26.

| Status | Quando |
|---|---|
| `401` | refresh ausente, desconhecido, expirado, revogado ou reapresentado |
| `404` | `pacienteId` inexistente ou de outro profissional |

## CORS

Um `CorsConfigurationSource` registrado na `SecurityConfiguration`:

- Origens de `APP_CORS_ORIGINS` (lista separada por vírgula), **nunca `*`** — o
  próprio spec de CORS proíbe combinar curinga com credenciais, e a requisição
  falharia no navegador.
- `allowCredentials(true)`, sem o qual o cookie não viaja.
- Métodos `GET, POST, PUT, PATCH, DELETE, OPTIONS`; headers `Authorization` e
  `Content-Type`.

**Em desenvolvimento o frontend usa o proxy do Vite**, o que torna as chamadas
same-origin e tira CORS e `SameSite=None` do caminho. A configuração cross-site
existe para produção, onde os dois são hospedados separadamente.

O proxy **espelha os caminhos da API** (`/auth`, `/pacientes`, `/consultas`,
`/anotacoes`, `/lembretes`, `/auditoria`) em vez de prefixar tudo com `/api`. O
motivo é o cookie: com um prefixo, o navegador veria `/api/auth/refresh` e não
enviaria um cookie marcado `Path=/auth`, e a renovação falharia só em
desenvolvimento — o pior lugar para descobrir isso.

## Regras

### Geração do token

`SecureRandom` de 32 bytes, codificado em base64url sem padding. Nunca
`Math.random`, nunca `UUID.randomUUID()` — UUID v4 tem 122 bits de entropia e um
formato reconhecível; para credencial, 256 bits opacos.

### Validade

`APP_REFRESH_EXPIRACAO_DIAS`, padrão 14. Prazo de consultório: cabe uma pausa de
férias curta sem forçar login novo, e não deixa credencial viva por meses.

### Transação

`refresh` grava duas linhas (revoga uma, insere outra) e `login` grava e apaga na
mesma chamada. Ambos levam `@Transactional`, pela regra que o `ai/Claude.md` já
fixa: todo método de service que grava mais de uma linha é transacional.

### Isolamento

`GET /pacientes/{pacienteId}/consultas` resolve o usuário logado, busca o
paciente por `findByIdAndUsuarioAndDeletedAtIsNull` e responde `404` se não for
dele — nunca `403`. As consultas saem de um método derivado que já filtra por
usuário e por `deleted_at`, sem versão sem filtro, como o resto do projeto.

## Auditoria de sessão — fora de escopo, com motivo

Login, refresh e logout **não** entram em `LogAuditoria` nesta entrega.

A trilha hoje responde "quem acessou o dado deste paciente". Acesso ao sistema é
outra pergunta, e misturar as duas em `GET /auditoria` muda o que o endpoint
significa sem que ninguém tenha pedido. Registrar sessão tem valor num domínio
clínico, mas é decisão de produto, não consequência técnica do refresh.

## Testes

**Service (Mockito):** rotação emite token novo e revoga o anterior; reuso de
token revogado revoga a cadeia; token expirado não autentica; logout sem cookie
não estoura.

**Repository (Testcontainers):** o método derivado encontra por hash e ignora
revogado e expirado; a limpeza apaga só os expirados do usuário certo; o `UNIQUE`
de `hash_token` é real; `SchemaMigrationTest` continua verde com a `V8`.

**HTTP (MockMvc):** login responde com `Set-Cookie` marcado `HttpOnly`; refresh
sem cookie responde `401` no envelope; refresh válido devolve access novo e
cookie novo; o mesmo refresh usado duas vezes responde `401` na segunda e derruba
a sessão; logout responde `204` e o cookie seguinte não serve mais; preflight
`OPTIONS` de origem permitida responde com os cabeçalhos de CORS.

**Composição:** estender o `FluxoCompletoHttpTest`, ou acrescentar um irmão, que
percorra `login → chamada autenticada → refresh → chamada com o access novo`. É a
única forma de provar que os três passos encaixam — nenhum teste por endpoint
prova isso, que é exatamente a lição que o `AnotacaoMapper` deixou.

## Fora de escopo

Auditoria de sessão (acima). Múltiplos dispositivos com listagem e revogação
individual de sessões. "Lembrar-me" com prazos distintos. Rate limit no login.
Bloqueio por tentativas. Nada disso é pré-requisito do frontend.

## Pontos em aberto

- **Limite de refresh tokens vivos por usuário.** Cada login abre uma cadeia; um
  psicólogo que use três navegadores acumula três. Não há teto, e não há tela que
  os mostre. Aceitável enquanto a limpeza no login recolhe os expirados.
- **Relógio.** A expiração compara com o relógio da aplicação, não do banco.
  Instância única no Render, sem impacto hoje; passa a ter se houver mais de uma.
