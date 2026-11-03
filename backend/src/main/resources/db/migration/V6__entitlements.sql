-- V6 : droits d'accès aux moyens de paiement, calculés à partir du contrat actif (moteur des droits)

CREATE TABLE subscriber_entitlement (
    id                    uuid          PRIMARY KEY,
    subscriber_id         uuid          NOT NULL REFERENCES subscriber (id),
    contract_id           uuid          NOT NULL REFERENCES contract (id),
    payment_method_id     uuid          NOT NULL REFERENCES payment_method (id),
    valid_from            date          NOT NULL,
    valid_to              date          NOT NULL,
    -- copie des plafonds du contrat (figé à la signature) pour contrôler chaque paiement sans relire le contrat
    per_transaction_limit numeric(15, 2) NOT NULL,
    monthly_limit         numeric(15, 2) NOT NULL,
    status                varchar(20)   NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED')),
    activated_at          timestamptz   NOT NULL DEFAULT now(),
    revoked_at            timestamptz,
    revoke_reason         varchar(300),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT entitlement_period_chk CHECK (valid_to >= valid_from),
    -- Idempotence : rejouer l'activation d'un contrat ne crée jamais de doublon.
    CONSTRAINT entitlement_contract_method_uk UNIQUE (contract_id, payment_method_id)
);

CREATE INDEX entitlement_subscriber_status_idx ON subscriber_entitlement (subscriber_id, status);

-- Verrou des tâches planifiées (ShedLock) : une seule instance exécute l'expiration quotidienne.
CREATE TABLE shedlock (
    name       varchar(64)  PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);
