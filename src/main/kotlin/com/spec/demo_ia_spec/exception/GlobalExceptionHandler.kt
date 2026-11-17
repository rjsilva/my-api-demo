package com.spec.demo_ia_spec.exception

import com.spec.demo_ia_spec.observability.LogSanitizer
import com.spec.demo_ia_spec.observability.RequestIdFilter
import com.spec.demo_ia_spec.observability.SanitizedException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.sql.SQLTransientConnectionException
import java.util.UUID
import org.springframework.web.ErrorResponse as SpringErrorResponse

/**
 * Maps exceptions to the API error body and logs each one at the right level (research §7):
 * business errors as WARN without stack, dependency failures and unexpected errors as ERROR.
 * Logged messages are fixed text plus key-value context; exception messages go through
 * [LogSanitizer] and user input values are never logged (FR-011/012).
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(TaskNotFoundException::class)
    fun handleTaskNotFound(ex: TaskNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.atWarn().setMessage("Task not found")
            .addKeyValue("error.reason", "task_not_found")
            .addKeyValue("task.id", ex.id)
            .log()
        return error(HttpStatus.NOT_FOUND, ex.message ?: "Task not found", request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors
        // Field names only: rejected values are user input.
        log.atWarn().setMessage("Request validation failed")
            .addKeyValue("error.reason", "validation_failed")
            .addKeyValue("validation.fields", fieldErrors.map { it.field }.distinct().joinToString(","))
            .log()
        val message = fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return error(HttpStatus.BAD_REQUEST, message, request)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedBody(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.atWarn().setMessage("Malformed request body").addKeyValue("error.reason", "malformed_body").log()
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", request)
    }

    // e.g. a non-numeric task id. Not a Spring ErrorResponse, so it must not fall into the 500 handler.
    @ExceptionHandler(TypeMismatchException::class)
    fun handleTypeMismatch(ex: TypeMismatchException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.atWarn().setMessage("Invalid request parameter")
            .addKeyValue("error.reason", "invalid_parameter")
            .addKeyValue("parameter.name", ex.propertyName)
            .log()
        val message = ex.propertyName?.let { "Invalid value for '$it'" } ?: "Invalid request parameter"
        return error(HttpStatus.BAD_REQUEST, message, request)
    }

    // Only "storage unreachable" failures become 503; other data errors are not retryable and stay 500.
    @ExceptionHandler(CannotCreateTransactionException::class, DataAccessResourceFailureException::class)
    fun handleStorageUnavailable(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.atError().setMessage("Dependency unavailable")
            .addKeyValue("dependency", "database")
            .addKeyValue("failure", if (isTimeout(ex)) "timeout" else "unavailable")
            .addKeyValue("error.type", ex.javaClass.name)
            .addKeyValue("error.message", LogSanitizer.sanitize(ex.message))
            .log()
        return error(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE_MESSAGE, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        // Standard Spring MVC exceptions (404 no resource, 405, 415, ...) keep their own status.
        if (ex is SpringErrorResponse) {
            val status = ex.statusCode
            log.atWarn().setMessage("Request rejected")
                .addKeyValue("error.type", ex.javaClass.name)
                .addKeyValue("http.status_code", status.value())
                .log()
            val message = HttpStatus.resolve(status.value())?.reasonPhrase ?: "Request failed"
            return ResponseEntity.status(status).headers(ex.headers).body(ErrorResponse(message, requestIdOf(request)))
        }
        // The stack is rendered from a sanitized copy; error.type keeps the original class name
        // (a logged throwable would be reported under the copy's class by console and OTLP alike).
        val sanitized = SanitizedException.of(ex)
        log.atError().setMessage("Unexpected error while handling request")
            .addKeyValue("error.type", ex.javaClass.name)
            .addKeyValue("error.message", sanitized.message)
            .addKeyValue("error.stack_trace", sanitized.stackTraceText())
            .addKeyValue("http.method", request.method)
            .addKeyValue("http.route", RequestIdFilter.route(request))
            .log()
        return error(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE, request)
    }

    private fun isTimeout(ex: Throwable): Boolean =
        generateSequence(ex) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSE_DEPTH)
            .any { it is SQLTransientConnectionException || it.javaClass.simpleName.contains("timeout", ignoreCase = true) }

    private fun error(status: HttpStatus, message: String, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse(message, requestIdOf(request)))

    // The filter always sets the attribute; the fallback only covers handlers invoked outside it.
    private fun requestIdOf(request: HttpServletRequest): String =
        request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String ?: UUID.randomUUID().toString()

    private companion object {
        const val STORAGE_UNAVAILABLE_MESSAGE = "Storage temporarily unavailable. Please try again later."
        const val UNEXPECTED_ERROR_MESSAGE = "Unexpected error. Please contact support with the request id."
        const val MAX_CAUSE_DEPTH = 20
    }
}
