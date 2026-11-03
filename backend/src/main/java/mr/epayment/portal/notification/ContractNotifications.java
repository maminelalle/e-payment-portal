package mr.epayment.portal.notification;

import mr.epayment.portal.contract.ContractActivated;
import mr.epayment.portal.contract.ContractSentForSignature;
import mr.epayment.portal.notification.EmailSender.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails liés aux contrats. Envoyés après validation de la transaction et en arrière-plan :
 * une panne du serveur mail ne bloque ni n'annule l'envoi ou l'activation du contrat.
 */
@Configuration
@EnableAsync
class ContractNotifications {

    private static final Logger log = LoggerFactory.getLogger(ContractNotifications.class);

    private final EmailSender email;
    private final String portalUrl;

    ContractNotifications(EmailSender email, @Value("${epayment.portal-url}") String portalUrl) {
        this.email = email;
        this.portalUrl = portalUrl;
    }

    @Async
    @TransactionalEventListener
    void onContractSent(ContractSentForSignature event) {
        String link = portalUrl + "/abonne/contrat";
        String text = """
                Bonjour %s,

                Votre contrat %s est disponible sur le portail E-Payment.
                Connectez-vous pour le consulter, le télécharger puis le signer électroniquement :
                %s

                Vos moyens de paiement seront accessibles après signature et activation du contrat.

                Portail E-Payment""".formatted(event.subscriberName(), event.reference(), link);
        String html = """
                <p>Bonjour %s,</p>
                <p>Votre contrat <strong>%s</strong> est disponible sur le portail E-Payment.</p>
                <p><a href="%s">Consulter et signer mon contrat</a></p>
                <p>Vos moyens de paiement seront accessibles après signature et activation du contrat.</p>
                <p>Portail E-Payment</p>""".formatted(HtmlUtils.htmlEscape(event.subscriberName()),
                HtmlUtils.htmlEscape(event.reference()), HtmlUtils.htmlEscape(link));
        send(new Email(event.subscriberId(), "CONTRACT_AVAILABLE", event.subscriberEmail(),
                "Votre contrat " + event.reference() + " est prêt à être signé", text, html), event.reference());
    }

    @Async
    @TransactionalEventListener
    void onContractActivated(ContractActivated event) {
        String link = portalUrl + "/abonne";
        String text = """
                Bonjour %s,

                Votre contrat %s est actif. Les moyens de paiement qu'il prévoit sont désormais disponibles
                dans votre espace :
                %s

                Le contrat signé reste téléchargeable dans « Mon contrat ».

                Portail E-Payment""".formatted(event.subscriberName(), event.reference(), link);
        String html = """
                <p>Bonjour %s,</p>
                <p>Votre contrat <strong>%s</strong> est actif. Les moyens de paiement qu'il prévoit sont
                désormais disponibles dans votre espace.</p>
                <p><a href="%s">Accéder à mes moyens de paiement</a></p>
                <p>Le contrat signé reste téléchargeable dans « Mon contrat ».</p>
                <p>Portail E-Payment</p>""".formatted(HtmlUtils.htmlEscape(event.subscriberName()),
                HtmlUtils.htmlEscape(event.reference()), HtmlUtils.htmlEscape(link));
        send(new Email(event.subscriberId(), "CONTRACT_ACTIVATED", event.subscriberEmail(),
                "Votre contrat " + event.reference() + " est actif", text, html), event.reference());
    }

    private void send(Email message, String reference) {
        try {
            email.send(message, true);
        } catch (RuntimeException e) {
            log.warn("Email {} non envoyé pour {}", message.template(), reference, e);
        }
    }
}
