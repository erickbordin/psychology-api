# Implantação: do zero até o produto no ar

Este roteiro leva de uma conta vazia até `https://{dominio}` respondendo, e cada passo termina numa **checagem que prova** que ele deu certo. Quem executa é o dono; nada aqui é automático.

A infraestrutura é uma VM ARM no Oracle Cloud (Always Free, região São Paulo) com Docker Compose: `caddy` (HTTPS e front), `api`, `postgres` e `backup`. O desenho e as decisões estão em `docs/superpowers/specs/2026-10-04-fatia-5a-no-ar-design.md`.

Tudo que depende de preço, limite, tela ou nome de botão de um terceiro (Oracle, registrador de domínio, monitor externo) está marcado **confirmar no site**: essas coisas mudam, e este documento não é a fonte.

Convenção: `{dominio}` é o domínio escolhido; os exemplos usam `exemplo.com`. Nenhum valor real entra no repositório.

## Visão geral do que precisa existir

| # | Passo | Prova |
|---|---|---|
| 1 | Conta Oracle e VM ARM em São Paulo | `ssh` na VM funciona |
| 2 | Firewall: 80 e 443 abertos | `curl` de fora chega na VM (passo 9) |
| 3 | SSH só por chave e atualizações automáticas | login por senha recusado |
| 4 | Docker instalado | `docker run --rm hello-world` |
| 5 | Repositório clonado | `git status` limpo em `psychology-api` |
| 6 | Bucket privado e chave S3 | bucket existe e a chave lista ele |
| 7 | Domínio com registro `A` para o IP da VM | `dig +short {dominio}` devolve o IP |
| 8 | Par de chaves `age` gerado fora da VM | só a chave pública está na VM |
| 9 | Arquivo de variáveis preenchido | `docker compose config -q` sem erro |
| 10 | `infra/deploy.sh` | `no ar: https://{dominio}/api/saude` |
| 11 | Primeiro backup manual | `/api/saude` devolve 200 |
| 12 | Monitor externo | e-mail de teste do monitor chega |
| 13 | Checagem final no navegador | login, F5, cookie com `Path=/api/auth` |

## 1. Conta Oracle e VM

1. Criar a conta Oracle Cloud escolhendo a região **Brazil East (São Paulo)** como região principal. A região não muda depois. Exige cartão para verificação; **confirmar no site** como a verificação e a cobrança funcionam.
2. Criar uma instância de computação com forma ARM (Ampere) e imagem Ubuntu LTS. **Confirmar no site** quais formas e quantos recursos o Always Free inclui hoje e se há disponibilidade em São Paulo; capacidade ARM gratuita às vezes falta e é preciso tentar de novo mais tarde.
3. Gerar o par de chaves SSH **no seu computador** (`ssh-keygen -t ed25519`) e informar só a chave pública na criação da instância.
4. Anotar o IP público da VM.

**Prova:** `ssh ubuntu@{ip-da-vm}` entra sem pedir senha. O usuário padrão da imagem pode variar; **confirmar no site**.

## 2. Firewall: 80 e 443

São **duas** camadas, e as duas precisam estar abertas. Abrir só uma é o erro mais comum: a VM parece no ar e nada chega nela.

1. **Lista de segurança da VCN** (console Oracle, rede da instância): regra de entrada TCP para as portas 80 e 443, origem `0.0.0.0/0`. A porta 22 já existe. **Confirmar no site** onde fica a tela.
2. **Firewall da própria VM.** As imagens Ubuntu do Oracle costumam trazer regras de `iptables` que bloqueiam tudo além do SSH. Na VM:

```bash
sudo iptables -L INPUT -n --line-numbers
```

Se houver uma regra `REJECT` no fim, insira o 80 e o 443 antes dela e persista:

```bash
sudo iptables -I INPUT 5 -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
sudo apt-get install -y iptables-persistent
sudo netfilter-persistent save
```

O número da linha (5 e 6) depende da listagem anterior: use a posição logo acima do `REJECT`. Se a VM usar `ufw`, o equivalente é `sudo ufw allow 80/tcp` e `sudo ufw allow 443/tcp`, sem esquecer `sudo ufw allow 22/tcp` antes de ativá-lo.

Postgres (5432) e API (8080) **não** são abertos em lugar nenhum: só o Caddy publica portas.

**Prova:** depois do passo 10, `curl -I https://{dominio}` de **outra máquina** responde. Se travar sem resposta, é firewall.

## 3. SSH por chave e atualizações automáticas

