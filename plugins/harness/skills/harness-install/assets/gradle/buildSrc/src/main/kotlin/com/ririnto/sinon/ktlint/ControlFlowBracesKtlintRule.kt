package com.ririnto.sinon.ktlint

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
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhileExpression

/**
 * Flags unbraced bodies in `if`, `for`, `while`, and `do-while` control flow; wrap every branch and
 * body in `{ ... }` so structure stays explicit and edits stay safe.
 */
class ControlFlowBracesKtlintRule :
    Rule(
        ruleId = RuleId("code:control-flow-braces"),
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
            super.visitIfExpression(expression)
            expression.`else`
                ?.takeIf { elseBranch -> elseBranch !is KtBlockExpression && elseBranch !is KtIfExpression }
                ?.let { elseBranch ->
                    if (emit(elseBranch.textOffset, "wrap the `else` branch in `{ ... }`", true) ==
                        AutocorrectDecision.ALLOW_AUTOCORRECT
                    ) {
                        elseBranch.node.replaceWith(
                            blockNode(elseBranch)
                        )
                    }
                }
            expression.then
                ?.takeIf { thenBranch -> thenBranch !is KtBlockExpression }
                ?.let { thenBranch ->
                    if (emit(thenBranch.textOffset, "wrap the `if` branch in `{ ... }`", true) ==
                        AutocorrectDecision.ALLOW_AUTOCORRECT
                    ) {
                        thenBranch.node.replaceWith(
                            blockNode(thenBranch)
                        )
                    }
                }
        }

        override fun visitForExpression(expression: KtForExpression) {
            super.visitForExpression(expression)
            when (val body = expression.body) {
                null -> emit(expression.textOffset, "wrap the `for` body in `{ ... }`", false)
                is KtBlockExpression -> Unit
                else -> if (emit(body.textOffset, "wrap the `for` body in `{ ... }`", true) ==
                    AutocorrectDecision.ALLOW_AUTOCORRECT
                ) {
                    body.node.replaceWith(blockNode(body))
                }
            }
        }

        override fun visitWhileExpression(expression: KtWhileExpression) {
            super.visitWhileExpression(expression)
            when (val body = expression.body) {
                null -> emit(expression.textOffset, "wrap the `while` body in `{ ... }`", false)
                is KtBlockExpression -> Unit
                else -> if (emit(body.textOffset, "wrap the `while` body in `{ ... }`", true) ==
                    AutocorrectDecision.ALLOW_AUTOCORRECT
                ) {
                    body.node.replaceWith(blockNode(body))
                }
            }
        }

        override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
            super.visitDoWhileExpression(expression)
            when (val body = expression.body) {
                null -> emit(expression.textOffset, "wrap the `do-while` body in `{ ... }`", false)
                is KtBlockExpression -> Unit
                else -> if (emit(body.textOffset, "wrap the `do-while` body in `{ ... }`", true) ==
                    AutocorrectDecision.ALLOW_AUTOCORRECT
                ) {
                    body.node.replaceWith(blockNode(body))
                }
            }
        }

        private fun blockNode(expression: KtExpression): ASTNode {
            val lineIndentation =
                expression.containingFile.text
                    .substring(
                        expression.containingFile.text.lastIndexOf('\n', expression.textOffset - 1) + 1,
                        expression.textOffset
                    )
                    .takeWhile { character -> character == ' ' || character == '\t' }
            val factory = KtPsiFactory.contextual(expression, false)
            val block =
                factory.createBlock(
                    expression.text.lines().joinToString("\n") { line -> "$lineIndentation    $line" }
                )
            block.node.addChild(
                factory.createWhiteSpace(lineIndentation).node,
                checkNotNull(block.node.findChildByType(KtTokens.RBRACE))
            )
            return block.node
        }
    }
}
