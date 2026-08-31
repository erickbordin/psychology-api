#!/usr/bin/env bash
set -e

# Carrega as variáveis do .env local (onde já está o JWT_SECRET)
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
else
    echo "Aviso: arquivo .env não encontrado na raiz do projeto."
fi

# Variáveis de banco para ambiente local
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/psychology-api
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres

mvn spring-boot:run