1. Desligar a senha no SSH: em `/etc/ssh/sshd_config` (ou num arquivo de `/etc/ssh/sshd_config.d/`), `PasswordAuthentication no`; depois `sudo systemctl reload ssh`. Antes de fechar a sessão atual, abra uma segunda e confirme que a chave entra.
2. Atualizações de segurança automáticas:

```bash
sudo apt-get update
sudo apt-get install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades
```

**Prova:** `ssh -o PubkeyAuthentication=no ubuntu@{ip-da-vm}` é recusado, e `systemctl status unattended-upgrades` mostra o serviço ativo.

## 4. Docker

Instalar o Docker Engine e o plugin Compose pelo método oficial para Ubuntu (**confirmar no site** os comandos atuais da documentação do Docker). Depois, permitir o usuário sem `sudo`:

```bash
sudo usermod -aG docker "$USER"
```

Saia e entre de novo na sessão SSH.

**Prova:**

```bash
docker run --rm hello-world
docker compose version
```

## 5. Clonar o repositório

```bash
git clone {url-do-repositorio} psychology-api
cd psychology-api
```

`infra/deploy.sh` faz `git pull --ff-only` dentro deste clone. Quem altera código altera aqui só por `git pull`; nada se edita à mão na VM.

**Prova:** `git status` sem alterações.

## 6. Bucket privado e chave de acesso S3

No console Oracle, em Object Storage (**confirmar no site** nomes e caminhos de menu):

1. Criar um **bucket privado** (sem acesso público), na região São Paulo, por exemplo `psychology-backup`. Anote o nome.
2. Criar um **usuário IAM dedicado** (por exemplo `backup-psychology`, num grupo só dele, `grp-backup-psychology`) e uma **política** que permite só este bucket. **Nunca** gere a chave no seu usuário de dono do tenancy (administrador): quem invadir a VM apagaria todos os dumps e teria direitos sobre a conta inteira. Políticas, no compartimento onde o bucket está (sintaxe do OCI IAM; **confirmar no site** nomes de grupo, compartimento e o escopo exato do `where`):

```
Allow group grp-backup-psychology to read objectstorage-namespaces in compartment {compartimento}
Allow group grp-backup-psychology to manage objects in compartment {compartimento} where target.bucket.name = 'psychology-backup'
```

   O `manage objects` é necessário porque o `retencao.sh` apaga dumps antigos e o teste de restauração apaga o próprio objeto. A primeira linha só deixa o cliente S3 descobrir o namespace; **confirmar no site** se ela é dispensável no seu caso.

   Depois crie a **chave de acesso compatível com S3** (Customer Secret Key) **para esse usuário dedicado**. O segredo aparece **uma vez só**: copie na hora para o seu gerenciador de senhas. Uma regra de retenção do bucket (impede apagar dumps com menos de 30 dias) reforça a proteção, mas conflita com a limpeza do `retencao.sh` e com o objeto de teste; só adote depois de **confirmar no site** e separar o teste em outro bucket.
3. Anotar o **namespace** do tenancy e montar o endpoint compatível com S3. O formato costuma ser `https://{namespace}.compat.objectstorage.sa-saopaulo-1.oraclecloud.com`; **confirmar no site** o endereço exato e o identificador da região.

**O bucket precisa existir antes do primeiro backup.** O serviço `backup` usa `no_check_bucket`: o `rclone` não tenta criar o bucket nem verificar se ele existe, só envia. Bucket ausente significa que o primeiro backup falha na etapa `envio` e `/api/saude` fica em 503.

**Prova (no seu computador, não na VM):** liste o bucket com o cliente S3 de sua preferência usando a chave nova e confira que a listagem não dá erro. **Confirmar no site** como usar a chave com o cliente escolhido.

## 7. Domínio e DNS

1. Registrar um domínio `.com` num registrador de sua escolha (a sugestão do projeto é Cloudflare como registrador e DNS; **confirmar no site** preço e condições). O domínio é o único custo previsto.
2. Criar um registro `A` de `{dominio}` para o IP público da VM. Se o DNS for da Cloudflare, deixe o registro **sem proxy** ("somente DNS"): o Caddy obtém o certificado Let's Encrypt pela própria VM e precisa receber a validação direta.

**Não publique registro `AAAA` (IPv6) para o domínio**, a menos que o IPv6 esteja configurado de ponta a ponta. O proxy do Docker faz todo cliente IPv6 chegar à API com o mesmo IP interno, e o limite de tentativas por IP passaria a tratar todos eles como um só: 10 tentativas por minuto somadas, e o login de todos trava junto.

