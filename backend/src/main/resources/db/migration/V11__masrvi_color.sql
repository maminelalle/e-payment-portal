-- Masrvi : vert un peu plus saturé, distinct du bleu Bankily et du violet Visa dans les graphiques
-- (contrôle de palette : #0F766E paraissait gris). Seulement si l'admin n'a pas changé la couleur.
UPDATE payment_method SET color = '#0D9488' WHERE code = 'MASRVI' AND color = '#0F766E';
