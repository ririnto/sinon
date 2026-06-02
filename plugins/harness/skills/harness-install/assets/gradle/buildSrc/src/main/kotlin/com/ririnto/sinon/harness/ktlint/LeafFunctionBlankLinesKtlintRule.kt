package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.ktlint.PsiTraversal.hasDescendantOfType
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags consecutive blank lines within leaf functions that exceed the configured threshold.
 */
class LeafFunctionBlankLinesKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "leafFunctionBlankLines"
    }

    private val maxConsecutiveBlankLines: Int =
        ctx.manifest
            .categoryObject(CATEGORY)
            ?.get("parameters")
            ?.jsonObject
            ?.get("maxConsecutiveBlankLines")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 1

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitNamedFunction(function: KtNamedFunction) {
                        super.visitNamedFunction(function)
                        if (function.hasDescendantOfType<KtNamedFunction> { nestedFunc -> nestedFunc !== function } ||
                            function.hasDescendantOfType<KtLambdaExpression> { true }
                        ) {
                            return
                        }
                        function.bodyExpression?.firstChild?.run {
                            generateSequence(this) { element -> element.nextSibling }
                                .filter { child -> child is PsiWhiteSpace }
                                .map { child -> child to child.text.count { char -> char == '\n' } - 1 }
                                .filter { pair -> 0 < pair.second }
                                .forEach { pair ->
                                    if (maxConsecutiveBlankLines < pair.second) {
                                        val functionName = function.name ?: "unknown"
                                        emit(pair.first.textOffset, message(mapOf("function" to functionName)), false)
                                    }
                                }
                        }
                    }
                },
            )
        }
    }
}
