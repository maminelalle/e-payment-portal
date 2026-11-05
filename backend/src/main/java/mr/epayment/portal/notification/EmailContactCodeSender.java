package mr.epayment.portal.notification;

import java.util.UUID;
import mr.epayment.portal.notification.EmailSender.Email;
import mr.epayment.portal.subscriber.ContactCodeSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Changement de coordonnées : code de confirmation puis avis de sécurité, par email (Mailpit en développement). */
@Component
class EmailContactCodeSender implements ContactCodeSender {

    private final EmailSender email;

    EmailContactCodeSender(EmailSender email) {
        this.email = email;
    }

    @Override
    public void sendCode(ContactCodeMessage message) {
        long minutes = message.validity().toMinutes();
        String text = """
                Bonjour %s,

                Votre code pour confirmer le changement de vos coordonnées est : %s

                Il expire dans %d minutes. Ne le communiquez à personne : le portail ne vous le demandera
                jamais par téléphone. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message
                et changez votre mot de passe.

                Portail E-Payment""".formatted(message.recipientName(), message.code(), minutes);
        String html = """
                <p>Bonjour %s,</p>
                <p>Votre code pour confirmer le changement de vos coordonnées est :</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">%s</p>
                <p>Il expire dans %d minutes. Ne le communiquez à personne : le portail ne vous le demandera
                jamais par téléphone. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message
                et changez votre mot de passe.</p>
                <p>Portail E-Payment</p>""".formatted(HtmlUtils.htmlEscape(message.recipientName()), message.code(),
                minutes);
        // Corps non conservé : il contient le code.
        email.send(new Email(message.subscriberId(), "CONTACT_CHANGE_CODE", message.recipientEmail(),
                "Votre code de confirmation E-Payment", text, html), false);
    }

    @Override
    public void notifyChanged(UUID subscriberId, String recipientEmail, String recipientName, String phone,
            String address) {
        String shownAddress = address == null ? "(aucune)" : address;
        String text = """
                Bonjour %s,

                Les coordonnées de votre compte E-Payment viennent d'être modifiées :
                - téléphone : %s
                - adresse : %s

                Si vous n'êtes pas à l'origine de ce changement, changez votre mot de passe et contactez
                l'administrateur du portail.

                Portail E-Payment""".formatted(recipientName, phone, shownAddress);
        String html = """
                <p>Bonjour %s,</p>
                <p>Les coordonnées de votre compte E-Payment viennent d'être modifiées :</p>
                <ul><li>téléphone : <strong>%s</strong></li><li>adresse : <strong>%s</strong></li></ul>
                <p>Si vous n'êtes pas à l'origine de ce changement, changez votre mot de passe et contactez
                l'administrateur du portail.</p>
                <p>Portail E-Payment</p>""".formatted(HtmlUtils.htmlEscape(recipientName), HtmlUtils.htmlEscape(phone),
                HtmlUtils.htmlEscape(shownAddress));
        email.send(new Email(subscriberId, "CONTACT_CHANGED", recipientEmail,
                "Vos coordonnées E-Payment ont été modifiées",
                text, html), true);
    }
}
