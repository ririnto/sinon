package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Path

/**
 * Rule that delegates single-top-level declaration findings to source AST/PSI analysis.
 */
object KotlinTopLevelDeclarationCountRule : HarnessCheckRule() {
    override val category: String = "kotlinTopLevelDeclarationCount"

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
        findings: List<PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> =
        buildList {
            val parameters = manifest[category]?.jsonObject?.get("parameters")?.jsonObject
            if (parameters != null) {
                val allowedDeclarations = ai.harness.gradle.HarnessCheck.stringArrayFrom(parameters, "allowedDeclarations")
                addAll(
                    PsiFindingRenderer.renderEach(
                        findings.filter { finding ->
                            finding.intDetail("count") != 1 ||
                                (finding.detail("firstKind") != "unknown" && finding.detail("firstKind") !in allowedDeclarations)
                        },
                        manifest,
                    ),
                )
            }
        }

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: KtPsiFactory?,
    ): List<PsiFinding> = buildList {
        val ktFile = PsiRuleSupport.parse(file, psiFactory)
        if (ktFile != null) {
            val declarations = ktFile.declarations
            val firstKind =
                if (declarations.isEmpty()) {
                    "unknown"
                } else {
                    when (declarations.first()::class.simpleName) {
                        "KtClass" -> "class"
                        "KtObjectDeclaration" -> "object"
                        "KtTypeAlias" -> "typealias"
                        else -> "unknown"
                    }
                }
            add(
                PsiFinding(
                    rule = category,
                    file = PsiRuleSupport.relativeFilePath(file, root),
                    line = 1,
                    details =
                        mapOf(
                            "count" to declarations.size.toString(),
                            "firstKind" to firstKind,
                        ),
                ),
            )
        }
    }
}
