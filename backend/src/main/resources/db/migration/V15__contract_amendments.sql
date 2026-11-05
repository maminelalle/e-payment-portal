-- Avenants (conception §6.1 et §11, hypothèse H3) : un nouveau contrat signé remplace le contrat en vigueur,
-- en gardant le lien avec l'original (parent_contract_id) et un numéro de version.

ALTER TABLE contract DROP CONSTRAINT contract_status_check;
ALTER TABLE contract ADD CONSTRAINT contract_status_check CHECK (status IN
    ('DRAFT', 'PENDING_SIGNATURE', 'SIGNED', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'TERMINATED', 'REPLACED'));

-- 0 pour le contrat initial, 1 pour l'avenant n°1 (référence CTR-2026-0001-A1)…
ALTER TABLE contract ADD COLUMN amendment_number int NOT NULL DEFAULT 0 CHECK (amendment_number >= 0);
CREATE INDEX contract_parent_idx ON contract (parent_contract_id);

-- Modèle standard : titre et préambule propres à l'avenant. Les PDF déjà envoyés sont figés (D15) ;
-- le rendu d'un contrat initial ne change pas (blocs conditionnels).
UPDATE contract_template
SET content_html = replace(replace(content_html,
    '<h1>CONTRAT D''ABONNEMENT E-PAYMENT</h1>',
    '<h1 th:if="${contract.amendment == null}">CONTRAT D''ABONNEMENT E-PAYMENT</h1>
  <h1 th:if="${contract.amendment != null}">AVENANT N°<span th:text="${contract.amendment.number}">1</span> AU CONTRAT D''ABONNEMENT</h1>'),
    '<h2>Entre les parties</h2>',
    '<p th:if="${contract.amendment != null}" style="border-left: 3px solid #2563eb; padding: 6px 10px; background: #eff6ff;">
  Le présent avenant remplace, à compter de son activation, le contrat
  <strong th:text="${contract.amendment.replaces}">CTR-2026-0001</strong>. Les moyens de paiement, plafonds,
  dates et conditions ci-dessous se substituent intégralement à ceux de ce contrat ; les opérations réalisées
  sous le contrat remplacé restent acquises.
</p>

<h2>Entre les parties</h2>')
WHERE code = 'STANDARD';
