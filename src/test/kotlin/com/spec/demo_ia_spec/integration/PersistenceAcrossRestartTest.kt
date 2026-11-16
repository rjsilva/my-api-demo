package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.DemoIaSpecApplication
import com.spec.demo_ia_spec.TestcontainersConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Simulates application restarts by starting two independent application contexts,
 * one after the other, against the same PostgreSQL container (US1, SC-001, SC-004).
 */
class PersistenceAcrossRestartTest {

    companion object {
        private val postgres = PostgreSQLContainer(DockerImageName.parse(TestcontainersConfiguration.POSTGRES_IMAGE))

        @JvmStatic
        @BeforeAll
        fun startDatabase() = postgres.start()

        @JvmStatic
        @AfterAll
        fun stopDatabase() = postgres.stop()
    }

    private val json = JsonMapper()

    // Passed as command-line args: SpringApplicationBuilder.properties() only sets defaults,
    // which application.yaml would override.
    private fun startApplication(): ConfigurableApplicationContext =
        SpringApplicationBuilder(DemoIaSpecApplication::class.java).run(
            "--spring.datasource.url=${postgres.jdbcUrl}",
            "--spring.datasource.username=${postgres.username}",
            "--spring.datasource.password=${postgres.password}",
            "--server.port=0",
            "--spring.docker.compose.enabled=false",
        )

    private fun client(context: ConfigurableApplicationContext): RestClient {
        val port = context.environment.getRequiredProperty("local.server.port")
        return RestClient.create("http://localhost:$port")
    }

    private fun RestClient.create(title: String): JsonNode =
        json.readTree(
            post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"title":"$title"}""")
                .retrieve()
                .body(String::class.java),
        )

    private fun RestClient.listJson(): String = get().uri("/api/tasks").retrieve().body(String::class.java)!!

    private fun RestClient.statusOf(spec: RestClient.RequestHeadersSpec<*>): Int =
        spec.exchange { _, response -> response.statusCode.value() }!!

    @Test
    fun `tasks keep their exact state across application restarts`() {
        val listingBeforeRestart: String
        val idB: Long
        val idA: Long
        val idC: Long

        startApplication().use { context ->
            val api = client(context)
            idA = api.create("A")["id"].asLong()
            idB = api.create("B")["id"].asLong()
            idC = api.create("C")["id"].asLong()
            api.patch().uri("/api/tasks/$idA/complete").retrieve().toBodilessEntity()
            api.delete().uri("/api/tasks/$idB").retrieve().toBodilessEntity()
            listingBeforeRestart = api.listJson()
        }

        startApplication().use { context ->
            val api = client(context)

            val listingAfterRestart = api.listJson()
            assertEquals(json.readTree(listingBeforeRestart), json.readTree(listingAfterRestart))
            val tasksAfterRestart = json.readTree(listingAfterRestart)
            assertEquals(listOf(idC, idA), (0 until tasksAfterRestart.size()).map { tasksAfterRestart[it]["id"].asLong() })

            assertEquals(404, api.statusOf(api.patch().uri("/api/tasks/$idB/complete")))
            assertEquals(404, api.statusOf(api.delete().uri("/api/tasks/$idB")))

            val newId = api.create("D")["id"].asLong()
            assertTrue(newId > maxOf(idA, idB, idC), "new id $newId must be greater than all previous ids")
        }
    }
}
