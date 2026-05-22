package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of a mutable collection factory or type usage in `file` at `line`.
 */
@Serializable
data class MutableCollectionResult(
    val file: String,
    val name: String,
    val line: Int,
)
