package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import com.spec.demo_ia_spec.support.ACCESS_LOG_MESSAGE
import com.spec.demo_ia_spec.support.awaitJsonLogLines
import com.spec.demo_ia_spec.support.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** SC-002: under concurrency no log line is attributed to the wrong request. */
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class ConcurrentRequestIdIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    @Test
    fun `50 parallel requests keep their own request id`(output: CapturedOutput) {
        val ids = (1..50).map { "conc-$it" }
        val pool = Executors.newFixedThreadPool(16)
        val echoed = try {
            pool.invokeAll(ids.map { id ->
                Callable {
                    api.get().uri("/api/tasks").header("X-Request-Id", id)
                        .exchange { _, response -> response.headers.getFirst("X-Request-Id") }
                }
            }).map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(ids, echoed, "each response must echo its own id")

        val accessCount = { lines: List<tools.jackson.databind.JsonNode> ->
            lines.filter { it.text("message") == ACCESS_LOG_MESSAGE && it.text("request_id") in ids }
                .groupingBy { it.text("request_id")!! }.eachCount()
        }
        val lines = awaitJsonLogLines(output) { accessCount(it).size == ids.size }

        val counts = accessCount(lines)
        ids.forEach { assertEquals(1, counts[it], "access log lines for $it") }

        val requestIdsByTrace = lines.filter { it.text("trace_id") != null }
            .groupBy({ it.text("trace_id")!! }, { it.text("request_id") })
            .mapValues { it.value.toSet() }
        requestIdsByTrace.forEach { (trace, requestIds) ->
            assertEquals(1, requestIds.size, "trace $trace mixes request ids $requestIds")
        }
    }
}
