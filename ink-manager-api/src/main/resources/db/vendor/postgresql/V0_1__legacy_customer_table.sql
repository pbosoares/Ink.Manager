-- Preserve the original JDBC table when upgrading a legacy installation.
DO $$
BEGIN
    IF to_regclass(current_schema() || '.clientes') IS NOT NULL AND to_regclass(current_schema() || '.cliente') IS NOT NULL THEN
        RAISE EXCEPTION 'Both clientes and cliente exist: reconcile customer records and foreign keys before migration';
    END IF;
    IF to_regclass(current_schema() || '.clientes') IS NOT NULL AND to_regclass(current_schema() || '.cliente') IS NULL THEN
        ALTER TABLE clientes RENAME TO cliente;
    END IF;
END $$;

