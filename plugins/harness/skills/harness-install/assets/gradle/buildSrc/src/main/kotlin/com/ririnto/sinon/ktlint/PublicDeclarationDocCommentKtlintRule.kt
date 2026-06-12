package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
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
    companion object {
        private val VISIBILITY: Set<String> = setOf("public", "protected", "internal")
        private val ALLOWED_DECLARATION_KINDS: Set<String> = setOf("class", "function", "property")
        private const val EXEMPT_OVERRIDE_DECLARATIONS: Boolean = true
        private const val EXEMPT_LOCAL_DECLARATIONS: Boolean = true
    }

    private fun effectiveVisibility(declaration: KtModifierListOwner): String =
        when (declaration.visibilityModifierType()) {
            KtTokens.PUBLIC_KEYWORD -> "public"
            KtTokens.PROTECTED_KEYWORD -> "protected"
            KtTokens.INTERNAL_KEYWORD -> "internal"
            KtTokens.PRIVATE_KEYWORD -> "private"
            else -> "public"
        }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.takeUnless { ktFile -> ktFile.isScript() }
            ?.let { ktFile ->
                ktFile.accept(
                    object : KtTreeVisitorVoid() {
                        override fun visitClass(klass: KtClass) {
                            super.visitClass(klass)
                            if (shouldCheck(klass, "class") && klass.docComment == null) {
                                emit(
                                    klass.textOffset,
                                    "public declaration `${klass.name ?: "unknown"}` is missing a documentation comment",
                                    false
                                )
                            }
                        }

                        override fun visitNamedFunction(function: KtNamedFunction) {
                            super.visitNamedFunction(function)
                            if (!(EXEMPT_OVERRIDE_DECLARATIONS && function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) &&
                                shouldCheck(
                                    function,
                                    "function"
                                ) && function.docComment == null
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
                            if (!(EXEMPT_LOCAL_DECLARATIONS && property.isLocal) &&
                                shouldCheck(
                                    property,
                                    "property"
                                ) && property.docComment == null
                            ) {
                                emit(
                                    property.textOffset,
                                    "public declaration `${property.name ?: "unknown"}` is missing a documentation comment",
                                    false
                                )
                            }
                        }

                        private fun shouldCheck(
                            declaration: KtModifierListOwner,
                            kind: String
                        ): Boolean =
                            effectiveVisibility(declaration) in VISIBILITY &&
                                kind in ALLOWED_DECLARATION_KINDS &&
                                !(declaration is KtNamedFunction && declaration.parent is KtBlockExpression)
                    }
                )
            }
    }
}
