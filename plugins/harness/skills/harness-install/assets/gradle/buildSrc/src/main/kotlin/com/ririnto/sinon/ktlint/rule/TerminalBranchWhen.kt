package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags complete if-else chains that should use when instead.
 */
class TerminalBranchWhen :
    Rule(
        ruleId = RuleId("code:terminal-branch-when"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(TerminalWhenVisitor(emit))
    }

    private class TerminalWhenVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            super.visitIfExpression(expression)
            if (!isElseIfBranch(expression, expression.parent) && hasFinalElseBranch(expression.`else`)) {
                emit(expression.textOffset, "if/else chain; use when instead", false)
            }
        }

        private tailrec fun isElseIfBranch(expression: KtIfExpression, ancestor: PsiElement?): Boolean =
            when (ancestor) {
                null, is KtFile -> false
                is KtIfExpression -> ancestor.`else` === expression
                else -> isElseIfBranch(expression, ancestor.parent)
            }

        private tailrec fun hasFinalElseBranch(expression: KtExpression?): Boolean =
            when (expression) {
                null -> false
                is KtIfExpression -> hasFinalElseBranch(expression.`else`)
                else -> true
            }
    }
}
