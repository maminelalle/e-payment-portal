package mr.epayment.portal.notification;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Envoi d'un email texte + HTML, tracé dans la table notification. */
@Component
class EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationLog log;
    private final String from;

    EmailSender(JavaMailSender mailSender, NotificationLog log, @Value("${epayment.mail-from}") String from) {
        this.mailSender = mailSender;
        this.log = log;
        this.from = from;
    }

    /**
     * @param keepBody faux pour un message contenant un secret (code de signature) : seul l'objet est conservé
     * @throws RuntimeException si l'envoi échoue (l'échec est tracé)
     */
    void send(Email email, boolean keepBody) {
        UUID id = log.queued(email.subscriberId(), email.template(), email.to(), email.subject(),
                keepBody ? email.text() : null);
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mailSender.send(message);
            log.sent(id);
        } catch (MessagingException e) {
            log.failed(id, e.getMessage());
            throw new IllegalStateException("Préparation de l'email impossible", e);
        } catch (RuntimeException e) {
            log.failed(id, e.getMessage());
            throw e;
        }
    }

    /** @param template CONTRACT_AVAILABLE, CONTRACT_ACTIVATED, PAYMENT_SUCCEEDED, PAYMENT_FAILED, SIGNATURE_CODE */
    record Email(UUID subscriberId, String template, String to, String subject, String text, String html) {
    }
}
