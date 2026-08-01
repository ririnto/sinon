package com.ririnto.sinon.ktlint.rule

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
 * Flags unbraced bodies in `if`, `for`, `while`, and `do-while` control flow.
 *
 * Wrap every branch and body in `{ ... }` so structure stays explicit and edits stay safe.
 */
class ControlFlowBraces :
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
                    if (emit(
                            elseBranch.textOffset,
                            "wrap the `else` branch in `{ ... }`",
                            !elseBranch.containsMultilineRawString()
                        ) ==
                        AutocorrectDecision.ALLOW_AUTOCORRECT
                    ) {
                        elseBranch.node.replaceWith(elseBranch.blockNode())
                    }
                }
            expression.then
                ?.takeIf { thenBranch -> thenBranch !is KtBlockExpression }
                ?.let { thenBranch ->
                    if (emit(
                            thenBranch.textOffset,
                            "wrap the `if` branch in `{ ... }`",
                            !thenBranch.containsMultilineRawString()
                        ) ==
                        AutocorrectDecision.ALLOW_AUTOCORRECT
                    ) {
                        thenBranch.node.replaceWith(thenBranch.blockNode())
                    }
                }
        }

        override fun visitForExpression(expression: KtForExpression) {
            super.visitForExpression(expression)
            when (val body = expression.body) {
                null -> {
                    emit(expression.textOffset, "wrap the `for` body in `{ ... }`", false)
                }

                else -> {
                    if (body !is KtBlockExpression && emit(
                            body.textOffset,
                            "wrap the `for` body in `{ ... }`",
                            !body.containsMultilineRawString()
                        ) ==
                        AutocorrectDecision.ALLOW_AUTOCORRECT
                    ) {
                        body.node.replaceWith(body.blockNode())
                    }
                }
            }
        }

        override fun visitWhileExpression(expression: KtWhileExpression) {
            super.visitWhileExpression(expression)
            when (val body = expression.body) {
                null -> {
                    emit(expression.textOffset, "wrap the `while` body in `{ ... }`", false)
                }

                else -> {
                    if (body !is KtBlockExpression && emit(
                            body.textOffset,
                            "wrap the `while` body in `{ ... }`",
                            !body.containsMultilineRawString()
                        ) ==
                        AutocorrectDecision.ALLOW_AUTOCORRECT
                    ) {
                        body.node.replaceWith(body.blockNode())
                    }
                }
            }
        }

        override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
            super.visitDoWhileExpression(expression)
            when (val body = expression.body) {
                null -> {
                    emit(expression.textOffset, "wrap the `do-while` body in `{ ... }`", false)
                }

                else -> {
                    if (body !is KtBlockExpression && emit(
                            body.textOffset,
                            "wrap the `do-while` body in `{ ... }`",
                            !body.containsMultilineRawString()
                        ) ==
                        AutocorrectDecision.ALLOW_AUTOCORRECT
                    ) {
                        body.node.replaceWith(body.blockNode())
                    }
                }
            }
        }

        private fun KtExpression.blockNode(): ASTNode {
            val lineIndentation =
                containingFile.text
                    .substring(
                        containingFile.text.lastIndexOf('\n', textOffset - 1) + 1,
                        textOffset
                    ).takeWhile { character -> character == ' ' || character == '\t' }
            return KtPsiFactory.contextual(this, false).let { factory ->
                factory
                    .createBlock(
                        text.lines().joinToString("\n") { line -> "$lineIndentation    $line" }
                    ).node
                    .also { node ->
                        node.addChild(
                            factory.createWhiteSpace(lineIndentation).node,
                            checkNotNull(node.findChildByType(KtTokens.RBRACE))
                        )
                    }
            }
        }

        private fun KtExpression.containsMultilineRawString(): Boolean =
            with(LiteralTypeInference) {
                (
                    listOfNotNull(this@containsMultilineRawString as? KtStringTemplateExpression) +
                        collectDescendantsOfType<KtStringTemplateExpression>()
                ).any { template -> template.isMultilineRawString() }
            }
    }
}
