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
    -- valor padrao que a consulta COPIA no agendamento (5.3). Nulo ate o
    -- psicologo configurar
    valor_padrao_sessao_usuario NUMERIC(10,2),
    antecedencia_aviso_usuario VARCHAR(20) NOT NULL DEFAULT 'H24',
    -- desligado por padrao: ligado, o remetente revela que a mensagem e de um
    -- psicologo, e quem vir a caixa de entrada do paciente descobre que ele
    -- faz terapia (7.1). E escolha dele, ciente do que expoe
    titulo_no_email_usuario BOOLEAN NOT NULL DEFAULT false,
    created_at_usuario TIMESTAMP NOT NULL
);

-- QUAIS frases opcionais do aviso estao ligadas — a escolha, nunca o texto
-- (5.5). Guardar o texto congelaria a versao velha em cada aviso; guardando a
-- escolha, corrigir uma frase mal escrita conserta os avisos que ainda nao
-- sairam. Tabela filha, e nao coluna com virgulas, para que acrescentar um
-- bloco novo nao exija parsing
CREATE TABLE usuario_bloco_aviso (
    id_usuario UUID NOT NULL,
    bloco VARCHAR(30) NOT NULL,

    PRIMARY KEY (id_usuario, bloco),

    CONSTRAINT fk_bloco_aviso_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario)
);

CREATE TABLE paciente (
    id_paciente UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,

    nm_paciente VARCHAR(100) NOT NULL,
    telefone_paciente VARCHAR(20),
    email_paciente VARCHAR(100),
    dt_nascimento_paciente DATE,
    -- valor padrao desta pessoa; a consulta COPIA no agendamento (5.3), nao
    -- referencia. Nulo = usa o valor padrao do usuario (5.5)
    valor_sessao_paciente NUMERIC(10,2),
    -- consentimento do aviso: data em que o psicologo declarou ter obtido.
    -- Nulo = sem consentimento, e entao nenhum aviso sai para esta pessoa
    consentimento_aviso_em_paciente TIMESTAMP,
    canal_aviso_paciente VARCHAR(20) NOT NULL DEFAULT 'NENHUM',
    -- opaco, sem relacao com o id: link de e-mail vaza em encaminhamento e em
    -- log de proxy (7.1). Estavel por paciente, sobrevive a remarcacao, e
    -- revogar consentimento NAO o troca — link antigo continua funcionando
    token_descadastro_paciente VARCHAR(64) NOT NULL UNIQUE,
    created_at_paciente TIMESTAMP NOT NULL,
    -- exclusao reversivel: o registro sai das consultas da aplicacao, mas
    -- continua no banco e na trilha de auditoria
    deleted_at_paciente TIMESTAMP,

    CONSTRAINT fk_paciente_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario),

    -- chave candidata que permite a FK composta de consulta amarrar dono e
    -- paciente na mesma linha (C1 do laudo da revisao do conjunto)
    CONSTRAINT uq_paciente_usuario
        UNIQUE (id_paciente, id_usuario)
);

CREATE INDEX idx_paciente_usuario ON paciente (id_usuario);

CREATE TABLE consulta (
    id_consulta UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    id_paciente UUID NOT NULL,

    data_hora_consulta TIMESTAMP NOT NULL,
    status_consulta VARCHAR(20) NOT NULL,
    -- COPIA do valor vigente no agendamento, nao referencia ao paciente:
    -- reajustar a sessao de 150 para 200 nao pode reescrever o historico
    -- faturado (5.3). Nulo quando a consulta foi agendada sem valor definido,
    -- nem no paciente nem no padrao do usuario
    valor_consulta NUMERIC(10,2),
    -- data da baixa. Nulo NAO significa "a receber" sozinho: so significa
    -- isso quando status_consulta = 'REALIZADA', porque falta nao e cobrada
    -- (5.3, decisao do dono). Agregacao que filtre so por esta coluna conta
    -- falta como divida
    pago_em_consulta TIMESTAMP,
    -- serie semanal: cada ocorrencia e uma consulta independente, e a serie e
    -- so o vinculo entre elas. Nulo em consulta avulsa
    id_serie UUID,
    created_at_consulta TIMESTAMP NOT NULL,
    deleted_at_consulta TIMESTAMP,

    -- FK composta, nao duas simples: trava que o id_paciente informado
    -- pertence de fato ao id_usuario informado. Duas FKs simples deixam as
    -- duas colunas divergir (dono de um, paciente de outro) sem o banco
    -- reclamar (C1 do laudo da revisao do conjunto)
    CONSTRAINT fk_consulta_paciente
        FOREIGN KEY (id_paciente, id_usuario)
        REFERENCES paciente(id_paciente, id_usuario),

    -- chave candidata que permite a FK composta de anotacao amarrar
    -- consulta e paciente na mesma linha
    CONSTRAINT uq_consulta_paciente
        UNIQUE (id_consulta, id_paciente)
);

CREATE INDEX idx_consulta_serie ON consulta (id_serie);

-- a consulta mais executada do produto: agenda de um psicologo num
-- intervalo (Hoje, Agenda, Financeiro, checagem de conflito). Sem este
-- indice e varredura sequencial da tabela inteira (I1 do laudo)
CREATE INDEX idx_consulta_usuario_data ON consulta (id_usuario, data_hora_consulta);

