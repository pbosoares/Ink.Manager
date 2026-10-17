ALTER TABLE assinatura_pagamentos ADD COLUMN aprovado_em TIMESTAMP(6);
ALTER TABLE assinatura_pagamentos ADD COLUMN provider_updated_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE assinatura_checkouts ADD COLUMN provider_updated_at BIGINT NOT NULL DEFAULT 0;
