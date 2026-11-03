-- V7 : notifications (webhooks) reçues des fournisseurs de paiement

CREATE TABLE webhook_event (
    id                 bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_code      varchar(30)  NOT NULL,
    event_id           varchar(100) NOT NULL,
    received_at        timestamptz  NOT NULL DEFAULT now(),
    provider_reference varchar(100),
    merchant_reference varchar(100),
    outcome            varchar(20)  NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'PENDING', 'CANCELLED')),
    provider_status    varchar(50)  NOT NULL,
    -- corps reçu, vérifié par signature ; jamais de numéro de carte (la passerelle ne transmet qu'un PAN masqué)
    payload            jsonb        NOT NULL,
    processed_at       timestamptz,
    -- Anti-rejeu : un même événement n'est traité qu'une fois, même s'il est renvoyé.
    CONSTRAINT webhook_event_uk UNIQUE (provider_code, event_id)
);

CREATE INDEX webhook_event_merchant_reference_idx ON webhook_event (merchant_reference);
