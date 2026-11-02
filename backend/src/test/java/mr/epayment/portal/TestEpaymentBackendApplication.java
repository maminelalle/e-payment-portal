package mr.epayment.portal;

import org.springframework.boot.SpringApplication;

public class TestEpaymentBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(EpaymentBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
