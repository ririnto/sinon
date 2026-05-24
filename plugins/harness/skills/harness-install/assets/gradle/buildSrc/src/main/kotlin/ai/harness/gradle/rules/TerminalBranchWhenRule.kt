package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates terminal-if findings to source AST/PSI analysis.
 */
object TerminalBranchWhenRule : HarnessCheckRule() {
    override val category: String = "terminalBranchWhen"

    override fun applies(manifest: JsonObject): Boolean {
        return manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> = emptyList()

    override fun renderPsiFindings(
        findings: List<PsiFinding>,
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
                    override fun visitBlockExpression(expression: KtBlockExpression) {
                        super.visitBlockExpression(expression)
                        val terminalExpression = expression.statements.lastOrNull()
                        val terminalIf = terminalIfElseExpression(terminalExpression)
                        if (terminalIf != null) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, terminalIf.node?.startOffset),
                                    details = mapOf("context" to "block"),
                                ),
                            )
                        }
                    }

                    override fun visitNamedFunction(function: KtNamedFunction) {
                        super.visitNamedFunction(function)
                        val terminalIf = terminalIfElseExpression(function.bodyExpression)
                        if (terminalIf != null) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, terminalIf.node?.startOffset),
                                    details = mapOf("context" to "function `${function.name ?: "unknown"}`"),
                                ),
                            )
                        }
                    }

                    override fun visitProperty(property: KtProperty) {
                        super.visitProperty(property)
                        val terminalIf = terminalIfElseExpression(property.initializer)
                        if (!property.isLocal && terminalIf != null) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, terminalIf.node?.startOffset),
                                    details = mapOf("context" to "property `${property.name ?: "property"}`"),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }

    private fun terminalIfElseExpression(expression: PsiElement?): KtIfExpression? =
        when (expression) {
            is KtIfExpression -> expression.takeIf { candidate -> candidate.`else` != null }
            is KtReturnExpression -> terminalIfElseExpression(expression.returnedExpression)
            else -> null
        }
}
