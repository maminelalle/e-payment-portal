-- V5 : tentatives et preuves de signature électronique (voir docs/02-modele-donnees.md)

CREATE TABLE signature (
    id                     uuid         PRIMARY KEY,
    contract_id            uuid         NOT NULL REFERENCES contract (id),
    signer_user_id         uuid         NOT NULL REFERENCES app_user (id),
    method                 varchar(20)  NOT NULL CHECK (method IN ('OTP_EMAIL', 'OTP_SMS', 'EXTERNAL')),
    -- empreinte HMAC du code : le code lui-même n'est jamais stocké
    otp_hash               varchar(100) NOT NULL,
    otp_expires_at         timestamptz  NOT NULL,
    otp_attempts           smallint     NOT NULL DEFAULT 0 CHECK (otp_attempts BETWEEN 0 AND 3),
    otp_sent_count         smallint     NOT NULL DEFAULT 1,
    otp_last_sent_at       timestamptz  NOT NULL,
    status                 varchar(20)  NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'SIGNED', 'FAILED', 'EXPIRED', 'CANCELLED')),
    document_sha256        char(64)     NOT NULL,
    signed_document_sha256 char(64),
    signed_at              timestamptz,
    ip_address             inet,
    user_agent             varchar(300),
    created_at             timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT signature_signed_chk CHECK (status <> 'SIGNED'
        OR (signed_at IS NOT NULL AND signed_document_sha256 IS NOT NULL))
);

-- Une seule tentative en cours par contrat ; une seule signature réussie par contrat.
CREATE UNIQUE INDEX signature_one_pending_per_contract ON signature (contract_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX signature_one_signed_per_contract ON signature (contract_id) WHERE status = 'SIGNED';
CREATE INDEX signature_contract_idx ON signature (contract_id);
