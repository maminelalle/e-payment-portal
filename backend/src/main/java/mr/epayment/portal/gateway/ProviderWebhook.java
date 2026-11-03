package mr.epayment.portal.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import mr.epayment.portal.gateway.PaymentProvider.Outcome;

/**
 * Notification d'un fournisseur, vérifiée puis traduite dans le modèle commun du portail.
 * Publiée comme événement : le module transaction (semaine 10) met à jour le paiement correspondant.
 *
 * @param merchantReference référence du portail transmise au fournisseur à l'initiation
 * @param providerStatus    statut brut du fournisseur, conservé tel quel
 * @param maskedPan         carte uniquement : PAN masqué (jamais le numéro complet)
 */
public record ProviderWebhook(
        String providerCode,
        String eventId,
        String providerReference,
        String merchantReference,
        Outcome outcome,
        String providerStatus,
        String failureCode,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        String maskedPan,
        String cardToken) {
}
