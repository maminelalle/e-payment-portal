// Génère docs/rapport/Rapport_de_stage_Portail_E-Payment.docx
const fs = require('node:fs');
const path = require('node:path');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, ImageRun, Table, TableRow, TableCell,
  WidthType, ShadingType, BorderStyle, PageBreak, Header, Footer, PageNumber, TableOfContents, LevelFormat,
  TabStopType, VerticalAlign,
} = require('docx');

const ROOT = 'C:/Users/lalle/Desktop/e-payment-portal/docs/rapport';
const FIG = ROOT + '/figures';
const BRAND = '1E3A8A';
const MUTED = '4B5563';
const FONT = 'Calibri';
const CONTENT = 9070; // largeur utile en DXA (A4, marges 2,5 cm)

// ---------- outils ----------
function runs(text, base = {}) {
  // **gras**, *italique*, [[à compléter]] surligné, `code`
  const out = [];
  const re = /(\*\*[^*]+\*\*|\*[^*]+\*|\[\[[^\]]+\]\]|`[^`]+`)/g;
  let last = 0, m;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(new TextRun({ text: text.slice(last, m.index), ...base }));
    const t = m[0];
    if (t.startsWith('**')) out.push(new TextRun({ text: t.slice(2, -2), bold: true, ...base }));
    else if (t.startsWith('[[')) out.push(new TextRun({ text: '[' + t.slice(2, -2) + ']', highlight: 'yellow', ...base }));
    else if (t.startsWith('`')) out.push(new TextRun({ text: t.slice(1, -1), font: 'Consolas', size: 19, ...base }));
    else out.push(new TextRun({ text: t.slice(1, -1), italics: true, ...base }));
    last = m.index + t.length;
  }
  if (last < text.length) out.push(new TextRun({ text: text.slice(last), ...base }));
  return out;
}
const P = (text, opts = {}) => new Paragraph({ children: runs(text, opts.run), alignment: opts.align ?? AlignmentType.JUSTIFIED, spacing: { after: 120, line: 300 }, ...opts.para });
const H1 = (text) => new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, children: [new TextRun(text)] });
const H1nb = (text) => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(text)] });
const H2 = (text) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(text)] });
const H3 = (text) => new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(text)] });
const B = (text, level = 0) => new Paragraph({ numbering: { reference: 'puces', level }, children: runs(text), spacing: { after: 60, line: 280 }, alignment: AlignmentType.LEFT });
const N = (text, ref = 'num') => new Paragraph({ numbering: { reference: ref, level: 0 }, children: runs(text), spacing: { after: 60, line: 280 } });
const blank = () => new Paragraph({ children: [] });
const pageBreak = () => new Paragraph({ children: [new PageBreak()] });

function note(text) {
  return new Paragraph({
    children: runs(text, { color: '1F2937' }),
    shading: { type: ShadingType.CLEAR, fill: 'EEF2FF', color: 'auto' },
    border: { left: { style: BorderStyle.SINGLE, size: 18, color: BRAND, space: 8 } },
    spacing: { before: 120, after: 160, line: 280 },
    indent: { left: 200, right: 200 },
  });
}

function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}
let figNo = 0;
const figures = [];
function fig(file, caption, widthPx = 600) {
  const f = path.join(FIG, file);
  const { w, h } = pngSize(f);
  const height = Math.round((h / w) * widthPx);
  figNo++;
  figures.push(`Figure ${figNo} – ${caption}`);
  return [
    new Paragraph({
      alignment: AlignmentType.CENTER, keepNext: true, spacing: { before: 120, after: 60 },
      children: [new ImageRun({ type: 'png', data: fs.readFileSync(f), transformation: { width: widthPx, height },
        altText: { title: caption, description: caption, name: file } })],
    }),
    new Paragraph({ style: 'Legende', alignment: AlignmentType.CENTER, children: [new TextRun(`Figure ${figNo} – ${caption}`)] }),
  ];
}

const border = { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1' };
const borders = { top: border, bottom: border, left: border, right: border };
let tabNo = 0;
const tables = [];
function table(headers, rows, widths, caption) {
  const total = widths.reduce((a, b) => a + b, 0);
  const scale = CONTENT / total;
  const w = widths.map((x) => Math.round(x * scale));
  w[w.length - 1] += CONTENT - w.reduce((a, b) => a + b, 0);
  const cell = (text, i, head) => new TableCell({
    borders, width: { size: w[i], type: WidthType.DXA }, verticalAlign: VerticalAlign.CENTER,
    shading: head ? { fill: 'E0E7FF', type: ShadingType.CLEAR, color: 'auto' } : undefined,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: String(text).split('\n').map((line) => new Paragraph({ children: runs(line, { size: 19, bold: head || undefined }), spacing: { after: 0, line: 260 } })),
  });
  const out = [];
  if (caption) {
    tabNo++;
    tables.push(`Tableau ${tabNo} – ${caption}`);
    out.push(new Paragraph({ style: 'Legende', alignment: AlignmentType.CENTER, keepNext: true, spacing: { before: 160, after: 80 }, children: [new TextRun(`Tableau ${tabNo} – ${caption}`)] }));
  }
  out.push(new Table({
    width: { size: CONTENT, type: WidthType.DXA }, columnWidths: w,
    rows: [new TableRow({ tableHeader: true, children: headers.map((h, i) => cell(h, i, true)) }),
      ...rows.map((r) => new TableRow({ cantSplit: true, children: r.map((c, i) => cell(c, i, false)) }))],
  }));
  out.push(new Paragraph({ children: [], spacing: { after: 120 } }));
  return out;
}

// ---------- page de garde ----------
const cover = [
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new ImageRun({ type: 'png', data: fs.readFileSync(path.join(ROOT, 'sources/logo-smart.png')),
    transformation: { width: 195, height: 82 }, altText: { title: 'Logo Smart', description: 'Logo de Smart MS SA', name: 'logo-smart' } })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [new TextRun({ text: 'SMART MS SA', size: 30, bold: true })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [new TextRun({ text: 'Direction des Opérations et Supports', size: 22 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 1100 }, children: [new TextRun({ text: 'Département CET / P. Développement', size: 22 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: 'RAPPORT DE STAGE DE PRÉ-EMBAUCHE', size: 30, bold: true, color: MUTED, characterSpacing: 40 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 160 }, border: { top: { style: BorderStyle.SINGLE, size: 12, color: BRAND, space: 12 }, bottom: { style: BorderStyle.SINGLE, size: 12, color: BRAND, space: 12 } },
    children: [new TextRun({ text: 'Portail E-Payment', size: 52, bold: true, color: BRAND })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 1400 }, children: [new TextRun({ text: 'Conception et réalisation d’une plateforme de paiement électronique où le contrat signé décide seul des moyens de paiement de chaque abonné', size: 26, italics: true })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 80 }, children: runs('Réalisé par : **Lalle Vatma MAMINE**', { size: 24 }) }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 80 }, children: runs('Ingénieur de Développement et Intégration', { size: 22, color: MUTED }) }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 800 }, children: runs('Encadrant : **Edje**', { size: 24 }) }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 80 }, children: [new TextRun({ text: 'Période de pré-embauche à partir du 2 novembre 2026', size: 22 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: 'Nouakchott — 2026 / 2027', size: 22 })] }),
];

