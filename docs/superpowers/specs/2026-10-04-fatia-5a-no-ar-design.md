# Fatia 5A — No ar

**Data:** 4 de outubro de 2026
**Estado:** design aprovado pelo dono, seção a seção, em 4 de outubro de 2026; versão escrita aguardando revisão
**Base:** `2026-09-01-redesenho-produto-design.md` §7.4, §9 (Fatia 5) e §10; débitos registrados nas Fatias 3 e 4
**Execução:** um plano (5A). A Fatia 5 segue com 5B (exportação e purga), 5C (concorrência nas escritas de dinheiro) e 5D (assinatura automática), cada uma com spec própria.

---

## 1. O que a fatia entrega

*No fim:* o produto está em `https://{dominio}.com`, com dado no Brasil, custo de
infraestrutura zero (só o domínio), backup diário cifrado com restauração
comprovada, sessão que não cai a cada 15 minutos, cadastro só por convite e com
aceite dos termos — e um checklist do que não é código, com dono e estado.

**A Fatia 5 inteira só fecha quando o checklist do §8 fechar.** A 5A fecha quando
o código, os roteiros e o deploy estiverem prontos e a restauração real tiver sido
executada uma vez.

## 2. Registro de decisões

Todas de 4 de outubro de 2026, do dono, salvo onde indicado.

| # | Decisão | Alternativa recusada |
|---|---|---|
| B1 | **"Pronto" = pronto para o primeiro pagante**: código + checklist não-código com dono e estado | Só código; só colocar no ar |
| B2 | **Ordem 5A no ar → 5B dados → 5C dinheiro → 5D assinatura** | 5B primeiro; tudo numa spec |
| B3 | **Dado no Brasil**: banco e API na região São Paulo | Render (EUA); menor custo sem região |
| B4 | **Custo de infraestrutura zero**: Oracle Cloud Always Free, uma VM ARM em São Paulo com Docker Compose | Supabase + Render free; adiar a produção |
| B5 | **Domínio `.com` próprio** — o único custo | Subdomínio gratuito |
| B6 | **Mesma origem**: Caddy serve o front em `/` e repassa `/api/*` à API, tirando o prefixo | Subdomínios `app.`/`api.` com CORS |
| B7 | **Backup diário cifrado com a chave pública do dono**; a chave privada nunca está na VM | Backup sem cifra; chave na VM |
| B8 | **Teste semanal de restauração com dump efêmero cifrado por uma chave de teste**; a restauração do dump real é roteiro manual | Teste com o dump real; só manual |
| B9 | **Restauração real executada uma vez pelo dono, com data registrada**, é critério de pronto | Backup nunca restaurado |
| B10 | **Provedor de e-mail decidido depois**; até lá o job de avisos fica desligado em produção | Brevo/Resend agora |
| B11 | **Aceite dos termos na 5A**, texto vindo de arquivo versionado; sem arquivo, o cadastro fica indisponível | Na 5B; só depois do texto |
| B12 | **Conta só com pagamento — por enquanto, cadastro por código de convite de uso único**; o dono cobra os primeiros fora do sistema | Cadastro aberto; assinatura automática já |
| B13 | **Assinatura automática vira a sub-fatia 5D** | Dentro da 5A; bloquear o deploy |
| B14 | **Sessão renovada no boot e no 401**, com uma única renovação compartilhada; expirou → `/entrar` com aviso e volta à página | Recarregar leva ao login (hoje) |

Decisões técnicas desta spec (não de negócio, registradas para revisão):

| # | Decisão | Por quê |
|---|---|---|
| T1 | Alerta de backup por **`GET /api/saude` + monitor externo gratuito** que avisa o dono por e-mail, e não um aviso dentro de Ajustes | Ajustes é do psicólogo-cliente, não do operador; não há papel de administrador |
| T2 | Cookie de refresh `SameSite=Strict`, `Path` configurável (`/api/auth` em produção) | Mesma origem permite `Strict`; atrás do prefixo, `Path=/auth` nunca seria enviado |
| T3 | IP do cliente para o limite de tentativas vem de `request.getRemoteAddr()` com `server.forward-headers-strategy=native` | O Tomcat só aceita `X-Forwarded-For` de proxy em rede interna (o Caddy na rede do Compose) — nenhum cliente forja o IP |
| T4 | `Referrer-Policy: no-referrer` no Caddy | O link de descadastro tem token na URL; nenhum recurso externo deve recebê-lo |

