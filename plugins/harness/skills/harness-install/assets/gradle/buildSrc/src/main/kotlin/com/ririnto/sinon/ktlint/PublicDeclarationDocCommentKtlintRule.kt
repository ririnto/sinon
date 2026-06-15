package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

/**
 * Flags public declarations without documentation comments; require KDoc on public APIs.
 */
class PublicDeclarationDocCommentKtlintRule :
    Rule(
        ruleId = RuleId("code:public-declaration-doc-comment"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.takeUnless { ktFile -> ktFile.isScript() }
            ?.accept(PublicDocVisitor(emit))
    }

    private class PublicDocVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            super.visitClass(klass)
            if (shouldCheck(klass, KtTokens.CLASS_KEYWORD) && klass.docComment == null) {
                emit(
                    klass.textOffset,
                    "public declaration `${klass.name ?: "unknown"}` is missing a documentation comment",
                    false
                )
            }
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                shouldCheck(function, KtTokens.FUN_KEYWORD) &&
                function.docComment == null
            ) {
                emit(
                    function.textOffset,
                    "public declaration `${function.name ?: "unknown"}` is missing a documentation comment",
                    false
                )
            }
        }

        override fun visitProperty(property: KtProperty) {
            super.visitProperty(property)
            if (!property.isLocal &&
                shouldCheck(property, KtTokens.VAL_KEYWORD, KtTokens.VAR_KEYWORD) &&
                property.docComment == null
            ) {
                emit(
                    property.textOffset,
                    "public declaration `${property.name ?: "unknown"}` is missing a documentation comment",
                    false
                )
            }
        }

        private fun shouldCheck(declaration: KtModifierListOwner, vararg declarationTokens: KtKeywordToken): Boolean {
            val visibility = declaration.visibilityModifierType()
            return (visibility == null || visibility in setOf(KtTokens.PUBLIC_KEYWORD, KtTokens.PROTECTED_KEYWORD)) &&
                declarationTokens.any { token -> declaration.node.findChildByType(token) != null } &&
                !(declaration is KtNamedFunction && declaration.parent is KtBlockExpression)
        }
    }
}
