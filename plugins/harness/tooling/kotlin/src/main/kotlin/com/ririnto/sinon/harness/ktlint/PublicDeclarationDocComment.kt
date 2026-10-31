package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

/**
 * Requires KDoc on effective public and protected types, objects, and functions.
 *
 * The rule runs by default. Opt out with `ktlint_harness_public-declaration-doc-comment = disabled` in `.editorconfig`.
 * Overrides inherit their contract. Property documentation is reviewed with its enclosing API contract.
 */
class PublicDeclarationDocComment :
    Rule(
        ruleId = RuleId("harness:public-declaration-doc-comment"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val NON_PUBLIC_VISIBILITIES: Set<KtKeywordToken> =
            setOf(KtTokens.PRIVATE_KEYWORD, KtTokens.INTERNAL_KEYWORD)
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.takeUnless(KtFile::isScript)
            ?.accept(PublicDocVisitor(emit))
    }

    private class PublicDocVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            super.visitClass(klass)
            if (klass.parent !is KtBlockExpression &&
                shouldCheck(klass, klass.parent, KtTokens.CLASS_KEYWORD, KtTokens.INTERFACE_KEYWORD) &&
                klass.docComment === null
            ) {
                report(klass, klass.name ?: "unknown")
            }
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                shouldCheck(function, function.parent, KtTokens.FUN_KEYWORD) &&
                function.docComment === null
            ) {
                report(function, function.name ?: "unknown")
            }
        }

        override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
            super.visitObjectDeclaration(declaration)
            if (declaration.name !== null &&
                !declaration.isCompanion() &&
                shouldCheck(declaration, declaration.parent, KtTokens.OBJECT_KEYWORD) &&
                declaration.docComment === null
            ) {
                report(declaration, declaration.name ?: "object")
            }
        }

        private fun report(
            declaration: KtModifierListOwner,
            name: String
        ) {
            emit(
                (declaration as PsiElement).textOffset,
                "add a documentation comment to public declaration `$name`",
                false
            )
        }

        private fun shouldCheck(
            declaration: KtModifierListOwner,
            scopeStart: PsiElement?,
            vararg declarationTokens: KtKeywordToken
        ): Boolean =
            when (declaration.visibilityModifierType()) {
                null, KtTokens.PUBLIC_KEYWORD, KtTokens.PROTECTED_KEYWORD -> true
                else -> false
            } &&
                !isEnclosedByNonPublic(scopeStart) &&
                declarationTokens.any { token -> declaration.node.findChildByType(token) !== null } &&
                (declaration !is KtNamedFunction || declaration.parent !is KtBlockExpression)

        private fun isEnclosedByNonPublic(scopeStart: PsiElement?): Boolean =
            generateSequence(scopeStart, PsiElement::getParent)
                .any { enclosing ->
                    (enclosing as? KtModifierListOwner)?.visibilityModifierType()?.let { token ->
                        token in NON_PUBLIC_VISIBILITIES
                    } == true ||
                        (enclosing is KtClass && enclosing.parent is KtBlockExpression) ||
                        (enclosing is KtObjectDeclaration && enclosing.isObjectLiteral())
                }
    }
}
