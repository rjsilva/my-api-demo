package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import com.spec.demo_ia_spec.support.has
import com.spec.demo_ia_spec.support.parseJsonLogLines
import com.spec.demo_ia_spec.support.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext

/**
 * FR-002: every log line is a JSON object with the mandatory fields; lines emitted outside a
 * request have no request_id.
 */
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest
@Import(TestcontainersConfiguration::class)
// Fresh context so the startup lines are emitted while the output is being captured.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class StructuredConsoleFormatIntegrationTest {

    @Test
    fun `every startup log line is ECS JSON with the mandatory fields and no request id`(output: CapturedOutput) {
        val lines = parseJsonLogLines(output)

        assertTrue(lines.isNotEmpty(), "expected startup log lines")
        for (line in lines) {
            for (field in listOf("@timestamp", "log.level", "message", "log.logger")) {
                assertFalse(line.text(field).isNullOrBlank(), "missing $field in $line")
            }
            assertEquals("demo-ia-spec", line.text("service.name"), "service.name in $line")
            assertFalse(line.has("request_id"), "unexpected request_id outside a request: $line")
        }
    }
}
