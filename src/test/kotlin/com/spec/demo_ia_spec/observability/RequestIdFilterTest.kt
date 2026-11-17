package com.spec.demo_ia_spec.observability

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

class RequestIdFilterTest {

    private val filter = RequestIdFilter()

    private val logger = LoggerFactory.getLogger(RequestIdFilter::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun cleanUp() {
        logger.detachAppender(appender)
        MDC.clear()
    }

    private fun run(
        headerValue: String? = null,
        chain: FilterChain = MockFilterChain(),
        request: MockHttpServletRequest = MockHttpServletRequest("GET", "/api/tasks"),
    ): MockHttpServletResponse {
        if (headerValue != null) request.addHeader(RequestIdFilter.HEADER, headerValue)
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        return response
    }

    private fun assertGeneratedUuid(response: MockHttpServletResponse) {
        val id = response.getHeader(RequestIdFilter.HEADER)
        assertDoesNotThrow({ UUID.fromString(id) }, "expected a generated UUID, got '$id'")
    }

    @Test
    fun `without header a UUID is generated and returned`() {
        assertGeneratedUuid(run())
    }

    @Test
    fun `valid client value is reused`() {
        assertEquals("suporte-1234", run("suporte-1234").getHeader(RequestIdFilter.HEADER))
    }

    @Test
    fun `value with 128 allowed characters is accepted`() {
        val value = "aZ09._-".repeat(19).take(128)
        assertEquals(value, run(value).getHeader(RequestIdFilter.HEADER))
    }

    @Test
    fun `invalid values are discarded and replaced by a UUID`() {
        for (value in listOf("a".repeat(129), "", "with space", "abc\r\nInjected: x")) {
            val response = run(value)
            assertNotEquals(value, response.getHeader(RequestIdFilter.HEADER), "value '$value' must be discarded")
            assertGeneratedUuid(response)
        }
    }

    @Test
    fun `MDC and request attribute hold the effective id during the chain`() {
        var mdcValue: String? = null
        var attributeValue: Any? = null
        val chain = FilterChain { request, _ ->
            mdcValue = MDC.get(RequestIdFilter.MDC_KEY)
            attributeValue = request.getAttribute(RequestIdFilter.ATTRIBUTE)
        }

        run("req-42", chain)

        assertEquals("req-42", mdcValue)
        assertEquals("req-42", attributeValue)
        assertEquals("request_id", RequestIdFilter.MDC_KEY)
    }

    @Test
    fun `MDC is cleared after the filter`() {
        run("req-42")
        assertNull(MDC.get(RequestIdFilter.MDC_KEY))
    }

    @Test
    fun `MDC is cleared even when the chain throws`() {
        val chain = FilterChain { _, _ -> throw IllegalStateException("boom") }

        assertThrows(IllegalStateException::class.java) { run("req-42", chain) }

        assertNull(MDC.get(RequestIdFilter.MDC_KEY))
    }

    @Test
    fun `response header is set before the chain runs`() {
        var headerDuringChain: String? = null
        val chain = FilterChain { _, response ->
            headerDuringChain = (response as MockHttpServletResponse).getHeader(RequestIdFilter.HEADER)
        }

        run("req-42", chain)

        assertEquals("req-42", headerDuringChain)
    }

    // --- Access log (US2, FR-007) ---

    private fun accessLogEvent(): ILoggingEvent {
        val events = appender.list.filter { it.message == "HTTP request completed" }
        assertEquals(1, events.size, "access log events: ${appender.list.map { it.message }}")
        return events.single()
    }

    private fun ILoggingEvent.kv(): Map<String, Any?> = (keyValuePairs ?: emptyList()).associate { it.key to it.value }

    @Test
    fun `access log carries method, route pattern, status and duration`() {
        val request = MockHttpServletRequest("PATCH", "/api/tasks/7/complete").apply { queryString = "password=s3cr3t" }
        val chain = FilterChain { req, res ->
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/tasks/{id}/complete")
            (res as MockHttpServletResponse).status = 200
        }

        run("req-42", chain, request)

        val event = accessLogEvent()
        assertEquals(Level.INFO.toString(), event.level.toString())
        val kv = event.kv()
        assertEquals("PATCH", kv["http.method"])
        assertEquals("/api/tasks/{id}/complete", kv["http.route"])
        assertEquals(200, kv["http.status_code"])
        assertTrue((kv["duration_ms"] as Long) >= 0)
        assertEquals("req-42", event.mdcPropertyMap[RequestIdFilter.MDC_KEY])
        assertTrue(!event.formattedMessage.contains("s3cr3t") && kv.values.none { it.toString().contains("s3cr3t") })
    }

    @Test
    fun `access log route is UNMATCHED without a handler pattern`() {
        run("req-42", FilterChain { _, res -> (res as MockHttpServletResponse).status = 404 })

        val kv = accessLogEvent().kv()
        assertEquals("UNMATCHED", kv["http.route"])
        assertEquals(404, kv["http.status_code"])
    }

    @Test
    fun `access log is emitted even when the chain throws`() {
        assertThrows(IllegalStateException::class.java) {
            run("req-42", FilterChain { _, _ -> throw IllegalStateException("boom") })
        }

        accessLogEvent()
    }
}
