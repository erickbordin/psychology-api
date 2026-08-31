CREATE TABLE usuario (
    id_usuario UUID PRIMARY KEY,
    nm_usuario VARCHAR(100) NOT NULL,
    email_usuario VARCHAR(100) NOT NULL UNIQUE,
    senha_usuario TEXT NOT NULL,
    created_at_usuario TIMESTAMP NOT NULL
);

CREATE TABLE paciente (
    id_paciente UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,

    nm_paciente VARCHAR(100) NOT NULL,
    telefone_paciente VARCHAR(20),
    email_paciente VARCHAR(100),
    dt_nascimento_paciente DATE,
    observacoes_paciente TEXT,
    created_at_paciente TIMESTAMP NOT NULL,

    CONSTRAINT fk_paciente_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario)
);

CREATE TABLE consulta (
    id_consulta UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    id_paciente UUID NOT NULL,

    data_hora_consulta TIMESTAMP NOT NULL,
    status_consulta VARCHAR(20) NOT NULL,
    created_at_consulta TIMESTAMP NOT NULL,

    CONSTRAINT fk_consulta_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario),

    CONSTRAINT fk_consulta_paciente
        FOREIGN KEY (id_paciente)
        REFERENCES paciente(id_paciente)
);

CREATE TABLE anotacao (
    id_anotacao UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    id_paciente UUID NOT NULL,

    anotacao TEXT NOT NULL,
    created_at_anotacao TIMESTAMP NOT NULL,

    CONSTRAINT fk_anotacao_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario),

    CONSTRAINT fk_anotacao_paciente
        FOREIGN KEY (id_paciente)
        REFERENCES paciente(id_paciente)
);

CREATE TABLE lembrete (
    id_lembrete UUID PRIMARY KEY,
    id_paciente UUID NOT NULL,

    descricao TEXT NOT NULL,
    created_at_lembrete TIMESTAMP NOT NULL,

    CONSTRAINT fk_lembrete_paciente
        FOREIGN KEY (id_paciente)
        REFERENCES paciente(id_paciente)
);