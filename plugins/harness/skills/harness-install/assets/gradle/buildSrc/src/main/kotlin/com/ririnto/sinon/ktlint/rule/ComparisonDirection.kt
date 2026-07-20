package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags comparison operators `>` and `>=`; prefer `<` and `<=` with the operands swapped so the
 * smaller value stays on the left (e.g. `a > b` -> `b < a`, `a >= b` -> `b <= a`). This rule is
 * diagnostic-only because operand swapping is not generally equivalent for asymmetric or custom
 * `Comparable` implementations.
 */
class ComparisonDirection :
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
            emit(
                expression.operationReference.textOffset,
                when (expression.operationToken) {
                    KtTokens.GT -> "avoid `>` in comparisons; prefer `<` with operands swapped"
                    KtTokens.GTEQ -> "avoid `>=` in comparisons; prefer `<=` with operands swapped"
                    else -> return
                },
                false
            )
        }
    }
}
