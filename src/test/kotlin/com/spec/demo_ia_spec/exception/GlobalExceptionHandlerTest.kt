package com.spec.demo_ia_spec.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
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

    @Suppress("unused")
    private fun dummyTarget(value: String) = value
}