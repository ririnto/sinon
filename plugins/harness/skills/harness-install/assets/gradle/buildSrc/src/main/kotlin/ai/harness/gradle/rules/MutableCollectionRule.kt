package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import java.nio.file.Path

/**
 * Rule that delegates mutable-collection findings to source AST/PSI analysis.
 */
object MutableCollectionRule : HarnessCheckRule() {
    override val category: String = "mutableCollection"

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
        val mutableFactories =
            setOf("mutableListOf", "mutableSetOf", "mutableMapOf", "ArrayList", "HashSet", "HashMap", "LinkedHashMap", "LinkedHashSet")
        if (ktFile != null) {
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitCallExpression(expression: KtCallExpression) {
                        super.visitCallExpression(expression)
                        val calleeName = expression.calleeExpression?.text ?: ""
                        if (calleeName in mutableFactories) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, expression.node?.startOffset),
                                    details = mapOf("name" to calleeName),
                                ),
                            )
                        }
                    }

                    override fun visitUserType(type: KtUserType) {
                        super.visitUserType(type)
                        val typeName = type.text.substringBefore("<")
                        if (typeName in mutableFactories) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, type.node?.startOffset),
                                    details = mapOf("name" to typeName),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }
}
