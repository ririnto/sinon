package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates greater-than comparison findings to source AST/PSI analysis.
 */
object GreaterThanComparisonRule : HarnessCheckRule() {
    override val category: String = "greaterThanComparison"

    override fun applies(manifest: JsonObject): Boolean {
        return manifest[category]
            ?.jsonObject
            ?.get("enabled")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBoolean()
            ?: true
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> = emptyList()

    override fun renderPsiFindings(
        findings: List<ai.harness.gradle.PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> = PsiFindingRenderer.renderEach(findings, manifest)

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: KtPsiFactory?,
    ): List<PsiFinding> = buildList {
        val ktFile = PsiRuleSupport.parse(file, psiFactory)
        if (ktFile != null) {
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitBinaryExpression(expression: KtBinaryExpression) {
                        super.visitBinaryExpression(expression)
                        if (expression.operationToken == KtTokens.GT || expression.operationToken == KtTokens.GTEQ) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, expression.node?.startOffset),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }
}
