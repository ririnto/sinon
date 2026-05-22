package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Implicit lambda it result.
 */
@Serializable
data class ImplicitLambdaItResult(val file: String, val line: Int)