**Prova:**

```bash
dig +short {dominio}
```

Tem que devolver o IP da VM. A propagação pode levar de minutos a horas; **confirmar no site** do registrador se demorar.

Sem o `A` apontando para a VM, o Caddy não consegue o certificado e o `deploy.sh` não passa.

## 8. Par de chaves `age`, fora da VM

O backup é cifrado com uma chave **pública**; a privada nunca sobe na VM. Quem tiver só o bucket, ou só a VM, não lê o dump real.

No **seu computador** (instale `age` pelo gerenciador de pacotes):

```bash
age-keygen -o chave-backup.txt
```

O comando imprime `Public key: age1...`. Essa linha é a que vai para a VM. O arquivo `chave-backup.txt` é a chave **privada**:

1. Guarde duas cópias fora da VM (gerenciador de senhas e mídia offline).
2. Nunca coloque no repositório, no bucket ou na VM.
3. Perder a chave significa perder todos os backups: eles ficam ilegíveis.

**Prova:** `grep -c "AGE-SECRET-KEY" chave-backup.txt` devolve `1` no seu computador, e a VM só recebeu a linha `age1...`.

## 9. Arquivo de variáveis da VM

As variáveis ficam num arquivo `infra/.env` **só na VM**. Ele é ignorado pelo git e nunca é versionado. O Compose o lê com `--env-file infra/.env` (o `deploy.sh` já faz isso).

Crie o arquivo com permissão restrita:

```bash
umask 077
nano infra/.env
```

Conteúdo, com **valores de exemplo** (troque cada um; `troque-me` nunca vai para produção):

```
DOMINIO=exemplo.com
TZ=America/Sao_Paulo
POSTGRES_DB=psychology
POSTGRES_USER=psychology
POSTGRES_PASSWORD=troque-me
JWT_SECRET=troque-me
APP_CADASTRO_CONVITES=
APP_FRONTEND_URL=https://exemplo.com
APP_CORS_ORIGINS=https://exemplo.com
APP_AVISOS_HABILITADO=false
BACKUP_CHAVE_PUBLICA=age1...
BACKUP_S3_ENDPOINT=https://exemplo-endpoint-s3
BACKUP_S3_REGIAO=sa-saopaulo-1
BACKUP_S3_BUCKET=psychology-backup
BACKUP_S3_CHAVE=troque-me
BACKUP_S3_SEGREDO=troque-me
```

Regras que quebram o deploy quando esquecidas:

| Variável | Regra |
|---|---|
| `DOMINIO` | **Sem aspas e sem `https://`.** O `deploy.sh` lê a linha com `grep` e `cut`; aspas entram no valor e a checagem de saúde aponta para um endereço que não existe |
| `POSTGRES_PASSWORD` | gere: `openssl rand -hex 16`. O valor só é aplicado quando o volume do Postgres nasce; trocar depois exige o `ALTER USER` descrito em `incidente.md` |
| `JWT_SECRET` | Base64 de pelo menos 32 bytes: `openssl rand -base64 48`. Sem ele a API não sobe |
| `APP_CADASTRO_CONVITES` | vazia no começo: nenhum convite vale. Códigos entram pelo `docs/operacao/convites.md` |
| `APP_FRONTEND_URL` e `APP_CORS_ORIGINS` | `https://{dominio}`, sem barra no fim |
| `APP_AVISOS_HABILITADO` | `false` até existir provedor de e-mail |
| `BACKUP_CHAVE_PUBLICA` | a linha `age1...` do passo 8, nunca a chave privada |
| `BACKUP_S3_*` | endpoint, região, nome do bucket e o par de chaves do passo 6 |

`DOMINIO`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `BACKUP_CHAVE_PUBLICA` e `BACKUP_S3_*` são obrigatórias: o Compose recusa subir com uma delas ausente ou vazia e diz qual. As constantes de segurança de produção (`APP_REFRESH_COOKIE_PATH=/api/auth`, `APP_REFRESH_COOKIE_SAMESITE=Strict`, `APP_REFRESH_COOKIE_SEGURO=true` e `SPRINGDOC_ENABLED=false`) estão fixas no `infra/compose.yml`: não vão no arquivo de variáveis e o arquivo não as altera.

