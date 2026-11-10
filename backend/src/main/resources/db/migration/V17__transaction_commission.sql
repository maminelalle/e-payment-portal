-- Commission (contrat, article 4 : « une commission de x % s'applique au montant de chaque paiement réussi », D32).
-- Le taux du contrat qui autorise le paiement est figé sur la transaction ; le montant est calculé à la réussite.

ALTER TABLE payment_transaction
    ADD COLUMN commission_rate   numeric(5, 2) CHECK (commission_rate BETWEEN 0 AND 100),
    ADD COLUMN commission_amount numeric(15, 2) CHECK (commission_amount >= 0);

-- Transactions déjà enregistrées : taux de leur contrat, commission des paiements réussis (arrondi au centime).
UPDATE payment_transaction t
SET commission_rate = c.commission_rate,
    commission_amount = CASE
        WHEN t.status = 'SUCCEEDED' AND c.commission_rate IS NOT NULL
            THEN round(t.amount * c.commission_rate / 100, 2)
        END
FROM contract c
WHERE c.id = t.contract_id;
