package mr.epayment.portal.signature;

import java.time.Duration;

/**
 * Canal d'envoi du code de signature. Implémenté par email (module notification) ;
 * un envoi par SMS pourra le remplacer sans toucher au parcours de signature.
 */
public interface OtpSender {

    /** @throws RuntimeException si l'envoi échoue : la tentative de signature est alors annulée. */
    void send(OtpMessage message);

    record OtpMessage(String recipientEmail, String recipientName, String code, String contractReference,
            Duration validity) {

        /** Le code n'apparaît jamais dans les logs. */
        @Override
        public String toString() {
            return "OtpMessage[to=" + recipientEmail + ", contract=" + contractReference + "]";
        }
    }
}
