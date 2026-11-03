package mr.epayment.portal.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findBySubscriberIdAndIdempotencyKey(UUID subscriberId, String idempotencyKey);

    Optional<PaymentTransaction> findByReference(String reference);

    Page<PaymentTransaction> findBySubscriberIdOrderByCreatedAtDesc(UUID subscriberId, Pageable pageable);

    /**
     * Montant déjà engagé ce mois-ci sur ce moyen (réussi ou en cours) : base du plafond mensuel.
     * Les paiements en cours comptent pour qu'on ne puisse pas dépasser le plafond en parallèle.
     */
    @Query("""
            select coalesce(sum(t.amount), 0) from PaymentTransaction t
            where t.subscriberId = :subscriberId and t.paymentMethodId = :paymentMethodId
              and t.createdAt >= :since and t.status in ('CREATED', 'PENDING', 'SUCCEEDED')
            """)
    BigDecimal engagedSince(UUID subscriberId, UUID paymentMethodId, Instant since);

    /** Historique filtré (abonné : toujours limité à lui-même ; admin : tous). Filtres facultatifs. */
    @Query("""
            select t from PaymentTransaction t
            where (:subscriberId is null or t.subscriberId = :subscriberId)
              and (:status is null or t.status = :status)
              and (:methodId is null or t.paymentMethodId = :methodId)
              and (cast(:from as Instant) is null or t.createdAt >= :from)
              and (cast(:to as Instant) is null or t.createdAt < :to)
              and (:reference is null or lower(t.reference) like :reference)
            order by t.createdAt desc
            """)
    Page<PaymentTransaction> search(UUID subscriberId, TransactionStatus status, UUID methodId, Instant from,
            Instant to, String reference, Pageable pageable);

    /** Paiements à rapprocher : en cours depuis un moment (webhook perdu, abonné parti avant la fin…). */
    @Query("select t from PaymentTransaction t where t.status in ('CREATED', 'PENDING') and t.createdAt < :before")
    java.util.List<PaymentTransaction> findUnfinishedCreatedBefore(Instant before);

    @Query(value = "select nextval('transaction_ref_seq')", nativeQuery = true)
    long nextReferenceNumber();
}
