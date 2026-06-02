package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.HarnessTextEdit
import com.ririnto.sinon.harness.core.RuleContext
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Flags `>` and `>=` comparisons and rewrites safe name-versus-literal forms with `<` / `<=`
 * so the smaller value is on the left.
 */
class GreaterThanComparisonKtlintRule(
    private val ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    companion object {
        private const val CATEGORY: String = "greaterThanComparison"

        private fun KtExpression.isSafeOperand(): Boolean = this is KtNameReferenceExpression || this is KtConstantExpression

        private fun replacementFor(expression: KtBinaryExpression): String? {
            val left = expression.left ?: return null
            val right = expression.right ?: return null
            if (!left.isSafeOperand() || !right.isSafeOperand()) {
                return null
            }
            if (left is KtNameReferenceExpression && right is KtNameReferenceExpression) {
                return null
            }
            val operator =
                when (expression.operationToken) {
                    KtTokens.GT -> "<"
                    KtTokens.GTEQ -> "<="
                    else -> return null
                }
            return "${right.text} $operator ${left.text}"
        }

        private fun hasCommentDescendant(expression: KtBinaryExpression): Boolean =
            PsiTreeUtil.findChildOfType(expression, PsiComment::class.java) != null
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        val expression = node.psi as? KtBinaryExpression ?: return
        if (expression.operationToken != KtTokens.GT && expression.operationToken != KtTokens.GTEQ) {
            return
        }
        val replacement = replacementFor(expression)
        val canAutoCorrect = replacement != null && !hasCommentDescendant(expression)
        emit(expression.textOffset, message(), canAutoCorrect)
        if (replacement != null && canAutoCorrect) {
            ctx.fixEdits?.add(
                HarnessTextEdit(
                    startOffset = expression.textOffset,
                    endOffsetExclusive = expression.textOffset + expression.text.length,
                    replacement = replacement,
                )
            )
        }
    }
}
