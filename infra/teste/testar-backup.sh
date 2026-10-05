#!/usr/bin/env bash
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
trabalho="$(mktemp -d)"
projeto="psy-teste-backup"
compose=(docker compose -p "$projeto" -f "$raiz/infra/teste/compose.teste.yml")

limpar() {
    "${compose[@]}" down -v --remove-orphans > /dev/null 2>&1 || true
    rm -rf "$trabalho"
}
trap limpar EXIT

falhar() {
    echo "FALHOU: $1" >&2
    exit 1
}

age-keygen -o "$trabalho/chave" > /dev/null 2>&1 || {
    docker run --rm alpine:3.20 sh -c 'apk add --no-cache age > /dev/null && age-keygen 2> /dev/null' > "$trabalho/chave"
}
BACKUP_CHAVE_PUBLICA="$(docker run --rm -i alpine:3.20 sh -c 'apk add --no-cache age > /dev/null && age-keygen -y' < "$trabalho/chave")"
SENHA_TESTE="$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
CHAVE_TESTE="chave$(head -c 6 /dev/urandom | od -An -tx1 | tr -d ' \n')"
export BACKUP_CHAVE_PUBLICA SENHA_TESTE CHAVE_TESTE

"${compose[@]}" up -d --build

no_backup() {
    "${compose[@]}" exec -T backup "$@"
}

for tentativa in $(seq 1 30); do
    if "${compose[@]}" exec -T -e RCLONE_CONFIG_DESTINO_NO_CHECK_BUCKET=false backup rclone mkdir destino:teste > /dev/null 2>&1; then
        break
    fi
    [ "$tentativa" -lt 30 ] || falhar "s3 nao respondeu"
    sleep 2
done

no_backup psql -q <<'SQL'
CREATE TABLE flyway_schema_history (installed_rank int, version varchar(50), success boolean);
INSERT INTO flyway_schema_history VALUES (1, '1', true), (2, '3', true);
CREATE TABLE usuario (id int PRIMARY KEY, nome text);
CREATE TABLE paciente (id int PRIMARY KEY, nome text);
CREATE TABLE consulta (id int PRIMARY KEY);
CREATE TABLE anotacao (id int PRIMARY KEY);
INSERT INTO usuario VALUES (1, 'Cliente_001');
SQL

no_backup /app/backup.sh
no_backup /app/testar-restauracao.sh

estado="$(no_backup cat /estado/backup.estado)"
echo "$estado" | grep -q '^falha=$' || falhar "falha nao esta vazia apos sucesso"
echo "$estado" | grep -q '^ultimo_teste_ok=true$' || falhar "ultimo_teste_ok diferente de true"
echo "$estado" | grep -q '^ultimo_dump_ok=.' || falhar "ultimo_dump_ok ausente"

em_claro="$(no_backup sh -c "find /backups /estado /tmp -name '*.dump' -o -name 'tmp.*' -o -name '*.parcial' | head -5")"
[ -z "$em_claro" ] || falhar "sobrou arquivo temporario ou em claro: $em_claro"
cifrado="$(no_backup sh -c "ls /backups | grep -c '\.dump\.age$'")"
[ "$cifrado" = "1" ] || falhar "esperava um dump cifrado local, achei $cifrado"
restos="$(no_backup rclone lsf destino:teste/teste)"
[ -z "$restos" ] || falhar "sobrou arquivo de teste no bucket"
no_backup rclone lsf destino:teste | grep -q 'dump.age$' || falhar "dump nao chegou ao bucket"
echo "rodada 1 ok: sucesso"

hoje="$(date +%F)"
{
    for dias in $(seq 0 39); do
        date -d "$hoje - $dias days" +%F
    done
    for meses in $(seq 0 14); do
        date -d "$(date +%Y-%m-01) - $meses months" +%F
    done
} | sort -u | sed 's/.*/psychology-&.dump.age/' > "$trabalho/semeados"
semear="while read -r nome; do printf x | rclone rcat \"destino:teste/\$nome\"; done"
no_backup bash -c "$semear" < "$trabalho/semeados"
no_backup bash -c 'printf x | rclone rcat destino:teste/outro.txt'
limite="$(date -d '12 months ago' +%F)"
sort -r "$trabalho/semeados" | awk -v limite="$limite" '
    NR <= 30 { print; next }
    { data = substr($0, 12, 10) }
    substr(data, 9, 2) == "01" && data >= limite { print }
' | sort > "$trabalho/esperados"
echo outro.txt >> "$trabalho/esperados"
sort -o "$trabalho/esperados" "$trabalho/esperados"
no_backup /app/retencao.sh
restantes="$(no_backup rclone lsf destino:teste --files-only | sort)"
if [ "$restantes" != "$(cat "$trabalho/esperados")" ]; then
    diff <(echo "$restantes") "$trabalho/esperados" >&2 || true
    falhar "retencao deixou sobreviventes diferentes do esperado"
fi
echo "retencao ok: $(wc -l < "$trabalho/esperados") sobreviventes"

"${compose[@]}" stop s3 > /dev/null
if no_backup /app/backup.sh > /dev/null 2>&1; then
    falhar "backup deveria falhar com o storage parado"
fi
estado="$(no_backup cat /estado/backup.estado)"
echo "$estado" | grep -q '^falha=envio$' || falhar "esperava falha=envio, estado: $estado"
echo "$estado" | grep -q '^ultimo_dump_ok=.' || falhar "ultimo_dump_ok foi apagado"
echo "$estado" | grep -q '^falha_em=.' || falhar "falha_em ausente"
echo "rodada 2 ok: falha=envio"

"${compose[@]}" start s3 > /dev/null
for tentativa in $(seq 1 30); do
    if no_backup rclone lsd destino: > /dev/null 2>&1; then
        break
    fi
    [ "$tentativa" -lt 30 ] || falhar "s3 nao voltou"
    sleep 2
done
no_backup /app/testar-restauracao.sh
estado="$(no_backup cat /estado/backup.estado)"
echo "$estado" | grep -q '^falha=envio$' || falhar "teste com sucesso apagou falha do dump: $estado"
echo "$estado" | grep -q '^ultimo_teste_ok=true$' || falhar "ultimo_teste_ok diferente de true na rodada 3"
no_backup /app/backup.sh
estado="$(no_backup cat /estado/backup.estado)"
echo "$estado" | grep -q '^falha=$' || falhar "dump com sucesso nao limpou falha=envio"
echo "rodada 3 ok: falha de dump so some com dump"
