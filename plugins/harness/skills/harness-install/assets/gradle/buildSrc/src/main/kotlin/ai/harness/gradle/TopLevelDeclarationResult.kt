package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Analysis result of top-level declarations in a Kotlin source file.
 */
@Serializable
data class TopLevelDeclarationResult(
    val file: String,
    val count: Int,
    val firstKind: String,
)
