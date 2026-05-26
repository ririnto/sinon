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
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates wildcard-import findings to source AST/PSI analysis.
 */
object WildcardImportRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "wildcardImport"

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
            ktFile?.accept(Visitor({ finding -> add(finding) }, file, ctx, ktFile))
        }

    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitImportDirective(importDirective: KtImportDirective) {
            super.visitImportDirective(importDirective)
            if (importDirective.isAllUnder) {
                record(
                    AstFinding(
                        rule = "wildcardImport",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, importDirective.node?.startOffset),
                        details = mapOf("imported" to (importDirective.importPath?.pathStr ?: "")),
                    ),
                )
            }
        }
    }
}
