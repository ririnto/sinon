package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of a silent catch block: catch parameter in `file` is unused at `line`.
 */
@Serializable
data class SilentCatchResult(
    val file: String,
    val line: Int,
)
