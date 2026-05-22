package ai.harness.gradle

import ai.harness.gradle.rules.ForbidBlankLineInLeafFunctionRule
import ai.harness.gradle.rules.ForbidEarlyReturnRule
import ai.harness.gradle.rules.ForbidEmptyCatchBlockRule
import ai.harness.gradle.rules.ForbidGreaterThanComparisonRule
import ai.harness.gradle.rules.ForbidImplicitLambdaItRule
import ai.harness.gradle.rules.ForbidMutableCollectionRule
import ai.harness.gradle.rules.ForbidSilentCatchRule
import ai.harness.gradle.rules.ForbidUnstructuredLoggingRule
import ai.harness.gradle.rules.ForbidWildcardImportRule
import ai.harness.gradle.rules.RequireBracesOnIfRule
import ai.harness.gradle.rules.RequireCompanionObjectPositionRule
import ai.harness.gradle.rules.RequireDocCommentOnPublicDeclarationRule
import ai.harness.gradle.rules.RequireImportOverFqnRule
import ai.harness.gradle.rules.RequireSingleTopLevelKotlinDeclarationRule
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
    val earlyReturns: List<ForbidEarlyReturnRule.Result>,
    val silentCatches: List<ForbidSilentCatchRule.Result>,
    val mutableCollections: List<ForbidMutableCollectionRule.Result>,
    val unstructuredLoggings: List<ForbidUnstructuredLoggingRule.Result>,
    val wildcardImports: List<ForbidWildcardImportRule.Result>,
    val fqnUsages: List<RequireImportOverFqnRule.Result>,
    val docCommentMissings: List<RequireDocCommentOnPublicDeclarationRule.Result>,
    val emptyCatchBlocks: List<ForbidEmptyCatchBlockRule.Result>,
    val braceOnIfs: List<RequireBracesOnIfRule.Result>,
    val companionPositions: List<RequireCompanionObjectPositionRule.Result>,
)
