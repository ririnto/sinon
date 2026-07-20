package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import kotlin.reflect.KFunction1

/**
 * Flags forbidden unstructured logging calls; recommend structured logging instead.
 */
class UnstructuredLogging :
    Rule(
        ruleId = RuleId("code:unstructured-logging"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val PRINTLN: KFunction1<Any?, Unit> = ::println
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(UnstructuredLoggingVisitor(emit))
    }

    private class UnstructuredLoggingVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)
            calleeName(expression)?.let { callName ->
                val qualifiedCall =
                    (expression.parent as? KtDotQualifiedExpression)
                        ?.takeIf { qualified -> qualified.selectorExpression === expression }
                        ?.let { qualified -> (expressionParts(qualified.receiverExpression) + callName).joinToString(".") }
                        ?: callName
                when {
                    callName == PRINTLN.name || qualifiedCall == PRINTLN.name -> {
                        emit(
                            expression.textOffset,
                            "unstructured logging `$qualifiedCall`; use structured logger",
                            false
                        )
                    }
                }
            }
        }

        private fun calleeName(expression: KtCallExpression): String? =
            (expression.calleeExpression as? KtNameReferenceExpression)
                ?.takeIf { name -> name.node.findChildByType(KtTokens.IDENTIFIER) !== null }
                ?.getReferencedName()

        private fun expressionParts(expression: KtExpression): List<String> =
            when (expression) {
                is KtNameReferenceExpression -> {
                    listOf(expression.getReferencedName())
                }

                is KtDotQualifiedExpression -> {
                    expressionParts(expression.receiverExpression) +
                        expression.selectorExpression?.let(::expressionParts).orEmpty()
                }

                is KtCallExpression -> {
                    listOfNotNull(calleeName(expression))
                }

                else -> {
                    emptyList()
                }
            }
    }
}
