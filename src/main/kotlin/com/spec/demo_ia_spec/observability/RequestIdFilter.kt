package com.spec.demo_ia_spec.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

/**
 * Gives every request an identifier (FR-003..FR-005): the client's `X-Request-Id` when it is
 * safe to reuse, otherwise a new UUID. The id goes to the MDC (`request_id`), to a request
 * attribute (for error bodies) and to the response header, which is written before the chain
 * runs so it is present on every status, including errors.
 *
 * It also writes one structured access log per request (FR-007). It runs right after Spring's
 * ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1), so the HTTP span is still in scope and
 * the access log carries trace_id/span_id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class RequestIdFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val start = System.nanoTime()
        val requestId = request.getHeader(HEADER)?.takeIf { VALID.matches(it) } ?: UUID.randomUUID().toString()
        response.setHeader(HEADER, requestId)
        request.setAttribute(ATTRIBUTE, requestId)
        MDC.put(MDC_KEY, requestId)
        try {
            chain.doFilter(request, response)
        } finally {
            logAccess(request, response, start)
            // Threads are pooled: never let the id leak into the next request (SC-002).
            MDC.remove(MDC_KEY)
        }
    }

    // Fixed field list: never the URI with values, query string, headers or body (FR-011/012).
    private fun logAccess(request: HttpServletRequest, response: HttpServletResponse, start: Long) {
        log.atInfo()
            .setMessage("HTTP request completed")
            .addKeyValue("http.method", request.method)
            .addKeyValue("http.route", route(request))
            .addKeyValue("http.status_code", response.status)
            .addKeyValue("duration_ms", (System.nanoTime() - start) / 1_000_000)
            .log()
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "request_id"
        const val UNMATCHED_ROUTE = "UNMATCHED"
        val ATTRIBUTE: String = RequestIdFilter::class.java.name + ".requestId"

        // Whitelist blocks header/log injection (CR/LF) and abusive values.
        val VALID = Regex("^[A-Za-z0-9._-]{1,128}$")

        /** Route pattern of the request (never the concrete URI), or [UNMATCHED_ROUTE]. */
        // "/**" is the static-resource catch-all: in this API it only matches unknown routes.
        fun route(request: HttpServletRequest): String =
            (request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String)
                ?.takeIf { it != "/**" }
                ?: UNMATCHED_ROUTE
    }
}
