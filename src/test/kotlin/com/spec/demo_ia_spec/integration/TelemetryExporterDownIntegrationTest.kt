package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import com.spec.demo_ia_spec.support.accessLogFor
import com.spec.demo_ia_spec.support.awaitJsonLogLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * FR-015 / SC-007 / Constitution VII: with the OTLP collector unreachable (port 1 refuses
 * connections), requests keep succeeding quickly and logs keep going to the console.
 */
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.logging.export.otlp.enabled=true",
        "management.opentelemetry.logging.export.otlp.endpoint=http://localhost:1/v1/logs",
        "management.opentelemetry.logging.export.schedule-delay=100ms",
        "management.tracing.export.otlp.enabled=true",
        "management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:1/v1/traces",
        "management.opentelemetry.tracing.export.schedule-delay=100ms",
        "management.otlp.metrics.export.enabled=true",
        "management.otlp.metrics.export.url=http://localhost:1/v1/metrics",
        "management.otlp.metrics.export.step=1s",
    ],
)
@Import(TestcontainersConfiguration::class)
class TelemetryExporterDownIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    @Test
    fun `requests succeed fast and console logging continues when the collector is down`(output: CapturedOutput) {
        val ids = (1..20).map { "down-$it" }
        for (id in ids) {
            val start = System.nanoTime()
            val status = api.get().uri("/api/tasks").header("X-Request-Id", id)
                .exchange { _, response -> response.statusCode.value() }
            val elapsed = Duration.ofNanos(System.nanoTime() - start)

            assertEquals(200, status, "request $id")
            assertTrue(elapsed < Duration.ofSeconds(1), "request $id took $elapsed")
        }

        val lines = awaitJsonLogLines(output) { all -> ids.all { all.accessLogFor(it) != null } }
        ids.forEach { assertTrue(lines.accessLogFor(it) != null, "no console access log for $it") }
    }
}
