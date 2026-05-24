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
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates if-brace findings to source AST/PSI analysis.
 */
object IfStatementBracesRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "ifStatementBraces"

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
        ktFile?.accept(Visitor(::add, file, ctx, ktFile))
    }

    /**
     * AST visitor for if-statement brace analysis.
     *
     * Identifies if-expression branches that are not wrapped in block expressions.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            super.visitIfExpression(expression)
            if (expression.then != null && expression.then !is KtBlockExpression) {
                record(
                    AstFinding(
                        rule = category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, expression.node?.startOffset),
                        details = mapOf("kind" to "then"),
                    ),
                )
            }
            if (expression.`else` != null &&
                expression.`else` !is KtBlockExpression &&
                expression.`else` !is KtIfExpression
            ) {
                record(
                    AstFinding(
                        rule = category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, expression.node?.startOffset),
                        details = mapOf("kind" to "else"),
                    ),
                )
            }
        }
    }
}
