package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of a single early-return violation: function `function` in `file` contains a non-tail return at `line`.
 */
@Serializable
data class EarlyReturnResult(
    val file: String,
    val function: String,
    val line: Int,
)
