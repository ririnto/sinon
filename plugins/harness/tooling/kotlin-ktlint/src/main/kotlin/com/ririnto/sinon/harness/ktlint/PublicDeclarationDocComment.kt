package com.ririnto.sinon.harness.ktlint

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
 * Requires KDoc on effective public and protected declarations.
 *
 * Enable the rule with `ktlint_harness_public_declaration_doc_comment = on` in `.editorconfig`.
 */
class PublicDeclarationDocComment :
    Rule(
        ruleId = RuleId("harness:public-declaration-doc-comment"),
        about = About(),
        usesEditorConfigProperties = setOf(DOC_COMMENT_MODE)
    ),
    RuleAutocorrectApproveHandler {
    companion object {
        val DOC_COMMENT_MODE =
            EditorConfigProperty(
                type =
                    PropertyType(
                        "ktlint_harness_public_declaration_doc_comment",
                        "Public declaration documentation mode",
                        PropertyType.PropertyValueParser.IDENTITY_VALUE_PARSER
                    ),
                defaultValue = "off"
            )
        val NON_PUBLIC_VISIBILITIES = setOf(KtTokens.PRIVATE_KEYWORD, KtTokens.INTERNAL_KEYWORD)
    }

    private var enabled = false

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        enabled = editorConfig[DOC_COMMENT_MODE] == "on"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (enabled) {
            (node.psi as? KtFile)
                ?.takeUnless { file -> file.isScript() }
                ?.accept(PublicDocVisitor(emit))
        }
    }

    private class PublicDocVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            super.visitClass(klass)
            if (klass.parent !is KtBlockExpression &&
                shouldCheck(klass, KtTokens.CLASS_KEYWORD, KtTokens.INTERFACE_KEYWORD) &&
                klass.docComment == null
            ) {
                report(klass, klass.name ?: "unknown", "public declaration")
            }
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                shouldCheck(function, KtTokens.FUN_KEYWORD) &&
                function.docComment == null
            ) {
                report(function, function.name ?: "unknown", "public declaration")
            }
        }

        override fun visitProperty(property: KtProperty) {
            super.visitProperty(property)
            if (!property.isLocal && shouldCheck(property, KtTokens.VAL_KEYWORD, KtTokens.VAR_KEYWORD) && property.docComment == null) {
                report(property, property.name ?: "property", "public declaration")
            }
        }

        override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
            super.visitObjectDeclaration(declaration)
            if (declaration.name != null &&
                !declaration.isCompanion() &&
                shouldCheck(declaration, KtTokens.OBJECT_KEYWORD) &&
                declaration.docComment == null
            ) {
                report(declaration, declaration.name ?: "object", "public declaration")
            }
        }

        private fun report(
            declaration: KtModifierListOwner,
            name: String,
            kind: String
        ) {
            emit(
                (declaration as PsiElement).textOffset,
                "add a documentation comment to $kind `$name`",
                false
            )
        }

        private fun shouldCheck(
            declaration: KtModifierListOwner,
            vararg declarationTokens: KtKeywordToken
        ): Boolean {
            val visibility = declaration.visibilityModifierType()
            return !isEnclosedByNonPublic(declaration) &&
                (visibility == null || visibility == KtTokens.PUBLIC_KEYWORD || visibility == KtTokens.PROTECTED_KEYWORD) &&
                declarationTokens.any { token -> declaration.node.findChildByType(token) != null } &&
                (declaration !is KtNamedFunction || declaration.parent !is KtBlockExpression)
        }

        private fun isEnclosedByNonPublic(declaration: PsiElement): Boolean =
            generateSequence(declaration.parent) { parent -> parent.parent }
                .filterIsInstance<KtModifierListOwner>()
                .any { owner -> owner.visibilityModifierType() in NON_PUBLIC_VISIBILITIES }
    }
}
