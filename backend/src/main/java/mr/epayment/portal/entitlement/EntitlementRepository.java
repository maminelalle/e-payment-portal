package mr.epayment.portal.entitlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

    /** Insertion idempotente : la contrainte (contrat, moyen) ignore un droit déjà présent. */
    @Modifying
    @Query(value = """
            insert into subscriber_entitlement (id, subscriber_id, contract_id, payment_method_id, valid_from, valid_to,
                                                per_transaction_limit, monthly_limit, status, activated_at, updated_at)
            values (:id, :subscriberId, :contractId, :paymentMethodId, :validFrom, :validTo,
                    :perTransactionLimit, :monthlyLimit, 'ACTIVE', now(), now())
            on conflict (contract_id, payment_method_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID id, UUID subscriberId, UUID contractId, UUID paymentMethodId, LocalDate validFrom,
            LocalDate validTo, BigDecimal perTransactionLimit, BigDecimal monthlyLimit);

    List<Entitlement> findByContractIdOrderByActivatedAt(UUID contractId);

    List<Entitlement> findBySubscriberIdAndStatus(UUID subscriberId, Entitlement.Status status);
}
