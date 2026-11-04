package mr.epayment.portal.notification;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Préférences de notification des abonnés ; sans ligne enregistrée, tout est envoyé. */
@Repository
class NotificationPreferences {

    private final JdbcTemplate jdbc;

    NotificationPreferences(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean paymentEmails(UUID subscriberId) {
        return jdbc.query("select payment_emails from notification_preference where subscriber_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : true, subscriberId);
    }

    void setPaymentEmails(UUID subscriberId, boolean enabled) {
        jdbc.update("""
                insert into notification_preference (subscriber_id, payment_emails) values (?, ?)
                on conflict (subscriber_id) do update set payment_emails = excluded.payment_emails, updated_at = now()
                """, subscriberId, enabled);
    }
}
