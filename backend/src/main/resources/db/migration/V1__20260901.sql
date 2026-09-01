-- Schema inteiro numa migration so. As oito anteriores (V1..V8, de 30/07 a
-- 26/08) foram fundidas aqui antes do primeiro deploy: nenhum banco de producao
-- existia ainda, entao o historico de ALTERs nao servia a ninguem — so obrigava
-- quem chegasse a ler oito arquivos para saber o formato de uma tabela.
--
-- A partir do primeiro deploy isto deixa de ser possivel: migration aplicada em
-- producao nao se edita nem se apaga, e mudanca de schema passa a ser uma V2,
-- V3, ... novas.

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
    -- exclusao reversivel: o registro sai das consultas da aplicacao, mas
    -- continua no banco e na trilha de auditoria
    deleted_at_paciente TIMESTAMP,

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
    -- serie semanal: cada ocorrencia e uma consulta independente, e a serie e
    -- so o vinculo entre elas. Nulo em consulta avulsa
    id_serie UUID,
    created_at_consulta TIMESTAMP NOT NULL,
    deleted_at_consulta TIMESTAMP,

    CONSTRAINT fk_consulta_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario),

    CONSTRAINT fk_consulta_paciente
        FOREIGN KEY (id_paciente)
        REFERENCES paciente(id_paciente)
);

CREATE INDEX idx_consulta_serie ON consulta (id_serie);

CREATE TABLE anotacao (
    id_anotacao UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    id_paciente UUID NOT NULL,

    anotacao TEXT NOT NULL,
    created_at_anotacao TIMESTAMP NOT NULL,
    deleted_at_anotacao TIMESTAMP,

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
    concluido BOOLEAN NOT NULL DEFAULT false,
    created_at_lembrete TIMESTAMP NOT NULL,
    deleted_at_lembrete TIMESTAMP,

    CONSTRAINT fk_lembrete_paciente
        FOREIGN KEY (id_paciente)
        REFERENCES paciente(id_paciente)
);

-- A trilha so cresce: nada aqui tem coluna de exclusao, de proposito
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

CREATE TABLE refresh_token (
    id_refresh_token UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,

    -- so o hash: o token em claro nunca toca o banco
    hash_token VARCHAR(64) NOT NULL UNIQUE,
    created_at_refresh_token TIMESTAMP NOT NULL,
    expires_at_refresh_token TIMESTAMP NOT NULL,
    revoked_at_refresh_token TIMESTAMP,
    -- rotacao: aponta para o token que substituiu este. E o que permite
    -- detectar reapresentacao de um refresh ja usado e revogar a cadeia
    substituido_por UUID,

    CONSTRAINT fk_refresh_token_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario),

    CONSTRAINT fk_refresh_token_substituido
        FOREIGN KEY (substituido_por)
        REFERENCES refresh_token(id_refresh_token)
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (id_usuario);
