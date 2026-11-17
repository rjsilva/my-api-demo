package com.spec.demo_ia_spec.observability

import java.io.PrintWriter
import java.io.StringWriter
import java.util.IdentityHashMap

/**
 * Copy of an exception (and its causes and suppressed exceptions) with sanitized messages and
 * the original stack frames. [toString] reports the original class, so the rendered stack trace
 * reads exactly like the original one, minus the secrets.
 */
class SanitizedException private constructor(
    private val originalClass: String,
    message: String?,
) : RuntimeException(message) {

    override fun toString(): String = message?.let { "$originalClass: $it" } ?: originalClass

    /** The full stack trace as text, as `printStackTrace` would print it. */
    fun stackTraceText(): String = StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()

    companion object {
        fun of(ex: Throwable): SanitizedException = copy(ex, IdentityHashMap())

        // `seen` keeps cyclic cause chains finite and maps each original to a single copy.
        private fun copy(ex: Throwable, seen: IdentityHashMap<Throwable, SanitizedException>): SanitizedException {
            seen[ex]?.let { return it }
            val copy = SanitizedException(ex.javaClass.name, LogSanitizer.sanitize(ex.message))
            copy.stackTrace = ex.stackTrace
            seen[ex] = copy
            ex.cause?.let { copy.initCause(copy(it, seen)) }
            ex.suppressed.forEach { suppressed -> copy(suppressed, seen).takeIf { it !== copy }?.let(copy::addSuppressed) }
            return copy
        }
    }
}
