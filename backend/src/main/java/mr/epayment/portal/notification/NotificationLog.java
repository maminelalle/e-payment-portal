package mr.epayment.portal.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Journal des notifications : chaque envoi est tracé avant d'être tenté, puis marqué envoyé ou en échec. */
@Repository
class NotificationLog {

    private final JdbcTemplate jdbc;

    NotificationLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    UUID queued(UUID subscriberId, String template, String recipient, String subject, String body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into notification (id, subscriber_id, channel, recipient, template, subject, body, status)
                values (?, ?, 'EMAIL', ?, ?, ?, ?, 'QUEUED')
                """, id, subscriberId, recipient, template, subject, body);
        return id;
    }

    void sent(UUID id) {
        jdbc.update("update notification set status = 'SENT', sent_at = ? where id = ?",
                Timestamp.from(Instant.now()), id);
    }

    void failed(UUID id, String error) {
        jdbc.update("update notification set status = 'FAILED', error = ? where id = ?",
                error == null ? null : error.substring(0, Math.min(error.length(), 500)), id);
    }
}
