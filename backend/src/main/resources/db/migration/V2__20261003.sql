ALTER TABLE usuario ADD COLUMN titulo_profissional_usuario VARCHAR(80);
ALTER TABLE usuario ADD COLUMN resumo_diario_usuario BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE usuario ADD COLUMN expediente_inicio_usuario SMALLINT;
ALTER TABLE usuario ADD COLUMN expediente_fim_usuario SMALLINT;
ALTER TABLE usuario ADD CONSTRAINT chk_expediente_usuario CHECK (
    (expediente_inicio_usuario IS NULL AND expediente_fim_usuario IS NULL)
    OR (expediente_inicio_usuario BETWEEN 0 AND 23
        AND expediente_fim_usuario BETWEEN 1 AND 24
        AND expediente_inicio_usuario < expediente_fim_usuario)
);

ALTER TABLE paciente ADD COLUMN descadastrado_em_paciente TIMESTAMP;

ALTER TABLE aviso_de_consulta ADD COLUMN data_hora_consulta_aviso TIMESTAMP NOT NULL;
ALTER TABLE aviso_de_consulta ADD COLUMN tentativas_aviso SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE aviso_de_consulta ADD COLUMN proxima_tentativa_aviso TIMESTAMP;
ALTER TABLE aviso_de_consulta ADD CONSTRAINT uq_aviso_consulta_destinatario_horario
    UNIQUE (id_consulta, destinatario_aviso, data_hora_consulta_aviso);
CREATE INDEX idx_aviso_pendente ON aviso_de_consulta (estado_aviso, proxima_tentativa_aviso);

CREATE TABLE resumo_diario (
    id_resumo UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    data_resumo DATE NOT NULL,
    estado_resumo VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    tentativas_resumo SMALLINT NOT NULL DEFAULT 0,
    proxima_tentativa_resumo TIMESTAMP,
    enviado_em_resumo TIMESTAMP,
    erro_resumo TEXT,
    created_at_resumo TIMESTAMP NOT NULL,
    CONSTRAINT fk_resumo_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT uq_resumo_usuario_data UNIQUE (id_usuario, data_resumo)
);
CREATE INDEX idx_resumo_pendente ON resumo_diario (estado_resumo, proxima_tentativa_resumo);
