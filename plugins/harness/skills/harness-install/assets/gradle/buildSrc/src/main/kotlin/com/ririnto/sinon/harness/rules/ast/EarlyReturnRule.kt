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
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Rule that delegates early-return findings to source AST/PSI analysis.
 */
object EarlyReturnRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "earlyReturn"

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
            ktFile?.accept(Visitor({ finding -> add(finding) }, file, ctx, ktFile))
        }

    /**
     * AST visitor for early return analysis.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            val bodyStatements = function.bodyBlockExpression?.statements ?: return
            bodyStatements
                .filterIsInstance<KtReturnExpression>()
                .filter { statement -> statement !== bodyStatements.last() }
                .forEach { statement ->
                    record(
                        AstFinding(
                            rule = "earlyReturn",
                            file = AstSupport.relativeFilePath(file, ctx.root),
                            line = AstSupport.lineOf(ktFile, statement.node?.startOffset),
                            details = mapOf("function" to (function.name ?: "unknown")),
                        ),
                    )
                }
        }
    }
}
