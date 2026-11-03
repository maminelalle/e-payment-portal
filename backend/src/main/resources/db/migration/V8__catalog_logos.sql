-- V8 : logos des moyens de paiement, et moyens prévus hors démo (Sedad, Mastercard), désactivés au départ

ALTER TABLE payment_method
    ADD COLUMN logo_content_type varchar(30) CHECK (logo_content_type IN ('image/png', 'image/jpeg', 'image/webp')),
    ADD COLUMN logo_updated_at   timestamptz;

INSERT INTO payment_provider (id, code, name, base_url, credentials_ref, status) VALUES
    ('7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0004', 'SEDAD', 'Sedad', 'http://localhost:8090/sedad', 'epayment.providers.sedad',
     'DISABLED');

-- Mastercard passe par la même passerelle bancaire que Visa.
INSERT INTO payment_method (id, code, name, category, provider_id, short_label, color, description, instructions, status)
VALUES
    ('3f0c2a10-5b7e-4c1d-8e2f-6a9b0c1d0004', 'SEDAD', 'Sedad', 'WALLET',
     '7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0004', 'SD', '#EA580C',
     'Wallet mobile',
     'Saisissez le numéro de téléphone associé à votre compte Sedad, puis confirmez le paiement dans l''application.',
     'INACTIVE'),
    ('3f0c2a10-5b7e-4c1d-8e2f-6a9b0c1d0005', 'MASTERCARD', 'Mastercard', 'CARD',
     '7b1e6c52-0d4a-4f5e-9a51-0b6b3c1d0003', 'MC', '#B91C1C',
     'Carte bancaire · passerelle bancaire',
     'Vous serez redirigé vers la page sécurisée de la passerelle bancaire pour saisir votre carte. Le portail ne voit jamais votre numéro de carte.',
     'INACTIVE');
