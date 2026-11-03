-- V9 : transactions de paiement (voir docs/02-modele-donnees.md, entité 11)

CREATE SEQUENCE transaction_ref_seq START 1;

CREATE TABLE payment_transaction (
    id                 uuid          PRIMARY KEY,
    reference          varchar(20)   NOT NULL UNIQUE,
    subscriber_id      uuid          NOT NULL REFERENCES subscriber (id),
    payment_method_id  uuid          NOT NULL REFERENCES payment_method (id),
    -- contrat qui a autorisé le paiement (moteur des droits)
    contract_id        uuid          NOT NULL REFERENCES contract (id),
    amount             numeric(15, 2) NOT NULL CHECK (amount > 0),
    currency           char(3)       NOT NULL,
    -- statut interne du portail, distinct du statut brut du fournisseur
    status             varchar(20)   NOT NULL CHECK (status IN
                       ('CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUNDED')),
    provider_status    varchar(50),
    provider_reference varchar(100),
    idempotency_key    varchar(100)  NOT NULL,
    payer_phone        varchar(20),
    -- carte : uniquement le PAN masqué renvoyé par la passerelle, jamais le numéro complet ni le CVV
    card_masked_pan    varchar(19)   CHECK (card_masked_pan IS NULL OR card_masked_pan ~ '^\d{6}\*{6}\d{4}$'),
    redirect_url       varchar(500),
    description        varchar(140),
    failure_code       varchar(50),
    failure_message    varchar(300),
    receipt_object_key varchar(300),
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    completed_at       timestamptz,
    version            bigint        NOT NULL DEFAULT 0,
    -- Idempotence : la même demande (même clé) ne crée jamais un second paiement.
    CONSTRAINT transaction_idempotency_uk UNIQUE (subscriber_id, idempotency_key),
    CONSTRAINT transaction_provider_reference_uk UNIQUE (payment_method_id, provider_reference)
);

CREATE INDEX transaction_subscriber_created_idx ON payment_transaction (subscriber_id, created_at DESC);
CREATE INDEX transaction_status_idx ON payment_transaction (status);
CREATE INDEX transaction_month_idx ON payment_transaction (subscriber_id, payment_method_id, created_at);
