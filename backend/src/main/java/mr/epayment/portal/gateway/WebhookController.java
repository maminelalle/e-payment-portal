package mr.epayment.portal.gateway;

import java.time.Instant;
import java.util.Map;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.gateway.WebhookParsers.MalformedWebhookException;
import mr.epayment.portal.gateway.WebhookVerifier.WebhookRejectedException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Réception des webhooks des fournisseurs. Point d'entrée public (le fournisseur n'a pas de jeton Keycloak) :
 * la sécurité repose sur la signature HMAC, la fraîcheur de l'horodatage et l'unicité de l'événement.
 * <ul>
 *   <li>200 « accepted » : événement nouveau, enregistré et publié ;</li>
 *   <li>200 « duplicate » : déjà reçu (rejeu ou réessai du fournisseur), ignoré ;</li>
 *   <li>401 : signature ou horodatage invalide ; 400 : corps illisible ; 404 : fournisseur inconnu.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/webhooks")
@EnableConfigurationProperties(ProvidersProperties.class)
class WebhookController {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final ProvidersProperties providers;
    private final WebhookParsers parsers;
    private final WebhookEventStore store;
    private final ApplicationEventPublisher events;
    private final AuditService auditService;

    WebhookController(ProvidersProperties providers, WebhookParsers parsers, WebhookEventStore store,
            ApplicationEventPublisher events, AuditService auditService) {
        this.providers = providers;
        this.parsers = parsers;
        this.store = store;
        this.events = events;
        this.auditService = auditService;
    }

    @PostMapping("/{provider}")
    @Transactional
    ResponseEntity<Map<String, String>> receive(@PathVariable String provider,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Event-Id", required = false) String eventId,
            @RequestBody byte[] body) {
        var named = providers.byWebhookPath(provider).orElse(null);
        if (named == null) {
            return ResponseEntity.notFound().build();
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new MalformedWebhookException("Corps trop volumineux.");
        }
        try {
            WebhookVerifier.verify(named.settings().webhookSecret(), signature, timestamp, body,
                    providers.webhookTolerance(), Instant.now());
        } catch (WebhookRejectedException e) {
            audit("WEBHOOK_REJECTED", named.code(), eventId, Outcome.FAILURE, Map.of("reason", e.getMessage()));
            throw e;
        }

        ProviderWebhook webhook = parsers.parse(named.code(), body);
        if (eventId != null && !eventId.equals(webhook.eventId())) {
            throw new MalformedWebhookException("X-Event-Id ne correspond pas à l'événement du corps.");
        }
        if (!store.recordIfNew(webhook, body)) {
            if (!store.sameAsRecorded(named.code(), webhook.eventId(), body)) {
                // Même identifiant, contenu différent : pas un rejeu mais une erreur du fournisseur.
                // Rien n'est appliqué (le rapprochement retrouvera le bon statut) ; un humain doit regarder.
                audit("WEBHOOK_EVENT_ID_REUSED", named.code(), webhook.eventId(), Outcome.FAILURE, Map.of(
                        "merchantReference", String.valueOf(webhook.merchantReference()),
                        "providerStatus", webhook.providerStatus()));
            }
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }
        events.publishEvent(webhook);
        audit("WEBHOOK_RECEIVED", named.code(), webhook.eventId(), Outcome.SUCCESS, Map.of(
                "merchantReference", String.valueOf(webhook.merchantReference()),
                "outcome", webhook.outcome().name(),
                "providerStatus", webhook.providerStatus()));
        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    @ExceptionHandler(WebhookRejectedException.class)
    ResponseEntity<Map<String, String>> rejected(WebhookRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_signature"));
    }

    @ExceptionHandler(MalformedWebhookException.class)
    ResponseEntity<Map<String, String>> malformed(MalformedWebhookException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "malformed_webhook", "message", e.getMessage()));
    }

    private void audit(String action, String providerCode, String eventId, Outcome outcome,
            Map<String, Object> details) {
        auditService.record(new AuditEvent(null, "PROVIDER:" + providerCode, action, "WEBHOOK", eventId, outcome,
                details));
    }
}
