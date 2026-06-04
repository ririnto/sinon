package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
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
class TerminalBranchWhenKtlintRule : KtlintRule(
    ruleId = RuleId("code:terminal-branch-when"),
) {
    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitBlockExpression(expression: KtBlockExpression) {
                        super.visitBlockExpression(expression)
                        terminalIfElseExpressions(expression.statements.lastOrNull()).forEach { terminalIf ->
                            emit(terminalIf.textOffset, "terminal if/else expression in block; use when instead", false)
                        }
                    }

                    override fun visitNamedFunction(function: KtNamedFunction) {
                        super.visitNamedFunction(function)
                        terminalIfElseExpressions(function.bodyExpression).forEach { terminalIf ->
                            val functionName = function.name ?: "unknown"
                            emit(terminalIf.textOffset, "terminal if/else expression in function `$functionName`; use when instead", false)
                        }
                    }

                    override fun visitProperty(property: KtProperty) {
                        super.visitProperty(property)
                        if (property.isLocal) {
                            return
                        }
                        terminalIfElseExpressions(property.initializer).forEach { terminalIf ->
                            val propertyName = property.name ?: "property"
                            emit(terminalIf.textOffset, "terminal if/else expression in property `$propertyName`; use when instead", false)
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
                    val elseExpr = expression.`else`
                    if (elseExpr != null && elseExpr !is KtIfExpression) {
                        add(expression)
                    }
                }
                is KtReturnExpression -> addAll(terminalIfElseExpressions(expression.returnedExpression))
            }
        }
}
