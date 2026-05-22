package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of a companion object that violates position requirements in `file` at `line`.
 */
@Serializable
data class CompanionPositionResult(
    val file: String,
    val className: String,
    val line: Int,
)
