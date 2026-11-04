package mr.epayment.portal.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import mr.epayment.portal.subscriber.CurrentSubscriber;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Notifications reçues par l'abonné connecté et ses préférences (écran Profil). */
@RestController
@RequestMapping("/api/abonne/notifications")
class SubscriberNotificationsController {

    private static final int HISTORY_SIZE = 20;

    private final CurrentSubscriber currentSubscriber;
    private final NotificationPreferences preferences;
    private final JdbcTemplate jdbc;

    SubscriberNotificationsController(CurrentSubscriber currentSubscriber, NotificationPreferences preferences,
            JdbcTemplate jdbc) {
        this.currentSubscriber = currentSubscriber;
        this.preferences = preferences;
        this.jdbc = jdbc;
    }

    /** Derniers emails envoyés à l'abonné (objet et état, sans le corps). */
    @GetMapping
    List<SentNotification> history(Authentication authentication) {
        UUID subscriberId = currentSubscriber.require(authentication).id();
        return jdbc.query("""
                select id, channel, template, subject, status, created_at, sent_at from notification
                where subscriber_id = ? order by created_at desc limit ?
                """, (rs, i) -> new SentNotification(rs.getObject("id", UUID.class), rs.getString("channel"),
                        rs.getString("template"), rs.getString("subject"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant()),
                subscriberId, HISTORY_SIZE);
    }

    @GetMapping("/preferences")
    Preferences preferences(Authentication authentication) {
        return new Preferences(preferences.paymentEmails(currentSubscriber.require(authentication).id()));
    }

    @PutMapping("/preferences")
    Preferences update(Authentication authentication, @RequestBody Preferences changes) {
        UUID subscriberId = currentSubscriber.require(authentication).id();
        preferences.setPaymentEmails(subscriberId, changes.paymentEmails());
        return new Preferences(preferences.paymentEmails(subscriberId));
    }

    record SentNotification(UUID id, String channel, String template, String subject, String status,
            Instant createdAt, Instant sentAt) {
    }

    /** @param paymentEmails email à chaque paiement réussi ou échoué */
    record Preferences(boolean paymentEmails) {
    }
}
