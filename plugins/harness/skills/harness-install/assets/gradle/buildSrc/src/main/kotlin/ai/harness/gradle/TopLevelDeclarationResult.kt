package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Top-level declaration result.
 */
@Serializable
data class TopLevelDeclarationResult(val file: String, val count: Int, val firstKind: String)
