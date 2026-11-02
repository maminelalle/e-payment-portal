-- V1 : comptes, rôles et abonnés (voir docs/02-modele-donnees.md)

CREATE TABLE app_user (
    id            uuid         PRIMARY KEY,
    keycloak_id   varchar(64)  NOT NULL UNIQUE,
    email         varchar(254) NOT NULL UNIQUE,
    phone         varchar(20),
    full_name     varchar(200) NOT NULL,
    status        varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'DISABLED')),
    last_login_at timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE role (
    id    smallint    PRIMARY KEY,
    code  varchar(20) NOT NULL UNIQUE,
    label varchar(50) NOT NULL
);

INSERT INTO role (id, code, label) VALUES
    (1, 'ADMIN',  'Administrateur'),
    (2, 'ABONNE', 'Abonné');

CREATE TABLE user_role (
    user_id uuid     NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id smallint NOT NULL REFERENCES role (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE SEQUENCE subscriber_ref_seq START 1;

CREATE TABLE subscriber (
    id           uuid         PRIMARY KEY,
    reference    varchar(20)  NOT NULL UNIQUE,
    type         varchar(10)  NOT NULL CHECK (type IN ('PHYSIQUE', 'MORALE')),
    display_name varchar(200) NOT NULL,
    nni          varchar(10)  CHECK (nni ~ '^[0-9]{10}$'),
    nif          varchar(20),
    phone        varchar(20)  NOT NULL CHECK (phone ~ '^\+222[0-9]{8}$'),
    email        varchar(254) NOT NULL UNIQUE,
    address      varchar(300),
    status       varchar(20)  NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'DISABLED')),
    user_id      uuid         UNIQUE REFERENCES app_user (id),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT subscriber_identifier_chk CHECK (
        (type = 'PHYSIQUE' AND nni IS NOT NULL) OR (type = 'MORALE' AND nif IS NOT NULL)
    )
);

CREATE UNIQUE INDEX subscriber_nni_uk ON subscriber (nni) WHERE nni IS NOT NULL;
CREATE UNIQUE INDEX subscriber_nif_uk ON subscriber (nif) WHERE nif IS NOT NULL;
CREATE INDEX subscriber_status_idx ON subscriber (status);
