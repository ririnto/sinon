package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

/**
 * Flags direct SLF4J logging calls.
 *
 * A logger name is resolved lexically to its nearest declaration, so a same-named value that is not a logger is never reported.
 * Prefer the fluent SLF4J logging API.
 */
class SlfDirectLogging :
    Rule(
        ruleId = RuleId("harness:slf-direct-logging"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val LOG_LEVELS: Set<String> = setOf("trace", "debug", "info", "warn", "error")
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.let { ktFile ->
                val imports =
                    ktFile.importDirectives
                        .filter { directive -> directive.aliasName === null }
                        .mapNotNull { directive -> directive.importPath?.pathStr }
                        .toSet()
                ktFile.accept(
                    DirectLoggingVisitor(
                        loggerImported = "org.slf4j.Logger" in imports || "org.slf4j.*" in imports,
                        loggerFactoryImported = "org.slf4j.LoggerFactory" in imports || "org.slf4j.*" in imports,
                        emit = emit
                    )
                )
            }
    }

    private class DirectLoggingVisitor(
        private val loggerImported: Boolean,
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
                ?.takeIf { name -> name in LOG_LEVELS }
                ?.let { logLevel ->
                    (expression.parent as? KtQualifiedExpression)
                        ?.takeIf { qualified -> qualified.selectorExpression == expression }
                        ?.let { qualified -> qualified.receiverExpression.unwrapNonNullAssertion() }
                        ?.takeIf { receiverExpression -> receiverExpression.isLoggerReceiver() }
                        ?.let { receiverExpression ->
                            emit(
                                expression.textOffset,
                                "direct SLF4J logging `$logLevel`; use " +
                                    "`${receiverExpression.text}.at${logLevel.replaceFirstChar(Char::uppercase)}()` fluent logging",
                                false
                            )
                        }
                }
        }

        private fun KtExpression?.unwrapNonNullAssertion(): KtExpression? =
            when {
                this is KtPostfixExpression && operationToken == KtTokens.EXCLEXCL -> baseExpression
                else -> this
            }

        private fun KtExpression.isLoggerReceiver(): Boolean =
            when (this) {
                is KtNameReferenceExpression -> nearestDeclaration()?.isLogger() == true
                else -> isLoggerFactoryCall()
            }

        private fun KtExpression.isLoggerFactoryCall(): Boolean =
            when (this) {
                is KtParenthesizedExpression -> {
                    expression?.isLoggerFactoryCall() == true
                }

                is KtDotQualifiedExpression -> {
                    val call = selectorExpression as? KtCallExpression
                    (call?.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == "getLogger" &&
                        when (receiverExpression.text) {
                            "org.slf4j.LoggerFactory" -> {
                                true
                            }

                            "LoggerFactory" -> {
                                loggerFactoryImported &&
                                    !KotlinLexicalScope.isDeclared("LoggerFactory", receiverExpression)
                            }

                            else -> {
                                false
                            }
                        }
                }

                else -> {
                    false
                }
            }

        private fun KtCallableDeclaration.isLogger(): Boolean =
            when {
                typeReference !== null -> typeReference.isLoggerTypeReference()
                else -> (this as? KtProperty)?.initializer?.isLoggerFactoryCall() == true
            }

        private fun KtTypeReference?.isLoggerTypeReference(): Boolean {
            val typeElement = this?.typeElement
            val userType =
                when (typeElement) {
                    is KtNullableType -> typeElement.innerType as? KtUserType
                    is KtUserType -> typeElement
                    else -> null
                }
            val referencedNames =
                generateSequence(userType, KtUserType::getQualifier)
                    .mapNotNull { currentUserType ->
                        (currentUserType.referenceExpression as? KtNameReferenceExpression)?.getReferencedName()
                    }.toList()
                    .asReversed()
            return referencedNames == listOf("org", "slf4j", "Logger") ||
                (referencedNames == listOf("Logger") && loggerImported && !KotlinLexicalScope.isDeclared("Logger", checkNotNull(this)))
        }

        private fun KtNameReferenceExpression.nearestDeclaration(): KtCallableDeclaration? =
            generateSequence<PsiElement>(parent, PsiElement::getParent).firstNotNullOfOrNull { scope ->
                scope.declarationNamed(getReferencedName(), textOffset)
            }

        private fun PsiElement.declarationNamed(
            name: String,
            referenceOffset: Int
        ): KtCallableDeclaration? =
            when (this) {
                is KtBlockExpression -> {
                    statements
                        .takeWhile { statement -> statement.textRange.endOffset <= referenceOffset }
                        .mapNotNull { statement -> statement.declaredLocal(name) }
                        .lastOrNull()
                }

                is KtFunctionLiteral -> {
                    valueParameters.firstOrNull { parameter -> parameter.name == name }
                        ?: valueParameters.firstNotNullOfOrNull { parameter ->
                            parameter.destructuringDeclaration?.entries?.firstOrNull { entry -> entry.name == name }
                        }
                }

                is KtNamedFunction -> {
                    valueParameters.firstOrNull { parameter -> parameter.name == name }
                }

                is KtForExpression -> {
                    loopParameter?.takeIf { parameter -> parameter.name == name }
                        ?: destructuringDeclaration?.entries?.firstOrNull { entry -> entry.name == name }
                }

                is KtCatchClause -> {
                    catchParameter?.takeIf { parameter -> parameter.name == name }
                }

                is KtClassOrObject -> {
                    memberNamed(name)
                }

                is KtFile -> {
                    declarations.filterIsInstance<KtProperty>().firstOrNull { property -> property.name == name }
                }

                else -> {
                    null
                }
            }

        private fun KtExpression.declaredLocal(name: String): KtCallableDeclaration? =
            when (this) {
                is KtProperty -> {
                    takeIf { property -> property.name == name }
                }

                is KtDestructuringDeclaration -> {
                    entries.firstOrNull { entry -> entry.name == name }
                }

                else -> {
                    null
                }
            }

        private fun KtClassOrObject.memberNamed(name: String): KtCallableDeclaration? =
            primaryConstructorParameters.firstOrNull { parameter -> parameter.hasValOrVar() && parameter.name == name }
                ?: declarations.filterIsInstance<KtProperty>().firstOrNull { property -> property.name == name }
                ?: declarations
                    .filterIsInstance<KtObjectDeclaration>()
                    .filter(KtObjectDeclaration::isCompanion)
                    .firstNotNullOfOrNull { companion ->
                        companion.declarations.filterIsInstance<KtProperty>().firstOrNull { property -> property.name == name }
                    }
    }
}
