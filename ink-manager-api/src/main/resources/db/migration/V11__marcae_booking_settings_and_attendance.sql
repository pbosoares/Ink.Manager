ALTER TABLE usuarios ADD CONSTRAINT uq_usuario_estudio UNIQUE(id, estudio_id);
ALTER TABLE recebimentos ADD CONSTRAINT fk_recebimento_usuario_estudio
    FOREIGN KEY(confirmado_por, estudio_id) REFERENCES usuarios(id, estudio_id);

ALTER TABLE estudios ADD COLUMN fuso_horario VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo';
ALTER TABLE estudios ADD COLUMN antecedencia_minima_minutos INTEGER NOT NULL DEFAULT 120
    CHECK(antecedencia_minima_minutos BETWEEN 0 AND 43200);
ALTER TABLE estudios ADD COLUMN limite_agendamentos_email INTEGER NOT NULL DEFAULT 3
    CHECK(limite_agendamentos_email BETWEEN 1 AND 50);

ALTER TABLE cliente ADD COLUMN email VARCHAR(254);
ALTER TABLE marcacoes ADD COLUMN presenca VARCHAR(20)
    CHECK(presenca IS NULL OR presenca IN ('COMPARECEU','FALTOU'));
ALTER TABLE marcacoes ADD COLUMN aviso_privacidade_versao VARCHAR(30);
ALTER TABLE marcacoes ADD COLUMN aviso_privacidade_lido_em TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX idx_marcacoes_estudio_presenca_data ON marcacoes(estudio_id,data,presenca,status);
CREATE INDEX idx_cliente_estudio_email ON cliente(estudio_id,email);
