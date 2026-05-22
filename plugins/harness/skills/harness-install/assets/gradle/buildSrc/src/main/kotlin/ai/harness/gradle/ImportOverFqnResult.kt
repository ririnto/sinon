package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of a fully qualified name (FQN) usage that should be imported in `file` at `line`.
 */
@Serializable
data class ImportOverFqnResult(
    val file: String,
    val line: Int,
)
