package com.spec.demo_ia_spec.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(TaskNotFoundException::class)
    fun handleTaskNotFound(ex: TaskNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "Task not found"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message))
    }

    // Only "storage unreachable" failures become 503; other data errors are not retryable and stay 500.
    @ExceptionHandler(CannotCreateTransactionException::class, DataAccessResourceFailureException::class)
    fun handleStorageUnavailable(ex: Exception): ResponseEntity<ErrorResponse> {
        log.warn("Storage unavailable: {}", ex.message)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse(STORAGE_UNAVAILABLE_MESSAGE))
    }

    private companion object {
        const val STORAGE_UNAVAILABLE_MESSAGE = "Storage temporarily unavailable. Please try again later."
    }
}
