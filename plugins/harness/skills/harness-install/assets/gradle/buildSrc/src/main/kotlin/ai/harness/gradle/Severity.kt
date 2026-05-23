package ai.harness.gradle

/**
 * Represents the severity level of a harness validation finding.
 */
enum class Severity {
    /**
     * Critical validation failure.
     */
    ERROR,

    /**
     * Non-critical validation issue.
     */
    WARN,

    /**
     * Informational validation message.
     */
    INFO,
}
