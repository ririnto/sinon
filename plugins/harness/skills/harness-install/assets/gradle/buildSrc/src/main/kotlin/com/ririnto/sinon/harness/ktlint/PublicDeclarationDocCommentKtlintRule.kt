package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
class PublicDeclarationDocCommentKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "publicDeclarationDocComment"
    }

    private val visibility = loadVisibilityTokens(ctx)
    private val allowedDeclarationKinds = loadAllowedDeclarationKinds(ctx)
    private val exemptOverrideDeclarations = loadExemptOverrideDeclarations(ctx)
    private val exemptLocalDeclarations = loadExemptLocalDeclarations(ctx)

    private fun loadVisibilityTokens(ctx: RuleContext): Set<String> =
        (
            ctx.manifest
                .categoryObject(CATEGORY)
                ?.get("parameters")
                ?.jsonObject
                ?.get("visibility")
                ?.jsonArray
                ?.mapNotNull { entry -> entry.jsonPrimitive.contentOrNull }
                ?: listOf("public", "protected", "internal")
        ).toSet()

    private fun loadAllowedDeclarationKinds(ctx: RuleContext): Set<String> =
        (
            ctx.manifest
                .categoryObject(CATEGORY)
                ?.get("parameters")
                ?.jsonObject
                ?.get("allowedDeclarationKinds")
                ?.jsonArray
                ?.mapNotNull { entry -> entry.jsonPrimitive.contentOrNull }
                ?: listOf("class", "function", "property")
        ).toSet()

    private fun loadExemptOverrideDeclarations(ctx: RuleContext): Boolean =
        ctx.manifest
            .categoryObject(CATEGORY)
            ?.get("parameters")
            ?.jsonObject
            ?.get("exemptOverrideDeclarations")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBoolean() ?: true

    private fun loadExemptLocalDeclarations(ctx: RuleContext): Boolean =
        ctx.manifest
            .categoryObject(CATEGORY)
            ?.get("parameters")
            ?.jsonObject
            ?.get("exemptLocalDeclarations")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBoolean() ?: true

    private fun effectiveVisibility(declaration: KtModifierListOwner): String {
        val visibilityModifierType = declaration.visibilityModifierType()
        return when (visibilityModifierType) {
            KtTokens.PUBLIC_KEYWORD -> "public"
            KtTokens.PROTECTED_KEYWORD -> "protected"
            KtTokens.INTERNAL_KEYWORD -> "internal"
            KtTokens.PRIVATE_KEYWORD -> "private"
            else -> "public"
        }
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitClass(klass: KtClass) {
                        super.visitClass(klass)
                        if (shouldCheck(klass, "class") && klass.docComment == null) {
                            emit(klass.textOffset, message(mapOf("name" to (klass.name ?: "unknown"))), false)
                        }
                    }

                    override fun visitNamedFunction(function: KtNamedFunction) {
                        super.visitNamedFunction(function)
                        val isOverride = exemptOverrideDeclarations && function.hasModifier(KtTokens.OVERRIDE_KEYWORD)
                        if (!isOverride && shouldCheck(function, "function") && function.docComment == null) {
                            emit(function.textOffset, message(mapOf("name" to (function.name ?: "unknown"))), false)
                        }
                    }

                    override fun visitProperty(property: KtProperty) {
                        super.visitProperty(property)
                        val isLocal = exemptLocalDeclarations && property.isLocal
                        if (!isLocal && shouldCheck(property, "property") && property.docComment == null) {
                            emit(property.textOffset, message(mapOf("name" to (property.name ?: "unknown"))), false)
                        }
                    }

                    private fun shouldCheck(declaration: KtModifierListOwner, kind: String): Boolean {
                        val visib = effectiveVisibility(declaration)
                        val inBlockExpression = declaration is KtNamedFunction && declaration.parent is KtBlockExpression
                        return visib in visibility && kind in allowedDeclarationKinds && !inBlockExpression
                    }
                },
            )
        }
    }
}
