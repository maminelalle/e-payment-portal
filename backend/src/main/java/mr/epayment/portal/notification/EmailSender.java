package mr.epayment.portal.notification;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Envoi d'un email texte + HTML. La persistance des notifications (table notification) arrive en semaine 11. */
@Component
class EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    EmailSender(JavaMailSender mailSender, @Value("${epayment.mail-from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    void send(String to, String subject, String text, String html) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Préparation de l'email impossible", e);
        }
    }
}
