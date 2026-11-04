-- Préférences de notification de l'abonné (module notification). Les emails de contrat et le code de
-- signature partent toujours ; seuls les emails de fin de paiement peuvent être coupés.
CREATE TABLE notification_preference (
    subscriber_id  uuid        PRIMARY KEY REFERENCES subscriber (id),
    payment_emails boolean     NOT NULL DEFAULT true,
    updated_at     timestamptz NOT NULL DEFAULT now()
);
