package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Aggregated PSI analysis results produced by the worker action.
 */
@Serializable
data class HarnessPsiResults(
    val greaterThanComparisons: List<GreaterThanComparisonResult>,
    val blankLinesInLeafFunctions: List<BlankLineInFunctionResult>,
    val implicitLambdaIt: List<ImplicitLambdaItResult>,
    val topLevelDeclarations: List<TopLevelDeclarationResult>,
    val earlyReturns: List<EarlyReturnResult>,
    val silentCatches: List<SilentCatchResult>,
    val mutableCollections: List<MutableCollectionResult>,
    val unstructuredLoggings: List<UnstructuredLoggingResult>,
    val wildcardImports: List<WildcardImportResult>,
    val fqnUsages: List<ImportOverFqnResult>,
    val docCommentMissings: List<DocCommentMissingResult>,
    val emptyCatchBlocks: List<EmptyCatchBlockResult>,
    val braceOnIfs: List<BraceOnIfResult>,
    val companionPositions: List<CompanionPositionResult>,
)
