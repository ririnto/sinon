package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Greater-than comparison result.
 */
@Serializable
data class GreaterThanComparisonResult(val file: String, val line: Int)
