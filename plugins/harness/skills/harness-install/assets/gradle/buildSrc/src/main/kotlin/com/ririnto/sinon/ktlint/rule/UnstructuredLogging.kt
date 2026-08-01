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
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags forbidden unstructured logging calls.
 *
 * Recommend structured logging instead.
 */
class UnstructuredLogging :
    Rule(
        ruleId = RuleId("code:unstructured-logging"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val PRINTLN_PATHS: Set<String> =
            setOf(
                "println",
                "kotlin.println",
                "kotlin.io.println"
            )
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
            expression.calleeName()?.let { callName ->
                val qualifiedCall =
                    (expression.parent as? KtDotQualifiedExpression)
                        ?.takeIf { qualified -> qualified.selectorExpression == expression }
                        ?.let { qualified ->
                            qualified.receiverExpression
                                .expressionParts()
                                .takeIf { parts -> parts.isNotEmpty() }
                                ?.plus(callName)
                                ?.joinToString(".")
                        }
                if (qualifiedCall?.let { call -> call in PRINTLN_PATHS } == true ||
                    (qualifiedCall === null && expression.parent !is KtQualifiedExpression && callName in PRINTLN_PATHS)
                ) {
                    emit(
                        expression.textOffset,
                        "unstructured logging `${qualifiedCall ?: callName}`; use structured logger",
                        false
                    )
                }
            }
        }

        private fun KtCallExpression.calleeName(): String? =
            (calleeExpression as? KtNameReferenceExpression)
                ?.takeIf { name -> name.node.findChildByType(KtTokens.IDENTIFIER) !== null }
                ?.getReferencedName()

        private fun KtExpression.expressionParts(): List<String> =
            when (this) {
                is KtNameReferenceExpression -> {
                    listOf(getReferencedName())
                }

                is KtDotQualifiedExpression -> {
                    receiverExpression.expressionParts() +
                        selectorExpression?.expressionParts().orEmpty()
                }

                is KtCallExpression -> {
                    listOfNotNull(calleeName())
                }

                else -> {
                    emptyList()
                }
            }
    }
}
