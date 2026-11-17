package com.spec.demo_ia_spec.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LogSanitizerTest {

    private fun assertMasked(input: String, secret: String) {
        val output = LogSanitizer.sanitize(input)!!
        assertFalse(output.contains(secret), "'$secret' leaked in '$output'")
        assertEquals(true, output.contains("***"), "no mask in '$output'")
    }

    @Test
    fun `password and pwd values are masked with = or colon, ignoring case`() {
        assertMasked("login failed password=abc123", "abc123")
        assertMasked("PASSWORD: abc123 rest", "abc123")
        assertMasked("pwd=abc123", "abc123")
        assertMasked("Pwd:abc123", "abc123")
    }

    @Test
    fun `token, secret and api key values are masked`() {
        assertMasked("token=tok999", "tok999")
        assertMasked("secret=sec999", "sec999")
        assertMasked("api_key=key999", "key999")
    }

    @Test
    fun `authorization headers and bearer tokens are masked`() {
        assertMasked("Authorization: Basic dXNlcjpwYXNz", "dXNlcjpwYXNz")
        assertMasked("sent Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig", "eyJhbGciOiJIUzI1NiJ9")
    }

    @Test
    fun `credentials in connection URLs are masked`() {
        assertEquals(
            "connection to jdbc:postgresql://***@host:5432/db failed",
            LogSanitizer.sanitize("connection to jdbc:postgresql://user:pass@host:5432/db failed"),
        )
    }

    @Test
    fun `text without secrets is unchanged and null stays null`() {
        val text = "Task not found: 42 (connection refused to localhost:5432)"
        assertEquals(text, LogSanitizer.sanitize(text))
        assertNull(LogSanitizer.sanitize(null))
    }
}
