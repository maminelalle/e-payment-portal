package mr.epayment.portal.notification;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import mr.epayment.portal.notification.EmailSender.Email;
import mr.epayment.portal.shared.Amounts;
import mr.epayment.portal.transaction.PaymentCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Email à l'abonné à la fin de chaque paiement (réussi ou échoué), après validation, en arrière-plan.
 * L'abonné peut le couper dans son profil.
 */
@Component
class PaymentNotifications {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotifications.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")
            .withZone(ZoneId.of("Africa/Nouakchott"));

    private final EmailSender email;
    private final NotificationPreferences preferences;
    private final String portalUrl;

    PaymentNotifications(EmailSender email, NotificationPreferences preferences,
            @Value("${epayment.portal-url}") String portalUrl) {
        this.email = email;
        this.preferences = preferences;
        this.portalUrl = portalUrl;
    }

    @Async
    @TransactionalEventListener
    void onPaymentCompleted(PaymentCompleted event) {
        if (!preferences.paymentEmails(event.subscriberId())) {
            return;
        }
        String amount = Amounts.format(event.amount()) + " " + event.currency();
        String when = DATE_TIME.format(event.completedAt());
        String link = portalUrl + "/abonne/transactions";
        String subject;
        String outcomeText;
        String outcomeHtml;
        if (event.succeeded()) {
            subject = "Paiement " + event.reference() + " réussi";
            outcomeText = "a réussi. Le reçu est téléchargeable dans « Mes transactions »";
            outcomeHtml = "<strong style=\"color:#15803d\">a réussi</strong>. Le reçu est téléchargeable dans "
                    + "« Mes transactions »";
        } else {
            subject = "Paiement " + event.reference() + " échoué";
            String reason = event.failureMessage() == null ? "" : " : " + event.failureMessage();
            outcomeText = "a échoué" + reason + ". Aucun montant n'a été débité";
            outcomeHtml = "<strong style=\"color:#b91c1c\">a échoué</strong>" + HtmlUtils.htmlEscape(reason)
                    + ". Aucun montant n'a été débité";
        }
        String text = """
                Bonjour %s,

                Votre paiement %s de %s par %s (%s) %s :
                %s

                Portail E-Payment""".formatted(event.subscriberName(), event.reference(), amount, event.methodName(),
                when, outcomeText, link);
        String html = """
                <p>Bonjour %s,</p>
                <p>Votre paiement <strong>%s</strong> de <strong>%s</strong> par %s (%s) %s.</p>
                <p><a href="%s">Voir mes transactions</a></p>
                <p>Portail E-Payment</p>""".formatted(HtmlUtils.htmlEscape(event.subscriberName()),
                HtmlUtils.htmlEscape(event.reference()), HtmlUtils.htmlEscape(amount),
                HtmlUtils.htmlEscape(event.methodName()), when, outcomeHtml, HtmlUtils.htmlEscape(link));
        try {
            email.send(new Email(event.subscriberId(), event.succeeded() ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED",
                    event.subscriberEmail(), subject, text, html), true);
        } catch (RuntimeException e) {
            log.warn("Email de fin de paiement non envoyé pour {}", event.reference(), e);
        }
    }
}
