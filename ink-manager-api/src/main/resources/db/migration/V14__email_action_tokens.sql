CREATE TABLE email_action_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    estudio_id BIGINT NOT NULL REFERENCES estudios(id),
    marcacao_id BIGINT NOT NULL,
    acao VARCHAR(16) NOT NULL CHECK (acao IN ('CONFIRMAR','REAGENDAR','CANCELAR')),
    criado_em TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    usado_em TIMESTAMP(6) WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_email_action_marcacao_tenant FOREIGN KEY (marcacao_id, estudio_id) REFERENCES marcacoes(id, estudio_id)
);
CREATE INDEX idx_email_action_expira ON email_action_tokens(estudio_id, marcacao_id, expira_em);
