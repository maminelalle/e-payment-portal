package mr.epayment.portal.contract;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ContractRepository extends JpaRepository<Contract, UUID> {

    @Query("""
            select c from Contract c
            where (:status is null or c.status = :status)
              and (:subscriberId is null or c.subscriberId = :subscriberId)
              and (:reference is null or lower(c.reference) like :reference)
            order by c.createdAt desc
            """)
    Page<Contract> search(ContractStatus status, UUID subscriberId, String reference, Pageable pageable);

    List<Contract> findBySubscriberIdAndStatusIn(UUID subscriberId, Collection<ContractStatus> statuses);

    Optional<Contract> findFirstBySubscriberIdAndStatusInOrderByCreatedAtDesc(UUID subscriberId,
            Collection<ContractStatus> statuses);

    long countByStatus(ContractStatus status);

    @Query("select c.status as status, count(c) as total from Contract c group by c.status")
    List<StatusCount> countPerStatus();

    interface StatusCount {
        ContractStatus getStatus();

        long getTotal();
    }

    List<Contract> findTop5ByStatusInOrderByUpdatedAtDesc(Collection<ContractStatus> statuses);

    /** Contrats en vigueur dont la date de fin est dépassée (job quotidien d'expiration). */
    @Query("select c.id from Contract c where c.status in ('ACTIVE', 'SUSPENDED') and c.endDate < :today")
    List<UUID> findIdsInForceEndedBefore(LocalDate today);

    /** Contrats signés pas encore activés (rattrapage si l'activation automatique a échoué). */
    @Query("select c.id from Contract c where c.status = 'SIGNED'")
    List<UUID> findSignedIds();

    @Query(value = "select nextval('contract_ref_seq')", nativeQuery = true)
    long nextReferenceNumber();
}