// ---------- pages liminaires ----------
const front = [
  H1nb('Remerciements'),
  P('Je tiens à remercier Edje, mon encadrant, pour sa confiance, sa disponibilité et ses conseils tout au long de ce projet.'),
  P('Je remercie la société **Smart MS SA**, et en particulier la Direction des Opérations et Supports et l’équipe du département CET / P. Développement, de m’avoir accueillie une nouvelle fois et de m’avoir confié ce projet dans le cadre de ma période de pré-embauche.'),
  P('Merci également à mes enseignants de SupNum pour la formation qui m’a préparée à ce travail, et à toutes les personnes qui prendront le temps de lire et d’évaluer ce rapport.'),
  P('Enfin, merci à ma famille et à mes proches pour leur soutien constant.'),

  H1('Résumé'),
  P('Ce rapport présente la conception et la réalisation du **Portail E-Payment**, une plateforme web de gestion des paiements électroniques destinée au contexte mauritanien (wallets Bankily et Masrvi, cartes Visa et Mastercard, monnaie MRU). Son principe fondamental est que **le contrat signé et actif est la seule source des droits** : un abonné ne voit et n’utilise que les moyens de paiement inscrits dans son contrat, dans les plafonds et à la commission qui y figurent.'),
  P('La plateforme couvre l’ensemble du cycle : création de l’abonné par un administrateur, préparation et génération du contrat en PDF, signature électronique par code à usage unique et sceau PAdES, ouverture automatique des droits, paiements contrôlés côté serveur, réception des notifications des fournisseurs par webhooks signés, rapprochement automatique, reçus vérifiables par QR code, relevés mensuels, tableaux de bord et journal d’audit.'),
  P('Elle repose sur un monolithe modulaire Spring Boot 4.1 (Java 21, Spring Modulith), une interface Angular 21 bilingue français / arabe, Keycloak pour l’authentification avec une double authentification des administrateurs par WhatsApp, PostgreSQL 16 et un stockage objet compatible S3, le tout déployé avec Docker Compose derrière Nginx. Les fournisseurs de paiement sont imités par un simulateur fidèle à leurs contrats d’API. Le projet est couvert par 79 tests d’intégration backend, des tests unitaires frontend et 23 scénarios de bout en bout exécutés sur l’environnement de recette.'),
  P('**Mots-clés :** paiement électronique, signature électronique, PAdES, contrôle d’accès, Spring Boot, Angular, Keycloak, webhooks, idempotence, Mauritanie.', { align: AlignmentType.LEFT }),

  H1nb('Abstract'),
  P('This report describes the design and implementation of **E-Payment Portal**, a web platform for managing electronic payments in Mauritania (Bankily and Masrvi mobile wallets, Visa and Mastercard cards, MRU currency). Its core rule is that **the signed and active contract is the only source of entitlements**: a subscriber can only see and use the payment methods listed in their contract, within the limits and at the commission rate it defines.'),
  P('The platform covers the full lifecycle: subscriber onboarding by an administrator, contract generation as a PDF, electronic signature with a one-time code and a PAdES seal, automatic activation of entitlements, server-side checked payments, signed provider webhooks, automatic reconciliation, QR-verifiable receipts, monthly statements, dashboards and an audit log. It is built as a Spring Boot 4.1 modular monolith with an Angular 21 bilingual (French / Arabic) interface, Keycloak with WhatsApp-based two-factor authentication for administrators, PostgreSQL 16 and S3-compatible storage, deployed with Docker Compose behind Nginx.'),
  P('**Keywords:** electronic payment, e-signature, PAdES, access control, Spring Boot, Angular, Keycloak, webhooks, idempotency.', { align: AlignmentType.LEFT }),

  H1('Sommaire'),
  new TableOfContents('Sommaire', { hyperlink: true, headingStyleRange: '1-2' }),

  H1('Liste des abréviations'),
  ...table(['Sigle', 'Signification'], [
    ['API', 'Application Programming Interface — interface de programmation'],
    ['CI', 'Continuous Integration — intégration continue'],
    ['CSP', 'Content Security Policy — politique de sécurité du contenu (en-tête HTTP)'],
    ['E2E', 'End-to-End — tests de bout en bout'],
    ['HMAC', 'Hash-based Message Authentication Code — code d’authentification de message'],
    ['HSTS', 'HTTP Strict Transport Security'],
    ['JWT', 'JSON Web Token — jeton d’accès signé'],
    ['MFA', 'Multi-Factor Authentication — authentification à plusieurs facteurs'],
    ['MRU', 'Ouguiya, monnaie de la Mauritanie'],
    ['NIF / NNI', 'Numéro d’identification fiscale / Numéro national d’identification'],
    ['OIDC', 'OpenID Connect — protocole d’authentification'],
    ['OTP', 'One-Time Password — code à usage unique'],
    ['PAdES', 'PDF Advanced Electronic Signatures (norme ETSI EN 319 142)'],
    ['PDF', 'Portable Document Format'],
    ['RTL', 'Right-To-Left — écriture de droite à gauche (arabe)'],
    ['S3', 'Simple Storage Service — API de stockage objet'],
    ['SHA-256', 'Secure Hash Algorithm, empreinte de 256 bits'],
    ['TLS', 'Transport Layer Security — chiffrement des échanges'],
    ['TOTP', 'Time-based One-Time Password — code d’application d’authentification'],
  ], [1600, 7470]),
];

// ---------- introduction ----------
const intro = [
  H1('Introduction générale'),
  P('Les paiements électroniques se développent rapidement en Mauritanie, portés par les wallets mobiles comme Bankily ou Masrvi et par les cartes bancaires internationales. Pour une organisation qui met des moyens de paiement à la disposition de ses abonnés, chaque ouverture de service s’accompagne pourtant encore d’une contractualisation lourde : contrats papier, ouverture manuelle des accès, suivi dispersé des opérations et difficulté à prouver ce qui a été convenu.'),
  P('Le projet qui m’a été confié par Smart MS SA répond à ce besoin : concevoir et réaliser un **portail de paiement électronique** dans lequel la contractualisation est entièrement dématérialisée et où le **contrat signé pilote automatiquement les droits** de chaque abonné. Un abonné ne peut payer qu’avec les moyens prévus par son contrat actif, dans les plafonds et aux conditions qu’il fixe ; toute modification passe par un avenant signé.'),
  P('Le travail a été mené chez Smart MS SA, dans le cadre de ma période de pré-embauche au poste d’ingénieur de développement et intégration, selon un plan de douze semaines à partir du 2 novembre 2026, en partant d’un document de conception fonctionnelle existant. Il a couvert l’ensemble du cycle de développement : analyse et cadrage, choix d’architecture, réalisation du backend et de l’interface, intégration des fournisseurs (simulés), sécurité, tests automatisés et mise en place d’un environnement de recette en HTTPS.'),
  P('Ce rapport est organisé en six chapitres. Le premier présente le contexte, les objectifs et la démarche suivie. Le deuxième formalise les besoins. Le troisième détaille la conception : architecture, modèle de données et principaux flux. Le quatrième décrit la réalisation des deux espaces de l’application et des mécanismes clés. Le cinquième traite des tests, de la sécurité et du déploiement. Le dernier dresse le bilan du projet, des difficultés rencontrées et des perspectives.'),
];

