package com.ririnto.sinon.harness.ktlint

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
 * Flags unstructured console output through `print`, `println`, `System.out` and `System.err` print calls, and `printStackTrace`.
 *
 * Recommend structured logging instead.
 */
class UnstructuredLogging :
    Rule(
        ruleId = RuleId("harness:unstructured-logging"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val CONSOLE_FUNCTIONS: Set<String> = setOf("print", "println")
        val CONSOLE_STREAMS: Set<String> = setOf("System.out", "System.err", "java.lang.System.out", "java.lang.System.err")
        val STREAM_FUNCTIONS: Set<String> = setOf("print", "println", "printf")
        val CONSOLE_PATHS: Set<String> =
            CONSOLE_FUNCTIONS.flatMapTo(mutableSetOf()) { function -> listOf(function, "kotlin.$function", "kotlin.io.$function") } +
                CONSOLE_STREAMS.flatMap { stream -> STREAM_FUNCTIONS.map { function -> "$stream.$function" } }
        const val PRINT_STACK_TRACE: String = "printStackTrace"
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
                if (callName == PRINT_STACK_TRACE ||
                    qualifiedCall?.let { call ->
                        call in CONSOLE_PATHS && isStandardName(call.substringBefore('.'), expression)
                    } == true ||
                    (
                        qualifiedCall === null && expression.parent !is KtQualifiedExpression &&
                            callName in CONSOLE_FUNCTIONS && isStandardName(callName, expression)
                    )
                ) {
                    emit(
                        expression.textOffset,
                        "unstructured logging `${qualifiedCall ?: callName}`; use structured logger",
                        false
                    )
                }
            }
        }

        private fun isStandardName(
            name: String,
            expression: KtCallExpression
        ): Boolean =
            !KotlinLexicalScope.isDeclared(name, expression) &&
                (expression.containingFile as? KtFile)?.importDirectives?.none { directive ->
                    val path = directive.importedFqName?.asString()
                    val visibleName = directive.aliasName ?: directive.importedFqName?.shortName()?.asString()
                    when {
                        directive.isAllUnder -> path !in setOf("kotlin.io", "java.lang")
                        visibleName != name -> false
                        name == "System" -> path != "java.lang.System"
                        name in CONSOLE_FUNCTIONS -> path != "kotlin.io.$name"
                        else -> true
                    }
                } == true

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

                else -> {
                    emptyList()
                }
            }
    }
}
