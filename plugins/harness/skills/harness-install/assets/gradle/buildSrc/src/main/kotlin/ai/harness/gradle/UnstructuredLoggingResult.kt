package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Result of unstructured logging (println, print, System.out/err) in `file` at `line`.
 */
@Serializable
data class UnstructuredLoggingResult(
    val file: String,
    val line: Int,
)
