package com.spec.demo_ia_spec

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class DemoIaSpecApplicationTests {

	@Test
	fun contextLoads() {
	}

}
