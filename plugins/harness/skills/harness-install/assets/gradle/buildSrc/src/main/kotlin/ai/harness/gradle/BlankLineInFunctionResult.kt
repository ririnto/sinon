package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Blank line in function result.
 */
@Serializable
data class BlankLineInFunctionResult(val file: String, val function: String, val line: Int)
