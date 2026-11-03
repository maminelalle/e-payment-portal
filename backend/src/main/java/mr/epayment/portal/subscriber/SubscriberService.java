package mr.epayment.portal.subscriber;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.auth.SubscriberAccounts;
import mr.epayment.portal.shared.ConflictException;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.shared.NotFoundException;
import mr.epayment.portal.shared.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriberService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SubscriberRepository subscribers;
    private final SubscriberAccounts accounts;
    private final AuditService auditService;

    SubscriberService(SubscriberRepository subscribers, SubscriberAccounts accounts, AuditService auditService) {
        this.subscribers = subscribers;
        this.accounts = accounts;
        this.auditService = auditService;
    }

    /**
     * Crée l'abonné (statut PENDING), son compte de connexion Keycloak, puis envoie l'invitation par email.
     * Si l'enregistrement échoue, le compte Keycloak créé est supprimé (voir {@link SubscriberAccounts#create}).
     */
    @Transactional
    public CreatedSubscriber create(SubscriberType type, String email, SubscriberDetails details) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        checkIdentifier(type, details);
        if (subscribers.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Un abonné utilise déjà l'email " + normalizedEmail + ".");
        }
        checkIdentifierUnique(type, details, null);

        UUID userId = accounts.create(normalizedEmail, details.displayName(), details.phone());
        String reference = "ABN-%04d".formatted(subscribers.nextReferenceNumber());
        Subscriber subscriber = subscribers.saveAndFlush(new Subscriber(reference, type, details, normalizedEmail, userId));

        boolean invitationSent = accounts.sendInvitation(userId);
        audit("SUBSCRIBER_CREATED", subscriber, Map.of("invitationSent", invitationSent));
        return new CreatedSubscriber(subscriber.toView(), invitationSent);
    }

    @Transactional
    public SubscriberView update(UUID id, SubscriberDetails details) {
        Subscriber subscriber = find(id);
        checkIdentifier(subscriber.getType(), details);
        checkIdentifierUnique(subscriber.getType(), details, id);
        subscriber.update(details);
        audit("SUBSCRIBER_UPDATED", subscriber, Map.of());
        return subscriber.toView();
    }

    @Transactional
    public SubscriberView activate(UUID id) {
        return changeStatus(id, Subscriber::activate, "SUBSCRIBER_ACTIVATED", null);
    }

    @Transactional
    public SubscriberView suspend(UUID id, String reason) {
        return changeStatus(id, Subscriber::suspend, "SUBSCRIBER_SUSPENDED", reason);
    }

    /** Désactivation définitive : le compte de connexion est bloqué, l'historique est conservé. */
    @Transactional
    public SubscriberView disable(UUID id, String reason) {
        return changeStatus(id, Subscriber::disable, "SUBSCRIBER_DISABLED", reason);
    }

    public boolean resendInvitation(UUID id) {
        Subscriber subscriber = find(id);
        if (!subscriber.getStatus().canLogIn()) {
            throw new ConflictException("L'abonné est " + subscriber.getStatus() + " : son compte est bloqué.");
        }
        boolean sent = accounts.sendInvitation(subscriber.getUserId());
        audit("SUBSCRIBER_INVITATION_SENT", subscriber, Map.of("sent", sent));
        return sent;
    }

    @Transactional(readOnly = true)
    public SubscriberView get(UUID id) {
        return find(id).toView();
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriberView> search(String query, SubscriberStatus status, SubscriberType type, int page,
            int size) {
        String term = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT).replace(" ", "") + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.of(subscribers.search(term, status, type, pageable), Subscriber::toView);
    }

    @Transactional(readOnly = true)
    public Map<SubscriberStatus, Long> countByStatus() {
        Map<SubscriberStatus, Long> counts = new EnumMap<>(SubscriberStatus.class);
        for (SubscriberStatus status : SubscriberStatus.values()) {
            counts.put(status, 0L);
        }
        subscribers.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    private SubscriberView changeStatus(UUID id, Consumer<Subscriber> transition, String action, String reason) {
        Subscriber subscriber = find(id);
        boolean couldLogIn = subscriber.getStatus().canLogIn();
        transition.accept(subscriber);
        boolean canLogIn = subscriber.getStatus().canLogIn();
        if (couldLogIn != canLogIn) {
            accounts.setEnabled(subscriber.getUserId(), canLogIn);
        }
        audit(action, subscriber, reason == null || reason.isBlank() ? Map.of() : Map.of("reason", reason.trim()));
        return subscriber.toView();
    }

    private static void checkIdentifier(SubscriberType type, SubscriberDetails details) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (type == SubscriberType.PHYSIQUE && details.nni() == null) {
            errors.put("nni", "Le NNI est obligatoire pour une personne physique.");
        }
        if (type == SubscriberType.MORALE && details.nif() == null) {
            errors.put("nif", "Le NIF est obligatoire pour une personne morale.");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void checkIdentifierUnique(SubscriberType type, SubscriberDetails details, UUID currentId) {
        if (type == SubscriberType.PHYSIQUE && (currentId == null
                ? subscribers.existsByNni(details.nni())
                : subscribers.existsByNniAndIdNot(details.nni(), currentId))) {
            throw new ConflictException("Un abonné utilise déjà le NNI " + details.nni() + ".");
        }
        if (type == SubscriberType.MORALE && (currentId == null
                ? subscribers.existsByNif(details.nif())
                : subscribers.existsByNifAndIdNot(details.nif(), currentId))) {
            throw new ConflictException("Un abonné utilise déjà le NIF " + details.nif() + ".");
        }
    }

    private Subscriber find(UUID id) {
        return subscribers.findById(id).orElseThrow(() -> new NotFoundException("Abonné introuvable."));
    }

    private void audit(String action, Subscriber subscriber, Map<String, Object> details) {
        Map<String, Object> all = new LinkedHashMap<>(details);
        all.put("reference", subscriber.getReference());
        all.put("status", subscriber.getStatus().name());
        auditService.record(AuditEvent.of(SecurityContextHolder.getContext().getAuthentication(), action,
                "SUBSCRIBER", subscriber.getId().toString(), Outcome.SUCCESS, all));
    }

    public record CreatedSubscriber(SubscriberView subscriber, boolean invitationSent) {
    }
}
