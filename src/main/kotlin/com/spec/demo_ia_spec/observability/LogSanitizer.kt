package com.spec.demo_ia_spec.observability

/**
 * Masks secrets in free text (typically exception messages) before it is logged (FR-011/012).
 * Applied at the source, so console and OTLP export receive the same protected content.
 */
object LogSanitizer {

    private const val MASK = "***"

    // Applied in order; all case-insensitive.
    private val rules: List<Pair<Regex, String>> = listOf(
        // scheme://user:pass@host → scheme://***@host
        Regex("""([a-z][a-z0-9+.-]*://)[^/\s:@]+:[^/\s@]+@""", RegexOption.IGNORE_CASE) to "$1$MASK@",
        // Authorization: Basic xyz / Authorization=Bearer xyz
        Regex("""(authorization\s*[:=]\s*)(?:(?:basic|bearer|digest)\s+)?\S+""", RegexOption.IGNORE_CASE) to "$1$MASK",
        Regex("""\b(bearer\s+)[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE) to "$1$MASK",
        // password=..., pwd: ..., token=..., secret=..., api_key=...
        Regex("""\b((?:password|passwd|pwd|token|secret|api[_-]?key)\s*[:=]\s*)[^\s,;&"']+""", RegexOption.IGNORE_CASE) to "$1$MASK",
    )

    fun sanitize(text: String?): String? =
        text?.let { rules.fold(it) { acc, (regex, replacement) -> regex.replace(acc, replacement) } }
}
