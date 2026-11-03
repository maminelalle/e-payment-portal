package mr.epayment.portal.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.entitlement.EntitlementEngine;
import mr.epayment.portal.entitlement.PaymentAuthorization;
import mr.epayment.portal.gateway.PaymentGateway;
import mr.epayment.portal.gateway.PaymentProvider;
import mr.epayment.portal.gateway.PaymentProvider.PaymentRequest;
import mr.epayment.portal.gateway.PaymentProvider.ProviderResult;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.subscriber.CurrentSubscriber;
import mr.epayment.portal.subscriber.SubscriberView;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Paiement par l'abonné. Le droit est revérifié côté serveur à chaque appel (moteur des droits),
 * puis le paiement est transmis à l'adaptateur du fournisseur.
 * <p>
 * Semaine 7 : paiement simulé, tracé dans le journal d'audit. La table des transactions (référence TRX,
 * clé d'idempotence, statut interne / fournisseur, reçu, plafond mensuel cumulé) arrive en semaine 10.
 */
@Service
public class PaymentService {

    private final CurrentSubscriber currentSubscriber;
    private final EntitlementEngine entitlements;
    private final PaymentGateway gateway;
    private final AuditService auditService;

    PaymentService(CurrentSubscriber currentSubscriber, EntitlementEngine entitlements, PaymentGateway gateway,
            AuditService auditService) {
        this.currentSubscriber = currentSubscriber;
        this.entitlements = entitlements;
        this.gateway = gateway;
        this.auditService = auditService;
    }

    public PaymentResult pay(Authentication authentication, String methodCode, BigDecimal amount, String payerPhone,
            String description) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        // Semaine 10 : cumul réel des paiements réussis du mois ; d'ici là seul le plafond par transaction joue.
        PaymentAuthorization authorization = entitlements.authorize(subscriber.id(), methodCode, amount,
                BigDecimal.ZERO);
        if (!authorization.allowed()) {
            audit(authentication, "PAYMENT_REFUSED", null, Outcome.FAILURE, Map.of(
                    "method", methodCode, "amount", amount, "refusal", authorization.refusal().name()));
            throw new PaymentRefusedException(authorization.refusal(), authorization.message());
        }
        if ("WALLET".equals(authorization.category()) && (payerPhone == null || payerPhone.isBlank())) {
            throw new FieldValidationException(Map.of("payerPhone",
                    "Indiquez le numéro de téléphone du wallet " + authorization.methodName() + "."));
        }

        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProviderResult result = gateway.initiate(authorization.providerCode(), new PaymentRequest(reference, amount,
                authorization.currency(), payerPhone, description));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", methodCode);
        details.put("amount", amount);
        details.put("currency", authorization.currency());
        details.put("contractId", authorization.contractId().toString());
        details.put("outcome", result.outcome().name());
        details.put("providerReference", result.providerReference());
        audit(authentication, "PAYMENT_SIMULATED", reference,
                result.outcome() == PaymentProvider.Outcome.FAILED ? Outcome.FAILURE
                        : Outcome.SUCCESS, details);

        return new PaymentResult(reference, result.outcome().name(), result.providerStatus(), result.failureCode(),
                result.message(), methodCode, authorization.methodName(), amount, authorization.currency(),
                description, authorization.contractId(), Instant.now());
    }

    private void audit(Authentication authentication, String action, String reference, Outcome outcome,
            Map<String, Object> details) {
        auditService.record(AuditEvent.of(authentication, action, "PAYMENT", reference, outcome, details));
    }

    /** Résultat affiché à l'abonné (maquette B3). */
    public record PaymentResult(String reference, String status, String providerStatus, String failureCode,
            String message, String methodCode, String methodName, BigDecimal amount, String currency,
            String description, UUID contractId, Instant occurredAt) {
    }
}
