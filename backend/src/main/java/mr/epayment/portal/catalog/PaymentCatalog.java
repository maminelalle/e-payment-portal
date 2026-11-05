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
import mr.epayment.portal.shared.BusinessRuleException;
import mr.epayment.portal.shared.ConflictException;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.shared.NotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue des moyens de paiement (conception §3.3) : ajout, modification, activation / désactivation, logos.
 * Un moyen passe toujours par l'un des fournisseurs raccordés (adaptateur Bankily, Masrvi ou passerelle bancaire).
 */
@Service
public class PaymentCatalog {

    static final String STATUS_CHANGED = "PAYMENT_METHOD_STATUS_CHANGED";
    private static final int MAX_LOGO_BYTES = 256 * 1024;

    private final PaymentMethodRepository methods;
    private final PaymentProviderRepository providers;
    private final DocumentStorage storage;
    private final AuditService auditService;

    PaymentCatalog(PaymentMethodRepository methods, PaymentProviderRepository providers, DocumentStorage storage,
            AuditService auditService) {
        this.methods = methods;
        this.providers = providers;
        this.storage = storage;
        this.auditService = auditService;
    }

    /** Fournisseurs auxquels un nouveau moyen peut être rattaché, raccordés d'abord. */
    @Transactional(readOnly = true)
    public List<PaymentMethodView.Provider> providers() {
        return providers.findAllByOrderByName().stream()
                .sorted((a, b) -> Boolean.compare(b.isEnabled(), a.isEnabled()))
                .map(PaymentProvider::toView)
                .toList();
    }

    /**
     * Ajoute un moyen, désactivé. Son type (wallet ou carte) est celui du fournisseur ; le fournisseur doit être
     * raccordé, sinon aucun paiement ne pourrait aboutir.
     */
    @Transactional
    public PaymentMethodView create(String code, String providerCode, String currency, MethodDetails details) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (methods.findByCode(normalizedCode).isPresent()) {
            throw new ConflictException("Un moyen de paiement utilise déjà le code " + normalizedCode + ".");
        }
        PaymentProvider provider = providers.findByCode(providerCode)
                .orElseThrow(() -> new FieldValidationException(Map.of("providerCode", "Fournisseur inconnu.")));
        if (!provider.isEnabled()) {
            throw new FieldValidationException(Map.of("providerCode",
                    provider.getName() + " n'est pas encore raccordé au portail."));
        }
        PaymentMethod method = methods.saveAndFlush(new PaymentMethod(normalizedCode, provider,
                currency.trim().toUpperCase(Locale.ROOT), details));
        audit("PAYMENT_METHOD_CREATED", normalizedCode, Map.of("provider", provider.getCode(),
                "category", provider.getKind(), "name", details.name()));
        return method.toView();
    }

    /** Nom, pastille, couleur et textes ; le code, le fournisseur et la devise ne changent pas. */
    @Transactional
    public PaymentMethodView update(String code, MethodDetails details) {
        PaymentMethod method = find(code);
        String previousName = method.getName();
        method.update(details);
        audit("PAYMENT_METHOD_UPDATED", code, Map.of("previousName", previousName, "name", details.name()));
        return method.toView();
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
        if (status == PaymentMethodStatus.ACTIVE && !method.getProvider().isEnabled()) {
            throw new BusinessRuleException(method.getName() + " ne peut pas être activé : son fournisseur ("
                    + method.getProvider().getName() + ") n'est pas encore raccordé au portail.");
        }
        method.changeStatus(status);
        auditService.record(AuditEvent.of(SecurityContextHolder.getContext().getAuthentication(), STATUS_CHANGED,
                "PAYMENT_METHOD", code, Outcome.SUCCESS, Map.of("from", previous.name(), "to", status.name())));
        return method.toView();
    }
}
