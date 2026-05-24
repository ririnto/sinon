package com.ririnto.sinon.harness.ast

/**
 * Internal source-analysis detail shared by PSI-backed rules.
 */
data class AstFinding(
    val rule: String,
    val file: String,
    val line: Int,
    val details: Map<String, String> = emptyMap(),
) {
    /**
     * Function detail used by rule implementations.
     */
    val function: String get() = detail("function")

    /**
     * Imported-path detail used by rule implementations.
     */
    val imported: String get() = detail("imported")

    /**
     * Branch-kind detail used by rule implementations.
     */
    val kind: String get() = detail("kind")

    /**
     * Top-level declaration count used by rule implementations.
     */
    val count: Int get() = intDetail("count")

    /**
     * First-kind detail used by rule implementations.
     */
    val firstKind: String get() = detail("firstKind")

    /**
     * Owner identifier used by rule implementations.
     */
    val ownerId: String get() = detail("ownerId")

    /**
     * Returns the detail value for a key, or an empty string when absent.
     */
    fun detail(key: String): String = details[key].orEmpty()

    /**
     * Returns the integer detail value for a key, or zero when absent or invalid.
     */
    fun intDetail(key: String): Int = details[key]?.toIntOrNull() ?: 0
}
