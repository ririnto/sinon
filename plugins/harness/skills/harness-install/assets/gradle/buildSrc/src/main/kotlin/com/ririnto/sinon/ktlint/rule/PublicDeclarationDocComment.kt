package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import org.ec4j.core.model.PropertyType
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
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

/**
 * Flags public declarations without documentation comments.
 * Require KDoc on public APIs.
 *
 * Disabled by default.
 *
 * Set `ktlint_public_declaration_doc_comment_mode = on` in `.editorconfig` to enable.
 */
class PublicDeclarationDocComment :
    Rule(
        ruleId = RuleId("code:public-declaration-doc-comment"),
        about = About(),
        usesEditorConfigProperties = setOf(DOC_COMMENT_MODE)
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val ENABLED_MODES: Set<String> = setOf("on", "public")
        private val NON_PUBLIC_VISIBILITIES = setOf(KtTokens.PRIVATE_KEYWORD, KtTokens.INTERNAL_KEYWORD)

        val DOC_COMMENT_MODE: EditorConfigProperty<String> =
            EditorConfigProperty(
                type =
                    PropertyType(
                        "ktlint_public_declaration_doc_comment_mode",
                        "Public doc comment mode (`on` or `public` to enable; any other value disables)",
                        PropertyType.PropertyValueParser.IDENTITY_VALUE_PARSER
                    ),
                defaultValue = "off"
            )
    }

    private lateinit var mode: String

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        mode = editorConfig[DOC_COMMENT_MODE]
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (mode in ENABLED_MODES) {
            (node.psi as? KtFile)
                ?.takeUnless { ktFile -> ktFile.isScript() }
                ?.accept(PublicDocVisitor(emit))
        }
    }

    private class PublicDocVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            super.visitClass(klass)
            if (klass.parent !is KtBlockExpression &&
                klass.shouldCheck(KtTokens.CLASS_KEYWORD) &&
                klass.docComment === null
            ) {
                emit(
                    klass.textOffset,
                    "add a documentation comment to public declaration `${klass.name ?: "unknown"}`",
                    false
                )
            }
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                function.shouldCheck(KtTokens.FUN_KEYWORD) &&
                function.docComment === null
            ) {
                emit(
                    function.textOffset,
                    "add a documentation comment to public declaration `${function.name ?: "unknown"}`",
                    false
                )
            }
        }

        override fun visitProperty(property: KtProperty) {
            super.visitProperty(property)
            if (!property.isLocal &&
                property.shouldCheck(KtTokens.VAL_KEYWORD, KtTokens.VAR_KEYWORD) &&
                property.docComment === null
            ) {
                emit(
                    property.textOffset,
                    "add a documentation comment to public declaration `${property.name ?: "property"}`",
                    false
                )
            }
        }

        override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
            super.visitObjectDeclaration(declaration)
            if (declaration.name !== null &&
                !declaration.isCompanion() &&
                declaration.shouldCheck(KtTokens.OBJECT_KEYWORD) &&
                declaration.docComment === null
            ) {
                emit(
                    declaration.textOffset,
                    "add a documentation comment to public declaration `${declaration.name}`",
                    false
                )
            }
        }

        private fun KtModifierListOwner.shouldCheck(vararg declarationTokens: KtKeywordToken): Boolean {
            val visibility = visibilityModifierType()
            return !isEnclosedByNonPublic() &&
                (visibility === null || visibility in setOf(KtTokens.PUBLIC_KEYWORD, KtTokens.PROTECTED_KEYWORD)) &&
                declarationTokens.any { token -> node.findChildByType(token) !== null } &&
                (this !is KtNamedFunction || parent !is KtBlockExpression)
        }

        private fun PsiElement.isEnclosedByNonPublic(): Boolean {
            var parent = this.parent
            var enclosedByNonPublic = false
            while (parent !== null && !enclosedByNonPublic) {
                enclosedByNonPublic =
                    when (parent) {
                        is KtClass -> parent.visibilityModifierType() in NON_PUBLIC_VISIBILITIES
                        is KtObjectDeclaration -> parent.visibilityModifierType() in NON_PUBLIC_VISIBILITIES
                        else -> false
                    }
                parent = parent.parent
            }
            return enclosedByNonPublic
        }
    }
}
