package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags comparison operators `>` and `>=`; prefer `<` and `<=` with the operands swapped so the
 * smaller value stays on the left (e.g. `a > b` -> `b < a`, `a >= b` -> `b <= a`). Autocorrect
 * swaps the operands, but only when both are side-effect-free; otherwise the finding stays manual,
 * because reordering operands changes evaluation order when an operand has side effects.
 */
class ComparisonDirectionKtlintRule :
    Rule(
        ruleId = RuleId("code:comparison-direction"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(ComparisonVisitor(emit))
    }

    private class ComparisonVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            super.visitBinaryExpression(expression)
            val replacement = when (expression.operationToken) {
                KtTokens.GT -> OperatorReplacement(KtTokens.GT, KtTokens.LT)
                KtTokens.GTEQ -> OperatorReplacement(KtTokens.GTEQ, KtTokens.LTEQ)
                else -> return
            }
            val left = expression.left ?: return
            val right = expression.right ?: return
            val canFix = isSideEffectFree(left) && isSideEffectFree(right)
            if (
                emit(
                    expression.operationReference.textOffset,
                    "avoid `${replacement.forbiddenSource}` in comparisons; prefer " +
                        "`${replacement.replacementSource}` with operands swapped",
                    canFix
            ) == AutocorrectDecision.ALLOW_AUTOCORRECT &&
                canFix
            ) {
                expression.node.treeParent.replaceChild(
                    expression.node,
                    KtPsiFactory.contextual(expression)
                        .createExpression("${right.text} ${replacement.replacementSource} ${left.text}")
                        .node
                )
            }
        }

        private fun isSideEffectFree(expression: KtExpression): Boolean {
            if (
                expression is KtCallExpression ||
                expression is KtPostfixExpression ||
                expression is KtLambdaExpression ||
                expression is KtReturnExpression ||
                expression is KtThrowExpression ||
                expression is KtBinaryExpression && KtTokens.ALL_ASSIGNMENTS.contains(expression.operationToken)
            ) {
                return false
            }
            return expression.collectDescendantsOfType<KtCallExpression>().isEmpty() &&
                expression.collectDescendantsOfType<KtPostfixExpression>().isEmpty() &&
                expression.collectDescendantsOfType<KtLambdaExpression>().isEmpty() &&
                expression.collectDescendantsOfType<KtReturnExpression>().isEmpty() &&
                expression.collectDescendantsOfType<KtThrowExpression>().isEmpty() &&
                expression.collectDescendantsOfType<KtBinaryExpression>().none { binaryExpression ->
                    KtTokens.ALL_ASSIGNMENTS.contains(binaryExpression.operationToken)
                } &&
                expression.collectDescendantsOfType<KtPrefixExpression>().all { prefix ->
                    val token = prefix.operationReference.node.firstChildNode?.elementType
                    token != KtTokens.PLUSPLUS && token != KtTokens.MINUSMINUS
                }
        }

        private class OperatorReplacement(
            val forbiddenSource: String,
            val replacementSource: String
        ) {
            constructor(
                forbiddenToken: KtSingleValueToken,
                replacementToken: KtSingleValueToken
            ) : this(forbiddenToken.value, replacementToken.value)
        }
    }
}
