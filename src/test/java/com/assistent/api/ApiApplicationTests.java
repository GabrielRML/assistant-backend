package com.assistent.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Sobe o contexto completo: aplica as migrations no Postgres real e valida as entidades (ddl-auto=validate). */
@SpringBootTest
@ActiveProfiles("test")
class ApiApplicationTests {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", PostgresTestSupport.POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", PostgresTestSupport.POSTGRES::getUsername);
		registry.add("spring.datasource.password", PostgresTestSupport.POSTGRES::getPassword);
	}

	@Test
	void contextLoads() {
	}

}
