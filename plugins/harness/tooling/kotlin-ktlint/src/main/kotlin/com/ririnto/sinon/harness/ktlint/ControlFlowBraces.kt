package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Requires braces around every control-flow branch and loop body.
 *
 * The rule avoids changing multiline raw-string expressions because their indentation and trailing newline are value-bearing.
 */
class ControlFlowBraces :
    Rule(
        ruleId = RuleId("harness:control-flow-braces"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(ControlFlowBracesVisitor(emit))
    }

    private class ControlFlowBracesVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            expression.`else`
                ?.takeIf { branch -> branch !is KtBlockExpression && branch !is KtIfExpression }
                ?.let { branch -> wrap(branch, "else") }
            expression.then
                ?.takeIf { branch -> branch !is KtBlockExpression }
                ?.let { branch -> wrap(branch, "if") }
            super.visitIfExpression(expression)
        }

        override fun visitForExpression(expression: KtForExpression) {
            expression.body
                ?.takeIf { body -> body !is KtBlockExpression }
                ?.let { body -> wrap(body, "for") }
                ?: emit(expression.textOffset, "wrap the `for` body in `{ ... }`", false)
            super.visitForExpression(expression)
        }

        override fun visitWhileExpression(expression: KtWhileExpression) {
            expression.body
                ?.takeIf { body -> body !is KtBlockExpression }
                ?.let { body -> wrap(body, "while") }
                ?: emit(expression.textOffset, "wrap the `while` body in `{ ... }`", false)
            super.visitWhileExpression(expression)
        }

        override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
            expression.body
                ?.takeIf { body -> body !is KtBlockExpression }
                ?.let { body -> wrap(body, "do-while") }
                ?: emit(expression.textOffset, "wrap the `do-while` body in `{ ... }`", false)
            super.visitDoWhileExpression(expression)
        }

        private fun wrap(
            expression: KtExpression,
            keyword: String
        ) {
            val canAutocorrect = !containsMultilineRawString(expression)
            val kind =
                when (keyword) {
                    "if", "else" -> "branch"
                    else -> "body"
                }
            if (
                emit(
                    expression.textOffset,
                    "wrap the `$keyword` $kind in `{ ... }`",
                    canAutocorrect
                ) == AutocorrectDecision.ALLOW_AUTOCORRECT &&
                canAutocorrect
            ) {
                expression.node.replaceWith(blockNode(expression))
            }
        }

        private fun blockNode(expression: KtExpression): ASTNode {
            val lineIndentation =
                expression.containingFile.text
                    .substring(
                        expression.containingFile.text.lastIndexOf('\n', expression.textOffset - 1) + 1,
                        expression.textOffset
                    ).takeWhile { character -> character == ' ' || character == '\t' }
            val bodyText = expression.text
            val factory = KtPsiFactory.contextual(expression, false)
            return factory
                .createBlock(
                    "$lineIndentation    $bodyText"
                ).node
                .also { node ->
                    node.addChild(
                        factory.createWhiteSpace(lineIndentation).node,
                        checkNotNull(node.findChildByType(KtTokens.RBRACE))
                    )
                }
        }

        private fun containsMultilineRawString(expression: KtExpression): Boolean =
            (
                listOfNotNull(expression as? KtStringTemplateExpression) +
                    expression.collectDescendantsOfType<KtStringTemplateExpression>()
            ).any { template ->
                template.node.findChildByType(KtTokens.OPEN_QUOTE)?.text == "\"\"\"" &&
                    template.text.contains('\n')
            }
    }
}
