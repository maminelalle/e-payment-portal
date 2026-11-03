-- V4 : modèles de contrat, contrats et moyens inclus (voir docs/02-modele-donnees.md)

CREATE TABLE contract_template (
    id           uuid         PRIMARY KEY,
    code         varchar(50)  NOT NULL,
    name         varchar(100) NOT NULL,
    version      int          NOT NULL,
    content_html text         NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

CREATE SEQUENCE contract_ref_seq START 1;

CREATE TABLE contract (
    id                  uuid         PRIMARY KEY,
    reference           varchar(20)  NOT NULL UNIQUE,
    subscriber_id       uuid         NOT NULL REFERENCES subscriber (id),
    template_id         uuid         NOT NULL REFERENCES contract_template (id),
    parent_contract_id  uuid         REFERENCES contract (id),
    status              varchar(30)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN
                        ('DRAFT', 'PENDING_SIGNATURE', 'SIGNED', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'TERMINATED')),
    start_date          date,
    end_date            date,
    commission_rate     numeric(5, 2) CHECK (commission_rate BETWEEN 0 AND 100),
    pdf_object_key      varchar(300),
    pdf_sha256          char(64),
    signed_pdf_object_key varchar(300),
    signed_pdf_sha256   char(64),
    sent_at             timestamptz,
    signed_at           timestamptz,
    activated_at        timestamptz,
    terminated_at       timestamptz,
    termination_reason  varchar(300),
    created_by          varchar(254),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT contract_dates_chk CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    -- un contrat envoyé est figé : son PDF et ses dates existent forcément
    CONSTRAINT contract_sent_chk CHECK (status = 'DRAFT'
        OR (start_date IS NOT NULL AND end_date IS NOT NULL AND pdf_object_key IS NOT NULL AND pdf_sha256 IS NOT NULL))
);

-- Hypothèse H3 : un seul contrat en vigueur par abonné.
CREATE UNIQUE INDEX contract_one_in_force_per_subscriber
    ON contract (subscriber_id) WHERE status IN ('ACTIVE', 'SUSPENDED');
CREATE INDEX contract_subscriber_idx ON contract (subscriber_id);
CREATE INDEX contract_status_idx ON contract (status);

CREATE TABLE contract_payment_method (
    contract_id           uuid          NOT NULL REFERENCES contract (id) ON DELETE CASCADE,
    payment_method_id     uuid          NOT NULL REFERENCES payment_method (id),
    per_transaction_limit numeric(15, 2) NOT NULL CHECK (per_transaction_limit > 0),
    monthly_limit         numeric(15, 2) NOT NULL CHECK (monthly_limit >= per_transaction_limit),
    PRIMARY KEY (contract_id, payment_method_id)
);

