package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of a missing documentation comment on a public Kotlin declaration in `file` at `line`.
 */
@Serializable
data class DocCommentMissingResult(
    val file: String,
    val name: String,
    val line: Int,
)
