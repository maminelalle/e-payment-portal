package mr.epayment.portal.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:actor is null or lower(a.actorLabel) like lower(concat('%', cast(:actor as string), '%')))
              and (:resource is null or lower(a.resourceId) like lower(concat('%', cast(:resource as string), '%')))
              and (:outcome is null or a.outcome = :outcome)
              and (cast(:from as Instant) is null or a.occurredAt >= :from)
              and (cast(:to as Instant) is null or a.occurredAt < :to)
            order by a.occurredAt desc, a.id desc
            """)
    Page<AuditLog> search(String action, String actor, String resource, String outcome, Instant from, Instant to,
            Pageable pageable);

    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> findDistinctActions();
}
