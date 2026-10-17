ALTER TABLE estudios ADD COLUMN criado_em TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE estudios ADD COLUMN pago_ate TIMESTAMP(6);
CREATE TABLE assinatura_checkouts (id VARCHAR(36) PRIMARY KEY, estudio_id BIGINT NOT NULL REFERENCES estudios(id), metodo VARCHAR(10) NOT NULL CHECK(metodo IN ('CARTAO','PIX')), valor NUMERIC(12,2) NOT NULL CHECK(valor>0), provider_id VARCHAR(100), checkout_url VARCHAR(1000), status VARCHAR(30) NOT NULL DEFAULT 'pending', criado_em TIMESTAMP(6) NOT NULL, UNIQUE(id,estudio_id));
CREATE INDEX idx_checkout_estudio ON assinatura_checkouts(estudio_id,criado_em);
CREATE TABLE assinatura_pagamentos (id VARCHAR(100) PRIMARY KEY, checkout_id VARCHAR(36) NOT NULL, estudio_id BIGINT NOT NULL REFERENCES estudios(id), aprovado BOOLEAN NOT NULL DEFAULT FALSE, fim TIMESTAMP(6), FOREIGN KEY(checkout_id,estudio_id) REFERENCES assinatura_checkouts(id,estudio_id));
CREATE INDEX idx_pagamento_estudio ON assinatura_pagamentos(estudio_id,aprovado,fim);
