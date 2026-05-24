package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates empty-catch findings to source AST/PSI analysis.
 */
object EmptyCatchBlockRule : HarnessCheckRule() {
    override val category: String = "emptyCatchBlock"

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
                    override fun visitCatchSection(catchSection: KtCatchClause) {
                        super.visitCatchSection(catchSection)
                        if (catchSection.catchBody is KtBlockExpression &&
                            (catchSection.catchBody as KtBlockExpression).statements.isEmpty()
                        ) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, catchSection.node?.startOffset),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }
}
