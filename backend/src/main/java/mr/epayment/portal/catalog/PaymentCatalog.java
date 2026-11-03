package mr.epayment.portal.catalog;

import java.util.List;
import java.util.Map;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.shared.NotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogue des moyens de paiement : consultation et activation / désactivation. */
@Service
public class PaymentCatalog {

    static final String STATUS_CHANGED = "PAYMENT_METHOD_STATUS_CHANGED";

    private final PaymentMethodRepository methods;
    private final AuditService auditService;

    PaymentCatalog(PaymentMethodRepository methods, AuditService auditService) {
        this.methods = methods;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodView> findAll() {
        return methods.findAllWithProvider().stream().map(PaymentMethod::toView).toList();
    }

    /**
     * Un moyen désactivé n'est plus proposé dans les nouveaux contrats ni utilisable pour payer ;
     * les contrats et transactions existants ne sont pas modifiés.
     */
    @Transactional
    public PaymentMethodView changeStatus(String code, PaymentMethodStatus status) {
        PaymentMethod method = methods.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Moyen de paiement inconnu : " + code));
        PaymentMethodStatus previous = method.getStatus();
        method.changeStatus(status);
        auditService.record(AuditEvent.of(SecurityContextHolder.getContext().getAuthentication(), STATUS_CHANGED,
                "PAYMENT_METHOD", code, Outcome.SUCCESS, Map.of("from", previous.name(), "to", status.name())));
        return method.toView();
    }
}
