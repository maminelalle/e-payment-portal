package mr.epayment.portal.subscriber;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SubscriberRepository extends JpaRepository<Subscriber, UUID> {

    /** Recherche par nom, référence, email ou téléphone, filtrée par statut et type. */
    @Query("""
            select s from Subscriber s
            where (:status is null or s.status = :status)
              and (:type is null or s.type = :type)
              and (:term is null
                   or lower(s.displayName) like :term
                   or lower(s.reference) like :term
                   or lower(s.email) like :term
                   or s.phone like :term)
            order by s.createdAt desc
            """)
    Page<Subscriber> search(String term, SubscriberStatus status, SubscriberType type, Pageable pageable);

    Optional<Subscriber> findByUserId(UUID userId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNni(String nni);

    boolean existsByNif(String nif);

    boolean existsByNniAndIdNot(String nni, UUID id);

    boolean existsByNifAndIdNot(String nif, UUID id);

    @Query(value = "select nextval('subscriber_ref_seq')", nativeQuery = true)
    long nextReferenceNumber();

    long countByCreatedAtGreaterThanEqual(Instant since);

    @Query("select s.status as status, count(s) as total from Subscriber s group by s.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        SubscriberStatus getStatus();

        long getTotal();
    }
}
