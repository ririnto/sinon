package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates implicit-lambda 'it' findings to source AST/PSI analysis.
 */
object ImplicitLambdaItRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "implicitLambdaIt"

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
            ktFile?.accept(OuterVisitor({ finding -> add(finding) }, file, ctx, ktFile))
        }

    private class OuterVisitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitLambdaExpression(expression: KtLambdaExpression) {
            super.visitLambdaExpression(expression)
            if (expression.valueParameters.isNotEmpty()) {
                return
            }
            if (containsItReference(expression.bodyExpression)) {
                record(
                    AstFinding(
                        rule = "implicitLambdaIt",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, expression.node?.startOffset),
                    ),
                )
            }
        }

        /**
         * Recursively check if a KtElement tree contains an 'it' reference.
         */
        private fun containsItReference(element: KtElement?): Boolean =
            element != null &&
                ((element is KtSimpleNameExpression && element.text == "it") ||
                    element.children.any { child -> child is KtElement && containsItReference(child) })
    }
}
