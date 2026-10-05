#!/usr/bin/env bash
set -euo pipefail

arquivo_de_estado=/estado/backup.estado
etapa=dump
nome="psychology-$(date +%F).dump.age"
local_final="/backups/$nome"
local_parcial="$local_final.parcial"

estado_gravar() {
    local tmp chave
    tmp="$(mktemp /estado/.estado.XXXXXX)"
    if [ -f "$arquivo_de_estado" ]; then
        cp "$arquivo_de_estado" "$tmp"
    else
        : > "$tmp"
    fi
    while [ "$#" -ge 2 ]; do
        chave="$1"
        grep -v "^${chave}=" "$tmp" > "$tmp.novo" || true
        printf '%s=%s\n' "$chave" "$2" >> "$tmp.novo"
        mv "$tmp.novo" "$tmp"
        shift 2
    done
    chmod 644 "$tmp"
    mv "$tmp" "$arquivo_de_estado"
}

falha_atual() {
    grep '^falha=' "$arquivo_de_estado" 2>/dev/null | cut -d= -f2- || true
}

ao_sair() {
    local codigo=$?
    rm -f "$local_parcial"
    if [ "$codigo" -ne 0 ]; then
        estado_gravar falha "$etapa" falha_em "$(date +%Y-%m-%dT%H:%M:%S)" || true
    fi
    exit "$codigo"
}
trap ao_sair EXIT

pg_dump -Fc | age -r "$BACKUP_CHAVE_PUBLICA" > "$local_parcial"
mv "$local_parcial" "$local_final"

etapa=envio
rclone copyto "$local_final" "destino:${BACKUP_S3_BUCKET}/${nome}"

etapa=retencao
/app/retencao.sh

etapa=estado
agora="$(date +%Y-%m-%dT%H:%M:%S)"
if [[ "$(falha_atual)" == teste-* ]]; then
    estado_gravar ultimo_dump_ok "$agora"
else
    estado_gravar ultimo_dump_ok "$agora" falha "" falha_em ""
fi

find /backups -maxdepth 1 -name 'psychology-*.dump.age' -mtime +0 -delete
