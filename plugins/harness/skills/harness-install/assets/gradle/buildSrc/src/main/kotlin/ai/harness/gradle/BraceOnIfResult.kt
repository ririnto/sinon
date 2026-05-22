package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of an if statement without braces in `file` at `line`.
 */
@Serializable
data class BraceOnIfResult(
    val file: String,
    val line: Int,
)
