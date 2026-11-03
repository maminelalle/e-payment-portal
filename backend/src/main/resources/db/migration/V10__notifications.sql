-- Journal des notifications envoyées (emails ; SMS et notifications dans l'application plus tard).
-- Le corps des messages contenant un secret (code de signature) n'est pas conservé.
CREATE TABLE notification (
    id            uuid         PRIMARY KEY,
    subscriber_id uuid         REFERENCES subscriber (id),
    channel       varchar(10)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    recipient     varchar(254) NOT NULL,
    template      varchar(50)  NOT NULL,
    subject       text         NOT NULL,
    body          text,
    status        varchar(20)  NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    error         varchar(500),
    read_at       timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    sent_at       timestamptz
);

CREATE INDEX notification_subscriber_idx ON notification (subscriber_id, created_at DESC);
