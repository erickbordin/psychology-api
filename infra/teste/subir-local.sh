#!/usr/bin/env bash
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
pasta="${TMPDIR:-/tmp}/psychology-subir-local-$(id -u)"
arquivo="$pasta/variaveis"
compose=(docker compose -p psy-local -f "$raiz/infra/compose.yml" -f "$raiz/infra/teste/compose.local.yml" --env-file "$arquivo")

if [ "${1:-}" = "--derrubar" ]; then
    if [ -f "$arquivo" ]; then
        "${compose[@]}" down -v --remove-orphans
    fi
    rm -rf "$pasta"
    echo "derrubado"
    exit 0
fi

segredo() {
    head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'
}

mkdir -p "$pasta"
chmod 700 "$pasta"
umask 077
cat > "$arquivo" <<EOF
DOMINIO=localhost
TZ=America/Sao_Paulo
POSTGRES_DB=psychology
POSTGRES_USER=psychology
POSTGRES_PASSWORD=$(segredo 16)
JWT_SECRET=$(head -c 48 /dev/urandom | base64 -w0)
APP_CADASTRO_CONVITES=${CONVITES_LOCAIS:-convite-local-1,convite-local-2,convite-local-3,convite-local-4,convite-local-5,convite-local-6,convite-local-7,convite-local-8}
APP_REFRESH_COOKIE_PATH=/api/auth
APP_REFRESH_COOKIE_SAMESITE=Strict
APP_REFRESH_COOKIE_SEGURO=true
APP_FRONTEND_URL=https://localhost
APP_CORS_ORIGINS=https://localhost
APP_AVISOS_HABILITADO=false
SPRINGDOC_ENABLED=false
BACKUP_CHAVE_PUBLICA=age1local
BACKUP_S3_ENDPOINT=http://localhost:9
BACKUP_S3_REGIAO=local
BACKUP_S3_BUCKET=local
BACKUP_S3_CHAVE=local
BACKUP_S3_SEGREDO=$(segredo 8)
EOF

"${compose[@]}" up -d --build

for tentativa in $(seq 1 60); do
    if curl -ks "https://localhost/api/saude" 2> /dev/null | grep -q '"status":"ok"'; then
        echo "no ar: https://localhost"
        exit 0
    fi
    sleep 3
done

echo "sem resposta de https://localhost/api/saude" >&2
exit 1
