package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.Rule.VisitorModifier.RunAsLateAsPossible
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Forbids ordinary comments inside function bodies.
 *
 * KDoc and ktlint directives remain available when they carry a contract or control lint execution.
 */
class FunctionBodyComments :
    Rule(
        ruleId = RuleId("harness:function-body-comments"),
        about = About(),
        visitorModifiers = setOf(RunAsLateAsPossible)
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtNamedFunction)?.bodyBlockExpression?.accept(CommentVisitor(emit))
    }

    private class CommentVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitBlockExpression(expression: KtBlockExpression) {
            super.visitBlockExpression(expression)
            expression.node.getChildren(null).forEach { child ->
                if (child.elementType == KtTokens.EOL_COMMENT || child.elementType == KtTokens.BLOCK_COMMENT) {
                    val text = child.text
                    if (!text.startsWith("/**") &&
                        !text.startsWith("// ktlint-") &&
                        !text.startsWith("/* ktlint-")
                    ) {
                        emit(
                            child.startOffset,
                            "remove the inline function-body comment",
                            false
                        )
                    }
                }
            }
        }
    }
}
