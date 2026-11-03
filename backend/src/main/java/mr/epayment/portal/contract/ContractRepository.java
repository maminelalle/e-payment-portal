package mr.epayment.portal.contract;

import java.util.Collection;
import java.util.List;
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

    @Query(value = "select nextval('contract_ref_seq')", nativeQuery = true)
    long nextReferenceNumber();
}
