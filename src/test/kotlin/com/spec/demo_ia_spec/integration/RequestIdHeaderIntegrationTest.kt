package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** US1 / FR-005 / FR-006: every response carries X-Request-Id; error bodies carry the same id. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class RequestIdHeaderIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val json = JsonMapper()

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    private data class Response(val status: Int, val requestId: String?, val body: JsonNode?)

    private fun call(method: HttpMethod, path: String, body: String? = null, requestId: String? = null): Response {
        val request = api.method(method).uri(path)
        if (requestId != null) request.header("X-Request-Id", requestId)
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body)
        return request.exchange { _, response ->
            val text = response.bodyTo(String::class.java)
            Response(
                response.statusCode.value(),
                response.headers.getFirst("X-Request-Id"),
                text?.takeIf { it.isNotBlank() && it.trimStart().startsWith("{") }?.let { json.readTree(it) },
            )
        }!!
    }

    private fun assertHasRequestId(response: Response) {
        assertFalse(response.requestId.isNullOrBlank(), "missing X-Request-Id on status ${response.status}")
    }

    @Test
    fun `success responses carry the header`() {
        val created = call(HttpMethod.POST, "/api/tasks", """{"title":"Request id"}""")
        assertEquals(201, created.status)
        assertHasRequestId(created)
        val id = created.body!!["id"].asLong()

        val listed = call(HttpMethod.GET, "/api/tasks")
        assertEquals(200, listed.status)
        assertHasRequestId(listed)

        val completed = call(HttpMethod.PATCH, "/api/tasks/$id/complete")
        assertEquals(200, completed.status)
        assertHasRequestId(completed)

        val deleted = call(HttpMethod.DELETE, "/api/tasks/$id")
        assertEquals(204, deleted.status)
        assertHasRequestId(deleted)
    }

    @Test
    fun `validation error body carries the same request id as the header`() {
        val response = call(HttpMethod.POST, "/api/tasks", """{"title":""}""")

        assertEquals(400, response.status)
        assertHasRequestId(response)
        assertEquals(response.requestId, response.body!!["requestId"].asString())
    }

    @Test
    fun `task not found body carries the same request id as the header`() {
        val response = call(HttpMethod.DELETE, "/api/tasks/999999")

        assertEquals(404, response.status)
        assertHasRequestId(response)
        assertEquals(response.requestId, response.body!!["requestId"].asString())
    }

    @Test
    fun `unknown route and health endpoint carry the header`() {
        val unknown = call(HttpMethod.GET, "/rota-inexistente")
        assertEquals(404, unknown.status)
        assertHasRequestId(unknown)

        val health = call(HttpMethod.GET, "/actuator/health")
        assertEquals(200, health.status)
        assertHasRequestId(health)
    }

    @Test
    fun `two calls without header get different ids`() {
        val first = call(HttpMethod.GET, "/api/tasks")
        val second = call(HttpMethod.GET, "/api/tasks")

        assertHasRequestId(first)
        assertNotEquals(first.requestId, second.requestId)
    }

    @Test
    fun `client provided id is echoed back`() {
        assertEquals("suporte-1234", call(HttpMethod.GET, "/api/tasks", requestId = "suporte-1234").requestId)
    }
}
