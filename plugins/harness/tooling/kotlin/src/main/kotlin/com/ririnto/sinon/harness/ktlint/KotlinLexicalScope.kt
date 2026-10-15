package com.ririnto.sinon.harness.ktlint

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner

/**
 * Conservatively detects lexical declarations that prevent identifying imported types, registrations, and logging APIs.
 */
internal object KotlinLexicalScope {
    /**
     * Checks the enclosing scopes, including type parameters and local classifiers, for the visible name.
     */
    fun isDeclared(
        name: String,
        element: PsiElement,
        ignoredScopes: Set<PsiElement> = emptySet()
    ): Boolean =
        generateSequence(element, PsiElement::getParent).any { scope ->
            scope !in ignoredScopes &&
                (
                    hasTypeParameter(scope, name) ||
                        when (scope) {
                            is KtFile -> {
                                scope.declarations.any { declaration -> (declaration as? KtNamedDeclaration)?.name == name }
                            }

                            is KtBlockExpression -> {
                                scope.children.any { child -> (child as? KtNamedDeclaration)?.name == name } ||
                                    scope.children
                                        .filterIsInstance<KtDestructuringDeclaration>()
                                        .any { declaration -> declaration.entries.any { entry -> entry.name == name } }
                            }

                            is KtFunctionLiteral -> {
                                hasShadowedParameter(scope, name)
                            }

                            is KtNamedFunction -> {
                                scope.valueParameters.any { parameter -> parameter.name == name }
                            }

                            is KtForExpression -> {
                                scope.loopParameter?.name == name ||
                                    scope.destructuringDeclaration?.entries?.any { entry -> entry.name == name } == true
                            }

                            is KtCatchClause -> {
                                scope.catchParameter?.name == name
                            }

                            is KtClassOrObject -> {
                                scope.name == name ||
                                    scope.declarations.any { declaration -> (declaration as? KtNamedDeclaration)?.name == name } ||
                                    (scope as? KtClass)?.primaryConstructorParameters?.any { parameter ->
                                        parameter.name == name
                                    } == true
                            }

                            else -> {
                                false
                            }
                        }
                )
        }

    private fun hasTypeParameter(
        element: PsiElement,
        name: String
    ): Boolean = (element as? KtTypeParameterListOwner)?.typeParameters?.any { parameter -> parameter.name == name } == true

    private fun hasDestructuredName(
        element: PsiElement,
        name: String
    ): Boolean =
        (element as? KtDestructuringDeclaration)?.entries?.any { entry -> entry.name == name } == true ||
            element.children.any { child -> hasDestructuredName(child, name) }

    private fun hasShadowedParameter(
        functionLiteral: KtFunctionLiteral,
        name: String
    ): Boolean =
        functionLiteral.valueParameters.any { parameter -> parameter.name == name } ||
            functionLiteral.children
                .takeWhile { child -> child !== functionLiteral.bodyExpression }
                .any { child -> hasDestructuredName(child, name) }
}
