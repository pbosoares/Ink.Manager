ALTER TABLE cliente ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE marcacoes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE marcacoes ADD COLUMN duracao_minutos INTEGER NOT NULL DEFAULT 60 CHECK (duracao_minutos BETWEEN 1 AND 720);
CREATE INDEX idx_marcacoes_data ON marcacoes(data);
CREATE INDEX idx_marcacoes_cliente ON marcacoes(cliente_id);
CREATE TABLE agenda_lock (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO agenda_lock(id) VALUES (1);
