CREATE TABLE log_auditoria (
    id_log UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    acao VARCHAR(20) NOT NULL,
    entidade VARCHAR(50) NOT NULL,
    id_entidade UUID NOT NULL,
    created_at_log TIMESTAMP NOT NULL,

    CONSTRAINT fk_log_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario)
);

CREATE INDEX idx_log_usuario ON log_auditoria (id_usuario);
CREATE INDEX idx_log_entidade ON log_auditoria (entidade, id_entidade);