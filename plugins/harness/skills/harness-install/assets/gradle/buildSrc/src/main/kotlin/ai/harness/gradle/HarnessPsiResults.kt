package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Aggregated validation findings produced by source AST/PSI analysis.
 */
@Serializable
data class HarnessPsiResults(
    val findings: List<Finding>,
) {
    /**
     * Represents a validation finding with severity, category, and message.
     */
    @Serializable
    data class Finding(
        val severity: Severity,
        val category: String,
        val message: String,
    )
}
