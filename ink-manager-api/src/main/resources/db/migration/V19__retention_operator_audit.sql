ALTER TABLE retencoes_legais_financeiras_eventos
    ADD COLUMN ator_ref VARCHAR(80);

UPDATE retencoes_legais_financeiras_eventos
    SET ator_ref = 'USUARIO:' || CAST(ator_usuario_id AS VARCHAR);

ALTER TABLE retencoes_legais_financeiras_eventos
    ALTER COLUMN ator_ref SET NOT NULL;

ALTER TABLE retencoes_legais_financeiras_eventos
    ALTER COLUMN ator_usuario_id DROP NOT NULL;
