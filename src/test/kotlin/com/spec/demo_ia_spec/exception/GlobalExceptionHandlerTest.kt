package com.spec.demo_ia_spec.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpStatus
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `TaskNotFoundException maps to 404 with message body`() {
        val response = handler.handleTaskNotFound(TaskNotFoundException(42L))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue(response.body?.message?.isNotBlank() == true)
    }

    @Test
    fun `validation error maps to 400 with message body`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(FieldError("request", "title", "must not be blank"))
        val parameter = MethodParameter(
            GlobalExceptionHandlerTest::class.java.getDeclaredMethod("dummyTarget", String::class.java),
            0,
        )
        val exception = MethodArgumentNotValidException(parameter, bindingResult)

        val response = handler.handleValidation(exception)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body?.message?.isNotBlank() == true)
    }

    @Test
    fun `CannotCreateTransactionException maps to 503 with generic message`() {
        val response = handler.handleStorageUnavailable(CannotCreateTransactionException("x"))

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(STORAGE_UNAVAILABLE_MESSAGE, response.body?.message)
        assertFalse(response.body!!.message.contains("x"))
    }

    @Test
    fun `DataAccessResourceFailureException maps to 503 with generic message`() {
        val response = handler.handleStorageUnavailable(DataAccessResourceFailureException("x"))

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(STORAGE_UNAVAILABLE_MESSAGE, response.body?.message)
        assertFalse(response.body!!.message.contains("x"))
    }

    private companion object {
        const val STORAGE_UNAVAILABLE_MESSAGE = "Storage temporarily unavailable. Please try again later."
    }

    @Suppress("unused")
    private fun dummyTarget(value: String) = value
}