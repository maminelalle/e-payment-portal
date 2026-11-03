package mr.epayment.portal.signature;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContractSignatureRepository extends JpaRepository<ContractSignature, UUID> {

    Optional<ContractSignature> findByContractIdAndStatus(UUID contractId, ContractSignature.Status status);
}
