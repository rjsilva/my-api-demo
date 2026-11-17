package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import org.testcontainers.grafana.LgtmStackContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/**
 * FR-014 / FR-017 / SC-003 (end-to-end, tag "e2e", run with -Pe2e): a request's logs reach Loki
 * through OTLP and can be found by request_id.
 */
@Tag("e2e")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.logging.export.otlp.enabled=true",
        "management.tracing.export.otlp.enabled=true",
        "management.opentelemetry.logging.export.schedule-delay=200ms",
    ],
)
@Import(TestcontainersConfiguration::class, LgtmLogExportIntegrationTest.Lgtm::class)
class LgtmLogExportIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class Lgtm {
        @Bean
        @ServiceConnection
        fun lgtmContainer(): LgtmStackContainer = LgtmStackContainer(TestcontainersConfiguration.LGTM_IMAGE)
    }

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var lgtm: LgtmStackContainer

    private val json = JsonMapper()

    @Test
    fun `request logs are searchable in Loki by request id`() {
        val requestId = "e2e-${UUID.randomUUID()}"
        val start = Instant.now().minusSeconds(60)

        val status = RestClient.create("http://localhost:$port").get().uri("/api/tasks")
            .header("X-Request-Id", requestId)
            .exchange { _, response -> response.statusCode.value() }
        assertEquals(200, status)

        val query = """{service_name="demo-ia-spec"} | request_id="$requestId""""
        val loki = RestClient.create(lgtm.lokiUrl)
        val deadline = System.currentTimeMillis() + 30_000
        var streams: JsonNode? = null
        while (System.currentTimeMillis() < deadline) {
            val uri = UriComponentsBuilder.fromUriString(lgtm.lokiUrl).path("/loki/api/v1/query_range")
                .queryParam("query", "{query}")
                .queryParam("start", start.toEpochMilli() * 1_000_000)
                .queryParam("limit", 100)
                .buildAndExpand(query).encode().toUri()
            val body = loki.get().uri(uri).retrieve().body(String::class.java)
            streams = json.readTree(body)["data"]["result"]
            if (streams.any { stream -> stream["values"].any { it[1].asString().contains("HTTP request completed") } }) break
            Thread.sleep(1_000)
        }

        val access = streams!!.firstOrNull { stream ->
            stream["values"].any { it[1].asString().contains("HTTP request completed") }
        }
        assertTrue(access != null, "no access log for $requestId in Loki; got $streams")
        assertTrue(!access!!["stream"]["trace_id"].isNull && access["stream"].has("trace_id"), "no trace_id in $access")
    }
}
