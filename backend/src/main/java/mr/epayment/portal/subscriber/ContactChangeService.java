package mr.epayment.portal.subscriber;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.auth.SubscriberAccounts;
import mr.epayment.portal.shared.BusinessRuleException;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.shared.NotFoundException;
import mr.epayment.portal.subscriber.ContactCodeSender.ContactCodeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changement de téléphone et d'adresse par l'abonné (conception §4.5), en deux temps :
 * <ol>
 *   <li>il saisit ses nouvelles coordonnées : un code à 6 chiffres part à l'email du compte (5 minutes) ;</li>
 *   <li>il saisit le code (3 essais) : les coordonnées changent, l'audit garde l'avant/après et un avis
 *       de sécurité part par email.</li>
 * </ol>
 * Le contrat déjà signé n'est pas modifié : il garde les coordonnées du jour de la signature.
 */
@Service
public class ContactChangeService {

    static final Duration VALIDITY = Duration.ofMinutes(5);
    static final Duration RESEND_DELAY = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(ContactChangeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ContactChangeRepository changes;
    private final SubscriberRepository subscribers;
    private final CurrentSubscriber currentSubscriber;
    private final SubscriberAccounts accounts;
    private final ContactCodeSender sender;
    private final AuditService auditService;
    private final String secret;

    ContactChangeService(ContactChangeRepository changes, SubscriberRepository subscribers,
            CurrentSubscriber currentSubscriber, SubscriberAccounts accounts, ContactCodeSender sender,
            AuditService auditService,
            @Value("${epayment.profile.code-secret:${epayment.signature.otp-secret}}") String secret) {
        this.changes = changes;
        this.subscribers = subscribers;
        this.currentSubscriber = currentSubscriber;
        this.accounts = accounts;
        this.sender = sender;
        this.auditService = auditService;
        this.secret = secret;
    }

    /** Étape 1 : enregistre la demande et envoie le code. Une demande précédente encore ouverte est annulée. */
    @Transactional
    public ContactChallenge requestChange(Authentication authentication, String phone, String address) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        String newPhone = SubscriberDetails.normalizePhone(phone);
        if (newPhone == null || !newPhone.matches("\\+222[234]\\d{7}")) {
            throw new FieldValidationException(Map.of("phone",
                    "Numéro mauritanien attendu : +222 suivi de 8 chiffres commençant par 2, 3 ou 4."));
        }
        String newAddress = address == null || address.isBlank() ? null : address.trim();
        if (Objects.equals(newPhone, subscriber.phone()) && Objects.equals(newAddress, subscriber.address())) {
            throw new BusinessRuleException("Aucune modification : ce sont déjà vos coordonnées.");
        }

        Instant now = Instant.now();
        // Une demande par minute, quel que soit l'état de la précédente : après 3 mauvais codes, on ne peut pas
        // enchaîner aussitôt de nouveaux essais.
        changes.findFirstBySubscriberIdOrderByCreatedAtDesc(subscriber.id()).ifPresent(last -> {
            Instant nextAllowed = last.getCreatedAt().plus(RESEND_DELAY);
            if (now.isBefore(nextAllowed)) {
                throw new BusinessRuleException("Un code vient d'être envoyé. Vous pourrez en demander un autre dans "
                        + Duration.between(now, nextAllowed).toSeconds() + " s.");
            }
        });
        changes.findBySubscriberIdAndStatus(subscriber.id(), ContactChange.Status.PENDING)
                .forEach(ContactChange::cancel);

        UUID id = UUID.randomUUID();
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        Instant expiresAt = now.plus(VALIDITY);
        changes.saveAndFlush(new ContactChange(id, subscriber.id(), newPhone, newAddress, hash(id, code), now,
                expiresAt));

        // Envoi dans la transaction : s'il échoue, aucune demande ne reste ouverte.
        sender.sendCode(new ContactCodeMessage(subscriber.id(), subscriber.email(), subscriber.displayName(), code,
                VALIDITY));
        audit(authentication, "CONTACT_CHANGE_CODE_SENT", subscriber, Outcome.SUCCESS,
                Map.of("changeId", id.toString()));
        return new ContactChallenge(id, mask(subscriber.email()), expiresAt, now.plus(RESEND_DELAY),
                ContactChange.MAX_ATTEMPTS);
    }

    /** Étape 2 : vérifie le code puis applique le changement. */
    @Transactional(noRollbackFor = ContactCodeRejectedException.class)
    public SubscriberView confirm(Authentication authentication, UUID changeId, String code) {
        SubscriberView view = currentSubscriber.require(authentication);
        ContactChange change = changes.findById(changeId)
                .filter(c -> c.getSubscriberId().equals(view.id()))
                .orElseThrow(() -> new NotFoundException("Demande de modification introuvable."));
        if (change.getStatus() != ContactChange.Status.PENDING) {
            throw new ContactCodeRejectedException(
                    "Cette demande n'est plus valable : recommencez la modification.", 0);
        }
        Instant now = Instant.now();
        if (now.isAfter(change.getExpiresAt())) {
            change.expire();
            audit(authentication, "CONTACT_CHANGE_CODE_EXPIRED", view, Outcome.FAILURE, Map.of());
            throw new ContactCodeRejectedException("Ce code a expiré : recommencez la modification.", 0);
        }
        if (!MessageDigest.isEqual(hash(changeId, code).getBytes(StandardCharsets.US_ASCII),
                change.getCodeHash().getBytes(StandardCharsets.US_ASCII))) {
            int remaining = change.recordWrongCode();
            audit(authentication, "CONTACT_CHANGE_CODE_REJECTED", view, Outcome.FAILURE,
                    Map.of("remainingAttempts", remaining));
            throw new ContactCodeRejectedException(remaining > 0
                    ? "Code incorrect. Il vous reste " + remaining + " essai" + (remaining > 1 ? "s." : ".")
                    : "Code incorrect, nombre d'essais atteint : recommencez la modification.", remaining);
        }

        Subscriber subscriber = subscribers.findById(view.id())
                .orElseThrow(() -> new NotFoundException("Abonné introuvable."));
        subscriber.changeContact(change.getPhone(), change.getAddress());
        accounts.changePhone(view.accountId(), change.getPhone());
        change.confirm(now);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousPhone", view.phone());
        details.put("phone", change.getPhone());
        details.put("previousAddress", view.address() == null ? "" : view.address());
        details.put("address", change.getAddress() == null ? "" : change.getAddress());
        audit(authentication, "SUBSCRIBER_CONTACT_UPDATED", view, Outcome.SUCCESS, details);

        try {
            sender.notifyChanged(view.id(), view.email(), view.displayName(), change.getPhone(), change.getAddress());
        } catch (RuntimeException e) {
            log.warn("Avis de changement de coordonnées non envoyé à {}", view.reference(), e);
        }
        return subscriber.toView();
    }

    private String hash(UUID changeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("contact:" + changeId + ":" + code)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** contact@exemple.mr → c••••••@exemple.mr */
    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "••••••" : email.charAt(0) + "••••••" + email.substring(at);
    }

    private void audit(Authentication authentication, String action, SubscriberView subscriber, Outcome outcome,
            Map<String, Object> details) {
        auditService.record(AuditEvent.of(authentication, action, "SUBSCRIBER", subscriber.reference(), outcome,
                details));
    }

    /** Réponse à la demande (le code lui-même n'est jamais renvoyé). */
    public record ContactChallenge(UUID changeId, String sentTo, Instant expiresAt, Instant resendAvailableAt,
            int maxAttempts) {
    }
}
