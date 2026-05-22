package ai.harness.gradle

import kotlinx.serialization.Serializable

/**
 * Aggregated PSI analysis results produced by the worker action.
 */
@Serializable
data class HarnessPsiResults(
    val greaterThanComparisons: List<ForbidGreaterThanComparisonRule.Result>,
    val blankLinesInLeafFunctions: List<ForbidBlankLineInLeafFunctionRule.Result>,
    val implicitLambdaIt: List<ForbidImplicitLambdaItRule.Result>,
    val topLevelDeclarations: List<RequireSingleTopLevelKotlinDeclarationRule.Result>,
    val earlyReturns: List<EarlyReturnResult>,
    val silentCatches: List<SilentCatchResult>,
    val mutableCollections: List<MutableCollectionResult>,
    val unstructuredLoggings: List<UnstructuredLoggingResult>,
    val wildcardImports: List<ForbidWildcardImportRule.Result>,
    val fqnUsages: List<ImportOverFqnResult>,
    val docCommentMissings: List<DocCommentMissingResult>,
    val emptyCatchBlocks: List<ForbidEmptyCatchBlockRule.Result>,
    val braceOnIfs: List<RequireBracesOnIfRule.Result>,
    val companionPositions: List<CompanionPositionResult>,
)
