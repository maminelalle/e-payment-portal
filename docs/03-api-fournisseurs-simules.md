# 03 — Contrat d'API des fournisseurs simulés

Semaine 1 · Cadrage. Implémentation prévue en semaine 8 (application `simulator/`, Spring Boot séparée).

Les vraies APIs Bankily, Masrvi et de la passerelle Visa ne sont pas encore accessibles. Le simulateur imite des APIs de paiement mobile et de passerelle carte **courantes** ; il ne prétend pas reproduire les APIs réelles. Les formats sont volontairement **différents** entre fournisseurs : c'est le rôle des adaptateurs du portail de les ramener à un modèle commun. Passer du simulateur aux vraies APIs = écrire / ajuster l'adaptateur et changer la configuration (URL, clés).

## Interface commune côté portail

```java
public interface PaymentProvider {
    String code();                                              // BANKILY, MASRVI, VISA_GATEWAY
    InitiationResult initiate(PaymentRequest request);          // démarre le paiement
    ProviderStatus fetchStatus(String providerReference);       // rapprochement
    WebhookEvent parseWebhook(HttpHeaders headers, byte[] body);// vérifie la signature puis convertit
}
```

`InitiationResult` contient la référence fournisseur, le statut normalisé et, pour Visa, l'URL de redirection.

## Règles communes

| Sujet | Règle |
|-------|-------|
| Authentification portail → fournisseur | `Authorization: Bearer <apiKey>` (une clé par fournisseur, en configuration) |
| Idempotence | En-tête `Idempotency-Key` (= `idempotency_key` de la transaction) ; même clé ⇒ même réponse, aucun second débit |
| Devise | `MRU` (montants en décimal, 2 chiffres après la virgule) |
| Webhook fournisseur → portail | `POST {portail}/api/webhooks/{provider}` (`bankily`, `masrvi`, `visa`) |
| Signature des webhooks | `X-Signature: v1=<hex(HMAC-SHA256(webhookSecret, X-Timestamp + "." + corpsBrut))>` |
| Anti-rejeu | `X-Event-Id` unique (table `webhook_event`) ; `X-Timestamp` (epoch secondes) refusé si écart > 300 s |
| Réponse du portail | `200` si traité ou déjà traité (doublon) ; `401` si signature invalide ; le simulateur réessaie 3 fois sinon (10 s, 30 s, 60 s) |

### Scénarios du simulateur

Choisis par le **montant** (pratique pour la démo) ou forcés par l'en-tête `X-Simulator-Scenario` :

| Montant se terminant par | Scénario | Résultat |
|--------------------------|----------|----------|
| `…,00` (cas général) | `SUCCESS` | Succès après ~3 s |
| `…,13` | `FAILED` | Échec générique |
| `…,51` | `INSUFFICIENT_FUNDS` | Solde insuffisant |
| `…,99` | `DELAYED` | En attente 60 s puis succès (teste le rapprochement) |
| `…,98` | `NO_WEBHOOK` | Succès, mais aucun webhook envoyé (seul le rapprochement le découvre) |

Pour Visa, le scénario dépend du numéro de carte de test (voir plus bas).

---

## Bankily (wallet) — style REST anglais

Base : `{simulator}/bankily/api/v1`

### Initier un paiement

`POST /payments`

```json
{
  "merchantReference": "TRX-000148",
  "amount": 12500.00,
  "currency": "MRU",
  "payerPhone": "+22222000001",
  "description": "Paiement E-Payment TRX-000148"
}
```

Réponse `202 Accepted` :

```json
{
  "paymentId": "BKL-7f3c9a",
  "merchantReference": "TRX-000148",
  "status": "PENDING",
  "createdAt": "2026-11-02T11:42:05Z"
}
```

L'abonné « confirme » sur son téléphone (simulé). Erreurs : `400` (champ invalide), `401` (clé invalide), `409` (même `Idempotency-Key`, corps différent).

### Consulter le statut

`GET /payments/{paymentId}` → même objet, `status` ∈ `PENDING`, `SUCCESS`, `FAILED`, avec `failureCode` (`INSUFFICIENT_FUNDS`, `DECLINED`, `TIMEOUT`).

### Webhook

```json
{
  "eventId": "evt_01JBX…",
  "type": "payment.succeeded",
  "paymentId": "BKL-7f3c9a",
  "merchantReference": "TRX-000148",
  "status": "SUCCESS",
  "amount": 12500.00,
  "currency": "MRU",
  "failureCode": null,
  "occurredAt": "2026-11-02T11:42:09Z"
}
```

`type` ∈ `payment.succeeded`, `payment.failed`.

---

