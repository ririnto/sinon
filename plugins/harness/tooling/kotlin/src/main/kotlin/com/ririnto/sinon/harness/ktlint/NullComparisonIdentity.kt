package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression

/**
 * Requires referential equality operators for comparisons with the null literal.
 */
class NullComparisonIdentity :
    Rule(
        ruleId = RuleId("harness:null-comparison-identity"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtBinaryExpression)
            ?.takeIf { expression -> expression.comparesNullStructurally() }
            ?.let { expression -> emit(expression.textOffset, "use `===` or `!==` for comparisons with null", false) }
    }

    private fun KtBinaryExpression.comparesNullStructurally(): Boolean =
        when (operationReference.text) {
            "==", "!=" -> left.isNullLiteral() || right.isNullLiteral()
            else -> false
        }

    private fun KtExpression?.isNullLiteral(): Boolean =
        when (this) {
            is KtConstantExpression -> text == "null"
            is KtParenthesizedExpression -> expression.isNullLiteral()
            else -> false
        }
}
