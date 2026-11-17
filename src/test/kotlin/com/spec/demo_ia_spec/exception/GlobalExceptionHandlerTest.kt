package com.spec.demo_ia_spec.exception

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.spec.demo_ia_spec.observability.RequestIdFilter
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.beans.TypeMismatchException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.sql.SQLTransientConnectionException
import java.util.UUID

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    private val request = MockHttpServletRequest("GET", "/api/tasks").apply {
        setAttribute(RequestIdFilter.ATTRIBUTE, "req-1")
    }

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    private fun singleEvent(): ILoggingEvent {
        assertEquals(1, appender.list.size, "events: ${appender.list.map { it.formattedMessage }}")
        return appender.list.single()
    }

    private fun ILoggingEvent.kv(): Map<String, Any?> = (keyValuePairs ?: emptyList()).associate { it.key to it.value }

    private fun ILoggingEvent.everything(): String =
        "$formattedMessage ${kv()} ${throwableProxy?.message ?: ""} ${argumentArray?.toList() ?: ""}"

    private fun validationException(rejectedValue: Any? = null): MethodArgumentNotValidException {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(
            FieldError("request", "title", rejectedValue, false, null, null, "must not be blank"),
        )
        val parameter = MethodParameter(
            GlobalExceptionHandlerTest::class.java.getDeclaredMethod("dummyTarget", String::class.java),
            0,
        )
        return MethodArgumentNotValidException(parameter, bindingResult)
    }

    @Test
    fun `TaskNotFoundException maps to 404 with message body`() {
        val response = handler.handleTaskNotFound(TaskNotFoundException(42L), request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue(response.body?.message?.isNotBlank() == true)
    }

    @Test
    fun `validation error maps to 400 with message body`() {
        val response = handler.handleValidation(validationException(), request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body?.message?.isNotBlank() == true)
    }

    @Test
    fun `CannotCreateTransactionException maps to 503 with generic message`() {
        val response = handler.handleStorageUnavailable(CannotCreateTransactionException("x"), request)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(STORAGE_UNAVAILABLE_MESSAGE, response.body?.message)
        assertFalse(response.body!!.message.contains("x"))
    }

    @Test
    fun `DataAccessResourceFailureException maps to 503 with generic message`() {
        val response = handler.handleStorageUnavailable(DataAccessResourceFailureException("x"), request)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(STORAGE_UNAVAILABLE_MESSAGE, response.body?.message)
        assertFalse(response.body!!.message.contains("x"))
    }

    // --- requestId in the error body (US1, FR-006) ---

    @Test
    fun `404 body carries the request id and the unchanged message`() {
        val body = handler.handleTaskNotFound(TaskNotFoundException(42L), request).body!!

        assertEquals("req-1", body.requestId)
        assertEquals("Task not found: 42", body.message)
    }

    @Test
    fun `400 body carries the request id and the unchanged message`() {
        val body = handler.handleValidation(validationException(), request).body!!

        assertEquals("req-1", body.requestId)
        assertEquals("title: must not be blank", body.message)
    }

    @Test
    fun `503 body carries the request id and the unchanged message`() {
        val body = handler.handleStorageUnavailable(CannotCreateTransactionException("x"), request).body!!

        assertEquals("req-1", body.requestId)
        assertEquals(STORAGE_UNAVAILABLE_MESSAGE, body.message)
    }

    @Test
    fun `without the request attribute a fallback UUID is used`() {
        val body = handler.handleTaskNotFound(TaskNotFoundException(42L), MockHttpServletRequest()).body!!

        assertDoesNotThrow { UUID.fromString(body.requestId) }
    }

    // --- Log level and context per error type (US3, research §7) ---

    @Test
    fun `404 logs WARN without stack trace with reason and task id`() {
        handler.handleTaskNotFound(TaskNotFoundException(42L), request)

        val event = singleEvent()
        assertEquals(Level.WARN, event.level)
        assertNull(event.throwableProxy)
        assertEquals("task_not_found", event.kv()["error.reason"])
        assertEquals(42L, event.kv()["task.id"])
    }

    @Test
    fun `400 logs WARN with field names only, never the rejected value`() {
        handler.handleValidation(validationException(rejectedValue = "s3cr3t-value"), request)

        val event = singleEvent()
        assertEquals(Level.WARN, event.level)
        assertNull(event.throwableProxy)
        assertEquals("validation_failed", event.kv()["error.reason"])
        assertEquals("title", event.kv()["validation.fields"])
        assertFalse(event.everything().contains("s3cr3t-value"))
    }

    @Test
    fun `503 logs ERROR with dependency context and sanitized message, no stack`() {
        handler.handleStorageUnavailable(DataAccessResourceFailureException("db down password=abc"), request)

        val event = singleEvent()
        assertEquals(Level.ERROR, event.level)
        assertNull(event.throwableProxy)
        assertEquals("database", event.kv()["dependency"])
        assertEquals("unavailable", event.kv()["failure"])
        assertEquals(DataAccessResourceFailureException::class.java.name, event.kv()["error.type"])
        assertFalse(event.everything().contains("abc"))
    }

    @Test
    fun `503 caused by a connection timeout is logged as timeout`() {
        val ex = CannotCreateTransactionException("x", SQLTransientConnectionException("request timed out after 5000ms"))

        handler.handleStorageUnavailable(ex, request)

        assertEquals("timeout", singleEvent().kv()["failure"])
    }

    @Test
    fun `unexpected exception returns generic 500 and logs ERROR with sanitized stack`() {
        val routed = MockHttpServletRequest("PATCH", "/api/tasks/7/complete").apply {
            setAttribute(RequestIdFilter.ATTRIBUTE, "req-1")
            setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/tasks/{id}/complete")
        }

        val response = handler.handleUnexpected(IllegalStateException("boom password=abc"), routed)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Unexpected error. Please contact support with the request id.", response.body!!.message)
        assertEquals("req-1", response.body!!.requestId)

        val event = singleEvent()
        assertEquals(Level.ERROR, event.level)
        val kv = event.kv()
        assertEquals("java.lang.IllegalStateException", kv["error.type"])
        val stack = kv["error.stack_trace"] as String
        assertTrue(stack.startsWith("java.lang.IllegalStateException: boom"), stack)
        assertTrue(stack.contains("\tat "), "stack frames expected: $stack")
        assertEquals("PATCH", kv["http.method"])
        assertEquals("/api/tasks/{id}/complete", kv["http.route"])
        assertFalse(event.everything().contains("abc"))
    }

    @Test
    fun `standard Spring MVC exceptions keep their own status`() {
        val notFound = handler.handleUnexpected(NoResourceFoundException(HttpMethod.GET, "/x", "x"), request)
        assertEquals(404, notFound.statusCode.value())
        assertEquals("req-1", notFound.body!!.requestId)

        val notAllowed = handler.handleUnexpected(HttpRequestMethodNotSupportedException("PUT", listOf("GET")), request)
        assertEquals(405, notAllowed.statusCode.value())
        assertTrue(appender.list.all { it.level == Level.WARN })
    }

    @Test
    fun `malformed JSON body maps to 400`() {
        val ex = HttpMessageNotReadableException("JSON parse error", MockHttpInputMessage(ByteArray(0)))

        val response = handler.handleMalformedBody(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Malformed request body", response.body!!.message)
        assertEquals("malformed_body", singleEvent().kv()["error.reason"])
    }

    @Test
    fun `type mismatch on a path variable stays 400`() {
        val ex = TypeMismatchException("abc", Long::class.java)

        val response = handler.handleTypeMismatch(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("req-1", response.body!!.requestId)
        assertEquals(Level.WARN, singleEvent().level)
        assertFalse(singleEvent().everything().contains("abc"))
    }

    private companion object {
        const val STORAGE_UNAVAILABLE_MESSAGE = "Storage temporarily unavailable. Please try again later."
    }

    @Suppress("unused")
    private fun dummyTarget(value: String) = value
}