// ---------- chapitre 1 ----------
const ch1 = [
  H1('Chapitre 1 — Contexte et présentation du projet'),
  H2('1.1 Organisme d’accueil'),
  P('Le projet a été réalisé au sein de **Smart MS SA**, société anonyme basée à Nouakchott (67 Îlot M, Tevragh-Zeina). J’y ai été accueillie dans la **Direction des Opérations et Supports**, au sein du département **CET / P. Développement**, au poste d’**Ingénieur de Développement et Intégration**, pour une période d’essai de trois mois débutant le 2 novembre 2026 : un stage de pré-embauche, dont ce projet constitue la réalisation principale.'),
  P('Ce n’est pas ma première expérience dans l’entreprise : j’y avais effectué mon stage de fin d’études de licence (mars à juillet 2026), consacré à une application de gestion de cabinet médical en Django REST et React, au sein d’une équipe travaillant en Scrum.'),
  P('Le projet a été suivi par **Edje**, mon encadrant, qui a validé les orientations et assuré le suivi de l’avancement.'),
  P('[[Compléter si souhaité : activités et clients de Smart MS SA, date de création, effectif, organigramme de la Direction des Opérations et Supports.]]'),
  H2('1.2 Contexte : le paiement électronique en Mauritanie'),
  P('Le paysage mauritanien du paiement électronique repose sur deux familles de moyens :'),
  B('les **wallets mobiles**, adossés à des banques ou opérateurs locaux (Bankily, Masrvi, Sedad), qui permettent de payer depuis un téléphone en confirmant l’opération dans l’application du fournisseur ;'),
  B('les **cartes bancaires** (Visa, Mastercard), utilisées via une passerelle bancaire qui héberge la page de saisie de la carte et l’authentification 3-D Secure.'),
  P('Chaque fournisseur expose sa propre API (initiation d’un paiement, consultation du statut, notification du résultat par webhook), ses propres formats et ses propres codes d’erreur. Pour l’organisation, il faut donc à la fois **unifier** ces moyens derrière une interface unique et **contrôler** qui a le droit d’utiliser quoi.'),
  H2('1.3 Problématique'),
  P('Sans outil dédié, l’attribution des moyens de paiement aux abonnés est manuelle et peu traçable : rien ne garantit qu’un abonné n’utilise que ce qui a été convenu, ni que les plafonds et commissions appliqués correspondent au contrat. La question à laquelle répond le projet peut se formuler ainsi :'),
  note('Comment permettre à une organisation de contractualiser à distance avec ses abonnés, puis de **faire respecter automatiquement**, à chaque paiement, les moyens, plafonds et commissions prévus par le contrat signé, tout en gardant une preuve fiable de chaque étape ?'),
  H2('1.4 Objectifs du projet'),
  P('Le document de conception fixe cinq objectifs :'),
  N('**Centraliser** la gestion des abonnés, des contrats et des moyens de paiement dans un back-office unique.'),
  N('**Dématérialiser** la contractualisation par une signature électronique intégrée et probante.'),
  N('**Automatiser** l’ouverture, la suspension et la révocation des droits à partir du statut du contrat.'),
  N('**Permettre les paiements** par wallet et par carte, uniquement avec les moyens autorisés.'),
  N('**Tracer et restituer** les transactions : historiques, reçus, exports, tableaux de bord et audit.'),
  H2('1.5 Périmètre du stage'),
  P('Le projet est un projet de stage : aucun accès aux API réelles de Bankily, Masrvi ou de la passerelle bancaire n’a été demandé. Les fournisseurs sont donc remplacés par un **simulateur** développé pour l’occasion, qui respecte les contrats d’API documentés au cadrage (formats, signatures, délais, cas d’erreur). L’architecture par adaptateurs permet de brancher les vraies API en ne changeant que la configuration.'),
  ...table(['Élément', 'Démonstration (stage)', 'Cible complète'], [
    ['Moyens de paiement', 'Bankily, Masrvi, Visa (Mastercard et Sedad au catalogue)', 'Tous les partenaires, API réelles'],
    ['Espaces', 'Administrateur et abonné', 'Idem, avec rôles admin fins'],
    ['Signature', 'Code à usage unique + sceau PAdES du portail', 'Prestataire de signature qualifié si exigé'],
    ['Paiements', 'Simulateur fidèle aux API, rapprochement', 'Production, remboursements, contestations'],
    ['Authentification forte', 'MFA administrateur par WhatsApp (Vonage, réel)', 'Idem'],
  ], [2000, 3600, 3470], 'Périmètre de la démonstration et cible'),
  H2('1.6 Démarche et planification'),
  P('Le projet a été mené par une seule développeuse, selon un plan de douze semaines découpé en lots courts et vérifiables. Chaque lot se termine par une recette sur la vraie pile technique (et non sur des maquettes), et trois **jalons** structurent l’avancement : le workflow complet démontrable (S7), les paiements simulés réalistes (S10) et la version pilote en ligne (S12).'),
  ...fig('d-planning.png', 'Planning prévisionnel en douze lots et jalons'),
  P('Le suivi s’appuie sur des documents versionnés avec le code, dans le dossier `docs/` du dépôt : registre des décisions (D1 à D33), plan coché au fil de l’eau, journal d’avancement, matrice de conformité à la conception et scénario de démonstration. Le code est hébergé sur GitHub et chaque modification passe par une branche avant d’être intégrée.'),
];

