package mr.epayment.portal.transaction;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Indicateurs agrégés des transactions (tableaux de bord), calculés en base. Bornes : [from, to[. */
@Service
public class TransactionStatistics {

    /** Les jours du graphique sont ceux de Nouakchott (UTC toute l'année). */
    private static final ZoneId ZONE = ZoneId.of("Africa/Nouakchott");

    private final JdbcTemplate jdbc;

    TransactionStatistics(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param subscriberId null pour toute la plateforme */
    public Totals totals(UUID subscriberId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                select count(*) as total,
                       count(*) filter (where status = 'SUCCEEDED') as succeeded,
                       count(*) filter (where status = 'FAILED') as failed,
                       coalesce(sum(amount) filter (where status = 'SUCCEEDED'), 0) as collected
                from payment_transaction
                where created_at >= ? and created_at < ? and (cast(? as uuid) is null or subscriber_id = ?)
                """, (rs, i) -> new Totals(rs.getLong("total"), rs.getLong("succeeded"), rs.getLong("failed"),
                        rs.getBigDecimal("collected")),
                Timestamp.from(from), Timestamp.from(to), subscriberId, subscriberId);
    }

    /** Nombre de transactions par jour, jours sans transaction compris. */
    public List<DayCount> perDay(UUID subscriberId, LocalDate firstDay, LocalDate lastDay) {
        return jdbc.query("""
                select d.day::date as day, count(t.id) as total,
                       coalesce(sum(t.amount) filter (where t.status = 'SUCCEEDED'), 0) as collected
                from generate_series(cast(? as date), cast(? as date), interval '1 day') as d(day)
                left join payment_transaction t
                       on (t.created_at at time zone ?)::date = d.day::date
                      and (cast(? as uuid) is null or t.subscriber_id = ?)
                group by d.day order by d.day
                """, (rs, i) -> new DayCount(rs.getObject("day", LocalDate.class),
                        rs.getLong("total"), rs.getBigDecimal("collected")),
                firstDay, lastDay, ZONE.getId(), subscriberId, subscriberId);
    }

    /** Répartition par moyen de paiement (identifiant du moyen au catalogue), du plus utilisé au moins utilisé. */
    public List<MethodCount> perMethod(UUID subscriberId, Instant from, Instant to) {
        return jdbc.query("""
                select payment_method_id, count(*) as total,
                       coalesce(sum(amount) filter (where status = 'SUCCEEDED'), 0) as collected
                from payment_transaction
                where created_at >= ? and created_at < ? and (cast(? as uuid) is null or subscriber_id = ?)
                group by payment_method_id order by total desc
                """, (rs, i) -> new MethodCount(rs.getObject("payment_method_id", UUID.class), rs.getLong("total"),
                        rs.getBigDecimal("collected")),
                Timestamp.from(from), Timestamp.from(to), subscriberId, subscriberId);
    }

    public record Totals(long count, long succeeded, long failed, BigDecimal collected) {
    }

    public record DayCount(LocalDate day, long count, BigDecimal collected) {
    }

    public record MethodCount(UUID methodId, long count, BigDecimal collected) {
    }
}
