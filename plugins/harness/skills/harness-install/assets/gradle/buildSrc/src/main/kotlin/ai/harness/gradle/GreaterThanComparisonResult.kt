package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of a greater-than comparison detection in a Kotlin source file.
 */
@Serializable
data class GreaterThanComparisonResult(
    val file: String,
    val line: Int,
)
