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
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates silent-catch findings to source AST/PSI analysis.
 */
object SilentCatchRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "silentCatch"

    override fun applies(ctx: RuleContext): Boolean {
        return ctx.manifest.categoryObject(category)?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
    }

    override fun renderAstFindings(ctx: RuleContext, findings: Collection<AstFinding>): Collection<Finding> =
        AstFindingRenderer.renderEach(findings.toList(), ctx.manifest.raw)

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> = buildSet {
        val ktFile = AstSupport.parse(file, astFactory)
        ktFile?.accept(Visitor(::add, file, ctx, ktFile))
    }

    /**
     * AST visitor for silent catch analysis.
     *
     * Identifies catch clauses that do not use the caught exception parameter
     * or handle it in meaningful ways (throw, logger, println).
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitCatchSection(catchSection: KtCatchClause) {
            super.visitCatchSection(catchSection)
            val paramName = catchSection.catchParameter?.name
            val body = catchSection.catchBody
            if (
                paramName != null &&
                body != null &&
                !body.text.contains(paramName) &&
                !body.text.contains("throw") &&
                !body.text.contains("logger") &&
                !body.text.contains("println")
            ) {
                record(
                    AstFinding(
                        rule = category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, catchSection.node?.startOffset),
                    ),
                )
            }
        }
    }
}
