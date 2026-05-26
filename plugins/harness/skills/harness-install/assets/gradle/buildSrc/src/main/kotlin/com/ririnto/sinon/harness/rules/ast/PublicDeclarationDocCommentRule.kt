package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import java.nio.file.Path

/**
 * Rule that delegates public documentation findings to source AST/PSI analysis.
 */
object PublicDeclarationDocCommentRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "publicDeclarationDocComment"

    override fun applies(ctx: RuleContext): Boolean =
        ctx.manifest
            .categoryObject(category)
            ?.get("enabled")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBoolean() ?: true

    override fun renderAstFindings(
        ctx: RuleContext,
        findings: Collection<AstFinding>,
    ): Collection<Finding> = AstFindingRenderer.renderEach(findings.toList(), ctx.manifest.raw)

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> =
        buildSet {
            val ktFile = AstSupport.parse(file, astFactory)
            ktFile?.accept(Visitor({ finding -> add(finding) }, file, ctx, ktFile, loadConfiguredTokens(ctx)))
        }

    /**
     * Load configured visibility tokens from manifest parameters.
     */
    private fun loadConfiguredTokens(ctx: RuleContext): Set<String> =
        (
            ctx.manifest
                .categoryObject(category)
                ?.get("parameters")
                ?.jsonObject
                ?.get("visibility")
                ?.jsonArray
                ?.mapNotNull { entry -> entry.jsonPrimitive.contentOrNull }
                ?: listOf("public", "protected", "internal")
        ).toSet()

    /**
     * Determine the effective visibility of a Kotlin declaration.
     *
     * @param declaration The declaration to inspect.
     * @return One of "public", "protected", "internal", "private".
     */
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

    /**
     * Check if a declaration matches the configured visibility tokens.
     *
     * @param declaration The declaration to check.
     * @param tokens The set of visibility tokens to match against.
     * @return true if declaration's visibility is in tokens and it's not in a block expression.
     */
    private fun matchesVisibility(
        declaration: KtModifierListOwner,
        tokens: Set<String>,
    ): Boolean {
        val visibility = effectiveVisibility(declaration)
        val parent = declaration.parent
        return visibility in tokens &&
            !(declaration is KtProperty && declaration.isLocal) &&
            !(declaration is KtNamedFunction && parent is KtBlockExpression)
    }

    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
        private val configuredTokens: Set<String>,
    ) : KtTreeVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            super.visitClass(klass)
            if (matchesVisibility(klass, configuredTokens) && klass.docComment == null) {
                record(
                    AstFinding(
                        rule = "publicDeclarationDocComment",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, klass.node?.startOffset),
                        details = mapOf("name" to (klass.name ?: "unknown")),
                    ),
                )
            }
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (matchesVisibility(function, configuredTokens) &&
                !function.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                function.docComment == null
            ) {
                record(
                    AstFinding(
                        rule = "publicDeclarationDocComment",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, function.node?.startOffset),
                        details = mapOf("name" to (function.name ?: "unknown")),
                    ),
                )
            }
        }

        override fun visitProperty(property: KtProperty) {
            super.visitProperty(property)
            if (!property.isLocal && matchesVisibility(property, configuredTokens) && property.docComment == null) {
                record(
                    AstFinding(
                        rule = "publicDeclarationDocComment",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, property.node?.startOffset),
                        details = mapOf("name" to (property.name ?: "unknown")),
                    ),
                )
            }
        }
    }
}
