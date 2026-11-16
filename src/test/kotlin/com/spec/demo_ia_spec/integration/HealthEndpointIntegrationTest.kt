package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

/** contracts/health-api.md: health exposes the db component status, without details. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class HealthEndpointIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    private fun statusAndBody(path: String): Pair<Int, String?> =
        api.get().uri(path).exchange { _, response -> response.statusCode.value() to response.bodyTo(String::class.java) }!!

    @Test
    fun `health is UP and shows the db component status without details`() {
        val (status, body) = statusAndBody("/actuator/health")

        assertEquals(200, status)
        val health = JsonMapper().readTree(body)
        assertEquals("UP", health["status"].asString())
        val db = health["components"]?.get("db")
        assertNotNull(db, "expected a db component in: $body")
        assertEquals("UP", db!!["status"].asString())
        assertNull(db["details"], "db component must not expose details")
    }

    @Test
    fun `other actuator endpoints are not exposed`() {
        assertEquals(404, statusAndBody("/actuator/env").first)
    }
}
