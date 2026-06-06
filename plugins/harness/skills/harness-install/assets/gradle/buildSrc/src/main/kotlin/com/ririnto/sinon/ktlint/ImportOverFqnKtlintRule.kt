package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType

/**
 * Flags inline fully qualified Kotlin names that could be imported instead.
 */
class ImportOverFqnKtlintRule : KtlintRule(
    ruleId = RuleId("code:import-over-fqn"),
) {
    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            val importedNames = ktFile.importDirectives
                .mapNotNull { directive -> directive.importedName?.asString() }
                .toSet()

            fun addFqnFinding(nameParts: List<String>, element: PsiElement) {
                if (isPackageQualifiedName(nameParts) && nameParts.lastOrNull() !in importedNames) {
                    emit(element.textOffset, "fully qualified name `${nameParts.joinToString(".")}` used inline; add an import and use the simple name", false)
                }
            }

            ktFile.accept(Visitor(importedNames, ::addFqnFinding))
        }
    }

    private fun isPackageQualifiedName(parts: List<String>): Boolean =
        3 <= parts.size &&
            parts[0].firstOrNull()?.isLowerCase() == true &&
            parts[1].firstOrNull()?.isLowerCase() == true &&
            parts.last().firstOrNull()?.isUpperCase() == true

    private class Visitor(
        private val importedNames: Set<String>,
        private val addFqnFinding: (List<String>, PsiElement) -> Unit,
    ) : KtTreeVisitorVoid() {
        override fun visitUserType(userType: KtUserType) {
            super.visitUserType(userType)
            if (generateSequence(userType as PsiElement?) { element -> element.parent }.any { element ->
                    element is KtImportDirective
                }
            ) {
                return
            }
            if (userType.parent is KtUserType) {
                return
            }
            val fqnParts = generateSequence(userType) { parent -> parent.qualifier }
                .mapNotNull { ut -> ut.referencedName }
                .toList()
                .asReversed()
            if (2 <= fqnParts.size && fqnParts.first() !in importedNames) {
                addFqnFinding(fqnParts, userType)
            }
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            super.visitDotQualifiedExpression(expression)
            if (generateSequence(expression as PsiElement?) { element -> element.parent }.any { element ->
                    element is KtImportDirective
                }
            ) {
                return
            }
            if (expression.parent is KtDotQualifiedExpression) {
                return
            }
            classCandidateParts(expression)?.let { nameParts ->
                addFqnFinding(nameParts, expression.receiverExpression)
            }
        }

        private fun classCandidateParts(expression: KtDotQualifiedExpression): List<String>? {
            val parts = expressionParts(expression)
            return parts
                .indexOfFirst { part -> part.firstOrNull()?.isUpperCase() == true }
                .takeIf { index -> 2 <= index }
                ?.let { index -> parts.take(index + 1) }
        }

        private fun expressionParts(expression: KtExpression): List<String> =
            when (expression) {
                is KtNameReferenceExpression -> listOf(expression.getReferencedName())
                is KtDotQualifiedExpression -> expressionParts(expression.receiverExpression) +
                    expression.selectorExpression?.let(::expressionParts).orEmpty()
                is KtCallExpression -> expression.calleeExpression?.let(::expressionParts).orEmpty()
                else -> emptyList()
            }
    }
}
