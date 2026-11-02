-- V2 : journal d'audit (ajout seul, voir docs/02-modele-donnees.md)

CREATE TABLE audit_log (
    id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamptz  NOT NULL DEFAULT now(),
    -- identifiant Keycloak (claim sub) ; pas de clé étrangère : l'audit ne doit jamais échouer
    actor_subject varchar(64),
    actor_label   varchar(254) NOT NULL,
    action        varchar(50)  NOT NULL,
    resource_type varchar(50),
    resource_id   varchar(100),
    outcome       varchar(10)  NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    ip_address    inet,
    details       jsonb
);

CREATE INDEX audit_log_occurred_at_idx ON audit_log (occurred_at DESC);
CREATE INDEX audit_log_actor_idx ON audit_log (actor_subject);
CREATE INDEX audit_log_action_idx ON audit_log (action);
