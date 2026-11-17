package com.spec.demo_ia_spec.support

import org.junit.jupiter.api.Assertions.fail
import org.springframework.boot.test.system.CapturedOutput
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private val jsonMapper = JsonMapper()

// Logback's bootstrap format ("HH:mm:ss.SSS [thread] LEVEL logger -- msg"), used only by the
// Spring test framework before Spring Boot initializes logging. Not application output.
private val logbackBootstrapLine = Regex("""^\d{2}:\d{2}:\d{2}\.\d{3} \[[^]]+] [A-Z]+ +\S+ -- .*""")

/**
 * Parses every non-blank line of the captured stdout as JSON. Fails the test if any line is not
 * valid JSON, since every log line must be structured (FR-002).
 */
fun parseJsonLogLines(output: CapturedOutput): List<JsonNode> =
    output.out.lineSequence()
        .filter { it.isNotBlank() && !logbackBootstrapLine.matches(it) }
        .map { line ->
            try {
                jsonMapper.readTree(line).also { if (!it.isObject) fail<Unit>("Log line is not a JSON object: $line") }
            } catch (e: Exception) {
                fail("Log line is not valid JSON: $line", e)
            }
        }
        .toList()

fun List<JsonNode>.withRequestId(id: String): List<JsonNode> = filter { it.text("request_id") == id }

/**
 * Resolves an ECS field name such as `log.level` or `http.route`. Spring Boot's ECS formatter
 * writes dotted names as nested objects (`{"log":{"level":...}}`); a flat key is accepted too.
 */
fun JsonNode.field(name: String): JsonNode? {
    val flat = get(name)
    if (flat != null && !flat.isNull) return flat
    val nested = name.split('.').fold(this as JsonNode?) { node, part -> node?.get(part) }
    return nested?.takeIf { !it.isNull }
}

fun JsonNode.text(name: String): String? = field(name)?.asString()

fun JsonNode.has(name: String): Boolean = field(name) != null

/**
 * The access log is written after the response body is flushed, so the client can get the
 * response a moment before the line exists. Polls the captured output until [until] holds.
 */
fun awaitJsonLogLines(
    output: CapturedOutput,
    timeoutMillis: Long = 5_000,
    until: (List<JsonNode>) -> Boolean,
): List<JsonNode> {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        val lines = parseJsonLogLines(output)
        if (until(lines) || System.currentTimeMillis() > deadline) return lines
        Thread.sleep(50)
    }
}

const val ACCESS_LOG_MESSAGE = "HTTP request completed"

fun List<JsonNode>.accessLogFor(requestId: String): JsonNode? =
    withRequestId(requestId).firstOrNull { it.text("message") == ACCESS_LOG_MESSAGE }
