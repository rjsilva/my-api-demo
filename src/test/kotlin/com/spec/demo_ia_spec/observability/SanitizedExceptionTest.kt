package com.spec.demo_ia_spec.observability

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class SanitizedExceptionTest {

    @Test
    fun `keeps the stack trace and sanitizes the message`() {
        val original = IllegalStateException("boom password=abc")

        val sanitized = SanitizedException.of(original)

        assertArrayEquals(original.stackTrace, sanitized.stackTrace)
        assertFalse(sanitized.message!!.contains("abc"))
        assertTrue(sanitized.message!!.startsWith("boom"))
    }

    @Test
    fun `toString starts with the original class name`() {
        val sanitized = SanitizedException.of(IllegalStateException("boom"))

        assertEquals("java.lang.IllegalStateException: boom", sanitized.toString())
    }

    @Test
    fun `causes are wrapped and sanitized recursively`() {
        val root = RuntimeException("jdbc:postgresql://user:s3cr3t@db:5432/tasks unreachable")
        val original = IllegalStateException("outer token=t0k", root)

        val sanitized = SanitizedException.of(original)
        val cause = sanitized.cause

        assertNotNull(cause)
        assertTrue(cause.toString().startsWith("java.lang.RuntimeException: "))
        assertFalse(cause!!.message!!.contains("s3cr3t"))
        assertArrayEquals(root.stackTrace, cause.stackTrace)
        assertFalse(sanitized.message!!.contains("t0k"))
    }

    @Test
    fun `cyclic cause chains terminate`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)

        assertTimeoutPreemptively(Duration.ofSeconds(2)) { SanitizedException.of(a) }
    }
}
