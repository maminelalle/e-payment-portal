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

    @Query(value = "select nextval('transaction_ref_seq')", nativeQuery = true)
    long nextReferenceNumber();
}
