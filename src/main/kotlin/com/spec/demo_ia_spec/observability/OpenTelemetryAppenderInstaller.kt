package com.spec.demo_ia_spec.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component

/**
 * Connects the OTEL Logback appender (logback-spring.xml) to the OpenTelemetry SDK that Spring
 * Boot auto-configures with the OTLP log exporter. Events logged before this runs are buffered by
 * the appender and replayed once installed.
 */
@Component
class OpenTelemetryAppenderInstaller(private val openTelemetry: OpenTelemetry) : InitializingBean {

    override fun afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry)
    }
}
