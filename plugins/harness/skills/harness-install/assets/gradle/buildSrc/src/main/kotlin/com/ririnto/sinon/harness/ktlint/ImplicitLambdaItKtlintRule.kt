package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags implicit-lambda 'it' parameter usage; declare explicit parameters in lambda signatures.
 */
class ImplicitLambdaItKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "implicitLambdaIt"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitLambdaExpression(expression: KtLambdaExpression) {
                        super.visitLambdaExpression(expression)
                        if (expression.valueParameters.isEmpty() && containsItReference(expression.bodyExpression)) {
                            emit(expression.textOffset, message(), false)
                        }
                    }
                },
            )
        }
    }

    /**
     * Recursively check if a KtElement tree contains an 'it' reference.
     */
    private fun containsItReference(element: KtElement?): Boolean =
        element != null && (
            (element is KtSimpleNameExpression && element.text == "it") ||
                element.children.any { child -> child is KtElement && containsItReference(child) }
        )
}
