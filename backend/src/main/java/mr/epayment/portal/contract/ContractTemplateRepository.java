package mr.epayment.portal.contract;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ContractTemplateRepository extends JpaRepository<ContractTemplate, UUID> {

    @Query("select t from ContractTemplate t where t.status = 'ACTIVE' order by t.name, t.version desc")
    List<ContractTemplate> findActive();

    @Query("select t from ContractTemplate t order by t.name, t.version desc")
    List<ContractTemplate> findAllOrdered();
}
