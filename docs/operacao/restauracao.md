# Restauração real do backup

Este roteiro prova que o backup serve para alguma coisa. A **restauração real executada uma vez, com data registrada em `docs/operacao/portao.md`, é critério de pronto** da Fatia 5A: backup nunca restaurado é só uma esperança.

Quem executa é o dono, no **próprio computador**, nunca na VM. A chave privada do `age` não pode tocar a VM: é isso que impede que o dump real seja decifrável lá.

O teste semanal que roda na VM (`testar-restauracao.sh`) é outra coisa: usa um dump efêmero e uma chave descartável. Ele prova o mecanismo, não o dump real nem a sua chave.

## O que você precisa

| Item | De onde vem |
|---|---|
| Chave privada `chave-backup.txt` | gerada no passo 8 de `implantacao.md`, guardada fora da VM |
| Acesso ao bucket | console Oracle ou uma chave S3 de leitura (**confirmar no site**) |
| Docker | no seu computador |
| `age` | pelo gerenciador de pacotes do seu computador |

## 1. Baixar um dump do bucket

Os arquivos têm o nome `psychology-AAAA-MM-DD.dump.age`. Escolha o mais recente.

Pelo console da Oracle (Object Storage, o bucket, o objeto, baixar; **confirmar no site** os nomes dos menus), ou com o `rclone` no seu computador, configurado por variáveis de ambiente do mesmo jeito que o serviço `backup` (destino S3 com `provider=Other`, o endpoint, a região e o par de chaves do passo 6 de `implantacao.md`):

```bash
rclone lsf --files-only destino:psychology-backup
rclone copyto destino:psychology-backup/psychology-2026-10-04.dump.age ./dump.age
```

O nome do bucket e a data acima são exemplos. **Confirmar no site** se o seu cliente S3 precisa de opções extras para o endpoint compatível da Oracle.

**Prova:** `ls -l dump.age` mostra um arquivo com tamanho maior que zero.

## 2. Subir um Postgres local descartável

```bash
docker run -d --name restauracao-pg -e POSTGRES_PASSWORD=troque-me -e POSTGRES_DB=restauro postgres:16-alpine
```

O `troque-me` é só para este container local. O container não publica porta nenhuma: todos os comandos usam `docker exec`, e o banco com dado real de paciente não fica acessível pela rede do seu computador. Espere ficar pronto:

```bash
docker exec restauracao-pg pg_isready -U postgres
```

**Prova:** a resposta termina em `accepting connections`.

## 3. Decifrar e restaurar

O dump decifrado vai **por pipe**, direto do `age` para o `pg_restore`. Nenhum arquivo em claro precisa ficar no disco.

```bash
age -d -i chave-backup.txt dump.age | docker exec -i restauracao-pg pg_restore -U postgres -d restauro --no-owner --no-acl
```

`--no-owner` e `--no-acl` são os mesmos flags do teste semanal: o dump veio de um usuário que não existe neste Postgres local. Avisos sobre `owner` ou `role` ausentes são esperados; erro de `age` (como `no identity matched any of the recipients`) significa que a chave não é a do par usado no backup.

**Prova:** o comando termina com código 0. Confira:

```bash
echo $?
```

## 4. Conferir contagens

Liste as tabelas e conte as linhas:

```bash
docker exec restauracao-pg psql -U postgres -d restauro -c '\dt'
docker exec restauracao-pg psql -U postgres -d restauro -At -c "SELECT 'usuario', count(*) FROM usuario UNION ALL SELECT 'paciente', count(*) FROM paciente UNION ALL SELECT 'consulta', count(*) FROM consulta UNION ALL SELECT 'anotacao', count(*) FROM anotacao UNION ALL SELECT 'pendencia', count(*) FROM pendencia UNION ALL SELECT 'log_auditoria', count(*) FROM log_auditoria UNION ALL SELECT 'convite_usado', count(*) FROM convite_usado"
docker exec restauracao-pg psql -U postgres -d restauro -At -c 'SELECT max(version) FROM flyway_schema_history'
```

Compare com o banco vivo, **na VM**, no mesmo momento aproximado (o dump é de uma noite anterior, então diferenças pequenas e coerentes com o uso do dia são esperadas):

```bash
docker compose -f infra/compose.yml --env-file infra/.env exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "SELECT count(*) FROM usuario"'
```

O que conferir:

1. As tabelas do modelo existem: `usuario`, `paciente`, `consulta`, `anotacao`, `pendencia`, `log_auditoria`, `refresh_token`, `aviso_de_consulta`, `resumo_diario`, `convite_usado`, `usuario_bloco_aviso`.
2. A versão do Flyway restaurada é a mesma do banco vivo.
3. As contagens batem com o vivo, a menos do que mudou desde a madrugada do dump.
4. Uma amostra olhada por você faz sentido, **sem copiar nenhum dado de paciente para fora do seu computador** e sem colar dado em chat, nota ou ticket.

**Prova:** itens 1 a 3 conferidos e anotados.

## 5. Limpar

```bash
docker rm -f restauracao-pg
shred -u dump.age
```

(`shred` pode não existir em todos os sistemas; apague o arquivo de forma segura equivalente.) A chave privada **permanece** onde estava guardada.

## 6. Registrar

Em `docs/operacao/portao.md`, na linha "Restauração real executada e registrada", mude o `Estado` para `fechado` e preencha a coluna `Evidência` com:

- a data da execução;
- o nome do arquivo restaurado (a data do dump);
- o resultado: contagens conferidas, versão do Flyway igual, ou o que falhou.

Não escreva na evidência nenhum dado de paciente, nem e-mail, nem o endpoint ou as chaves do bucket.

Se a restauração falhou, o estado continua `aberto` e a evidência diz o que quebrou. Esse é um resultado válido: foi para isso que se testou.

## Quando dá errado

| Sintoma | Causa provável |
|---|---|
| `no identity matched any of the recipients` | a chave privada não é a do par cuja pública está em `BACKUP_CHAVE_PUBLICA` |
| `pg_restore: error: ... already exists` | o banco `restauro` não estava vazio; recrie o container |
| `pg_restore` reclama de versão do dump | o Postgres local é mais antigo que o 16 do servidor; use `postgres:16-alpine` |
| arquivo baixado com poucos bytes | o download falhou ou veio uma página de erro; baixe de novo e confira o tamanho no console |
| versão do Flyway diferente da do vivo | o dump é anterior a um deploy que rodou migration; compare com o dump mais novo |