## Masrvi (wallet) — style francophone, codes différents

Base : `{simulator}/masrvi/v2`

### Initier

`POST /transactions`

```json
{
  "reference": "TRX-000147",
  "montant": "4200.00",
  "devise": "MRU",
  "telephoneClient": "22246000004",
  "motif": "Paiement E-Payment"
}
```

Réponse `201 Created` :

```json
{ "idTransaction": "MSV-000912", "reference": "TRX-000147", "etat": "EN_COURS", "code": "00" }
```

### Consulter

`GET /transactions/{idTransaction}` → `etat` ∈ `EN_COURS`, `SUCCES`, `ECHEC` ; `code` : `00` OK, `51` solde insuffisant, `05` refus, `91` délai dépassé.

### Notification (webhook)

```json
{
  "evenementId": "ntf-5521",
  "idTransaction": "MSV-000912",
  "reference": "TRX-000147",
  "etat": "SUCCES",
  "code": "00",
  "montant": "4200.00",
  "dateHeure": "2026-11-02T10:15:41Z"
}
```

L'identifiant d'événement est lu dans le corps (`evenementId`) et repris dans `X-Event-Id`.

---

## Passerelle Visa — page de paiement hébergée

Le portail **ne voit jamais** le numéro de carte ni le CVV (périmètre PCI DSS SAQ A) : la saisie se fait sur la page du simulateur.

Base : `{simulator}/visa-gateway/api/v1`

### 1. Créer une session de paiement

`POST /checkout-sessions`

```json
{
  "orderReference": "TRX-000139",
  "amount": 48000.00,
  "currency": "MRU",
  "returnUrl": "https://portail/abonne/paiement/TRX-000139",
  "cancelUrl": "https://portail/abonne/paiement/TRX-000139?annule=1"
}
```

Réponse `201` :

```json
{
  "sessionId": "cs_9d2e…",
  "redirectUrl": "{simulator}/visa-gateway/pay/cs_9d2e…",
  "status": "OPEN",
  "expiresAt": "2026-11-02T16:20:00Z"
}
```

### 2. Redirection de l'abonné

Le navigateur va sur `redirectUrl` (formulaire carte + étape 3-D Secure simulée), puis revient sur `returnUrl?sessionId=…`. **Le retour navigateur n'est pas une preuve de paiement** : le portail attend le webhook ou interroge le statut.

Cartes de test :

| Numéro | Résultat |
|--------|----------|
| `4111 1111 1111 1111` | Autorisé |
| `4000 0000 0000 0002` | Refusé |
| `4000 0000 0000 9995` | Fonds insuffisants |
| `4000 0000 0000 3220` | 3-D Secure, confirmé après 60 s |

### 3. Consulter

`GET /checkout-sessions/{sessionId}` → `status` ∈ `OPEN`, `AUTHORIZED`, `DECLINED`, `EXPIRED`, avec `declineReason`.

### 4. Webhook

```json
{
  "id": "evt_v_8812",
  "type": "checkout.session.completed",
  "sessionId": "cs_9d2e…",
  "orderReference": "TRX-000139",
  "status": "AUTHORIZED",
  "amount": 48000.00,
  "currency": "MRU",
  "card": { "brand": "VISA", "maskedPan": "411111******1111", "token": "tok_4f1…" },
  "declineReason": null,
  "created": 1793616000
}
```

`type` ∈ `checkout.session.completed`, `checkout.session.declined`, `checkout.session.expired`.

---

## Correspondance des statuts → statut interne

| Interne (`payment_transaction.status`) | Bankily | Masrvi | Visa |
|----------------------------------------|---------|--------|------|
| `PENDING` | `PENDING` | `EN_COURS` | `OPEN` |
| `SUCCEEDED` | `SUCCESS` | `SUCCES` (code `00`) | `AUTHORIZED` |
| `FAILED` | `FAILED` | `ECHEC` | `DECLINED` |
| `CANCELLED` | — | — | `EXPIRED` |

Le statut brut est toujours conservé dans `provider_status`.

## Configuration côté portail

```yaml
epayment:
  providers:
    bankily:
      base-url: http://simulator:8090/bankily/api/v1
      api-key: ${BANKILY_API_KEY}
      webhook-secret: ${BANKILY_WEBHOOK_SECRET}
    masrvi:
      base-url: http://simulator:8090/masrvi/v2
      api-key: ${MASRVI_API_KEY}
      webhook-secret: ${MASRVI_WEBHOOK_SECRET}
    visa:
      base-url: http://simulator:8090/visa-gateway/api/v1
      api-key: ${VISA_API_KEY}
      webhook-secret: ${VISA_WEBHOOK_SECRET}
```
