package com.ririnto.sinon.ktlint

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
class TerminalBranchWhenKtlintRule :
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
            if (nearestParentIf(expression.parent)?.`else` == expression) {
                return
            }
            if (hasFinalElseBranch(expression.`else`)) {
                emit(expression.textOffset, "if/else chain; use when instead", false)
            }
        }

        private tailrec fun nearestParentIf(ancestor: PsiElement?): KtIfExpression? =
            when (ancestor) {
                null, is KtFile -> null
                is KtIfExpression -> ancestor
                else -> nearestParentIf(ancestor.parent)
            }

        private tailrec fun hasFinalElseBranch(expression: KtExpression?): Boolean =
            when (expression) {
                null -> false
                is KtIfExpression -> hasFinalElseBranch(expression.`else`)
                else -> true
            }
    }
}
