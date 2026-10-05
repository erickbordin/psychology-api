ALTER TABLE usuario ADD COLUMN termos_versao_usuario VARCHAR(20);
ALTER TABLE usuario ADD COLUMN termos_aceitos_em_usuario TIMESTAMP;

CREATE TABLE convite_usado (
    id_convite UUID PRIMARY KEY,
    hash_convite VARCHAR(64) NOT NULL UNIQUE,
    id_usuario UUID NOT NULL REFERENCES usuario(id_usuario),
    usado_em_convite TIMESTAMP NOT NULL
);
