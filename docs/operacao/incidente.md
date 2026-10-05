# Plano de incidente de segurança

Este documento tem duas partes de natureza diferente.

1. **Parte técnica** (seções 1 a 5): sinais, contenção, preservação e avaliação do que vazou. Foi escrita a partir de como o sistema está montado e pode ser seguida na hora.
2. **Parte de comunicação** (seção 6): prazos, destinatários e texto de aviso à ANPD e aos titulares. **Não foi validada por advogado. Nada ali deve ser tratado como fato jurídico.** Todo ponto marcado **[verificar com advogado]** precisa de confirmação antes de valer.

O sistema guarda dado sensível de saúde (anotação de sessão, histórico de paciente) em nome de psicólogos. Um incidente real quase sempre envolve dado de terceiros.

Todos os comandos rodam na VM, no diretório do clone, com `docker compose -f infra/compose.yml --env-file infra/.env`. Abreviado abaixo como `$C`:

```bash
C="docker compose -f infra/compose.yml --env-file infra/.env"
```

## 1. Sinais de que algo está errado

| Sinal | Onde olhar |
|---|---|
| Alerta do monitor externo de `/api/saude` | e-mail do monitor; `curl -s https://{dominio}/api/saude` |
| Psicólogo relata acesso que não fez, ou paciente que não é dele | a trilha: `GET /auditoria` na conta dele |
| Pico de `429` ou de `401` | log da API: `$C logs --since 1h api` |
| Entradas de convite que você não gerou | tabela `convite_usado` (usuários criados fora do esperado) |
| Cadastro novo que você não reconhece | `usuario` ordenado por data de criação |
| Arquivo ou processo estranho na VM, login SSH que não foi seu | `last`, `journalctl -u ssh` |
| Dump no bucket que você não criou, ou ausente | listagem do bucket |
| Vazamento da chave S3, do `JWT_SECRET`, da senha do banco ou do arquivo `infra/.env` | por você ou por terceiros avisando |
| Chave privada do `age` perdida ou exposta | você sabe; trate como incidente do backup (seção 4) |

Ao notar um sinal, **anote a data e a hora** agora, antes de qualquer ação. Esse horário é a base de todos os prazos da seção 6.

## 2. Contenção

Faça na ordem que o caso pedir. Registre cada ação com hora.

**Derrubar a API sem apagar nada.** O Caddy continua servindo o front, e as chamadas a `/api` passam a falhar:

```bash
$C stop api
```

Para derrubar tudo que é público (inclusive o front): `$C stop caddy api`. O banco e o backup continuam de pé, e os dados continuam íntegros.

**Fechar o cadastro.** Esvazie `APP_CADASTRO_CONVITES` em `infra/.env` e rode `$C up -d api`. Nenhum convite passa a valer.

**Trocar o `JWT_SECRET`.** Gere outro (`openssl rand -base64 48`), substitua em `infra/.env` e recrie a API:

```bash
$C up -d api
```

Isso invalida na hora todo **access token** emitido (eles duram 15 minutos). Mas o **refresh token** é um valor opaco guardado no banco, não assinado pelo segredo: com a API de volta, quem tinha o cookie ainda renova e recebe um token assinado com o segredo novo. Para encerrar também as sessões renováveis, apague os refresh tokens:

```bash
$C exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DELETE FROM refresh_token"'
```

Todos os profissionais precisam entrar de novo. É uma ação irreversível sobre as sessões (não sobre os dados): confirme com o dono antes. Avise as pessoas.

**Trocar a senha do banco.** O valor de `POSTGRES_PASSWORD` só vale na criação do volume, então a troca tem duas partes: primeiro dentro do banco, depois no arquivo. Abra o `psql` e use o comando interativo, que não deixa a senha no histórico do shell:

```bash
$C exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Dentro do `psql`, rode `\password` seguido do nome do usuário (o valor de `POSTGRES_USER`), digite a senha nova duas vezes e saia com `\q`. Depois substitua `POSTGRES_PASSWORD` em `infra/.env` e recrie quem usa o banco:

```bash
$C up -d api backup
```

A API e o backup não conseguem conectar entre a troca no banco e o `up -d`; faça os dois em seguida.

**Se a chave S3 vazou:** no console Oracle (**confirmar no site**), revogue a chave e crie outra; troque `BACKUP_S3_CHAVE` e `BACKUP_S3_SEGREDO` em `infra/.env` e rode `$C up -d backup`. Confira em seguida se há objetos no bucket que você não criou.

**Se a VM foi comprometida:** isolar vem antes de investigar. Pare as portas públicas na lista de segurança da VCN (console Oracle, **confirmar no site**), preserve (seção 3) e só então reconstrua a VM do zero seguindo `implantacao.md`. Considere todos os segredos de `infra/.env` como vazados e troque todos.

**Se a chave privada do `age` vazou:** quem tiver a chave **e** acesso a qualquer dump do bucket lê tudo o que ele contém. Gere um par novo (passo 8 de `implantacao.md`), troque `BACKUP_CHAVE_PUBLICA`, `$C up -d backup`, e trate os dumps antigos como expostos. Apagar os antigos do bucket só reduz o risco se ninguém já os copiou.

## 3. Preservação

**Antes de reconstruir ou apagar qualquer coisa**, guarde a evidência fora da VM, num lugar que só o dono acessa. Nada disso vai para o repositório, para ticket ou para conversa.

1. **Logs da API e do Caddy**, antes que o container seja recriado:

```bash
$C logs --no-log-prefix --timestamps api | age -r {chave-publica-do-dono} > api.log.age
$C logs --no-log-prefix --timestamps caddy | age -r {chave-publica-do-dono} > caddy.log.age
```

O Caddy, como está configurado, **não registra acessos** (não há diretiva de log no `Caddyfile`); o `caddy.log.age` traz só avisos e erros. A API registra erros, não cada requisição. Não existe log de acesso completo: diga isso na avaliação em vez de supor o que não ficou gravado.

2. **Cópia da trilha de auditoria**, que só cresce e é a melhor fonte de "quem fez o quê":

```bash
$C exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -t log_auditoria' | age -r {chave-publica-do-dono} > trilha.dump.age
```

3. **Cópia do estado dos usuários e das sessões**, para a avaliação (seção 5):

```bash
$C exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -t usuario -t refresh_token -t convite_usado' | age -r {chave-publica-do-dono} > acesso.dump.age
```

4. **Um dump novo e cifrado do banco inteiro**, com o mesmo mecanismo do backup (`$C exec backup /app/backup.sh`), e a data de cada dump já existente no bucket. Não apague os dumps antigos durante a investigação: a retenção só apaga o que passa de 30 diários e 12 mensais, mas não rode limpezas manuais agora.
5. **Instantâneo da VM**, se o console Oracle oferecer (**confirmar no site**), antes de qualquer reconstrução.
6. Os arquivos `api.log.age`, `caddy.log.age`, `trilha.dump.age` e `acesso.dump.age` já nascem cifrados: a evidência passa pelo `age` com a chave pública do dono (`-T` no `exec` mantém a saída binária intacta) e **nunca é gravada em claro no disco da VM comprometida**. Baixe os arquivos `.age` para o seu computador e, de lá, apague-os da VM.

## 4. Avaliação: o que vazou

Responda por escrito, com data, o que se sabe e o que **não** se sabe. "Não sei" é resposta válida; não preencha com suposição.

| Pergunta | Como investigar |
|---|---|
| Qual foi a porta de entrada? | credencial vazada, convite vazado, `JWT_SECRET`, chave S3, acesso à VM, falha no código |
| Desde quando? | primeiro sinal nos logs, na trilha e em `usuario`/`convite_usado`; compare com a data do último deploy |
| Que contas foram tocadas? | `log_auditoria` por usuário e por intervalo; contas criadas sem convite seu |
| Que dados dessas contas? | a trilha mostra criação, leitura individual, alteração e exclusão; **listagem não é auditada por item**, então leitura em lista não aparece |
| Houve acesso ao banco inteiro? | só se a senha do banco, a VM ou os dumps (com a chave privada) foram expostos |
| Houve dump lido? | sem a chave privada, não; com ela, sim, para todos os dumps do bucket |
| Dado ficou legível ou cifrado? | o dado no banco **não** é cifrado em repouso (só os backups são); senha de login é BCrypt, e refresh token é guardado como hash |
| Quantas pessoas, de que tipo? | psicólogos (titulares do cadastro) e pacientes deles; dado de saúde é categoria sensível |
| Está contido? | os itens da seção 2 feitos, com hora |

Limites conhecidos desta avaliação, para constar do registro:

1. A trilha cobre as ações feitas **pela aplicação**; acesso direto ao banco, à VM ou ao bucket não aparece nela.
2. Não há log de acesso HTTP completo (seção 3).
3. A trilha mostra que registro foi lido, não o conteúdo que saiu.

Escreva a avaliação num documento privado do dono, fora do repositório. Ele alimenta a seção 6.

## 5. Depois de contido

1. Causa raiz identificada e corrigida (código, configuração ou processo), com revisão humana antes de qualquer deploy.
2. Segredos trocados (os afetados, ou todos na dúvida).
3. Chave `age` nova se a antiga foi exposta; um backup novo executado e conferido em `/api/saude`.
4. Restauração de teste (`restauracao.md`), se houve qualquer chance de corrupção ou alteração do banco.
5. Registro do incidente: o que houve, quando, o que se fez, o que mudou. Sem dado pessoal.
6. Item de aprendizado: o que teria evitado ou detectado antes.

## 6. Comunicação à ANPD e aos titulares

**Esta seção não foi validada por advogado.** A empresa e o dono precisam definir, com orientação jurídica, quem é **controlador** e quem é **operador** neste produto (o psicólogo costuma ser o controlador do prontuário dos pacientes dele, e este sistema o operador; o contrato de operador está no `portao.md`). Isso muda **quem comunica a quem**. **[verificar com advogado]**

O que a parte técnica entrega ao advogado, em até um dia depois da contenção, como entrada da decisão:

1. A linha do tempo da seção 4.
2. A lista de categorias de dados e de titulares envolvidos, e o número aproximado.
3. As medidas de contenção e de segurança já aplicadas.
4. O que **não** se sabe.

O que precisa ser decidido por advogado, sem que este documento afirme a resposta:

| Ponto | Situação |
|---|---|
| O incidente é de comunicação obrigatória à ANPD? (risco ou dano relevante aos titulares) | **[verificar com advogado]** |
| Prazo para comunicar à ANPD e aos titulares, e de quando ele conta | **[verificar com advogado]** |
| Quem comunica: o controlador (psicólogo), o operador (este sistema), ou ambos | **[verificar com advogado]** |
| Conteúdo mínimo e canal da comunicação à ANPD | **[verificar com advogado]** |
| Texto da comunicação aos titulares (pacientes e psicólogos) | **[verificar com advogado]** |
| Dever de comunicar os conselhos profissionais ou outras autoridades | **[verificar com advogado]** |
| Prazo de guarda do registro do incidente | **[verificar com advogado]** |

Referência a confirmar: a **Resolução CD/ANPD nº 15/2024** (regulamento de comunicação de incidente de segurança) e a Lei Geral de Proteção de Dados (Lei nº 13.709/2018). **Confirmar** o número, a vigência, o texto atual e os prazos na fonte primária (ANPD e Planalto). Este documento cita a resolução apenas como o ponto de partida da busca; não reproduz nem resume os prazos dela.

Enquanto o advogado não validar esta seção, o item "Plano de incidente" do `portao.md` continua `aberto`.

## Contatos e papéis

Preencher fora do repositório, em documento privado do dono: quem decide, quem é o advogado, quem fala com os psicólogos, e onde ficam guardadas a chave privada do `age` e as credenciais do console Oracle. Este arquivo não guarda nome, telefone, e-mail nem endereço.