// ---------- chapitre 2 ----------
const ch2 = [
  H1('Chapitre 2 — Analyse des besoins'),
  H2('2.1 Acteurs'),
  P('Deux profils humains utilisent le portail ; plusieurs systèmes externes interviennent. Tout contrôle d’accès est fait **côté serveur**, jamais par simple masquage d’écran.'),
  ...table(['Acteur', 'Type', 'Rôle'], [
    ['Administrateur', 'Interne', 'Gère les abonnés, le catalogue des moyens, les contrats et leurs modèles, suit les transactions et l’audit'],
    ['Abonné', 'Utilisateur final (personne physique ou morale)', 'Consulte et signe son contrat, paie avec les moyens autorisés, suit ses paiements, gère son profil'],
    ['Fournisseurs wallets', 'Externe', 'Bankily, Masrvi : initiation, statut, webhook'],
    ['Passerelle bancaire', 'Externe', 'Paiement carte sur page hébergée, 3-D Secure, webhook'],
    ['Messagerie', 'Externe', 'Emails (contrat disponible, codes, paiements) ; WhatsApp pour le code MFA admin'],
  ], [1900, 2400, 4770], 'Acteurs du système'),
  H2('2.2 Besoins fonctionnels — espace administrateur'),
  ...table(['Module', 'Fonctions attendues'], [
    ['Tableau de bord', 'Abonnés par statut, contrats par statut, nombre et montant des transactions, répartition par moyen, type et fournisseur, transactions réussies / échouées / en attente'],
    ['Abonnés', 'Créer, consulter, modifier, activer, suspendre, désactiver ; référence, type, nom ou raison sociale, NNI / NIF, téléphone, email, adresse ; contrats, moyens autorisés et transactions de l’abonné'],
    ['Moyens de paiement', 'Catalogue des wallets et cartes, paramètres d’intégration, limites, conditions affichées, disponibilité des fournisseurs'],
    ['Contrats', 'Modèles versionnés, choix des moyens inclus, dates, plafonds, commission, génération PDF, envoi, suivi de la signature, suspension, résiliation, avenants, archivage'],
    ['Transactions', 'Historique de tous les abonnés, filtres, reçus, exports'],
    ['Paramètres et audit', 'Journal des actions sensibles, filtrable'],
  ], [2000, 7070], 'Besoins de l’espace administrateur'),
  H2('2.3 Besoins fonctionnels — espace abonné'),
  ...table(['Module', 'Fonctions attendues'], [
    ['Accueil', 'Statut et validité du contrat, moyens autorisés, consommation des plafonds, dernières transactions, notifications'],
    ['Mon contrat', 'Consultation, téléchargement, signature électronique (contrat ou avenant)'],
    ['Moyens de paiement', 'Uniquement ceux du contrat actif, avec instructions et conditions ; paiement'],
    ['Transactions', 'Historique filtrable, reçus PDF, export, relevé mensuel'],
    ['Profil', 'Coordonnées (téléphone, adresse) modifiables avec vérification, mot de passe, sessions ouvertes, préférences de notification'],
  ], [2000, 7070], 'Besoins de l’espace abonné'),
  H2('2.4 Règles métier structurantes'),
  B('**Principe fondamental** : les moyens visibles et utilisables sont déterminés par le contrat signé et actif ; les droits s’ouvrent automatiquement après vérification de la signature ; aucun paiement n’est possible avec un moyen non prévu.'),
  B('**Un seul contrat en vigueur** par abonné ; un changement passe par un **avenant**, qui remplace l’original à son activation.'),
  B('**Plafonds** par opération et par mois, pour chaque moyen inscrit au contrat.'),
  B('**Commission** contractuelle appliquée au montant de chaque paiement réussi (article 4 du contrat).'),
  B('Un contrat envoyé est **figé** : son contenu ne change plus, et son intégrité doit pouvoir être prouvée.'),
  B('Une transaction terminée ne change plus d’état ; un même paiement ne doit jamais être débité deux fois.'),
  H2('2.5 Besoins non fonctionnels'),
  ...table(['Exigence', 'Traduction dans le projet'], [
    ['Sécurité', 'Authentification centralisée, MFA pour les administrateurs, droits vérifiés côté serveur, aucune donnée de carte dans le portail, données d’identité chiffrées, en-têtes de sécurité, limitation de débit'],
    ['Traçabilité et preuve', 'Journal d’audit en ajout seul, preuves de signature (horodatage, IP, navigateur, empreintes), PDF scellés'],
    ['Fiabilité', 'Idempotence des paiements, webhooks signés et non rejouables, rapprochement automatique, sauvegardes quotidiennes testées'],
    ['Ergonomie', 'Interface responsive (bureau et mobile), bilingue français / arabe avec écriture de droite à gauche, mode sombre, recherche globale'],
    ['Maintenabilité', 'Modules isolés et vérifiés par des tests, migrations de base versionnées, décisions documentées'],
  ], [2000, 7070], 'Besoins non fonctionnels'),
  H2('2.6 Hypothèses de cadrage'),
  P('Le document de conception laissait plusieurs points ouverts. Ils ont été tranchés au cadrage et consignés dans le registre des décisions :'),
  ...table(['N°', 'Sujet', 'Hypothèse retenue'], [
    ['H1', 'Rôle du portail', 'L’abonné **paie** avec ses moyens ; il n’encaisse pas pour ses propres clients'],
    ['H2', 'Modèle des moyens', 'Chaque abonné utilise son propre wallet ou sa propre carte'],
    ['H3', 'Contrats multiples', 'Un seul contrat actif par abonné ; un avenant remplace le contrat'],
    ['H4–H5', 'Fuseau et devise', 'Africa/Nouakchott (UTC+0) ; montants en MRU'],
    ['H6', 'Signature', 'Intégrée : code à usage unique + sceau PAdES'],
    ['H8', 'Identifiants', 'NNI 10 chiffres, NIF 8 chiffres, téléphone +222 suivi de 8 chiffres'],
    ['H9', 'Cycle de vie de l’abonné', 'Créé « en attente », activé, suspendu (réversible) ou désactivé (définitif) ; l’email de connexion n’est pas modifiable'],
  ], [900, 2200, 5970], 'Hypothèses de cadrage'),
];

// ---------- chapitre 3 ----------
const ch3 = [
  H1('Chapitre 3 — Conception'),
  H2('3.1 Architecture générale'),
  P('L’application est un **monolithe modulaire** : un seul backend déployé, découpé en modules métier aux frontières vérifiées par des tests. Ce choix évite la complexité d’une architecture en microservices, disproportionnée pour une seule personne, tout en gardant un code organisé par domaine. Nginx est le seul point d’entrée : il sert l’interface, transmet `/api` au backend et `/auth` à Keycloak, et applique TLS, en-têtes de sécurité et limitation de débit.'),
  ...fig('d-architecture.png', 'Architecture technique du portail'),
  H2('3.2 Choix technologiques'),
  ...table(['Brique', 'Choix', 'Justification'], [
    ['Backend', 'Java 21, Spring Boot 4.1, Spring Modulith 2.1', 'Écosystème mûr pour les applications transactionnelles ; Spring Boot 3 n’avait plus de support open source en 2026 ; Modulith vérifie l’isolement des modules'],
    ['Interface', 'Angular 21, PrimeNG 21, signaux', 'Back-office riche en formulaires et tableaux ; Angular 21 en support long jusqu’en mai 2027'],
    ['Authentification', 'Keycloak 26 (OpenID Connect)', 'Mots de passe, sessions, MFA et protection contre la force brute sans développement spécifique'],
    ['Base de données', 'PostgreSQL 16, Flyway', 'Fiabilité transactionnelle ; contraintes d’unicité pour l’idempotence ; migrations versionnées (V1 à V18)'],
    ['Documents', 'Stockage S3 (SeaweedFS), OpenHTMLtoPDF, EU DSS', 'API S3 standard et interchangeable ; PDF depuis des modèles HTML ; sceau PAdES conforme aux normes européennes'],
    ['Fournisseurs', 'Simulateur Spring Boot séparé', 'Avancer sans accès sandbox ; mêmes contrats d’API que les vrais fournisseurs'],
    ['Exploitation', 'Docker Compose, Nginx', 'Déploiement reproductible sur un serveur unique ; TLS et sécurité en frontal'],
    ['Tests', 'JUnit, Testcontainers, WireMock, Vitest, Playwright', 'Tests d’intégration sur une vraie base PostgreSQL et un vrai stockage S3 ; tests de bout en bout dans un navigateur'],
  ], [1500, 2600, 4970], 'Choix technologiques et justification'),
  H2('3.3 Découpage en modules'),
  P('Chaque module n’expose que ses services publics. Un test d’architecture (`ModularityTests`) échoue si un module accède aux classes internes d’un autre.'),
  ...table(['Module', 'Responsabilité'], [
    ['auth', 'Compte local relié à Keycloak, rôles ADMIN / ABONNE, sessions'],
    ['subscriber', 'Abonnés, coordonnées, NNI / NIF chiffrés, changement de coordonnées par code'],
    ['catalog', 'Moyens de paiement, fournisseurs, logos, conditions'],
    ['contract', 'Modèles, contrats, génération PDF, avenants, historique des versions'],
    ['signature', 'Code à usage unique, sceau PAdES, preuves de signature'],
    ['entitlement', 'Moteur des droits : ouverture, suspension, révocation, contrôle de chaque paiement'],
    ['gateway', 'Adaptateurs fournisseurs, webhooks HMAC, état des fournisseurs'],
    ['transaction', 'Paiements, statuts, commission, reçus, relevés, exports, rapprochement'],
    ['notification', 'Emails et préférences de notification'],
    ['dashboard', 'Tableaux de bord administrateur et abonné'],
    ['audit', 'Journal des actions sensibles'],
    ['document', 'Rendu HTML vers PDF et stockage S3, partagé par les autres modules'],
  ], [1800, 7270], 'Modules du backend'),
  H2('3.4 Modèle de données'),
  P('Le modèle reprend les treize entités du document de conception, complétées par quelques tables techniques. Plusieurs règles métier sont portées directement par la base, ce qui les rend vraies même en cas d’accès concurrents : un **index unique partiel** interdit deux contrats en vigueur pour un même abonné, une contrainte d’unicité garantit un seul droit par contrat et par moyen, et la clé d’idempotence d’un paiement est unique par abonné.'),
  ...fig('d-donnees.png', 'Modèle de données simplifié'),
  H2('3.5 Workflow principal'),
  P('Le parcours de bout en bout, de la création de l’abonné à son premier paiement, enchaîne six étapes. Les trois premières relèvent de l’administrateur, la signature de l’abonné ; l’activation est automatique.'),
  ...fig('d-workflow.png', 'Du nouvel abonné au premier paiement'),
  H2('3.6 Cycle de vie du contrat et des droits'),
  P('Le statut du contrat détermine celui des droits. Le changement de statut d’un contrat et la mise à jour de ses droits sont toujours faits **dans la même transaction de base de données**, de sorte qu’aucun droit ne peut rester ouvert sans contrat actif. L’avenant, créé depuis un contrat actif, permet de modifier les moyens ou les plafonds sans coupure de service : l’original reste en vigueur jusqu’à la signature de l’avenant, puis passe à l’état « Remplacé ».'),
  ...fig('d-contrat-etats.png', 'Cycle de vie d’un contrat'),
  H2('3.7 Flux de paiement'),
  P('Un paiement se déroule en trois temps : enregistrement de la transaction, appel du fournisseur **en dehors de toute transaction de base de données** (pour ne jamais bloquer une connexion pendant un appel réseau), puis enregistrement du résultat. Le résultat arrive soit dans la réponse du fournisseur, soit par un webhook signé ; une tâche de rapprochement redemande toutes les deux minutes le statut des paiements restés en cours.'),
  ...fig('d-paiement.png', 'Séquence d’un paiement'),
  H2('3.8 Sécurité dès la conception'),
  B('**Authentification** déléguée à Keycloak (OpenID Connect, code + PKCE) ; MFA obligatoire pour le rôle administrateur.'),
  B('**Autorisation** côté serveur : règles par préfixe d’URL (`/api/admin/**`, `/api/abonne/**`), refus par défaut, abonné toujours déduit du jeton, jamais d’un paramètre.'),
  B('**Aucune donnée de carte** ne transite par le portail : la carte se saisit sur la page de la passerelle.'),
  B('**Codes à usage unique** jamais stockés en clair (empreinte HMAC ou salée), durée de vie de 5 minutes, nombre d’essais limité.'),
  B('**Intégrité des documents** : empreinte SHA-256 de chaque PDF envoyé, revérifiée avant chaque lecture ; sceau PAdES après signature.'),
  B('**Webhooks** authentifiés par HMAC du corps, horodatés (fenêtre de 5 minutes) et non rejouables (identifiant unique en base).'),
  B('**NNI / NIF chiffrés** en AES-256-GCM, avec une empreinte HMAC pour contrôler l’unicité sans déchiffrer.'),
];

