package mr.epayment.portal.notification;

import mr.epayment.portal.notification.EmailSender.Email;
import mr.epayment.portal.signature.OtpSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Code de signature envoyé par email (Mailpit en développement). Un envoi SMS pourra le remplacer. */
@Component
class EmailOtpSender implements OtpSender {

    private final EmailSender email;

    EmailOtpSender(EmailSender email) {
        this.email = email;
    }

    @Override
    public void send(OtpMessage message) {
        long minutes = message.validity().toMinutes();
        String text = """
                Bonjour %s,

                Votre code de signature du contrat %s est : %s

                Il expire dans %d minutes. Ne le communiquez à personne : le portail ne vous le demandera
                jamais par téléphone. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.

                Portail E-Payment""".formatted(message.recipientName(), message.contractReference(), message.code(),
                minutes);
        String html = """
                <p>Bonjour %s,</p>
                <p>Votre code de signature du contrat <strong>%s</strong> est :</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">%s</p>
                <p>Il expire dans %d minutes. Ne le communiquez à personne : le portail ne vous le demandera
                jamais par téléphone. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.</p>
                <p>Portail E-Payment</p>""".formatted(HtmlUtils.htmlEscape(message.recipientName()),
                HtmlUtils.htmlEscape(message.contractReference()), message.code(), minutes);
        // Corps non conservé : il contient le code.
        email.send(new Email(null, "SIGNATURE_CODE", message.recipientEmail(),
                "Votre code de signature du contrat " + message.contractReference(), text, html), false);
    }
}
