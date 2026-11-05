package mr.epayment.portal.subscriber;

import java.time.Duration;
import java.util.UUID;

/**
 * Emails du changement de coordonnées : le code de confirmation, puis l'avis de modification.
 * Implémenté par email (module notification) ; un SMS pourra le remplacer.
 */
public interface ContactCodeSender {

    /** @throws RuntimeException si l'envoi échoue : la demande est alors annulée. */
    void sendCode(ContactCodeMessage message);

    /** Avis de sécurité après un changement confirmé ; un échec d'envoi n'annule pas le changement. */
    void notifyChanged(UUID subscriberId, String recipientEmail, String recipientName, String phone,
            String address);

    record ContactCodeMessage(UUID subscriberId, String recipientEmail, String recipientName, String code,
            Duration validity) {

        /** Le code n'apparaît jamais dans les logs. */
        @Override
        public String toString() {
            return "ContactCodeMessage[to=" + recipientEmail + "]";
        }
    }
}
