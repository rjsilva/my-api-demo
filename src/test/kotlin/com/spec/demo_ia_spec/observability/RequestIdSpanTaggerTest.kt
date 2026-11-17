package com.spec.demo_ia_spec.observability

import io.micrometer.tracing.test.simple.SimpleSpan
import io.micrometer.tracing.test.simple.SimpleTracer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import io.micrometer.tracing.Tracer

class RequestIdSpanTaggerTest {

    private fun providerOf(tracer: Tracer?): ObjectProvider<Tracer> =
        StaticListableBeanFactory().apply { if (tracer != null) addBean("tracer", tracer) }.getBeanProvider(Tracer::class.java)

    private val request = MockHttpServletRequest().apply { setAttribute(RequestIdFilter.ATTRIBUTE, "req-7") }

    @Test
    fun `current span is tagged with the request id`() {
        val tracer = SimpleTracer()
        val span = tracer.nextSpan().name("http").start()

        tracer.withSpan(span).use {
            assertTrue(RequestIdSpanTagger(providerOf(tracer)).preHandle(request, MockHttpServletResponse(), Any()))
        }

        assertEquals("req-7", (span as SimpleSpan).tags["request.id"])
    }

    @Test
    fun `no tracer or no current span is a no-op`() {
        assertDoesNotThrow { RequestIdSpanTagger(providerOf(null)).preHandle(request, MockHttpServletResponse(), Any()) }
        assertDoesNotThrow { RequestIdSpanTagger(providerOf(SimpleTracer())).preHandle(request, MockHttpServletResponse(), Any()) }
    }
}
