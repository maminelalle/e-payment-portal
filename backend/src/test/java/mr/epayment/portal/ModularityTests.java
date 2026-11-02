package mr.epayment.portal;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Vérifie que les modules ne dépendent pas des classes internes des autres modules.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(EpaymentBackendApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }
}
