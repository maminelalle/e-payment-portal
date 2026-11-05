package mr.epayment.portal.subscriber;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContactChangeRepository extends JpaRepository<ContactChange, UUID> {

    List<ContactChange> findBySubscriberIdAndStatus(UUID subscriberId, ContactChange.Status status);

    Optional<ContactChange> findFirstBySubscriberIdOrderByCreatedAtDesc(UUID subscriberId);
}
