CREATE TABLE recuperacoes_senha (token_hash VARCHAR(64) PRIMARY KEY, usuario_id BIGINT NOT NULL REFERENCES usuarios(id), prazo TIMESTAMP(6) NOT NULL, consumido BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0);
CREATE INDEX idx_recuperacao_usuario ON recuperacoes_senha(usuario_id);
