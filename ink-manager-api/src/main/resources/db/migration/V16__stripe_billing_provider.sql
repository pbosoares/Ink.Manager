ALTER TABLE assinatura_checkouts
    ADD COLUMN provedor VARCHAR(20) NOT NULL DEFAULT 'MERCADOPAGO'
        CHECK (provedor IN ('MERCADOPAGO','STRIPE'));

ALTER TABLE assinatura_checkouts ADD COLUMN subscription_id VARCHAR(100);

CREATE INDEX idx_checkout_provider_subscription
    ON assinatura_checkouts(provedor, subscription_id);
