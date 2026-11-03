-- V3 : catalogue des moyens de paiement et de leurs fournisseurs (voir docs/02-modele-donnees.md)

CREATE TABLE payment_provider (
    id                   uuid         PRIMARY KEY,
    code                 varchar(30)  NOT NULL UNIQUE,
    name                 varchar(100) NOT NULL,
    base_url             varchar(300) NOT NULL,
    -- référence vers le secret (clé de configuration), jamais la valeur
    credentials_ref      varchar(200) NOT NULL,
    status               varchar(20)  NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    health_status        varchar(20)  NOT NULL DEFAULT 'UNKNOWN' CHECK (health_status IN ('UP', 'DOWN', 'UNKNOWN')),
    last_health_check_at timestamptz
);

CREATE TABLE payment_method (
    id              uuid         PRIMARY KEY,
    code            varchar(30)  NOT NULL UNIQUE,
    name            varchar(100) NOT NULL,
    category        varchar(10)  NOT NULL CHECK (category IN ('WALLET', 'CARD')),
    provider_id     uuid         NOT NULL REFERENCES payment_provider (id),
    short_label     varchar(4)   NOT NULL,
    color           varchar(7)   NOT NULL CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    logo_object_key varchar(300),
    description     text,
    instructions    text,
    currency        char(3)      NOT NULL DEFAULT 'MRU',
    status          varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0
);

-- Fournisseurs : en développement, tous pointent vers le simulateur (docs/03-api-fournisseurs-simules.md).
INSERT INTO payment_provider (id, code, name, base_url, credentials_ref) VALUES
    ('7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0001', 'BANKILY',      'Bankily', 'http://localhost:8090/bankily/api/v1',      'epayment.providers.bankily'),
    ('7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0002', 'MASRVI',       'Masrvi', 'http://localhost:8090/masrvi/v2',           'epayment.providers.masrvi'),
    ('7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0003', 'VISA_GATEWAY', 'Passerelle bancaire',  'http://localhost:8090/visa-gateway/api/v1', 'epayment.providers.visa');

INSERT INTO payment_method (id, code, name, category, provider_id, short_label, color, description, instructions) VALUES
    ('3f0c2a10-5b7e-4c1d-8e2f-6a9b0c1d0001', 'BANKILY', 'Bankily', 'WALLET',
     '7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0001', 'BK', '#2563EB',
     'Wallet mobile',
     'Saisissez le numéro de téléphone associé à votre compte Bankily, puis confirmez le paiement dans l''application.'),
    ('3f0c2a10-5b7e-4c1d-8e2f-6a9b0c1d0002', 'MASRVI', 'Masrvi', 'WALLET',
     '7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0002', 'MS', '#0F766E',
     'Wallet mobile',
     'Saisissez le numéro de téléphone associé à votre compte Masrvi, puis confirmez le paiement dans l''application.'),
    ('3f0c2a10-5b7e-4c1d-8e2f-6a9b0c1d0003', 'VISA', 'Visa', 'CARD',
     '7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0003', 'VI', '#7C3AED',
     'Carte bancaire · passerelle bancaire',
     'Vous serez redirigé vers la page sécurisée de la passerelle bancaire pour saisir votre carte. Le portail ne voit jamais votre numéro de carte.');