CREATE TABLE anotacao (
    id_anotacao UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    id_paciente UUID NOT NULL,
    -- a anotacao passa a ser DAQUELA SESSAO, nao do paciente em geral (5.1).
    -- Anulavel de proposito: anotacao avulsa ("liguei fora do horario")
    -- continua valida, so nao entra na linha do tempo da ficha
    id_consulta UUID,

    anotacao TEXT NOT NULL,
    created_at_anotacao TIMESTAMP NOT NULL,
    deleted_at_anotacao TIMESTAMP,

    CONSTRAINT fk_anotacao_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario),

    -- FK simples: garante que id_paciente sempre existe, inclusive na
    -- anotacao avulsa (id_consulta nulo), que e o caso normal, nao borda
    -- (5.1). Precisa conviver com a composta abaixo, nao substitui-la:
    -- MATCH SIMPLE (padrao do Postgres) nao checa a FK composta quando
    -- id_consulta e nulo, entao sem esta simples aqui uma anotacao avulsa
    -- apontando para paciente inexistente seria aceita (regressao pega na
    -- re-revisao de 2026-09-05, com INSERT real contra Postgres)
    CONSTRAINT fk_anotacao_paciente
        FOREIGN KEY (id_paciente)
        REFERENCES paciente(id_paciente),

    -- FK composta, alem da simples acima: trava que a consulta referenciada
    -- e DAQUELE paciente, nao de outro paciente do mesmo psicologo. Com FK
    -- simples so por id_consulta, nada impede uma anotacao do paciente A
    -- apontar para uma sessao do paciente B (C1 do laudo da revisao do
    -- conjunto). So se aplica quando id_consulta nao e nulo (MATCH SIMPLE);
    -- a anotacao avulsa (5.1) continua valendo por nao cair nesta FK, mas
    -- ainda tem id_paciente validado pela FK simples acima
    CONSTRAINT fk_anotacao_consulta
        FOREIGN KEY (id_consulta, id_paciente)
        REFERENCES consulta(id_consulta, id_paciente)
);

CREATE INDEX idx_anotacao_consulta ON anotacao (id_consulta);
CREATE INDEX idx_anotacao_paciente ON anotacao (id_paciente);

-- Pendencia clinica do paciente: sem data, sem envio, sem destinatario. E o
-- que a antiga tabela "lembrete" de fato era. O aviso agendado — a outra
-- metade do que "lembrete" tentava ser — e a tabela aviso_de_consulta (5.2)
CREATE TABLE pendencia (
    id_pendencia UUID PRIMARY KEY,
    id_paciente UUID NOT NULL,

    descricao TEXT NOT NULL,
    concluido BOOLEAN NOT NULL DEFAULT false,
    created_at_pendencia TIMESTAMP NOT NULL,
    deleted_at_pendencia TIMESTAMP,

    CONSTRAINT fk_pendencia_paciente
        FOREIGN KEY (id_paciente)
        REFERENCES paciente(id_paciente)
);

CREATE INDEX idx_pendencia_paciente ON pendencia (id_paciente);

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

-- Mensagem agendada, presa a uma Consulta (5.2). A outra metade do que a
-- antiga tabela "lembrete" tentava ser.
--
-- NAO TEM COLUNA DE CORPO, de proposito: a mensagem e derivada da Consulta no
-- envio, a partir dos blocos fixos que o psicologo escolheu (5.5). Guardar
-- corpo aqui abriria a porta para texto arbitrario chegar ao paciente, o que
-- a 7.1 proibe — foi a unica trava de escopo do prototipo
CREATE TABLE aviso_de_consulta (
    id_aviso UUID PRIMARY KEY,
    id_consulta UUID NOT NULL,

    destinatario_aviso VARCHAR(20) NOT NULL,
    canal_aviso VARCHAR(20) NOT NULL,
    agendado_para_aviso TIMESTAMP NOT NULL,
    enviado_em_aviso TIMESTAMP,
    estado_aviso VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    -- motivo da falha, quando estado = FALHOU. Diagnostico, nunca conteudo
    erro_aviso TEXT,

    CONSTRAINT fk_aviso_consulta
        FOREIGN KEY (id_consulta)
        REFERENCES consulta(id_consulta),

    -- canal_aviso reaproveita o enum Java CanalAviso (Task 1), que tem um
    -- terceiro valor, NENHUM, usado por paciente.canal_aviso_paciente para
    -- dizer "esta pessoa nao quer aviso". Aqui nao faz sentido: um
    -- aviso_de_consulta e uma mensagem que precisa sair por algum canal, e a
    -- 5.2 so preve EMAIL ou WHATSAPP. O CHECK fecha essa porta no banco, sem
    -- precisar de um enum novo so pra este caso
    CONSTRAINT chk_aviso_canal
        CHECK (canal_aviso IN ('EMAIL', 'WHATSAPP'))
);

-- o job varre exatamente por este par (5.7)
CREATE INDEX idx_aviso_varredura ON aviso_de_consulta (estado_aviso, agendado_para_aviso);
