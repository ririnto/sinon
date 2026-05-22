package ai.harness.gradle

/**
 * Represents a validation finding with severity, category, and message.
 */
data class Finding(
    val severity: Severity,
    val category: String,
    val message: String,
)
