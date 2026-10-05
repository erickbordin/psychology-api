FROM alpine:3.23
ARG TARGETARCH
ARG SUPERCRONIC_VERSION=v0.2.49
ARG SUPERCRONIC_SHA256_AMD64=a53ae236602c7338aba3fbaff40bda6300eae3b9fedb8261eb06cfe3724430c1
ARG SUPERCRONIC_SHA256_ARM64=02aa0cb229ba09050cba6638059dadb9eedc2276632ea43d6a57a2f8c1629dd5
RUN apk add --no-cache bash coreutils curl tzdata postgresql16 postgresql16-client age rclone
RUN set -eu; \
    case "$TARGETARCH" in \
      amd64) esperado="$SUPERCRONIC_SHA256_AMD64" ;; \
      arm64) esperado="$SUPERCRONIC_SHA256_ARM64" ;; \
      *) echo "arquitetura sem binario do supercronic: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    curl -fsSL -o /usr/local/bin/supercronic "https://github.com/aptible/supercronic/releases/download/${SUPERCRONIC_VERSION}/supercronic-linux-${TARGETARCH}"; \
    echo "${esperado}  /usr/local/bin/supercronic" | sha256sum -c -; \
    chmod +x /usr/local/bin/supercronic
RUN mkdir -p /app /backups /estado && chown postgres:postgres /backups /estado
COPY --chmod=755 backup.sh testar-restauracao.sh retencao.sh /app/
COPY crontab /app/crontab
USER postgres
CMD ["supercronic", "/app/crontab"]