// ---------- chapitre 4 ----------
const ch4 = [
  H1('Chapitre 4 — Réalisation'),
  H2('4.1 Environnement de développement'),
  P('Le développement s’est fait sur un poste Windows avec Docker Desktop. Deux environnements coexistent : un environnement de **développement** (services exposés en clair, emails capturés par Mailpit, code MFA reçu par email) et un environnement de **recette** en HTTPS (`https://localhost:8443`), configuré comme la production. Le dépôt unique contient le backend, l’interface, le simulateur, l’infrastructure et la documentation.'),
  ...table(['Partie', 'Volume', 'Remarque'], [
    ['Backend (Java)', '≈ 10 000 lignes, 149 fichiers', '12 modules, 18 migrations de base'],
    ['Tests backend', '≈ 2 600 lignes', '79 tests d’intégration'],
    ['Interface (TypeScript, HTML, SCSS)', '≈ 11 200 lignes', 'Espaces admin et abonné, pages publiques'],
    ['Simulateur des fournisseurs', '≈ 1 150 lignes', 'Bankily, Masrvi, passerelle Visa'],
    ['Extension Keycloak', '≈ 370 lignes', 'Authentificateur MFA WhatsApp / email'],
  ], [3000, 2600, 3470], 'Volume du code réalisé'),
  H2('4.2 Connexion et pages publiques'),
  P('La connexion passe par Keycloak, dont la page a été mise aux couleurs du portail par un thème dédié. Une page publique de statut affiche la disponibilité des moyens de paiement, et une page de vérification permet à quiconque de contrôler l’authenticité d’un reçu grâce à son QR code.'),
  ...fig('02-connexion-keycloak.png', 'Page de connexion (thème Keycloak du portail)', 520),
  H2('4.3 Espace administrateur'),
  H3('Tableau de bord'),
  P('Le tableau de bord donne les indicateurs du mois (abonnés actifs, contrats à signer, nombre et montant des transactions, commissions), comparés à la même période du mois précédent, la répartition des abonnés, contrats et transactions par statut, le volume quotidien et la répartition par moyen, type et fournisseur. Le pied du menu indique en continu l’état des fournisseurs.'),
  ...fig('10-admin-tableau-de-bord.png', 'Tableau de bord de l’administrateur'),
  H3('Gestion des abonnés'),
  P('L’administrateur crée un abonné (personne physique avec NNI ou personne morale avec NIF). Le backend crée alors automatiquement son compte dans Keycloak et lui envoie une invitation à choisir son mot de passe (lien valable 72 heures) : aucun mot de passe ne transite par le portail. La liste se filtre par statut, type ou texte, et la fiche regroupe coordonnées, contrats, moyens autorisés, dernières transactions et relevé mensuel.'),
  ...fig('11-admin-abonnes.png', 'Liste des abonnés'),
  ...fig('12-admin-fiche-abonne.png', 'Fiche d’un abonné', 470),
  H3('Catalogue des moyens de paiement'),
  P('Le catalogue liste les moyens (Bankily, Masrvi, Visa, Mastercard, Sedad), leur fournisseur, leur logo, leurs conditions et leur état. Un nouveau moyen est rattaché à un fournisseur déjà raccordé : le paiement est routé par le fournisseur, si bien qu’un moyen ajouté fonctionne sans code supplémentaire. Un bouton teste la connexion à chaque fournisseur.'),
  ...fig('13-admin-moyens.png', 'Catalogue des moyens de paiement'),
  H3('Contrats'),
  P('Un assistant en quatre étapes guide la création d’un contrat : abonné et modèle, moyens inclus avec leurs plafonds par opération et par mois, conditions (dates, commission), puis vérification. Le contrat est d’abord un brouillon modifiable ; à l’envoi, son PDF est généré à partir du modèle versionné, figé et son empreinte enregistrée, et l’abonné est prévenu par email. La fiche du contrat permet ensuite de suivre la signature, de suspendre, résilier ou créer un avenant, et affiche les droits ouverts et l’historique des versions.'),
  ...fig('15-admin-assistant-contrat.png', 'Assistant de création d’un contrat (étape 1)'),
  ...fig('14-admin-contrats.png', 'Liste des contrats et de leurs avenants'),
  P('Les modèles de contrat sont versionnés : l’écran des modèles montre chaque version avec le nombre de contrats qui l’utilisent et permet d’en prévisualiser le rendu. Le contenu d’un modèle n’est pas modifiable depuis l’écran, car le moteur de modèles exécute du code : une saisie libre ouvrirait la porte à une injection. Une nouvelle version arrive par une migration de base, ce qui garantit que les contrats déjà envoyés gardent leur texte.'),
  H3('Transactions et audit'),
  P('L’historique des transactions regroupe tous les abonnés, avec des filtres (statut, moyen, période, référence), la commission de chaque paiement, les totaux de la sélection, les reçus et un export Excel / CSV. Le journal d’audit trace les connexions, refus d’accès, actions administratives, signatures, paiements et changements de coordonnées, avec le détail de chaque événement.'),
  ...fig('18-admin-transactions.png', 'Historique des transactions (administrateur)'),
  ...fig('19-admin-audit.png', 'Journal d’audit'),

  H2('4.4 Espace abonné'),
  P('L’abonné accède à un espace plus simple, organisé autour de son contrat. L’accueil résume la situation du contrat, les paiements du mois et la consommation de chaque plafond mensuel.'),
  ...fig('20-abonne-accueil.png', 'Accueil de l’abonné'),
  P('La page « Moyens de paiement » n’affiche **que** les moyens ouverts par le contrat actif, avec leurs plafonds et instructions. Le paiement se fait dans un panneau latéral : montant, numéro du wallet, motif. Pour un wallet, l’abonné confirme ensuite dans l’application du fournisseur et l’écran suit le résultat en direct ; pour une carte, il est redirigé vers la page sécurisée de la passerelle.'),
  ...fig('23-abonne-paiement.png', 'Paiement avec un wallet depuis l’espace abonné'),
  P('L’historique des paiements se filtre, s’exporte et donne accès aux reçus PDF. Depuis la même page, l’abonné télécharge son **relevé mensuel** : la liste des paiements du mois, le total encaissé par moyen et les commissions dues, document qui tient aussi lieu de facture mensuelle des commissions.'),
  ...fig('24-abonne-transactions.png', 'Historique des paiements et relevé mensuel'),
  P('Le profil permet de modifier le téléphone et l’adresse, avec confirmation par un code envoyé à l’email du compte et un avis de sécurité, de changer de mot de passe, de fermer des sessions ouvertes et de régler ses notifications.'),

  H2('4.5 Signature électronique'),
  P('Faute de prestataire de signature disponible pour un projet de stage, la signature a été réalisée de manière intégrée, en combinant deux éléments :'),
  N('le **consentement** de l’abonné, prouvé par un code à usage unique à 6 chiffres envoyé à son email (valable 5 minutes, 3 essais, renvoi après 60 secondes) ;', 'num2'),
  N('un **sceau PAdES** (niveau BASELINE-B, bibliothèque EU DSS) apposé par le portail sur le PDF, qui atteste son intégrité : toute modification ultérieure du document invalide le sceau.', 'num2'),
  P('Avant de sceller, le portail revérifie l’empreinte du PDF envoyé. La preuve conservée comprend la date et l’heure, l’adresse IP, le navigateur, les empreintes du document avant et après signature, et un cachet visible est dessiné dans le cadre prévu du contrat. Le PDF signé est archivé à côté de l’original.'),
  ...fig('31-contrat-signature.png', 'Dernière page d’un contrat signé, avec le cachet de signature', 540),

  H2('4.6 Moteur des droits'),
  P('Juste après la signature, un écouteur active le contrat et ouvre **un droit par moyen inscrit** ; l’abonné en attente devient actif. L’activation est idempotente : rejouée, elle ne crée pas de doublon, et une tâche quotidienne rattrape une activation manquée. À chaque demande de paiement, le moteur vérifie dans l’ordre :'),
  B('que l’abonné est actif et que le droit sur ce moyen est actif ;'),
  B('que la date du jour est dans la période de validité du contrat ;'),
  B('que le moyen est actif au catalogue ;'),
  B('que le montant respecte le plafond par opération, et que le cumul du mois (paiements réussis **et en cours**) respecte le plafond mensuel.'),
  P('Un moyen non prévu est refusé avec l’erreur `NOT_ENTITLED` **avant** tout appel au fournisseur. La suspension d’un abonné bloque ses paiements sans toucher aux droits ; la suspension d’un contrat suspend ses droits : ce sont deux décisions distinctes, réversibles indépendamment.'),

  H2('4.7 Paiements, fournisseurs et rapprochement'),
  P('Chaque fournisseur est intégré par un **adaptateur** qui traduit son API vers un modèle commun. Le simulateur imite Bankily, Masrvi (avec ses propres formats) et la passerelle carte (page hébergée), et permet de provoquer chaque cas : succès, refus, solde insuffisant, délai, ou absence de webhook, selon les centimes du montant.'),
  ...table(['Mécanisme', 'Problème évité', 'Réalisation'], [
    ['Clé d’idempotence', 'Double débit après un double clic ou une coupure réseau', 'En-tête `Idempotency-Key` unique par abonné : la même demande renvoie la même transaction'],
    ['Webhooks signés', 'Notification falsifiée ou rejouée', 'HMAC-SHA256 du corps brut, horodatage ±5 min, identifiant d’événement unique en base'],
    ['Rapprochement', 'Paiement bloqué « en cours » après un webhook perdu', 'Tâche toutes les 2 minutes qui redemande le statut au fournisseur'],
    ['Appel hors transaction', 'Connexion à la base bloquée pendant un appel réseau', 'Enregistrement, appel, puis enregistrement du résultat dans trois étapes distinctes'],
    ['État final immuable', 'Statut modifié par un message tardif', 'Une transaction réussie, échouée ou annulée ne change plus'],
  ], [1900, 3000, 4170], 'Mécanismes de fiabilité des paiements'),
  H2('4.8 Commission, reçus et relevés'),
  P('Le taux de commission du contrat est **figé sur la transaction** au moment de sa création ; le montant (montant × taux, arrondi au centime) est calculé quand le paiement réussit, jamais sur un échec ou une annulation. Un avenant ultérieur ne modifie donc pas les commissions déjà dues. La commission apparaît dans les listes, les reçus, les exports, les totaux et le tableau de bord.'),
  P('Chaque paiement réussi produit un reçu PDF portant un QR code. La page publique ouverte par ce QR ne révèle le reçu que si le code de vérification (HMAC de la référence et du montant) est correct, ce qui empêche d’énumérer les paiements. Le relevé mensuel, enfin, récapitule l’activité d’un mois.'),
  ...fig('33-releve.png', 'Relevé mensuel PDF d’un abonné', 470),

  H2('4.9 Notifications, langues et accessibilité'),
  P('Les événements importants déclenchent un email (contrat disponible, contrat activé, paiement réussi ou échoué, changement de coordonnées), envoyé après validation de la transaction et en arrière-plan : une panne de messagerie n’annule rien. L’interface est entièrement utilisable en **arabe**, avec une mise en page de droite à gauche, ainsi qu’en mode sombre, et elle s’adapte aux écrans de téléphone (les tableaux deviennent des cartes).'),
  ...fig('26-abonne-accueil-arabe.png', 'Accueil de l’abonné en arabe (écriture de droite à gauche)'),

  H2('4.10 Double authentification des administrateurs par WhatsApp'),
  P('Les administrateurs confirment leur connexion par un code à 6 chiffres reçu sur **WhatsApp**, envoyé via l’API Messages de Vonage. Cette étape est réalisée par une extension Keycloak développée pour le projet : code valable 5 minutes, 5 essais, pas de renvoi dans la minute, et seule une empreinte salée du code est conservée. Comme les tests automatisés ne peuvent pas lire WhatsApp, l’extension propose aussi un canal email, réservé au développement et au compte de test, et qu’un administrateur ne peut pas activer lui-même.'),
];

