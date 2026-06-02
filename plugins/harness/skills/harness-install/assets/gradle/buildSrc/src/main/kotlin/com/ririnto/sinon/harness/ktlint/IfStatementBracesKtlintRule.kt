package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags if-expression branches that are not wrapped in block expressions; use braces on all branches.
 */
class IfStatementBracesKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "ifStatementBraces"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitIfExpression(expression: KtIfExpression) {
                        super.visitIfExpression(expression)
                        if (expression.then != null && expression.then !is KtBlockExpression) {
                            emit(expression.textOffset, message(), false)
                        }
                        if (expression.`else` != null &&
                            expression.`else` !is KtBlockExpression &&
                            expression.`else` !is KtIfExpression
                        ) {
                            emit(expression.textOffset, message(), false)
                        }
                    }
                },
            )
        }
    }
}
