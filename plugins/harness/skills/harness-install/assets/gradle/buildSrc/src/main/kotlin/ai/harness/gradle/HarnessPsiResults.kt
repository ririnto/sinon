package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Complete PSI analysis results.
 */
@Serializable
data class HarnessPsiResults(
    val greaterThanComparisons: List<GreaterThanComparisonResult> = emptyList(),
    val blankLinesInLeafFunctions: List<BlankLineInFunctionResult> = emptyList(),
    val implicitLambdaIt: List<ImplicitLambdaItResult> = emptyList(),
    val topLevelDeclarations: List<TopLevelDeclarationResult> = emptyList(),
)
