package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags if-expression branches that are not wrapped in block expressions; use braces on all branches.
 */
class IfStatementBracesKtlintRule :
    Rule(
        ruleId = RuleId("code:if-statement-braces"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(IfBracesVisitor(emit))
    }

    private class IfBracesVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            super.visitIfExpression(expression)
            if (expression.then != null && expression.then !is KtBlockExpression) {
                emit(expression.textOffset, "if/else without braces; wrap the body in `{ ... }`", false)
            }
            if (expression.`else` != null &&
                expression.`else` !is KtBlockExpression &&
                expression.`else` !is KtIfExpression
            ) {
                emit(expression.textOffset, "if/else without braces; wrap the body in `{ ... }`", false)
            }
        }
    }
}
