#!/usr/bin/env bash
set -euo pipefail

limite_mensal="$(date -d '12 months ago' +%F)"
padrao='^psychology-[0-9]{4}-[0-9]{2}-[0-9]{2}\.dump\.age$'

listagem="$(rclone lsf --files-only "destino:${BACKUP_S3_BUCKET}")"

mapfile -t arquivos < <(printf '%s\n' "$listagem" | { grep -E "$padrao" || true; } | sort -r)

posicao=0
for arquivo in "${arquivos[@]}"; do
    posicao=$((posicao + 1))
    data="${arquivo#psychology-}"
    data="${data%.dump.age}"
    if [ "$posicao" -le 30 ]; then
        continue
    fi
    if [ "${data:8:2}" = "01" ] && [[ ! "$data" < "$limite_mensal" ]]; then
        continue
    fi
    rclone deletefile "destino:${BACKUP_S3_BUCKET}/${arquivo}"
done
