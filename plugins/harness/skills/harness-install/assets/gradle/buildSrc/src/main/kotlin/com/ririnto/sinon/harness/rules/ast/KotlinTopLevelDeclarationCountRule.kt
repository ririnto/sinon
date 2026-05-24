package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeAlias
import java.nio.file.Path

/**
 * Rule that delegates single-top-level declaration findings to source AST/PSI analysis.
 */
object KotlinTopLevelDeclarationCountRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "kotlinTopLevelDeclarationCount"

    override fun renderAstFindings(ctx: RuleContext, findings: Collection<AstFinding>): Collection<Finding> =
        buildList {
            ctx.manifest.categoryObject(category)?.get("parameters")?.jsonObject?.let { parameters ->
                val allowedDeclarations = JsonAccess.stringArrayFromObject(parameters, "allowedDeclarations")
                addAll(
                    AstFindingRenderer.renderEach(
                        findings.filter { finding ->
                            finding.intDetail("count") != 1 ||
                                (finding.detail("firstKind") != "unknown" && finding.detail("firstKind") !in allowedDeclarations)
                        }.toList(),
                        ctx.manifest.raw,
                    ),
                )
            }
        }

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> = buildList {
        AstSupport.parse(file, astFactory)?.run {
            add(
                AstFinding(
                    rule = category,
                    file = AstSupport.relativeFilePath(file, ctx.root),
                    line = 1,
                    details =
                        mapOf(
                            "count" to declarations.size.toString(),
                            "firstKind" to when (declarations.firstOrNull()) {
                                is KtClass -> "class"
                                is KtObjectDeclaration -> "object"
                                is KtTypeAlias -> "typealias"
                                else -> "unknown"
                            },
                        ),
                ),
            )
        }
    }
}
