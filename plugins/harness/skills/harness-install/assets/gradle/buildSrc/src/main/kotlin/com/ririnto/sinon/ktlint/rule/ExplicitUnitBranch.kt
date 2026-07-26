package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * Flags explicit `Unit` results in when entries and if/else branches.
 */
class ExplicitUnitBranch :
    Rule(
        ruleId = RuleId("code:explicit-unit-branch"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(ExplicitUnitBranchVisitor(emit))
    }

    private class ExplicitUnitBranchVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitWhenExpression(expression: KtWhenExpression) {
            super.visitWhenExpression(expression)
            expression.entries.forEach { entry ->
                entry.expression?.let(::reportIfExplicitUnit)
            }
        }

        override fun visitIfExpression(expression: KtIfExpression) {
            super.visitIfExpression(expression)
            expression.then?.let(::reportIfExplicitUnit)
            expression.`else`
	                ?.takeUnless { branch -> branch is KtIfExpression }
                ?.let(::reportIfExplicitUnit)
        }

        private fun reportIfExplicitUnit(expression: KtExpression) {
            if (isExplicitUnitBranch(expression)) {
                emit(expression.textOffset, "explicit Unit branch result is forbidden", false)
            }
        }

        private fun isExplicitUnitBranch(expression: KtExpression): Boolean =
            when (expression) {
                is KtParenthesizedExpression -> expression.expression?.let(::isExplicitUnitBranch) == true
                is KtBlockExpression -> expression.statements.lastOrNull()?.let(::isExplicitUnitBranch) == true
                is KtNameReferenceExpression -> expression.getReferencedName() == "Unit"
                is KtDotQualifiedExpression -> when (val receiver = expression.receiverExpression) {
                    is KtNameReferenceExpression -> when (val selector = expression.selectorExpression) {
                        is KtNameReferenceExpression -> receiver.getReferencedName() == "kotlin" &&
                            selector.getReferencedName() == "Unit"
                        else -> false
                    }
                    else -> false
                }
                else -> false
            }
    }
}
