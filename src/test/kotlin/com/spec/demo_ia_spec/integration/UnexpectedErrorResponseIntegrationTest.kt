package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import com.spec.demo_ia_spec.support.awaitJsonLogLines
import com.spec.demo_ia_spec.support.text
import com.spec.demo_ia_spec.support.withRequestId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

/** FR-008 / FR-013 / SC-005: unexpected errors return a generic 500 and are logged with a stack. */
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, UnexpectedErrorResponseIntegrationTest.BoomController::class)
class UnexpectedErrorResponseIntegrationTest {

    // Nested in the test class, so component scanning ignores it; only this test imports it.
    @RestController
    class BoomController {
        @GetMapping("/test/boom")
        fun boom(): String = throw IllegalStateException("boom")
    }

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    @Test
    fun `unexpected error returns a generic body and logs the stack with the request id`(output: CapturedOutput) {
        val (status, header, body) = api.get().uri("/test/boom").exchange { _, response ->
            Triple(response.statusCode.value(), response.headers.getFirst("X-Request-Id"), response.bodyTo(String::class.java)!!)
        }!!

        assertEquals(500, status)
        assertNotNull(header)
        val json = JsonMapper().readTree(body)
        assertEquals(setOf("message", "requestId"), json.propertyNames().toSet())
        assertEquals("Unexpected error. Please contact support with the request id.", json["message"].asString())
        assertEquals(header, json["requestId"].asString())
        for (leak in listOf("boom", "IllegalStateException", "trace", "exception")) {
            assertFalse(body.contains(leak), "response body leaks '$leak': $body")
        }

        val lines = awaitJsonLogLines(output) { all -> all.withRequestId(header!!).any { it.text("log.level") == "ERROR" } }
        val error = lines.withRequestId(header!!).firstOrNull { it.text("log.level") == "ERROR" }
        assertNotNull(error, "no ERROR line for request $header")
        assertEquals("java.lang.IllegalStateException", error!!.text("error.type"))
        assertFalse(error.text("error.stack_trace").isNullOrBlank())
    }
}
