package mr.epayment.portal.gateway;

import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Journal des webhooks reçus ; la contrainte (fournisseur, événement) bloque les rejeux. */
@Repository
class WebhookEventStore {

    private final JdbcTemplate jdbc;

    WebhookEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code false} si l'événement a déjà été reçu */
    boolean recordIfNew(ProviderWebhook webhook, byte[] body) {
        return jdbc.update("""
                insert into webhook_event (provider_code, event_id, provider_reference, merchant_reference, outcome,
                                           provider_status, payload)
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (provider_code, event_id) do nothing
                """, webhook.providerCode(), webhook.eventId(), webhook.providerReference(),
                webhook.merchantReference(), webhook.outcome().name(), webhook.providerStatus(),
                new String(body, StandardCharsets.UTF_8)) == 1;
    }
}
