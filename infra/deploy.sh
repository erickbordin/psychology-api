#!/usr/bin/env bash
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$raiz"

dominio="$(grep '^DOMINIO=' infra/.env | cut -d= -f2- || true)"
if [ -z "$dominio" ]; then
    echo "DOMINIO vazio ou ausente em infra/.env" >&2
    exit 1
fi

git pull --ff-only

compose=(docker compose -f infra/compose.yml --env-file infra/.env)
"${compose[@]}" pull --ignore-buildable
"${compose[@]}" build --pull

if "${compose[@]}" ps --status running --services | grep -qx backup; then
    echo "backup antes de aplicar a nova versao"
    if ! "${compose[@]}" exec -T backup /app/backup.sh; then
        echo "backup falhou: deploy abortado, as migracoes nao rodam sem backup recente" >&2
        exit 1
    fi
else
    echo "primeiro deploy (container de backup fora do ar): sem backup previo"
fi

"${compose[@]}" up -d

resposta=""
for tentativa in $(seq 1 90); do
    resposta="$(curl -sS --max-time 5 -w '\n%{http_code}' "https://${dominio}/api/saude" 2>&1 || true)"
    if [[ "$resposta" == *'"status":"ok"'* ]]; then
        echo "no ar: https://${dominio}/api/saude -> ${resposta//$'\n'/ }"
        exit 0
    fi
    sleep 2
done

echo "sem resposta valida de https://${dominio}/api/saude depois de 90 tentativas" >&2
echo "ultima resposta: ${resposta//$'\n'/ }" >&2
exit 1
