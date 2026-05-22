package ai.harness.gradle

/**
 * Represents the severity level of a harness validation finding.
 */
enum class Severity {
	ERROR,
	WARN,
	INFO,
}

/**
 * Represents a validation finding with severity, category, and message.
 */
data class Finding(
	val severity: Severity,
	val category: String,
	val message: String,
)
