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
            if (!expression.isElseIfBranch(expression.parent) && expression.`else`.hasFinalElseBranch()) {
                emit(expression.textOffset, "if/else chain; use when instead", false)
            }
        }

        private tailrec fun KtIfExpression.isElseIfBranch(ancestor: PsiElement?): Boolean =
            when (ancestor) {
                null, is KtFile -> false
                is KtIfExpression -> ancestor.`else` == this
                else -> this.isElseIfBranch(ancestor.parent)
            }

        private tailrec fun KtExpression?.hasFinalElseBranch(): Boolean =
            when (this) {
                null -> false
                is KtIfExpression -> this.`else`.hasFinalElseBranch()
                else -> true
            }
    }
}