Opcionais, só se for ligar o aviso por e-mail: `APP_EMAIL_REMETENTE`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_AUTH`, `SPRING_MAIL_STARTTLS`, `APP_REFRESH_EXPIRACAO_DIAS`. Elas só passam adiante se estiverem no arquivo. Provedor de e-mail está decidido para depois (`portao.md`).

Conferir que o Compose enxerga tudo:

```bash
docker compose -f infra/compose.yml --env-file infra/.env config -q
```

**Prova:** o comando não imprime nada e sai com código 0. Aviso de variável não definida (`variable is not set`) significa que falta linha no arquivo.

## 10. Primeiro deploy

```bash
bash infra/deploy.sh
```

O script faz `git pull --ff-only`, `docker compose build`, `docker compose up -d` e espera `https://{dominio}/api/saude` responder (até 90 tentativas de 2 segundos). A primeira execução é lenta: constrói o front (Node) e a API (Maven) na própria VM. As migrations do Flyway rodam no boot da API.

**Prova:** o script termina com uma linha como

```
no ar: https://exemplo.com/api/saude -> {"status":"ok","backup":"atrasado"} 503
```

O `503` com `"status":"ok"` é o **esperado** neste ponto: ainda não existe nenhum backup, e a regra de `/api/saude` trata isso como atrasado. A API e o Caddy estão de pé; falta o passo 11.

Se o script falhar com "sem resposta valida", veja `docker compose -f infra/compose.yml --env-file infra/.env logs caddy api` e a tabela de problemas no fim deste documento.

## 11. Primeiro backup, à mão

Não espere as 03:00 para descobrir um erro de bucket ou de chave. Depois do primeiro deploy:

```bash
docker compose -f infra/compose.yml --env-file infra/.env exec backup /app/backup.sh
```

O script gera o dump, cifra, envia ao bucket, aplica a retenção e grava o estado. Qualquer falha termina com código diferente de zero e grava a etapa (`dump`, `envio`, `retencao` ou `estado`) no arquivo de estado.

**Prova:**

```bash
curl -s -w '\n%{http_code}\n' https://{dominio}/api/saude
```

Tem que devolver `{"status":"ok","backup":"ok"}` e `200`. Confira também que o arquivo `psychology-AAAA-MM-DD.dump.age` apareceu no bucket (pelo console, **confirmar no site**).

Recomendado, uma vez: provar o teste semanal de restauração sem esperar o domingo.

```bash
docker compose -f infra/compose.yml --env-file infra/.env exec backup /app/testar-restauracao.sh
```

Termina sem erro e sem deixar nada em `teste/` no bucket. Esse teste usa uma chave efêmera própria; ele **não** substitui a restauração real de `docs/operacao/restauracao.md`.

## 12. Monitor externo de `/api/saude`

O alerta de backup é `GET /api/saude` + um monitor externo gratuito que avisa o dono por e-mail. A API devolve `503` quando o último dump tem mais de 36 horas ou o último teste semanal falhou, e o monitor trata isso como queda.

1. Escolher um monitor gratuito (exemplo: UptimeRobot; **confirmar no site** plano gratuito, limites e intervalo mínimo).
2. Criar um monitor HTTP(S) para `https://{dominio}/api/saude`, com aviso para o seu e-mail. O monitor deve tratar status diferente de 2xx como falha (é o comportamento comum; **confirmar no site**).
3. Disparar um teste do próprio monitor para provar que o e-mail chega.

**Prova:** o monitor mostra o endpoint como "no ar" (200), e o e-mail de teste chegou. Marque o item em `docs/operacao/portao.md`.

O monitor também avisa se a VM inteira cair, o que é um bônus.

## 13. Checagem final no navegador

Com uma janela anônima, em `https://{dominio}`:

1. **HTTPS válido:** cadeado sem alerta de certificado.
2. **Login:** sem convite configurado ninguém cria conta. Siga `docs/operacao/convites.md` para gerar um código e criar a primeira conta em `/cadastro` (só funciona com o texto dos termos instalado; veja a seção "Termos de uso" abaixo). Depois entre em `/entrar`.
3. **F5 sem deslogar:** em `/pacientes`, aperte F5. A tela mostra `Carregando…` e volta logada. Se for para o login, o cookie de refresh não está voltando.
4. **Cookie com o caminho certo:** nas ferramentas do navegador (aba Aplicativo ou Armazenamento, Cookies), o cookie do refresh tem `Path=/api/auth`, `HttpOnly`, `Secure` e `SameSite=Strict`. Caminho `/auth` significa que `APP_REFRESH_COOKIE_PATH` não chegou na API.
5. **Cabeçalhos:** na aba Rede, a resposta do documento traz `Strict-Transport-Security`, `Content-Security-Policy` e `Referrer-Policy: no-referrer`.
6. **Contrato fechado:** `https://{dominio}/api/swagger-ui.html` não mostra a documentação interativa (`SPRINGDOC_ENABLED=false`).

