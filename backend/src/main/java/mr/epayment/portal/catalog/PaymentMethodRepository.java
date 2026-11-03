package mr.epayment.portal.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    @Query("select m from PaymentMethod m join fetch m.provider order by m.category desc, m.name")
    List<PaymentMethod> findAllWithProvider();

    Optional<PaymentMethod> findByCode(String code);
}
