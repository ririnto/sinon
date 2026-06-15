package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
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
class ImportOverFqnKtlintRule :
    Rule(
        ruleId = RuleId("code:import-over-fqn"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.let { ktFile ->
                ktFile.accept(
                    Visitor(ktFile, emit)
                )
            }
    }

    private class Visitor(
        private val ktFile: KtFile,
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitUserType(userType: KtUserType) {
            super.visitUserType(userType)
            if (
                generateSequence(userType as PsiElement?) { element -> element.parent }.none { element ->
                    element is KtImportDirective
                } &&
                userType.parent !is KtUserType
            ) {
                val fqnParts =
                    generateSequence(userType) { parent -> parent.qualifier }
                        .mapNotNull { ut -> ut.referencedName }
                        .toList()
                        .asReversed()
                if (2 <= fqnParts.size) {
                    addFqnFinding(fqnParts, userType)
                }
            }
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            super.visitDotQualifiedExpression(expression)
            if (
                generateSequence(expression as PsiElement?) { element -> element.parent }.none { element ->
                    element is KtImportDirective
                } &&
                expression.parent !is KtDotQualifiedExpression
            ) {
                val parts = expressionParts(expression)
                val classIndex = parts.indexOfFirst { part -> part.firstOrNull()?.isUpperCase() == true }
                if (2 <= classIndex) {
                    addFqnFinding(parts.take(classIndex + 1), expression.receiverExpression)
                }
            }
        }

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
                    expression.calleeExpression?.let(::expressionParts).orEmpty()
                }

                else -> {
                    emptyList()
                }
            }

        private fun addFqnFinding(
            nameParts: List<String>,
            element: PsiElement
        ) {
            if (3 <= nameParts.size &&
                nameParts[0].firstOrNull()?.isLowerCase() == true &&
                nameParts[1].firstOrNull()?.isLowerCase() == true &&
                nameParts.last().firstOrNull()?.isUpperCase() == true &&
                ktFile.importDirectives.none { directive -> directive.importedName?.asString() == nameParts.lastOrNull() }
            ) {
                emit(
                    element.textOffset,
                    "fully qualified name `${nameParts.joinToString(".")}` used inline; add an import and use the simple name",
                    false
                )
            }
        }
    }
}