// ---------- chapitre 5 ----------
const ch5 = [
  H1('Chapitre 5 — Tests, sécurité et déploiement'),
  H2('5.1 Stratégie de tests'),
  P('Les tests ont été écrits au fil du développement, sur une vraie base PostgreSQL et un vrai stockage S3 démarrés par Testcontainers, plutôt que sur des simulations de la base : les contraintes d’unicité, les index partiels et les migrations sont ainsi réellement exercés.'),
  ...table(['Niveau', 'Outil', 'Nombre', 'Ce qui est vérifié'], [
    ['Intégration backend', 'JUnit 5, Testcontainers, MockMvc', '79', 'Workflow complet, règles de sécurité, droits, paiements, webhooks, avenants, commissions, relevés, chiffrement'],
    ['Architecture', 'Spring Modulith', '1', 'Aucun module ne contourne l’interface publique d’un autre'],
    ['Adaptateurs', 'WireMock', 'inclus', 'Formats, délais et erreurs des trois fournisseurs'],
    ['Simulateur', 'JUnit 5', '8', 'Scénarios de paiement et signature des webhooks'],
    ['Unitaires interface', 'Vitest', '14', 'Composants et services Angular'],
    ['Bout en bout', 'Playwright', '23', 'Parcours réels sur la recette HTTPS : création, signature, paiement, reçus, relevé, arabe, mobile'],
  ], [1800, 2300, 900, 4070], 'Tests automatisés'),
  P('Le scénario de bout en bout le plus complet rejoue le parcours de la démonstration : l’administrateur crée un abonné, prépare et envoie son contrat ; l’abonné définit son mot de passe depuis l’email d’invitation, signe avec le code reçu, puis paie. Le code MFA de l’administrateur de test est lu dans Mailpit.'),
  H2('5.2 Conformité au document de conception'),
  P('Une relecture complète du document de conception a été faite à la fin du projet : chaque exigence est reportée dans une matrice qui indique où elle est réalisée et quel test le prouve. Toutes les exigences du périmètre du stage sont réalisées ; les seules exceptions sont explicitement hors périmètre (vraies API des fournisseurs, remboursements, rôles administrateur fins) et justifiées dans le registre des décisions.'),
  H2('5.3 Sécurité'),
  P('En plus des mesures de conception décrites au chapitre 3, un scan **OWASP ZAP** a été exécuté sur la recette. Il ne remonte aucun échec (63 contrôles réussis) et deux avertissements acceptés et justifiés : `style-src \'unsafe-inline\'`, exigé par la bibliothèque de composants, et l’absence d’en-tête Cross-Origin-Embedder-Policy, qui bloquerait les polices externes. Le scan a conduit à ajouter des en-têtes de sécurité (Permissions-Policy, COOP, CORP) sur toutes les réponses.'),
  ...table(['Mesure', 'Mise en œuvre'], [
    ['Transport', 'TLS 1.2 et 1.3, HSTS'],
    ['Navigateur', 'Content-Security-Policy stricte, X-Frame-Options DENY, X-Content-Type-Options, Referrer-Policy'],
    ['Comptes', 'Politique de mot de passe et protection contre la force brute (Keycloak), MFA administrateur'],
    ['Débit', 'Limitation des requêtes d’authentification par Nginx'],
    ['Données', 'NNI / NIF chiffrés (AES-256-GCM), aucune donnée de carte, codes jamais en clair'],
    ['Secrets', 'Variables d’environnement hors dépôt, clés jamais versionnées'],
  ], [2000, 7070], 'Principales mesures de sécurité'),
  H2('5.4 Déploiement et exploitation'),
  P('La production est décrite par un fichier Docker Compose : Nginx, backend, Keycloak, PostgreSQL, stockage S3, simulateur et un service de sauvegarde. Le service de sauvegarde exporte chaque jour la base du portail et celle de Keycloak (vérifiées par `pg_restore --list`), copie les documents (contrats, PDF signés, reçus), peut les envoyer vers un stockage externe et supprime les sauvegardes trop anciennes. La restauration a été testée sur une base temporaire, avec les mêmes nombres de lignes qu’à l’origine.'),
  note('La clé de chiffrement des NNI / NIF doit être conservée avec les sauvegardes, hors du serveur : sans elle, les identifiants restaurés seraient illisibles.'),
  P('L’intégration continue (GitHub Actions) compile et teste le backend et l’interface à chaque envoi. Des guides d’une page pour l’administrateur et pour l’abonné, un guide de mise en production et un scénario de démonstration complètent la livraison.'),
];