-- Modèle « Contrat standard » v1. Variables : contract, subscriber, methods, issuedAt (voir ContractDocument).
INSERT INTO contract_template (id, code, name, version, content_html) VALUES (
'5d2b7c40-1f3e-4a8b-9c6d-7e8f9a0b0001', 'STANDARD', 'Contrat standard', 1,
$tpl$<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8" />
<title th:text="'Contrat ' + ${contract.reference}">Contrat</title>
<style>
  @page {
    size: A4;
    margin: 22mm 20mm 24mm 20mm;
    @bottom-left { content: "Portail E-Payment"; font-size: 8pt; color: #64748b; }
    @bottom-right { content: "Page " counter(page) " / " counter(pages); font-size: 8pt; color: #64748b; }
  }
  body { font-family: sans-serif; font-size: 10pt; color: #0f172a; line-height: 1.45; }
  .header { border-bottom: 2px solid #2563eb; padding-bottom: 10px; margin-bottom: 18px; }
  .brand { font-size: 9pt; color: #2563eb; font-weight: bold; letter-spacing: 1px; }
  h1 { font-size: 15pt; margin: 6px 0 2px; text-align: center; }
  .ref { text-align: center; color: #64748b; font-size: 9pt; }
  h2 { font-size: 11pt; margin: 18px 0 6px; color: #1e3a8a; }
  table { width: 100%; border-collapse: collapse; margin: 6px 0; }
  th, td { border: 1px solid #e2e8f0; padding: 6px 8px; text-align: left; vertical-align: top; }
  th { background: #f1f5f9; font-weight: bold; }
  td.amount { text-align: right; white-space: nowrap; }
  .parties td:first-child { width: 32%; color: #475569; }
  .signature { margin-top: 28px; border: 1px dashed #94a3b8; padding: 12px; color: #475569; font-size: 9pt; }
  .draft { position: fixed; top: 110mm; left: 10mm; width: 170mm; text-align: center; font-size: 64pt;
           color: #e2e8f0; transform: rotate(-30deg); z-index: -1; }
</style>
</head>
<body>
<div class="draft" th:if="${contract.draft}">BROUILLON</div>

<div class="header">
  <div class="brand">PORTAIL E-PAYMENT</div>
  <h1>CONTRAT D'ABONNEMENT E-PAYMENT</h1>
  <div class="ref">Réf. <span th:text="${contract.reference}">CTR-2026-0001</span> · établi le <span th:text="${issuedAt}">01/11/2026</span></div>
</div>

<h2>Entre les parties</h2>
<table class="parties">
  <tr><td>Prestataire</td><td>Portail E-Payment</td></tr>
  <tr><td>Abonné</td><td><strong th:text="${subscriber.displayName}">Société ABC</strong> (<span th:text="${subscriber.typeLabel}">personne morale</span>)</td></tr>
  <tr><td th:text="${subscriber.identifierLabel}">NIF</td><td th:text="${subscriber.identifier}">01234567</td></tr>
  <tr><td>Adresse</td><td th:text="${subscriber.address}">Nouakchott</td></tr>
  <tr><td>Téléphone · email</td><td><span th:text="${subscriber.phone}">+222</span> · <span th:text="${subscriber.email}">contact@exemple.mr</span></td></tr>
</table>

<h2>Article 1 — Objet</h2>
<p>Le présent contrat définit les conditions dans lesquelles l'abonné accède, depuis le portail E-Payment,
aux moyens de paiement listés à l'article 2, et uniquement à ceux-ci. Aucun autre moyen de paiement
n'est accessible au titre du présent contrat.</p>

<h2>Article 2 — Moyens de paiement et plafonds</h2>
<table>
  <tr><th>Moyen de paiement</th><th>Type</th><th>Plafond par transaction</th><th>Plafond mensuel</th></tr>
  <tr th:each="m : ${methods}">
    <td th:text="${m.name}">Bankily</td>
    <td th:text="${m.categoryLabel}">Wallet mobile</td>
    <td class="amount" th:text="${m.perTransactionLimit}">50 000,00 MRU</td>
    <td class="amount" th:text="${m.monthlyLimit}">500 000,00 MRU</td>
  </tr>
</table>
<p>Tout paiement dépassant l'un de ces plafonds est refusé par le portail.</p>

<h2>Article 3 — Durée</h2>
<p>Le contrat est conclu du <strong th:text="${contract.startDate}">01/11/2026</strong>
au <strong th:text="${contract.endDate}">31/10/2027</strong> inclus (heure de Nouakchott).
Les moyens de paiement ne sont accessibles qu'après signature électronique et activation du contrat,
et seulement pendant cette période.</p>

<h2>Article 4 — Frais et commissions</h2>
<p th:if="${contract.commission != null}">Une commission de <strong th:text="${contract.commission}">1,50 %</strong>
s'applique au montant de chaque paiement réussi.</p>
<p th:if="${contract.commission == null}">Aucune commission n'est prévue au titre du présent contrat.</p>

<h2>Article 5 — Suspension et résiliation</h2>
<p>Le prestataire peut suspendre ou résilier le contrat, notamment en cas d'usage frauduleux ou de manquement
de l'abonné. La suspension, l'expiration ou la résiliation désactivent l'accès aux moyens de paiement ;
l'historique des transactions est conservé.</p>

<h2>Article 6 — Sécurité et données</h2>
<p>Le portail ne stocke ni numéro complet de carte bancaire ni cryptogramme. Les données personnelles de
l'abonné sont utilisées pour l'exécution du présent contrat et la traçabilité des opérations.</p>

<h2>Article 7 — Signature électronique</h2>
<p>L'abonné signe le présent contrat électroniquement depuis son espace, au moyen d'un code à usage unique.
L'empreinte du document, l'horodatage et l'adresse IP de signature sont conservés comme preuves.</p>

<div class="signature">Emplacement de la signature électronique de l'abonné.</div>
</body>
</html>
$tpl$);
