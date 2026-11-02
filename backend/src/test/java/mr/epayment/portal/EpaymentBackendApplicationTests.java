package mr.epayment.portal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EpaymentBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