## 3. Topologia (B4, B6)

Uma VM ARM do Oracle Cloud Always Free, região São Paulo. Um `infra/compose.yml`
com quatro serviços numa rede interna:

| Serviço | O quê | Exposto |
|---|---|---|
| `caddy` | HTTPS automático (Let's Encrypt) para `{DOMINIO}`; serve o build do front; `handle_path /api/*` → `api:8080`; cabeçalhos de segurança | 80, 443 |
| `api` | o `backend/Dockerfile` atual, construído na própria VM (ARM nativo) | não |
| `postgres` | Postgres 16, volume persistente | não |
| `backup` | dump, cifra, envio, retenção, teste semanal (§4) | não |

- O front entra numa imagem do `caddy` por build multi-estágio (`infra/caddy/Dockerfile`:
  estágio Node faz `npm ci && npm run build` com `VITE_API_URL=/api`; estágio Caddy copia `dist/`).
- Caddy: `try_files {path} /index.html` para o SPA; `Strict-Transport-Security`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
  `Content-Security-Policy` com `default-src 'self'; frame-ancestors 'none';
  style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com`
  (o `index.html` carrega a Inter do Google Fonts; servir a fonte localmente fica como débito).
- VM: firewall só 80/443 (e SSH por chave), atualizações automáticas de segurança,
  Docker com `restart: unless-stopped`.
- Segredos num `infra/.env` **só na VM** (há um `infra/.env.exemplo` versionado,
  sem valor real): `DOMINIO`, `POSTGRES_*`, `JWT_SECRET`, `APP_CADASTRO_CONVITES`,
  `BACKUP_*`, `APP_FRONTEND_URL=https://{DOMINIO}`, `APP_AVISOS_HABILITADO=false`
  até existir provedor (B10).
- Deploy: `infra/deploy.sh` rodado na VM — `git pull`, `docker compose build`,
  `docker compose up -d`; as migrations rodam pelo Flyway no boot da API. Sem CI nesta fatia.
- `GET /saude` (na API, público, sem dado nenhum): `200 {"status":"ok","backup":"ok"}`;
  `503` com `"backup":"atrasado"` quando o último dump bem-sucedido tem mais de 36 h
  ou o último teste semanal falhou. A API lê um arquivo de estado escrito pelo
  serviço `backup` num volume compartilhado só-leitura para ela. Um monitor
  externo gratuito (escolha do dono; ex.: UptimeRobot) consulta `https://{DOMINIO}/api/saude`
  e avisa o dono por e-mail.

## 4. Backup e restauração (B7, B8, B9)

- **Dump diário às 03:00 (Brasília):** `pg_dump -Fc` → `age -r {chave pública do dono}`
  → envio ao Object Storage do Oracle (bucket privado, API compatível com S3).
  Nome `psychology-{AAAA-MM-DD}.dump.age`. Cópia local da última noite na VM.
- **Retenção:** 30 diários + 12 mensais (o do dia 1); o resto é apagado do bucket.
- **Teste semanal (domingo 04:00):** `pg_dump` novo → cifrado com a **chave de teste**
  (par gerado na VM, só para isto) → envio para `teste/` no bucket → download →
  decifra → restaura num Postgres descartável (container efêmero) → confere a
  versão do Flyway igual à do banco vivo e a existência das tabelas do modelo →
  apaga o arquivo do bucket e local → grava o resultado no arquivo de estado.
- **Nada do dump real é decifrável na VM.** Quem só tem o bucket não lê nada;
  quem tem a VM já tem o banco vivo.
- **Restauração real (critério de pronto):** `docs/operacao/restauracao.md` — o dono
  baixa um dump real, decifra com a chave privada fora da VM, restaura num Postgres
  local e confere contagens; registra data e resultado em `docs/operacao/portao.md`.
- Falha em qualquer etapa: o arquivo de estado registra a etapa e o horário (sem
  conteúdo, sem caminho de dado), e `/saude` passa a 503.

## 5. Sessão no front (B14, T2)

- **Prefixo:** `api()` monta `` `${import.meta.env.VITE_API_URL ?? ''}${caminho}` ``.
  Produção `/api`; dev vazio (o proxy do Vite segue igual); testes vazios.
- **Cookie:** nova propriedade `app.refresh.cookie-path` (`APP_REFRESH_COOKIE_PATH`,
  padrão `/auth`); produção `/api/auth` e `APP_REFRESH_COOKIE_SAMESITE=Strict`.
- **Boot:** antes de qualquer rota protegida, `POST /auth/refresh`; 200 → sessão
  restaurada sem login; 401 → `/entrar`. Enquanto renova, a tela mostra `Carregando…`.
- **401 no meio do uso:** uma única renovação compartilhada (uma promise) entre todas
  as chamadas que falharem juntas; sucesso → repete a chamada original uma vez;
  falha → limpa a sessão e o cache do TanStack, vai para `/entrar` com
  `Sua sessão expirou. Entre novamente.` e, depois de entrar, volta para a rota em
  que estava. O que estava digitado num formulário aberto se perde (rascunho fica fora).
- **Não renovam no 401:** `/auth/*` e `/descadastro/*`.
- O README passa a dizer a verdade sobre "O caminho de uma sessão".

## 6. Termos (B11) — migration `V3__20261004.sql`

- `usuario`: `termos_versao_usuario VARCHAR(20) NULL`, `termos_aceitos_em_usuario TIMESTAMP NULL`.
- Textos em `backend/src/main/resources/termos/termos.md` e `privacidade.md`; a
  primeira linha de cada um é `versao: {texto}` (as duas iguais). **O repositório só
  recebe esses arquivos com o texto do advogado.** Sem os arquivos:
  `GET /termos/atual` → `404`; `POST /auth/registrar` → `503` `Cadastro ainda não está disponível.`
- `GET /termos/atual` (público) → `{versao, termos, privacidade}` (markdown sem a linha de versão).
- `POST /auth/registrar` exige `aceiteTermos: true` (`400` no campo:
  `Aceite os termos para criar a conta.`) e `versaoTermos`; versão diferente da
  atual → `409` `Os termos mudaram. Leia a versão atual.` Grava versão e data;
  trilha `CRIACAO "AceiteDosTermos"` com o id do usuário.
- Front: páginas públicas `/termos` e `/privacidade` (texto do markdown como
  parágrafos — sem HTML do arquivo); no cadastro, caixa
  `Li e aceito os Termos de Uso e a Política de Privacidade` com os dois links.
- Reaceite quando a versão mudar: **fora desta fatia** (débito; só importa na segunda versão).

## 7. Cadastro por convite e proteção (B12, T3)

- `APP_CADASTRO_CONVITES`: códigos separados por vírgula, definidos pelo dono no
  `.env`. Sem a variável, nenhum convite vale.
- Migration V3: tabela `convite_usado` — `id_convite UUID PK`,
  `hash_convite VARCHAR(64) NOT NULL UNIQUE` (SHA-256 do código),
  `id_usuario UUID NOT NULL FK`, `usado_em_convite TIMESTAMP NOT NULL`.
- `POST /auth/registrar` exige `codigoConvite`. Ordem: termos disponíveis (503) →
  validação de campos (400) → convite (403 `Código de convite inválido.`, mesma
  mensagem para desconhecido e já usado) → versão dos termos (409) → e-mail repetido
  (409, como hoje). Convite queimado e conta criada na **mesma transação**; corrida
  com o mesmo código → o índice único decide, o segundo recebe 403.
- O código nunca vai para log, trilha ou resposta.
- **Limite de tentativas** em `POST /auth/login`, `/auth/registrar`, `/auth/refresh`:
  10 por minuto por IP e por rota, em memória (uma instância), `429` no envelope com
  `Muitas tentativas. Tente de novo em alguns minutos.` IP por T3.
- **Tela `/cadastro`** (pública): `Código de convite`, `Nome`, `E-mail`, `Senha`
  (regras existentes: 10–64, `@SenhaForte`), caixa de aceite, botão `Criar conta`;
  sucesso (`201`) → o front chama `POST /auth/login` com o mesmo e-mail e senha e
  entra direto (o cadastro não emite sessão — uma regra de emissão só); `503` →
  `Cadastro ainda não está disponível.`; links `Criar conta` em `/entrar` e
  `Já tenho conta` em `/cadastro`.
- e2e: código fixo de teste por variável de ambiente; os e2e passam a cadastrar com ele.

## 8. Checklist do que não é código — `docs/operacao/portao.md`

| Item | Dono | Bloqueia |
|---|---|---|
| Registrar o domínio `.com` e apontar o DNS para a VM (sugestão: Cloudflare como registrador e DNS) | dono | deploy |
| Conta Oracle Cloud, VM ARM em São Paulo, bucket privado e chaves de acesso ao bucket | dono, pelo `docs/operacao/implantacao.md` | deploy |
| Par de chaves `age` do backup gerado fora da VM; privada guardada fora dela | dono | restauração real |
| Restauração real executada e registrada | dono, pelo `docs/operacao/restauracao.md` | 1º pagante |
| Monitor externo de `/api/saude` | dono | 1º pagante |
| Termo de Uso, Política de Privacidade, contrato de operador (LGPD art. 39) | advogado | 1º pagante |
| Prazo de guarda do prontuário (CFP) em fonte primária | dono/advogado | 5B (purga) |
| Plano de incidente: parte técnica escrita na 5A (`docs/operacao/incidente.md`); prazos e texto da comunicação à ANPD e aos titulares **[verificar]** com advogado (a Resolução CD/ANPD nº 15/2024 é a referência a confirmar) | Claude (técnica) + advogado | 1º pagante |
| Provedor de e-mail + SPF/DKIM/DMARC no domínio | dono, quando decidir | só o aviso |
| Homologação com TI/Infra, se o projeto for da Syonet | dono | — |

## 9. Documentação

- `docs/operacao/implantacao.md` (do zero até `https://{dominio}` no ar, com a checagem
  que prova cada passo), `restauracao.md`, `incidente.md`, `portao.md`, e a seção de
  convites (criar código, reiniciar a API).
- README: status ("no ar" só depois do deploy real), Deploy reescrito para a VM/Compose,
  as menções a Render saem, débitos atualizados, sessão verdadeira.

## 10. Invariantes

1. Nenhum segredo, código de convite, chave ou dump no repositório ou em log.
2. Postgres e API nunca expostos fora da rede do Compose.
3. Backup real nunca decifrável na VM.
4. Testes por rótulo escrito; nenhum comentário novo em código; suítes verdes; e2e passando.
5. Isolamento por psicólogo intocado; `/saude` e `/termos/atual` não revelam dado de ninguém.

## 11. Fora de escopo

Assinatura automática (5D); exportação e purga (5B); concorrência do dinheiro (5C);
confirmação de e-mail e recuperação de senha (dependem do provedor); painel
administrativo; CI/CD; mais de uma instância; reaceite de termos; rascunho de formulário
ao expirar a sessão; enumeração de e-mail pelo 409 do cadastro (mitigada: exige convite válido).

## 12. Em aberto para o dono

Nada para escrever o plano. Antes do deploy: nome do domínio e escolha do monitor externo.

**Para avaliação técnica:** o produto vai ao ar numa máquina gratuita em São Paulo, no
endereço próprio, com HTTPS; o banco é copiado todo dia, cifrado com uma chave que só o
dono tem, e a cópia é testada toda semana; entra só quem recebeu convite e aceitou os
termos; a sessão deixa de cair a cada 15 minutos.
