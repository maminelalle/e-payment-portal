package mr.epayment.portal.catalog;

import java.time.Instant;
import java.util.UUID;

/** Moyen de paiement tel qu'exposé par l'API (et aux autres modules). */
public record PaymentMethodView(
        UUID id,
        String code,
        String name,
        String category,
        String shortLabel,
        String color,
        /** Adresse publique du logo, ou null : l'interface affiche alors la pastille (shortLabel + color). */
        String logoUrl,
        String description,
        String instructions,
        /** Conditions d'utilisation (wallet) ou règles d'acceptation (carte), affichées à l'abonné. */
        String conditions,
        String currency,
        PaymentMethodStatus status,
        Provider provider) {

    /** @param kind WALLET ou CARD : le type des moyens rattachés à ce fournisseur */
    public record Provider(String code, String name, String kind, String status, String healthStatus,
            Instant lastHealthCheckAt) {
    }
}
