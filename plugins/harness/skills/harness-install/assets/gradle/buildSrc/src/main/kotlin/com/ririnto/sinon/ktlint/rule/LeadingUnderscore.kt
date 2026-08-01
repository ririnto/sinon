package com.ririnto.sinon.ktlint.rule

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
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags leading underscores in Kotlin declarations, including parameters.
 */
class LeadingUnderscore :
    Rule(
        ruleId = RuleId("code:leading-underscore"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(LeadingUnderscoreVisitor(emit))
    }

    private class LeadingUnderscoreVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
            super.visitNamedDeclaration(declaration)
            declaration.name?.let { name ->
                if (
                    name.isForbidden() &&
                    !declaration.isOverrideOrInterfaceDeclaration()
                ) {
                    emit(
                        declaration.textOffset,
                        "remove the leading underscore from declaration `$name`",
                        (declaration as? KtParameter)?.let { parameter ->
                            PsiTreeUtil.getParentOfType(parameter, KtNamedFunction::class.java)?.let { function ->
                                parameter.canAutocorrectWithoutSibling(function) &&
                                    function.valueParameters.none { sibling ->
                                        sibling != parameter &&
                                            sibling.name.orEmpty().isForbidden() &&
                                            sibling.canAutocorrectWithoutSibling(function)
                                    }
                            }
                        } ?: false
                    ).ifAutocorrectAllowed {
                        declaration.nameIdentifier?.node?.replaceTextWith("_")
                    }
                }
            }
        }

        private fun KtNamedDeclaration.isOverrideOrInterfaceDeclaration(): Boolean =
            modifierList?.let { modifiers ->
                modifiers.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
                    modifiers.hasModifier(KtTokens.OPEN_KEYWORD) ||
                    modifiers.hasModifier(KtTokens.ABSTRACT_KEYWORD)
            } == true ||
                PsiTreeUtil.getParentOfType(this, KtNamedFunction::class.java)?.let { function ->
                    function.modifierList?.let { modifiers ->
                        modifiers.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
                            modifiers.hasModifier(KtTokens.OPEN_KEYWORD) ||
                            modifiers.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                    }
                } == true ||
                (PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java) as? KtClass)
                    ?.isInterface() == true

        private fun KtParameter.canAutocorrectWithoutSibling(function: KtNamedFunction): Boolean =
            name?.let { name ->
                val modifiers = function.modifierList
                annotationEntries.isEmpty() &&
                    !hasValOrVar() &&
                    modifiers?.hasModifier(KtTokens.PRIVATE_KEYWORD) == true &&
                    !modifiers.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                    !modifiers.hasModifier(KtTokens.OPEN_KEYWORD) &&
                    !modifiers.hasModifier(KtTokens.EXTERNAL_KEYWORD) &&
                    !modifiers.hasModifier(KtTokens.EXPECT_KEYWORD) &&
                    PsiTreeUtil
                        .findChildrenOfType(function, KtNameReferenceExpression::class.java)
                        .none { reference -> reference.getReferencedName() == name } &&
                    PsiTreeUtil
                        .findChildrenOfType(containingFile, KtCallExpression::class.java)
                        .none { call ->
                            (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == function.name &&
                                call.valueArguments.any { argument -> argument.getArgumentName()?.asName?.asString() == name }
                        } &&
                    PsiTreeUtil
                        .findChildrenOfType(function, KtNameReferenceExpression::class.java)
                        .none { reference -> reference.getReferencedName() == this.name }
            } == true

        private fun String.isForbidden(): Boolean = startsWith("_") && this != "_"
    }
}
