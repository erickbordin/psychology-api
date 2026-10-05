#!/usr/bin/env bash
set -euo pipefail

arquivo_de_estado=/estado/backup.estado
etapa=preparo
trabalho="$(mktemp -d)"
remoto="destino:${BACKUP_S3_BUCKET}/teste/psychology-teste-$(date +%s).dump.age"
enviado=0
porta=5433
soquetes="$trabalho/soquete"
dados="$trabalho/dados"

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

limpar() {
    local codigo=$?
    pg_ctl -D "$dados" -m immediate stop > /dev/null 2>&1 || true
    if [ "$enviado" -eq 1 ]; then
        rclone deletefile "$remoto" > /dev/null 2>&1 || true
    fi
    rm -rf "$trabalho"
    local agora atual
    agora="$(date +%Y-%m-%dT%H:%M:%S)"
    if [ "$codigo" -eq 0 ]; then
        estado_gravar ultimo_teste "$agora" ultimo_teste_ok true || true
        if [[ "$(falha_atual)" == teste-* ]]; then
            estado_gravar falha "" falha_em "" || true
        fi
    else
        estado_gravar ultimo_teste "$agora" ultimo_teste_ok false || true
        atual="$(falha_atual)"
        if [ -z "$atual" ] || [[ "$atual" == teste-* ]]; then
            estado_gravar falha "teste-$etapa" falha_em "$agora" || true
        fi
    fi
    exit "$codigo"
}
trap limpar EXIT

age-keygen -o "$trabalho/chave" > /dev/null 2>&1
publica="$(age-keygen -y "$trabalho/chave")"

etapa=dump
coproc SESSAO { psql -qAt -v ON_ERROR_STOP=1; }
echo "BEGIN ISOLATION LEVEL REPEATABLE READ;" >&"${SESSAO[1]}"
echo "SELECT pg_export_snapshot();" >&"${SESSAO[1]}"
read -r snapshot <&"${SESSAO[0]}"
echo "SELECT count(*) FROM usuario;" >&"${SESSAO[1]}"
read -r usuarios_vivos <&"${SESSAO[0]}"
echo "SELECT count(*) FROM paciente;" >&"${SESSAO[1]}"
read -r pacientes_vivos <&"${SESSAO[0]}"
pg_dump -Fc --snapshot="$snapshot" | age -r "$publica" > "$trabalho/enviado.age"
echo "ROLLBACK;" >&"${SESSAO[1]}"
echo '\q' >&"${SESSAO[1]}"
wait "$SESSAO_PID" || true

etapa=envio
rclone copyto "$trabalho/enviado.age" "$remoto"
enviado=1

etapa=download
rclone copyto "$remoto" "$trabalho/baixado.age"

etapa=restauracao
mkdir -p "$soquetes"
initdb -D "$dados" -U postgres --auth=trust > /dev/null
pg_ctl -D "$dados" -w -l "$trabalho/pg.log" -o "-p $porta -c listen_addresses= -c unix_socket_directories=$soquetes" start > /dev/null
export_restauro=(env PGHOST="$soquetes" PGPORT="$porta" PGUSER=postgres PGPASSWORD= PGDATABASE=postgres)
"${export_restauro[@]}" createdb restauro
age -d -i "$trabalho/chave" "$trabalho/baixado.age" | "${export_restauro[@]}" pg_restore --no-acl --no-owner -d restauro

etapa=conferencia
consulta='SELECT max(version) FROM flyway_schema_history'
versao_viva="$(psql -Atc "$consulta")"
versao_restaurada="$("${export_restauro[@]}" psql -d restauro -Atc "$consulta")"
if [ -z "$versao_viva" ] || [ "$versao_viva" != "$versao_restaurada" ]; then
    echo "versao do flyway diferente" >&2
    exit 1
fi
for tabela in usuario paciente consulta anotacao; do
    existe="$("${export_restauro[@]}" psql -d restauro -Atc "SELECT to_regclass('public.${tabela}') IS NOT NULL")"
    if [ "$existe" != "t" ]; then
        echo "tabela ausente: $tabela" >&2
        exit 1
    fi
done
for tabela in usuario paciente; do
    restaurados="$("${export_restauro[@]}" psql -d restauro -Atc "SELECT count(*) FROM ${tabela}")"
    case "$tabela" in
        usuario) vivos="$usuarios_vivos" ;;
        paciente) vivos="$pacientes_vivos" ;;
    esac
    if [ "$vivos" != "$restaurados" ]; then
        echo "contagem de $tabela diferente: vivo=$vivos restaurado=$restaurados" >&2
        exit 1
    fi
done

etapa=limpeza
rclone deletefile "$remoto"
enviado=0
