package mr.epayment.portal.signature;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.contract.ContractService;
import mr.epayment.portal.contract.ContractService.DocumentToSign;
import mr.epayment.portal.contract.ContractView;
import mr.epayment.portal.shared.BusinessRuleException;
import mr.epayment.portal.shared.NotFoundException;
import mr.epayment.portal.signature.OtpSender.OtpMessage;
import mr.epayment.portal.subscriber.CurrentSubscriber;
import mr.epayment.portal.subscriber.SubscriberView;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signature électronique du contrat par l'abonné (maquette B2) :
 * <ol>
 *   <li>l'abonné demande un code : 6 chiffres envoyés par email, valables 5 minutes ;</li>
 *   <li>il le saisit : 3 essais au maximum par code ;</li>
 *   <li>code correct : le PDF envoyé (empreinte revérifiée) est scellé PAdES et archivé ; la preuve
 *       (horodatage, IP, navigateur, empreintes avant et après) est conservée ; le contrat passe « signé ».</li>
 * </ol>
 */
@Service
@EnableConfigurationProperties(SignatureProperties.class)
public class SignatureService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration SEND_LIMIT_COOLDOWN = Duration.ofMinutes(15);
    private static final DateTimeFormatter PROOF_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm:ss")
            .withZone(ZoneId.of("Africa/Nouakchott"));

    private final ContractSignatureRepository signatures;
    private final ContractService contracts;
    private final CurrentSubscriber currentSubscriber;
    private final OtpSender otpSender;
    private final PadesSealer sealer;
    private final AuditService auditService;
    private final SignatureProperties properties;

    SignatureService(ContractSignatureRepository signatures, ContractService contracts,
            CurrentSubscriber currentSubscriber, OtpSender otpSender, PadesSealer sealer, AuditService auditService,
            SignatureProperties properties) {
        this.signatures = signatures;
        this.contracts = contracts;
        this.currentSubscriber = currentSubscriber;
        this.otpSender = otpSender;
        this.sealer = sealer;
        this.auditService = auditService;
        this.properties = properties;
    }

    /** Étape 1 : envoie (ou renvoie) le code de signature à l'abonné connecté. */
    @Transactional
    public OtpChallenge requestCode(Authentication authentication) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        DocumentToSign document = contracts.documentToSign(subscriber.id());
        Instant now = Instant.now();

        ContractSignature signature = signatures
                .findByContractIdAndStatus(document.contractId(), ContractSignature.Status.PENDING)
                .filter(pending -> reusable(pending, document, now))
                .orElseGet(() -> new ContractSignature(document.contractId(), subscriber.accountId(), document.sha256()));

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        Instant expiresAt = now.plus(properties.otpValidity());
        signature.issueCode(hash(signature.getId(), code), now, expiresAt);
        signatures.saveAndFlush(signature);

        // Envoi dans la transaction : s'il échoue, aucun code « fantôme » ne reste valide.
        otpSender.send(new OtpMessage(subscriber.email(), subscriber.displayName(), code, document.reference(),
                properties.otpValidity()));
        audit(authentication, "SIGNATURE_CODE_SENT", signature, Outcome.SUCCESS,
                Map.of("contract", document.reference(), "sentCount", signature.getOtpSentCount()));
        return new OtpChallenge(signature.getId(), mask(subscriber.email()), expiresAt,
                now.plus(properties.resendDelay()), ContractSignature.MAX_ATTEMPTS);
    }

    /** Étape 2 : vérifie le code puis scelle et archive le contrat signé. */
    @Transactional(noRollbackFor = OtpRejectedException.class)
    public ContractView confirm(Authentication authentication, UUID signatureId, String code, String ipAddress,
            String userAgent) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        DocumentToSign document = contracts.documentToSign(subscriber.id());
        ContractSignature signature = signatures.findById(signatureId)
                .filter(s -> s.getContractId().equals(document.contractId()))
                .orElseThrow(() -> new NotFoundException("Demande de signature introuvable."));
        if (signature.getStatus() != ContractSignature.Status.PENDING) {
            throw new BusinessRuleException("Cette demande n'est plus valable : demandez un nouveau code.");
        }

        Instant now = Instant.now();
        if (now.isAfter(signature.getOtpExpiresAt())) {
            signature.expire();
            audit(authentication, "SIGNATURE_CODE_EXPIRED", signature, Outcome.FAILURE, Map.of());
            throw new OtpRejectedException("Ce code a expiré : demandez-en un nouveau.", 0);
        }
        if (!MessageDigest.isEqual(hash(signatureId, code).getBytes(StandardCharsets.US_ASCII),
                signature.getOtpHash().getBytes(StandardCharsets.US_ASCII))) {
            int remaining = signature.recordWrongCode();
            audit(authentication, "SIGNATURE_CODE_REJECTED", signature, Outcome.FAILURE,
                    Map.of("remainingAttempts", remaining));
            throw new OtpRejectedException(remaining > 0
                    ? "Code incorrect. Il vous reste " + remaining + " essai" + (remaining > 1 ? "s." : ".")
                    : "Code incorrect, nombre d'essais atteint : demandez un nouveau code.", remaining);
        }
        if (!document.sha256().equals(signature.getDocumentSha256())) {
            // Le document a changé depuis l'envoi du code : on ne signe pas autre chose que ce qui a été présenté.
            signature.cancel();
            throw new BusinessRuleException("Le document a changé depuis l'envoi du code : demandez un nouveau code.");
        }

        String reason = "Contrat %s signé électroniquement par %s (%s) le %s, code à usage unique vérifié."
                .formatted(document.reference(), subscriber.displayName(), subscriber.email(), PROOF_TIME.format(now));
        String stamp = """
                Signé électroniquement par %s (%s)
                le %s (heure de Nouakchott), code à usage unique vérifié
                Sceau du Portail E-Payment · contrat %s"""
                .formatted(subscriber.displayName(), subscriber.email(), PROOF_TIME.format(now), document.reference());
        byte[] sealed = sealer.seal(document.pdf(), document.reference() + ".pdf", reason, subscriber.email(),
                Date.from(now), stamp);
        ContractView signed = contracts.recordSignature(document.contractId(), sealed);
        signature.markSigned(signed.signedPdfSha256(), now, ipAddress, truncate(userAgent, 300));
        audit(authentication, "SIGNATURE_COMPLETED", signature, Outcome.SUCCESS, Map.of(
                "contract", document.reference(),
                "documentSha256", document.sha256(),
                "signedDocumentSha256", signed.signedPdfSha256()));
        return signed;
    }

    private boolean reusable(ContractSignature pending, DocumentToSign document, Instant now) {
        if (!pending.getDocumentSha256().equals(document.sha256())) {
            pending.cancel();
            signatures.flush();
            return false;
        }
        Instant nextAllowed = pending.getOtpLastSentAt().plus(properties.resendDelay());
        if (now.isBefore(nextAllowed)) {
            throw new BusinessRuleException("Un code vient d'être envoyé. Vous pourrez en demander un autre dans "
                    + Duration.between(now, nextAllowed).toSeconds() + " s.");
        }
        if (pending.getOtpSentCount() >= properties.maxSends()) {
            if (now.isBefore(pending.getOtpLastSentAt().plus(SEND_LIMIT_COOLDOWN))) {
                throw new BusinessRuleException("Trop de codes demandés. Réessayez dans quelques minutes.");
            }
            pending.cancel();
            signatures.flush();
            return false;
        }
        return true;
    }

    private String hash(UUID signatureId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.otpSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((signatureId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** contact@exemple.mr → c••••••@exemple.mr (maquette B2). */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "••••••" : email.charAt(0) + "••••••" + email.substring(at);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private void audit(Authentication authentication, String action, ContractSignature signature, Outcome outcome,
            Map<String, Object> details) {
        Map<String, Object> all = new LinkedHashMap<>(details);
        all.put("contractId", signature.getContractId().toString());
        auditService.record(AuditEvent.of(authentication, action, "SIGNATURE", signature.getId().toString(),
                outcome, all));
    }

    /** Réponse à la demande de code (le code lui-même n'est jamais renvoyé). */
    public record OtpChallenge(UUID signatureId, String sentTo, Instant expiresAt, Instant resendAvailableAt,
            int maxAttempts) {
    }
}
