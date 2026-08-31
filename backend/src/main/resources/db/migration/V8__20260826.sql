CREATE TABLE refresh_token (
    id_refresh_token          UUID        NOT NULL PRIMARY KEY,
    id_usuario                UUID        NOT NULL REFERENCES usuario (id_usuario),
    hash_token                VARCHAR(64) NOT NULL UNIQUE,
    created_at_refresh_token  TIMESTAMP   NOT NULL,
    expires_at_refresh_token  TIMESTAMP   NOT NULL,
    revoked_at_refresh_token  TIMESTAMP,
    substituido_por           UUID        REFERENCES refresh_token (id_refresh_token)
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (id_usuario);