// ---------- chapitre 6 ----------
const ch6 = [
  H1('Chapitre 6 — Bilan et perspectives'),
  H2('6.1 Résultats'),
  P('Les trois jalons du plan ont été atteints : le workflow complet est démontrable, les paiements simulés sont réalistes sur les trois moyens de la démonstration (un paiement réussi et un refusé sur chacun), et la version pilote tourne sur l’environnement de recette en HTTPS avec des données de démonstration. Plusieurs fonctions ont été ajoutées au-delà du minimum demandé : avenants, relevés mensuels, reçus vérifiables par QR code, interface en arabe, mode sombre, recherche globale et double authentification par WhatsApp.'),
  H2('6.2 Difficultés rencontrées et solutions'),
  ...table(['Difficulté', 'Solution apportée'], [
    ['Aucun accès aux API réelles des fournisseurs', 'Simulateur fidèle aux contrats d’API documentés, architecture par adaptateurs prête pour les vraies API'],
    ['Un webhook perdu laissait un paiement « en cours » indéfiniment (constaté en recette)', 'Tâche de rapprochement toutes les 2 minutes ; identifiants d’événements aléatoires dans le simulateur'],
    ['Paiements simultanés pouvant dépasser un plafond mensuel', 'Le cumul compte les paiements réussis et en cours ; contraintes d’unicité en base'],
    ['Pas de prestataire de signature électronique', 'Consentement par code à usage unique + sceau PAdES du portail, preuves conservées'],
    ['Versions en fin de support (Spring Boot 3, Angular 20) et image MinIO plus publiée', 'Passage à Spring Boot 4.1 et Angular 21 ; stockage SeaweedFS derrière l’API S3 standard'],
    ['Les tests automatisés ne peuvent pas lire un code WhatsApp', 'Canal email réservé au compte de test, lu dans Mailpit'],
    ['Keycloak ignorait silencieusement les attributs non déclarés (numéro, canal MFA)', 'Déclaration des attributs dans le profil utilisateur, modifiables par un administrateur Keycloak seulement'],
    ['Le bac à sable WhatsApp de Vonage ne livre qu’aux numéros inscrits depuis moins de 24 h', 'Procédure documentée : rejoindre le bac à sable avant une démonstration ; un compte WhatsApp Business validé en production'],
  ], [4000, 5070], 'Difficultés et solutions'),
  H2('6.3 Compétences acquises'),
  B('Conception d’une application métier complète, de l’analyse des besoins à la mise en ligne, en arbitrant et en documentant chaque décision.'),
  B('Développement backend Java / Spring Boot : modules, transactions, sécurité OAuth2, tâches planifiées, génération de PDF et signature électronique.'),
  B('Développement d’interface Angular moderne (signaux, composants autonomes), internationalisation et écriture de droite à gauche.'),
  B('Intégration de systèmes externes fiable : idempotence, webhooks signés, rapprochement.'),
  B('Sécurité applicative : authentification forte, chiffrement, en-têtes, audit, scan de vulnérabilités.'),
  B('Tests automatisés à plusieurs niveaux et exploitation avec Docker (déploiement, sauvegardes, restauration).'),
  H2('6.4 Limites et perspectives'),
  P('Le portail est prêt à servir de version pilote ; plusieurs évolutions restent à mener pour un usage en production réelle :'),
  B('raccorder les **API réelles** de Bankily, Masrvi et de la passerelle bancaire (seule la configuration des adaptateurs change) ;'),
  B('ajouter les **remboursements et contestations**, déjà prévus dans les statuts de transaction ;'),
  B('utiliser un **certificat de cachet reconnu** et un horodatage externe pour le sceau des contrats ;'),
  B('introduire des **rôles administrateur fins** (opérateur, auditeur) et plusieurs utilisateurs par personne morale ;'),
  B('traduire en arabe les **messages dynamiques** du serveur (une internationalisation par clés) ;'),
  B('proposer aux abonnés des **liens ou QR codes de paiement**, des paiements récurrents et une API avec webhooks.'),
];

