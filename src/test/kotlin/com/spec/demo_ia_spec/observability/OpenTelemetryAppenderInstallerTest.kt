package com.spec.demo_ia_spec.observability

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class OpenTelemetryAppenderInstallerTest {

    private val exporter = InMemoryLogRecordExporter.create()
    private val openTelemetry = OpenTelemetrySdk.builder()
        .setLoggerProvider(SdkLoggerProvider.builder().addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter)).build())
        .build()

    // Not additive: other tests in this JVM may have left an OTEL appender on the root logger.
    private val logger = (LoggerFactory.getLogger("otel-installer-test") as Logger).apply { isAdditive = false }

    // Same options as logback-spring.xml.
    private val appender = OpenTelemetryAppender().apply {
        context = LoggerFactory.getILoggerFactory() as LoggerContext
        setCaptureMdcAttributes("*")
        setCaptureKeyValuePairAttributes(true)
        start()
    }

    @AfterEach
    fun cleanUp() {
        logger.detachAppender(appender)
        MDC.clear()
        openTelemetry.close()
    }

    @Test
    fun `installed appender exports MDC and key-value pairs as attributes`() {
        logger.addAppender(appender)

        OpenTelemetryAppenderInstaller(openTelemetry).afterPropertiesSet()
        MDC.put("request_id", "x")
        logger.atInfo().setMessage("hello").addKeyValue("k", "v").log()

        val record = exporter.finishedLogRecordItems.single()
        assertEquals("hello", record.bodyValue?.asString())
        assertEquals("x", record.attributes.get(AttributeKey.stringKey("request_id")))
        assertEquals("v", record.attributes.get(AttributeKey.stringKey("k")))
    }
}
