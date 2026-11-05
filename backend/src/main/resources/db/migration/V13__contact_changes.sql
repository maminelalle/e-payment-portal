-- Changement de coordonnées par l'abonné (conception §4.5) : appliqué seulement après le code reçu par email.
CREATE TABLE subscriber_contact_change (
    id              uuid         PRIMARY KEY,
    subscriber_id   uuid         NOT NULL REFERENCES subscriber (id),
    phone           varchar(20)  NOT NULL,
    address         varchar(300),
    -- HMAC du code, jamais le code lui-même
    code_hash       varchar(64)  NOT NULL,
    expires_at      timestamptz  NOT NULL,
    wrong_attempts  int          NOT NULL DEFAULT 0,
    status          varchar(20)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'BLOCKED')),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    confirmed_at    timestamptz
);

CREATE INDEX subscriber_contact_change_pending ON subscriber_contact_change (subscriber_id) WHERE status = 'PENDING';
