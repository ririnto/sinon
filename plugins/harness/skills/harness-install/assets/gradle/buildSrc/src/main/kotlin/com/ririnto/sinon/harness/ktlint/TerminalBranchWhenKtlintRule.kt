package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.ktlint.PsiTraversal.hasDescendantOfType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags terminal if-else expressions that should use when instead.
 */
class TerminalBranchWhenKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "terminalBranchWhen"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitBlockExpression(expression: KtBlockExpression) {
                        super.visitBlockExpression(expression)
                        terminalIfElseExpressions(expression.statements.lastOrNull()).forEach { terminalIf ->
                            emit(terminalIf.textOffset, message(mapOf("context" to "block")), false)
                        }
                    }

                    override fun visitNamedFunction(function: KtNamedFunction) {
                        super.visitNamedFunction(function)
                        terminalIfElseExpressions(function.bodyExpression).forEach { terminalIf ->
                            val functionName = function.name ?: "unknown"
                            emit(terminalIf.textOffset, message(mapOf("context" to "function `$functionName`")), false)
                        }
                    }

                    override fun visitProperty(property: KtProperty) {
                        super.visitProperty(property)
                        if (property.isLocal) {
                            return
                        }
                        terminalIfElseExpressions(property.initializer).forEach { terminalIf ->
                            val propertyName = property.name ?: "property"
                            emit(terminalIf.textOffset, message(mapOf("context" to "property `$propertyName`")), false)
                        }
                    }
                },
            )
        }
    }

    private fun terminalIfElseExpressions(expression: PsiElement?): List<KtIfExpression> =
        buildList {
            when (expression) {
                is KtIfExpression -> {
                    if (expression.`else` != null) {
                        add(expression)
                    }
                }
                is KtReturnExpression -> addAll(terminalIfElseExpressions(expression.returnedExpression))
            }
        }
}
