-- Gestion du catalogue par l'admin (conception §3.3) : ajout et modification des moyens de paiement.

-- Type de fournisseur : le type d'un moyen (wallet ou carte) est celui de son fournisseur.
ALTER TABLE payment_provider ADD COLUMN kind varchar(10) NOT NULL DEFAULT 'WALLET' CHECK (kind IN ('WALLET', 'CARD'));
UPDATE payment_provider SET kind = 'CARD' WHERE code = 'VISA_GATEWAY';

-- Conditions d'utilisation (wallet) ou règles d'acceptation (carte), affichées à l'abonné.
ALTER TABLE payment_method ADD COLUMN conditions text;

-- Code technique stable (contrats, URL des logos) : majuscules, chiffres et « _ ».
ALTER TABLE payment_method ADD CONSTRAINT payment_method_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$');
