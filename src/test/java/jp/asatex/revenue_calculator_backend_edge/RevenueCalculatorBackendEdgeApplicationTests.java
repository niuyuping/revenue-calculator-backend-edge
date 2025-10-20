package jp.asatex.revenue_calculator_backend_edge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "EMPLOYEE_SERVICE_URI=http://localhost:8080",
    "EMPLOYEE_TEST_SERVICE_URI=http://localhost:8081"
})
class RevenueCalculatorBackendEdgeApplicationTests {

	@Test
	void contextLoads() {
	}

}
