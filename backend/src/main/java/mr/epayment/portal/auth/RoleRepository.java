package mr.epayment.portal.auth;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<Role, Short> {

    Set<Role> findByCodeIn(Collection<String> codes);
}