Se a tela abrir sem estilo ou sem a fonte, abra o console: a política `Content-Security-Policy` do Caddy só libera o próprio domínio e o Google Fonts.

## Termos de uso (necessários para o cadastro)

O cadastro **só funciona** com os textos de Termos de Uso e Política de Privacidade instalados. Sem eles, `GET /api/termos/atual` responde `404` e `POST /auth/registrar` responde `503` com `Cadastro ainda não está disponível.` O repositório só recebe esses arquivos com o texto do advogado (`portao.md`).

O diretório é lido de `APP_TERMOS_DIRETORIO` (padrão `classpath:termos/`). Cada arquivo, `termos.md` e `privacidade.md`, começa com uma linha `versao: {texto}` (as duas iguais); o resto é o texto em markdown.

Duas formas de entregar os textos:

1. **Dentro da imagem (como está hoje):** colocar `termos.md` e `privacidade.md` em `backend/src/main/resources/termos/`, versionar com o texto do advogado e rodar `bash infra/deploy.sh`. A API reconstrói e passa a servir.
2. **Montados de fora da imagem:** `APP_TERMOS_DIRETORIO=file:/termos/` com um volume apontando para uma pasta da VM. **O `infra/compose.yml` atual não repassa essa variável nem monta o volume**; usar esta forma exige acrescentar as duas linhas no serviço `api` do Compose. Até lá, vale a forma 1.

Trocar o texto muda a `versao`; quem se cadastra com a versão antiga recebe `409` e precisa recarregar. Reaceite de quem já tem conta está fora desta fatia.

## Atualizar depois

Para publicar uma versão nova do código:

```bash
cd psychology-api
bash infra/deploy.sh
```

É o mesmo comando do primeiro deploy. A cada rodada ele baixa as imagens-base atualizadas (Caddy, Temurin, Alpine, Postgres), faz um backup antes de subir a versão nova (se o backup falhar, o deploy aborta) e só então sobe. No primeiro deploy não há backup prévio e o passo é pulado com um aviso. Migration nova roda sozinha no boot da API.

Não há rollback automático. Para voltar a uma versão anterior: `git checkout {sha}` e `docker compose -f infra/compose.yml --env-file infra/.env up -d --build` (não use o `deploy.sh`: o `git pull --ff-only` dele falha fora de um ramo). Depois volte com `git checkout main`. Migration já aplicada não desfaz: se a nova versão a quebrou, restaure o backup feito antes do deploy (`restauracao.md`). Rodar `deploy.sh` não apaga volume: o banco, os backups locais e o estado do Caddy (certificado) persistem.

## Quando não funciona

| Sintoma | Causa provável |
|---|---|
| `deploy.sh`: `DOMINIO vazio ou ausente em infra/.env` | falta a linha `DOMINIO=` no arquivo, ou o caminho está errado |
| `deploy.sh` não passa e `curl` mostra erro de certificado ou de conexão | o registro `A` ainda não aponta para a VM, ou as portas 80/443 estão fechadas na VCN **ou** na VM; o Caddy precisa das duas para o Let's Encrypt |
| `caddy` em loop de reinício | `DOMINIO` com aspas ou com `https://` |
| API reiniciando, log com `PlaceholderResolutionException` | variável faltando no arquivo (em geral `APP_FRONTEND_URL`, `APP_CORS_ORIGINS` ou as de banco) |
| `/api/saude` fica `503` com `"backup":"atrasado"` depois do passo 11 | `backup.sh` falhou: leia a saída do comando; a causa mais comum é bucket inexistente ou chave S3 errada |
| `backup.sh` demora minutos e falha em `envio` | endpoint S3 errado ou sem rota; o `rclone` tem tempo-limite de 15 s para conectar e 60 s por operação |
| Login funciona e o F5 desloga | cookie com `Path` errado ou sem `Secure`; confira o passo 13 |
| `429 Muitas tentativas` | limite de 10 por minuto por IP e por rota em login, cadastro e refresh |
| Cadastro responde `503` | os textos dos termos não estão instalados |
| Cadastro responde `403` | código de convite desconhecido, em branco ou já usado |
