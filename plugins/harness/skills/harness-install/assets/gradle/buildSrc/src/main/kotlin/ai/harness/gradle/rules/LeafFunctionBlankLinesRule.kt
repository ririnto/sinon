package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates leaf-function blank-line findings to source AST/PSI analysis.
 */
object LeafFunctionBlankLinesRule : HarnessCheckRule() {
    override val category: String = "leafFunctionBlankLines"

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
    ): Collection<Finding> {
        val maxConsecutiveBlankLines =
            manifest[category]
                ?.jsonObject
                ?.get("parameters")
                ?.jsonObject
                ?.get("maxConsecutiveBlankLines")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toIntOrNull()
                ?.coerceAtLeast(0)
                ?: 1
        return PsiFindingRenderer.renderEach(
            findings.filter { finding -> maxConsecutiveBlankLines < finding.intDetail("blankLineCount") },
            manifest,
        )
    }

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: KtPsiFactory?,
    ): List<PsiFinding> = buildList {
        val ktFile = PsiRuleSupport.parse(file, psiFactory)
        if (ktFile != null) {
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitNamedFunction(function: KtNamedFunction) {
                        super.visitNamedFunction(function)
                        if (function.hasDescendantOfType<KtNamedFunction> { nestedFunc -> nestedFunc !== function } ||
                            function.hasDescendantOfType<KtLambdaExpression> { true }
                        ) {
                            return
                        }
                        generateSequence(function.bodyExpression?.firstChild ?: return) { element -> element.nextSibling }
                            .filter { child -> child is PsiWhiteSpace }
                            .map { child -> child to child.text.count { char -> char == '\n' } - 1 }
                            .filter { (_, blankLineCount) -> 0 < blankLineCount }
                            .forEach { (child, blankLineCount) ->
                                add(
                                    PsiFinding(
                                        rule = category,
                                        file = PsiRuleSupport.relativeFilePath(file, root),
                                        line = PsiRuleSupport.lineOf(ktFile, child.node?.startOffset),
                                        details =
                                            mapOf(
                                                "function" to (function.name ?: "unknown"),
                                                "blankLineCount" to blankLineCount.toString(),
                                            ),
                                    ),
                                )
                            }
                    }
                },
            )
        }
    }
}
