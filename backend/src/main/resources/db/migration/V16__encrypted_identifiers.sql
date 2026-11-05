-- NNI et NIF chiffrés en base (conception §9, D30) : la colonne garde le texte chiffré (AES-256-GCM,
-- préfixe « v1: »), une empreinte HMAC-SHA256 sert à refuser les doublons sans déchiffrer.
-- Les lignes existantes, encore en clair, sont chiffrées au démarrage du portail (SensitiveDataBackfill).

ALTER TABLE subscriber DROP CONSTRAINT subscriber_nni_check;
ALTER TABLE subscriber ALTER COLUMN nni TYPE varchar(200);
ALTER TABLE subscriber ALTER COLUMN nif TYPE varchar(200);

ALTER TABLE subscriber ADD COLUMN nni_hash char(64);
ALTER TABLE subscriber ADD COLUMN nif_hash char(64);

DROP INDEX subscriber_nni_uk;
DROP INDEX subscriber_nif_uk;
CREATE UNIQUE INDEX subscriber_nni_hash_uk ON subscriber (nni_hash) WHERE nni_hash IS NOT NULL;
CREATE UNIQUE INDEX subscriber_nif_hash_uk ON subscriber (nif_hash) WHERE nif_hash IS NOT NULL;
