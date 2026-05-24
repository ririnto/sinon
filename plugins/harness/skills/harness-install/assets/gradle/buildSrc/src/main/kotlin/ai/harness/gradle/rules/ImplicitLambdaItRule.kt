package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates implicit-lambda 'it' findings to source AST/PSI analysis.
 */
object ImplicitLambdaItRule : HarnessCheckRule() {
    override val category: String = "implicitLambdaIt"

    override fun applies(manifest: JsonObject): Boolean {
        return manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
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
                    override fun visitLambdaExpression(expression: KtLambdaExpression) {
                        super.visitLambdaExpression(expression)
                        if (expression.valueParameters.isNotEmpty()) {
                            return
                        }
                        val hasIt =
                            kotlin.run {
                                var found = false
                                expression.accept(
                                    object : KtTreeVisitorVoid() {
                                        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                                            super.visitSimpleNameExpression(expression)
                                            if (expression.text == "it") {
                                                found = true
                                            }
                                        }
                                    },
                                )
                                found
                            }
                        if (hasIt) {
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
