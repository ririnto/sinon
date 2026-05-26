package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that requires KDoc comments to use multiline block style.
 */
object MultilineDocStyleRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "multilineDocStyle"

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
            if (docStyleMode(ctx) != "multiline") {
                return@buildSet
            }
            val ktFile = AstSupport.parse(file, astFactory)
            ktFile?.accept(Visitor(::add, file, ctx, ktFile))
        }

    private fun docStyleMode(ctx: RuleContext): String =
        ctx.manifest
            .categoryObject(category)
            ?.get("parameters")
            ?.jsonObject
            ?.get("docStyleMode")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "multiline"

    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitDeclaration(declaration: KtDeclaration) {
            super.visitDeclaration(declaration)
            val docComment = declaration.docComment ?: return
            if (!docComment.text.contains('\n')) {
                record(
                    AstFinding(
                        rule = "multilineDocStyle",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, docComment.node?.startOffset),
                        details = mapOf("style" to "kdoc"),
                    ),
                )
            }
        }
    }
}
