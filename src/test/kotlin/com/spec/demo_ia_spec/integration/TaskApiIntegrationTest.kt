package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Regression suite: every acceptance scenario of feature 001, run over HTTP against a real
 * PostgreSQL database (US2, FR-007, SC-002).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class TaskApiIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val json = JsonMapper()

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    private data class Response(val status: Int, val body: JsonNode?)

    @BeforeEach
    fun cleanDatabase() {
        // No RESTART IDENTITY: ids must never be reused, not even across tests.
        jdbcTemplate.execute("TRUNCATE TABLE tasks")
    }

    private fun call(method: HttpMethod, path: String, body: String? = null): Response {
        val request = api.method(method).uri(path)
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body)
        return request.exchange { _, response ->
            val text = response.bodyTo(String::class.java)
            Response(response.statusCode.value(), text?.takeIf { it.isNotBlank() }?.let { json.readTree(it) })
        }!!
    }

    private fun create(title: String, description: String? = null): Response {
        val payload = mutableMapOf<String, Any?>("title" to title)
        if (description != null) payload["description"] = description
        return call(HttpMethod.POST, "/api/tasks", json.writeValueAsString(payload))
    }

    private fun createdId(title: String): Long = create(title).body!!["id"].asLong()

    private fun list(): JsonNode = call(HttpMethod.GET, "/api/tasks").also { assertEquals(200, it.status) }.body!!

    private fun JsonNode.ids(): List<Long> = (0 until size()).map { this[it]["id"].asLong() }

    // --- Create ---

    @Test
    fun `create without description returns 201 pending task with null description`() {
        val response = create("Buy milk")

        assertEquals(201, response.status)
        assertEquals("Buy milk", response.body!!["title"].asString())
        assertFalse(response.body["completed"].asBoolean())
        assertTrue(response.body["description"].isNull)
    }

    @Test
    fun `create with description returns 201 with given fields`() {
        val response = create("Estudar Spring Boot", "Capítulos 1 a 3")

        assertEquals(201, response.status)
        assertEquals("Estudar Spring Boot", response.body!!["title"].asString())
        assertEquals("Capítulos 1 a 3", response.body["description"].asString())
        assertFalse(response.body["completed"].asBoolean())
    }

    @Test
    fun `create with blank or whitespace title returns 400 with message`() {
        for (title in listOf("", "   ")) {
            val response = create(title)

            assertEquals(400, response.status, "title='$title'")
            assertTrue(response.body!!["message"].asString().isNotBlank())
        }
        assertEquals(0, list().size())
    }

    @Test
    fun `create with 101 character title returns 400 and saves nothing`() {
        val response = create("a".repeat(101))

        assertEquals(400, response.status)
        assertTrue(response.body!!["message"].asString().isNotBlank())
        assertEquals(0, list().size())
    }

    @Test
    fun `create with exactly 100 character title returns 201`() {
        assertEquals(201, create("a".repeat(100)).status)
    }

    // --- List ---

    @Test
    fun `list returns empty array when there are no tasks`() {
        assertEquals(0, list().size())
    }

    @Test
    fun `list returns pending before completed, each group in creation order`() {
        val first = createdId("First")
        val second = createdId("Second")
        val third = createdId("Third")
        val fourth = createdId("Fourth")
        call(HttpMethod.PATCH, "/api/tasks/$third/complete")
        call(HttpMethod.PATCH, "/api/tasks/$first/complete")

        assertEquals(listOf(second, fourth, first, third), list().ids())
    }

    // --- Complete ---

    @Test
    fun `complete returns 200 and moves the task to the end of the list`() {
        val first = createdId("First")
        val second = createdId("Second")

        val response = call(HttpMethod.PATCH, "/api/tasks/$first/complete")

        assertEquals(200, response.status)
        assertTrue(response.body!!["completed"].asBoolean())
        assertEquals(listOf(second, first), list().ids())
    }

    @Test
    fun `complete is idempotent`() {
        val id = createdId("Buy milk")
        call(HttpMethod.PATCH, "/api/tasks/$id/complete")

        val response = call(HttpMethod.PATCH, "/api/tasks/$id/complete")

        assertEquals(200, response.status)
        assertTrue(response.body!!["completed"].asBoolean())
    }

    @Test
    fun `complete unknown task returns 404 with message`() {
        val response = call(HttpMethod.PATCH, "/api/tasks/${Long.MAX_VALUE}/complete")

        assertEquals(404, response.status)
        assertTrue(response.body!!["message"].asString().isNotBlank())
    }

    // --- Delete ---

    @Test
    fun `delete pending or completed task returns 204 and removes it from the list`() {
        val pending = createdId("Pending")
        val completed = createdId("Completed")
        val kept = createdId("Kept")
        call(HttpMethod.PATCH, "/api/tasks/$completed/complete")

        assertEquals(204, call(HttpMethod.DELETE, "/api/tasks/$pending").status)
        assertEquals(204, call(HttpMethod.DELETE, "/api/tasks/$completed").status)

        assertEquals(listOf(kept), list().ids())
    }

    @Test
    fun `delete unknown task returns 404 with message`() {
        val response = call(HttpMethod.DELETE, "/api/tasks/${Long.MAX_VALUE}")

        assertEquals(404, response.status)
        assertTrue(response.body!!["message"].asString().isNotBlank())
    }
}
