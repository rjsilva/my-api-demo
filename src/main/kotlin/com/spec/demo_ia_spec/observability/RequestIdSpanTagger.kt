package com.spec.demo_ia_spec.observability

import io.micrometer.tracing.Tracer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Adds `request.id` to the current HTTP server span, so a trace can be found in Tempo by the
 * same identifier support gets from the client.
 */
@Configuration(proxyBeanMethods = false)
class RequestIdSpanTagger(private val tracer: ObjectProvider<Tracer>) : HandlerInterceptor, WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(this)
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String ?: return true
        tracer.ifAvailable { it.currentSpan()?.tag(SPAN_TAG, requestId) }
        return true
    }

    companion object {
        const val SPAN_TAG = "request.id"
    }
}
