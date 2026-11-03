package mr.epayment.portal.subscriber;

/**
 * Cycle de vie d'un abonné.
 * <pre>
 * PENDING ──activer──▶ ACTIVE ◀──activer── SUSPENDED
 *    │                   │ └──suspendre──────▲
 *    └─────────── désactiver (définitif) ────▶ DISABLED
 * </pre>
 * PENDING et ACTIVE peuvent se connecter (l'abonné en attente doit pouvoir définir son mot de passe
 * et signer son contrat) ; SUSPENDED et DISABLED ne le peuvent pas.
 */
public enum SubscriberStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    DISABLED;

    boolean canLogIn() {
        return this == PENDING || this == ACTIVE;
    }
}
