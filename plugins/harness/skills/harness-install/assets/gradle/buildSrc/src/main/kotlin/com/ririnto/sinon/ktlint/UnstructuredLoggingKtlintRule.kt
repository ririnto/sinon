package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags forbidden unstructured logging calls; recommend structured logging instead.
 */
class UnstructuredLoggingKtlintRule : KtlintRule(
    ruleId = RuleId("code:unstructured-logging"),
) {
    companion object {
        private val forbiddenApis = setOf("println")
        private val allowedApis = emptySet<String>()
    }

    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitCallExpression(expression: KtCallExpression) {
                        super.visitCallExpression(expression)
                        val callName = calleeName(expression) ?: return
                        val qualifiedCall = qualifiedCallName(expression, callName)
                        when {
                            callName in allowedApis || qualifiedCall in allowedApis -> return
                            callName in forbiddenApis || qualifiedCall in forbiddenApis -> {
                                emit(expression.textOffset, "unstructured logging `$qualifiedCall`; use structured logger", false)
                            }
                        }
                    }

                    private fun qualifiedCallName(
                        expression: KtCallExpression,
                        callName: String,
                    ): String =
                        (expression.parent as? KtDotQualifiedExpression)
                            ?.takeIf { qualified -> qualified.selectorExpression == expression }
                            ?.let { qualified -> (expressionParts(qualified.receiverExpression) + callName).joinToString(".") }
                            ?: callName

                    private fun calleeName(expression: KtCallExpression): String? =
                        (expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

                    private fun expressionParts(expression: KtExpression): List<String> =
                        (when (expression) {
                            is KtNameReferenceExpression -> listOf(expression.getReferencedName())
                            is KtDotQualifiedExpression -> expressionParts(expression.receiverExpression) +
                                expression.selectorExpression?.let(::expressionParts).orEmpty()
                            is KtCallExpression -> listOfNotNull(calleeName(expression))
                            else -> emptyList()
                        })
                },
            )
        }
    }
}
