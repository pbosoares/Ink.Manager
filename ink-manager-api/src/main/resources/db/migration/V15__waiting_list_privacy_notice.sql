ALTER TABLE lista_espera ADD COLUMN aviso_privacidade_versao VARCHAR(30);
ALTER TABLE lista_espera ADD COLUMN aviso_privacidade_lido_em TIMESTAMP(6) WITH TIME ZONE;

-- Existing entries predate this audit data. Keep both fields null rather than
-- inventing a consent version or timestamp for records that cannot prove it.
ALTER TABLE lista_espera ADD CONSTRAINT ck_lista_aviso_privacidade_par
    CHECK ((aviso_privacidade_versao IS NULL AND aviso_privacidade_lido_em IS NULL)
        OR (aviso_privacidade_versao IS NOT NULL AND aviso_privacidade_lido_em IS NOT NULL));
