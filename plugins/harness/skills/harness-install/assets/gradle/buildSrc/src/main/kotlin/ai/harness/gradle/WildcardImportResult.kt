package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of a wildcard import in `file` at `line`.
 */
@Serializable
data class WildcardImportResult(
    val file: String,
    val line: Int,
)
