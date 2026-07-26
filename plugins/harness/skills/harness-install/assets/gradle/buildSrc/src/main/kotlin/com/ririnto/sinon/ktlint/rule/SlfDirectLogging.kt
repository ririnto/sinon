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
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags direct SLF4J logging calls.
 *
 * Prefer the fluent SLF4J logging API.
 */
class SlfDirectLogging :
    Rule(
        ruleId = RuleId("code:slf-direct-logging"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.let { ktFile ->
                val loggerFactoryImported =
                    ktFile.importDirectives.any { directive ->
                        directive.importPath?.pathStr == "org.slf4j.LoggerFactory" ||
                            directive.importPath?.pathStr == "org.slf4j.*"
                    }
                ktFile.accept(
                    DirectLoggingVisitor(
                        loggerNames =
                            buildSet {
                                val loggerImported = ktFile.importDirectives.any { directive ->
                                    directive.importPath?.pathStr == "org.slf4j.Logger" ||
                                        directive.importPath?.pathStr == "org.slf4j.*"
                                }
                                ktFile.collectDescendantsOfType<KtProperty>().forEach { property ->
                                    property.name
                                        ?.takeIf {
                                            isLoggerTypeReference(property.typeReference, loggerImported) ||
                                                property.initializer
                                                    ?.collectDescendantsOfType<KtCallExpression>()
                                                    ?.any { callExpression ->
                                                        (callExpression.calleeExpression as? KtNameReferenceExpression)
                                                            ?.getReferencedName() == "getLogger" &&
                                                            when ((callExpression.parent as? KtDotQualifiedExpression)
                                                                ?.receiverExpression?.text) {
                                                                "org.slf4j.LoggerFactory" -> true
                                                                "LoggerFactory" -> loggerFactoryImported
                                                                else -> false
                                                            }
                                                    } == true
                                        }?.let(::add)
                                }
                                ktFile.collectDescendantsOfType<KtParameter>().forEach { parameter ->
                                    parameter.name
                                        ?.takeIf {
                                            parameter.hasValOrVar() &&
                                                isLoggerTypeReference(parameter.typeReference, loggerImported)
                                        }?.let(::add)
                                }
                                ktFile.collectDescendantsOfType<KtNamedFunction>()
                                    .flatMap { function -> function.valueParameters }
                                    .forEach { parameter ->
                                        parameter.name
                                            ?.takeIf {
                                                isLoggerTypeReference(parameter.typeReference, loggerImported)
                                            }?.let(::add)
                                    }
                            },
                        loggerFactoryImported = loggerFactoryImported,
                        emit = emit
                    )
                )
            }
    }

    private class DirectLoggingVisitor(
        private val loggerNames: Set<String>,
        private val loggerFactoryImported: Boolean,
        private val emit: (
            offset: Int,
            errorMessage: String,
            canBeAutoCorrected: Boolean
        ) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)
            (expression.calleeExpression as? KtNameReferenceExpression)
                ?.getReferencedName()
                ?.takeIf { name -> name in setOf("trace", "debug", "info", "warn", "error") }
                ?.let { logLevel ->
                    val receiverExpression = (expression.parent as? KtQualifiedExpression)
                        ?.takeIf { qualified -> qualified.selectorExpression == expression }
                        ?.let { qualified -> unwrapNonNullAssertion(qualified.receiverExpression) }
                    if (receiverExpression !== null && isLoggerReceiver(receiverExpression)) {
                        emit(
                            expression.textOffset,
                            "direct SLF4J logging `$logLevel`; use " +
                                "`${receiverExpression.text}.at${logLevel.replaceFirstChar(Char::uppercase)}()` fluent logging",
                            false
                        )
                    }
                }
        }

        private fun isLoggerReceiver(receiverExpression: KtExpression): Boolean =
            (receiverExpression as? KtNameReferenceExpression)?.getReferencedName() in loggerNames ||
                receiverExpression.collectDescendantsOfType<KtCallExpression>().any { callExpression ->
                    (callExpression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == "getLogger" &&
                        when ((callExpression.parent as? KtDotQualifiedExpression)?.receiverExpression?.text) {
                            "org.slf4j.LoggerFactory" -> true
                            "LoggerFactory" -> loggerFactoryImported
                            else -> false
                        }
                }
    }
}

private fun isLoggerTypeReference(typeReference: KtTypeReference?, loggerImported: Boolean): Boolean {
    val typeElement = typeReference?.typeElement
    val userType = when (typeElement) {
        is KtNullableType -> typeElement.innerType as? KtUserType
        is KtUserType -> typeElement
        else -> null
    }
    val referencedNames = generateSequence(userType) { currentUserType -> currentUserType.qualifier }
        .mapNotNull { currentUserType ->
            (currentUserType.referenceExpression as? KtNameReferenceExpression)?.getReferencedName()
        }
        .toList()
        .asReversed()
    return referencedNames == listOf("org", "slf4j", "Logger") ||
        referencedNames == listOf("Logger") && loggerImported
}

private fun unwrapNonNullAssertion(receiver: KtExpression?): KtExpression? {
    val postfix = receiver as? KtPostfixExpression
    return when {
        postfix !== null && postfix.operationToken == KtTokens.EXCLEXCL -> postfix.baseExpression
        else -> receiver
    }
}
