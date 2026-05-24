package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.Severity
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates companion-object position findings to source AST/PSI analysis.
 */
object CompanionObjectPositionRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "companionObjectPosition"

    override fun renderAstFindings(ctx: RuleContext, findings: Collection<AstFinding>): Collection<Finding> {
        return buildList {
            findings
                .filter { finding ->
                    when (
                        ctx.manifest.categoryObject(category)?.get("parameters")
                            ?.jsonObject
                            ?.get("position")
                            ?.jsonPrimitive
                            ?.contentOrNull
                            ?.takeIf { value -> value == "bottom" }
                            ?: "top"
                    ) {
                        "bottom" -> finding.intDetail("position") != finding.intDetail("lastPosition")
                        else -> finding.intDetail("position") != 0
                    }
                }
                .forEach { finding ->
                    add(
                        Finding(
                            ctx.manifest.severityOf(category),
                            category,
                            "${finding.file}:${finding.line}: companion object in class `${finding.detail("className")}` must be repositioned",
                        ),
                    )
                }
        }
    }

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> = buildSet {
        val ktFile = AstSupport.parse(file, astFactory)
        ktFile?.accept(Visitor(::add, file, ctx, ktFile))
    }

    /**
     * AST visitor for companion object position analysis.
     *
     * Analyzes class bodies to identify companion object declarations and their positions.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            super.visitClass(klass)
            klass.getBody()?.let { body ->
                val declarations = body.declarations.filter { declaration -> declaration !is KtEnumEntry }
                declarations.forEachIndexed { index, declaration ->
                    if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                        record(
                            AstFinding(
                                rule = category,
                                file = AstSupport.relativeFilePath(file, ctx.root),
                                line = AstSupport.lineOf(ktFile, declaration.node?.startOffset),
                                details =
                                    mapOf(
                                        "className" to (klass.name ?: "unknown"),
                                        "position" to index.toString(),
                                        "lastPosition" to declarations.lastIndex.toString(),
                                    ),
                            ),
                        )
                    }
                }
            }
        }
    }
}