const conclusion = [
  H1('Conclusion générale'),
  P('Ce stage avait pour objectif de concevoir et de réaliser un portail de paiement électronique dans lequel le contrat signé décide seul des droits de chaque abonné. Cet objectif est atteint : la plateforme couvre l’intégralité du cycle, de la création de l’abonné au suivi de ses paiements, et le principe fondamental est garanti côté serveur, à chaque paiement, par un moteur des droits alimenté uniquement par le contrat actif.'),
  P('Au-delà des fonctions, le projet a mis l’accent sur la **fiabilité** et la **preuve** : documents figés et scellés, paiements idempotents, webhooks authentifiés, rapprochement automatique, audit complet et tests automatisés à tous les niveaux. Les choix techniques, documentés au fil de l’eau, laissent une base saine et modulaire, prête à être raccordée aux vrais fournisseurs.'),
  P('Sur le plan personnel, ce projet m’a permis de mener seul une application complète, de la lecture d’un cahier de conception jusqu’à une version pilote en ligne, et de mesurer l’importance de la rigueur dans un domaine — le paiement — où chaque erreur a un coût réel.'),
];

const biblio = [
  H1('Références'),
  B('Spring Boot — documentation de référence, https://docs.spring.io/spring-boot/'),
  B('Spring Modulith — documentation de référence, https://docs.spring.io/spring-modulith/'),
  B('Angular — documentation officielle, https://angular.dev/'),
  B('PrimeNG — bibliothèque de composants, https://primeng.org/'),
  B('Keycloak — Server Administration Guide et Server Developer Guide, https://www.keycloak.org/documentation'),
  B('PostgreSQL 16 — documentation, https://www.postgresql.org/docs/16/'),
  B('ETSI EN 319 142 — PAdES digital signatures, Institut européen des normes de télécommunication'),
  B('Digital Signature Service (EU DSS) — Commission européenne, https://github.com/esig/dss'),
  B('OWASP ZAP — documentation, https://www.zaproxy.org/docs/'),
  B('Playwright — documentation, https://playwright.dev/'),
  B('Vonage Messages API — documentation, https://developer.vonage.com/en/messages/overview'),
  B('Document de conception du Portail E-Payment, version 1.0, novembre 2026 (document interne)'),
];

const annexes = [
  H1('Annexes'),
  H2('Annexe A — Registre des principales décisions'),
  ...table(['N°', 'Décision', 'Raison'], [
    ['D1', 'Monolithe modulaire Spring Boot + Spring Modulith, Java 21', 'Un seul déploiement, modules isolés'],
    ['D3', 'Keycloak (OIDC) pour l’authentification et la MFA', 'Sessions, mots de passe et MFA sans développement'],
    ['D4', 'PostgreSQL 16 + Flyway ; idempotence par contraintes d’unicité', 'Fiabilité transactionnelle'],
    ['D7', 'Fournisseurs simulés par une application séparée envoyant des webhooks HMAC', 'Avancer sans accès sandbox'],
    ['D9', 'Spring Boot 4.1 et Angular 21', 'Versions supportées pendant toute la durée du projet'],
    ['D15', 'Contrat envoyé figé : PDF stocké + empreinte SHA-256 revérifiée', 'Preuve d’intégrité'],
    ['D17', 'Signature = code à usage unique + sceau PAdES du portail', 'Pas de prestataire disponible ; prêt pour un prestataire externe'],
    ['D18', 'Activation immédiate après signature, rattrapage quotidien', 'Aucun droit ouvert sans contrat actif'],
    ['D20', 'Webhooks sécurisés par HMAC, horodatage et unicité', 'Le fournisseur n’a pas de jeton ; rejeu neutralisé'],
    ['D23', 'Paiement en trois temps, appel fournisseur hors transaction', 'Pas de connexion bloquée ; plafonds respectés'],
    ['D24', 'Rapprochement toutes les 2 minutes', 'Un webhook perdu ne bloque jamais une transaction'],
    ['D26', 'Reçu vérifiable par QR code signé HMAC', 'Vérification sans connexion, sans énumération'],
    ['D29', 'Avenant relié au contrat d’origine, qui passe « Remplacé » à son activation', 'Pas de coupure de service ; un seul contrat en vigueur'],
    ['D30', 'NNI / NIF chiffrés en AES-256-GCM + empreinte HMAC', 'Une fuite de la base n’expose pas les identifiants'],
    ['D32', 'Commission figée sur la transaction, calculée au succès', 'Les commissions dues ne changent pas après coup'],
    ['D33', 'MFA administrateur par code WhatsApp (Vonage), canal email pour les tests', 'Authentification forte sans application à installer'],
  ], [800, 4600, 3670], 'Extrait du registre des décisions'),
  H2('Annexe B — Documents produits par le portail'),
  P('Première page d’un contrat généré par le portail, et reçu d’un paiement réussi avec son QR code de vérification.'),
  ...fig('30-contrat.png', 'Première page d’un contrat', 430),
  ...fig('32-recu.png', 'Reçu de paiement avec QR code de vérification', 360),
  H2('Annexe C — Autres écrans'),
  ...fig('22-abonne-moyens.png', 'Moyens de paiement autorisés par le contrat'),
  ...fig('16-admin-modeles.png', 'Modèles de contrat versionnés'),
  ...fig('03-statut.png', 'Page publique de statut des moyens de paiement'),
  ...fig('27-abonne-mobile.png', 'Espace abonné sur téléphone', 260),
];

// ---------- document ----------
const header = new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1', space: 4 } },
  children: [new TextRun({ text: 'Rapport de stage — Portail E-Payment', size: 17, color: MUTED })] })] });
const footer = new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
  children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: MUTED })] })] });

const doc = new Document({
  creator: 'Portail E-Payment', title: 'Rapport de stage — Portail E-Payment', language: 'fr-FR',
  features: { updateFields: true },
  styles: {
    default: { document: { run: { font: FONT, size: 22 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: FONT, size: 34, bold: true, color: BRAND }, paragraph: { spacing: { before: 240, after: 240 }, keepNext: true, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: FONT, size: 27, bold: true, color: BRAND }, paragraph: { spacing: { before: 300, after: 120 }, keepNext: true, outlineLevel: 1 } },
      { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: FONT, size: 23, bold: true, color: '1F2937' }, paragraph: { spacing: { before: 200, after: 80 }, keepNext: true, outlineLevel: 2 } },
      { id: 'Legende', name: 'Légende', basedOn: 'Normal', run: { font: FONT, size: 19, italics: true, color: MUTED }, paragraph: { spacing: { before: 40, after: 200 } } },
    ],
  },
  numbering: { config: [
    { reference: 'puces', levels: [
      { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } },
      { level: 1, format: LevelFormat.BULLET, text: '–', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 1080, hanging: 270 } } } }] },
    { reference: 'num', levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 360 } } } }] },
    { reference: 'num2', levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 360 } } } }] },
  ] },
  sections: [
    { properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1417, bottom: 1417, left: 1418, right: 1418 } } }, children: cover },
    { properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1417, bottom: 1417, left: 1418, right: 1418 }, pageNumbers: { start: 1 } } },
      headers: { default: header }, footers: { default: footer },
      children: [...front, ...intro, ...ch1, ...ch2, ...ch3, ...ch4, ...ch5, ...ch6, ...conclusion, ...biblio, ...annexes] },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(ROOT, 'Rapport_de_stage_Portail_E-Payment.docx');
  fs.writeFileSync(out, buf);
  console.log(out, figNo, 'figures', tabNo, 'tableaux');
});
