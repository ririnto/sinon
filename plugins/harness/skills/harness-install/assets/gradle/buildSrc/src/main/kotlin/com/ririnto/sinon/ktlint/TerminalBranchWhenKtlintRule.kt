package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
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
        (node.psi as? KtFile)
            ?.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitIfExpression(expression: KtIfExpression) {
                        super.visitIfExpression(expression)
                        if (expression.isElseIfBranch()) {
                            return
                        }
                        if (expression.hasFinalElseBranch()) {
                            emit(expression.textOffset, "if/else chain; use when instead", false)
                        }
                    }
                }
            )
    }

    private fun KtIfExpression.isElseIfBranch(): Boolean =
        nearestParentIfOrFile()?.let { parentIf -> parentIf.`else` == this } == true

    private tailrec fun KtIfExpression.nearestParentIfOrFile(current: PsiElement? = parent): KtIfExpression? =
        when (current) {
            null, is KtFile -> null
            is KtIfExpression -> current
            else -> nearestParentIfOrFile(current.parent)
        }

    private tailrec fun KtIfExpression.hasFinalElseBranch(): Boolean {
        val elseExpression = `else` ?: return false
        return elseExpression !is KtIfExpression || elseExpression.hasFinalElseBranch()
    }
}
