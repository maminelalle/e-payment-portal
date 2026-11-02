package mr.epayment.portal.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:actor is null or lower(a.actorLabel) like lower(concat('%', cast(:actor as string), '%')))
            order by a.occurredAt desc, a.id desc
            """)
    Page<AuditLog> search(String action, String actor, Pageable pageable);
}
