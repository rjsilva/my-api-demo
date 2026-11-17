package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import com.spec.demo_ia_spec.support.accessLogFor
import com.spec.demo_ia_spec.support.awaitJsonLogLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/** SC-004 / FR-011 / FR-012: credentials, headers, query strings and bodies never reach the logs. */
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class SensitiveDataLoggingIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    private fun post(requestId: String, body: String): Int =
        api.post().uri("/api/tasks").header("X-Request-Id", requestId)
            .contentType(MediaType.APPLICATION_JSON).body(body)
            .exchange { _, response -> response.statusCode.value() }!!

    @Test
    fun `secrets in headers, query string and bodies never appear in the output`(output: CapturedOutput) {
        val listStatus = api.get().uri("/api/tasks?password=s3cr3t-q")
            .header("X-Request-Id", "sens-1")
            .header("Authorization", "Bearer s3cr3t-token")
            .header("Cookie", "session=s3cr3t-cookie")
            .exchange { _, response -> response.statusCode.value() }
        assertEquals(200, listStatus)

        assertEquals(201, post("sens-2", """{"title":"s3cr3t-title","description":"s3cr3t-desc"}"""))
        assertEquals(400, post("sens-3", """{"title":"${"s3cr3t-long".padEnd(101, 'x')}"}"""))

        // Wait until every request has finished logging before inspecting the output.
        awaitJsonLogLines(output) { lines -> listOf("sens-1", "sens-2", "sens-3").all { lines.accessLogFor(it) != null } }

        assertFalse(output.out.contains("s3cr3t"), "a secret leaked into the logs")
    }
}
