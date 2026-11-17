package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import com.spec.demo_ia_spec.support.accessLogFor
import com.spec.demo_ia_spec.support.awaitJsonLogLines
import com.spec.demo_ia_spec.support.field
import com.spec.demo_ia_spec.support.text
import com.spec.demo_ia_spec.support.withRequestId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** US2 / FR-004 / FR-007: request logs are correlated and every request emits an access log. */
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class StructuredLoggingIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val json = JsonMapper()

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    private fun call(
        method: HttpMethod,
        path: String,
        requestId: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, JsonNode?> {
        val request = api.method(method).uri(path).header("X-Request-Id", requestId)
        headers.forEach { (name, value) -> request.header(name, value) }
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body)
        return request.exchange { _, response ->
            val text = response.bodyTo(String::class.java)
            response.statusCode.value() to text?.takeIf { it.trimStart().startsWith("{") }?.let { json.readTree(it) }
        }!!
    }

    private fun accessLog(output: CapturedOutput, requestId: String): Pair<JsonNode, List<JsonNode>> {
        val lines = awaitJsonLogLines(output) { it.accessLogFor(requestId) != null }
        val access = lines.accessLogFor(requestId)
        assertNotNull(access, "no access log for $requestId")
        return access!! to lines.withRequestId(requestId)
    }

    @Test
    fun `create emits an access log with method, route, status and duration`(output: CapturedOutput) {
        call(HttpMethod.POST, "/api/tasks", "it-create-1", """{"title":"Logged"}""")

        val (access, _) = accessLog(output, "it-create-1")
        assertEquals("POST", access.text("http.method"))
        assertEquals("/api/tasks", access.text("http.route"))
        assertEquals(201, access.field("http.status_code")!!.asInt())
        assertTrue(access.field("duration_ms")!!.asLong() >= 0)
        assertEquals("INFO", access.text("log.level"))
    }

    @Test
    fun `all lines of a request share the same trace and carry a span id`(output: CapturedOutput) {
        call(HttpMethod.POST, "/api/tasks", "it-create-2", """{"title":"Traced"}""")

        val (_, lines) = accessLog(output, "it-create-2")
        val traceIds = lines.map { it.text("trace_id") }.toSet()
        assertEquals(1, traceIds.size, "trace ids: $traceIds")
        assertTrue(Regex("^[0-9a-f]{32}$").matches(traceIds.single() ?: ""), "trace id: $traceIds")
        lines.forEach { assertFalse(it.text("span_id").isNullOrBlank(), "missing span_id in $it") }
    }

    @Test
    fun `route is the pattern, not the concrete path`(output: CapturedOutput) {
        val id = call(HttpMethod.POST, "/api/tasks", "it-route-0", """{"title":"Route"}""").second!!["id"].asLong()

        call(HttpMethod.PATCH, "/api/tasks/$id/complete", "it-route-1")

        assertEquals("/api/tasks/{id}/complete", accessLog(output, "it-route-1").first.text("http.route"))
    }

    @Test
    fun `unknown route is logged as UNMATCHED with 404`(output: CapturedOutput) {
        call(HttpMethod.GET, "/rota-inexistente", "it-unmatched-1")

        val access = accessLog(output, "it-unmatched-1").first
        assertEquals("UNMATCHED", access.text("http.route"))
        assertEquals(404, access.field("http.status_code")!!.asInt())
    }

    @Test
    fun `incoming traceparent is continued`(output: CapturedOutput) {
        val traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"

        call(HttpMethod.GET, "/api/tasks", "it-traceparent-1", headers = mapOf("traceparent" to traceparent))

        accessLog(output, "it-traceparent-1").second.forEach {
            assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", it.text("trace_id"))
        }
    }

    @Test
    fun `malformed traceparent starts a new trace and the request succeeds`(output: CapturedOutput) {
        val (status, _) = call(
            HttpMethod.GET, "/api/tasks", "it-traceparent-2", headers = mapOf("traceparent" to "not-a-traceparent"),
        )

        assertEquals(200, status)
        val traceId = accessLog(output, "it-traceparent-2").first.text("trace_id")
        assertTrue(Regex("^[0-9a-f]{32}$").matches(traceId ?: ""), "trace id: $traceId")
        assertNotEquals("4bf92f3577b34da6a3ce929d0e0e4736", traceId)
    }
}
