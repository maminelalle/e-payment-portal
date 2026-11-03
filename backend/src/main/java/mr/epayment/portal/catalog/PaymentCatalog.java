package mr.epayment.portal.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.document.DocumentStorage;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.shared.NotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogue des moyens de paiement : consultation, activation / désactivation, logos. */
@Service
public class PaymentCatalog {

    static final String STATUS_CHANGED = "PAYMENT_METHOD_STATUS_CHANGED";
    private static final int MAX_LOGO_BYTES = 256 * 1024;

    private final PaymentMethodRepository methods;
    private final DocumentStorage storage;
    private final AuditService auditService;

    PaymentCatalog(PaymentMethodRepository methods, DocumentStorage storage, AuditService auditService) {
        this.methods = methods;
        this.storage = storage;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodView> findAll() {
        return methods.findAllWithProvider().stream().map(PaymentMethod::toView).toList();
    }

    /**
     * Remplace le logo : PNG, JPEG ou WebP de 256 Ko au plus, type vérifié sur le contenu réel du fichier.
     * Le SVG est refusé (il peut contenir du script).
     */
    @Transactional
    public PaymentMethodView changeLogo(String code, byte[] content) {
        PaymentMethod method = find(code);
        if (content.length == 0 || content.length > MAX_LOGO_BYTES) {
            throw new FieldValidationException(Map.of("logo", "Le logo doit peser au plus 256 Ko."));
        }
        ImageType type = ImageType.detect(content)
                .orElseThrow(() -> new FieldValidationException(Map.of("logo",
                        "Format accepté : PNG, JPEG ou WebP.")));
        String key = "catalogue/logos/%s-%s.%s".formatted(code.toLowerCase(Locale.ROOT), UUID.randomUUID(),
                type.extension());
        storage.put(key, content, type.contentType());
        method.changeLogo(key, type.contentType());
        audit("PAYMENT_METHOD_LOGO_CHANGED", code, Map.of("contentType", type.contentType(), "bytes", content.length));
        return method.toView();
    }

    @Transactional
    public PaymentMethodView removeLogo(String code) {
        PaymentMethod method = find(code);
        method.removeLogo();
        audit("PAYMENT_METHOD_LOGO_REMOVED", code, Map.of());
        return method.toView();
    }

    /** Logo à servir, s'il existe. */
    @Transactional(readOnly = true)
    public Optional<Logo> logo(String code) {
        return methods.findByCode(code)
                .filter(m -> m.getLogoObjectKey() != null)
                .map(m -> new Logo(storage.get(m.getLogoObjectKey()), m.getLogoContentType()));
    }

    private PaymentMethod find(String code) {
        return methods.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Moyen de paiement inconnu : " + code));
    }

    private void audit(String action, String code, Map<String, Object> details) {
        auditService.record(AuditEvent.of(SecurityContextHolder.getContext().getAuthentication(), action,
                "PAYMENT_METHOD", code, Outcome.SUCCESS, details));
    }

    public record Logo(byte[] content, String contentType) {
    }

    /** Formats d'image acceptés, reconnus à leurs premiers octets. */
    enum ImageType {
        PNG("image/png", "png"), JPEG("image/jpeg", "jpg"), WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        String contentType() {
            return contentType;
        }

        String extension() {
            return extension;
        }

        static Optional<ImageType> detect(byte[] b) {
            if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
                return Optional.of(PNG);
            }
            if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return Optional.of(JPEG);
            }
            if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
                return Optional.of(WEBP);
            }
            return Optional.empty();
        }
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
