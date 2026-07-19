package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags leading underscores in Kotlin file basenames and declarations, including parameters.
 */
class LeadingUnderscoreKtlintRule :
    Rule(
        ruleId = RuleId("code:leading-underscore"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        fun isForbidden(name: String): Boolean = name.startsWith("_") && name != "_"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            val rawName = ktFile.virtualFile?.name ?: ktFile.name
            val basename = java.io.File(rawName).nameWithoutExtension
            if (isForbidden(basename)) {
                emit(ktFile.textOffset, "declaration `$basename` uses a leading underscore", false)
            }
            ktFile.accept(LeadingUnderscoreVisitor(emit))
        }
    }

    private class LeadingUnderscoreVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
            super.visitNamedDeclaration(declaration)
            declaration.name?.let { name ->
                if (isForbidden(name)) {
                    val canAutocorrect = declaration is KtParameter && canAutocorrect(declaration)
                    emit(
                        declaration.textOffset,
                        "declaration `$name` uses a leading underscore",
                        canAutocorrect
                    ).ifAutocorrectAllowed {
                        if (canAutocorrect) {
                            declaration.nameIdentifier?.node?.replaceTextWith("_")
                        }
                    }
                }
            }
        }

        private fun canAutocorrect(parameter: KtParameter): Boolean {
            val function = PsiTreeUtil.getParentOfType(parameter, KtNamedFunction::class.java) ?: return false
            val name = parameter.name ?: return false
            val modifiers = function.modifierList
            return canAutocorrectWithoutSibling(parameter, function) &&
                function.valueParameters.none { sibling ->
                    sibling !== parameter &&
                        isForbidden(sibling.name.orEmpty()) &&
                        canAutocorrectWithoutSibling(sibling, function)
                }
        }

        private fun canAutocorrectWithoutSibling(parameter: KtParameter, function: KtNamedFunction): Boolean {
            val name = parameter.name ?: return false
            val modifiers = function.modifierList
            return parameter.annotationEntries.isEmpty() &&
                !parameter.hasValOrVar() &&
                modifiers?.hasModifier(KtTokens.PRIVATE_KEYWORD) == true &&
                !modifiers.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                !modifiers.hasModifier(KtTokens.OPEN_KEYWORD) &&
                !modifiers.hasModifier(KtTokens.EXTERNAL_KEYWORD) &&
                !modifiers.hasModifier(KtTokens.EXPECT_KEYWORD) &&
                PsiTreeUtil.findChildrenOfType(function, KtNameReferenceExpression::class.java)
                    .none { reference -> reference.getReferencedName() == name } &&
                PsiTreeUtil.findChildrenOfType(parameter.containingFile, KtCallExpression::class.java)
                    .none { call ->
                        (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == function.name &&
                            call.valueArguments.any { argument -> argument.getArgumentName()?.asName?.asString() == name }
                    } &&
                PsiTreeUtil.findChildrenOfType(function, KtNameReferenceExpression::class.java)
                    .none { reference -> reference.getReferencedName() == parameter.name }
        }
    }
}
