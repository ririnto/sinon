package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstFinding

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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

    override fun applies(ctx: RuleContext): Boolean {
        return ctx.manifest.categoryObject(category)?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
    }

    override fun renderAstFindings(ctx: RuleContext, findings: Collection<AstFinding>): Collection<Finding> = AstFindingRenderer.renderEach(findings.toList(), ctx.manifest.raw)

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> = buildSet {
        val ktFile = AstSupport.parse(file, astFactory)
        if (ktFile != null) {
            ktFile.accept(Visitor(::add, file, ctx, ktFile))
        }
    }

    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            super.visitClass(klass)
            if (isExternallyVisible(klass) && klass.docComment == null) {
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
            if (isExternallyVisible(function) &&
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
            if (!property.isLocal && isExternallyVisible(property) && property.docComment == null) {
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
        private fun isExternallyVisible(element: KtModifierListOwner): Boolean {
            val visibilityType = element.visibilityModifierType()
            val parent = element.parent
            return visibilityType != KtTokens.PRIVATE_KEYWORD &&
                visibilityType != KtTokens.INTERNAL_KEYWORD &&
                !(element is KtProperty && element.isLocal) &&
                !(element is KtNamedFunction && parent is KtBlockExpression)
        }
    }
}
