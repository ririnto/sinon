package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of an empty catch block in `file` at `line`.
 */
@Serializable
data class EmptyCatchBlockResult(
    val file: String,
    val line: Int,
)
