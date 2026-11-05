package mr.epayment.portal.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentProviderRepository extends JpaRepository<PaymentProvider, UUID> {

    Optional<PaymentProvider> findByCode(String code);

    List<PaymentProvider> findAllByOrderByName();
}
