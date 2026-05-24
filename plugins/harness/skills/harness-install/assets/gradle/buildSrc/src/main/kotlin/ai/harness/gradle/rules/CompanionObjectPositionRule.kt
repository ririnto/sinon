package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates companion-object position findings to source AST/PSI analysis.
 */
object CompanionObjectPositionRule : HarnessCheckRule() {
    override val category: String = "companionObjectPosition"

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
    ): Collection<Finding> {
        val position =
            manifest[category]
                ?.jsonObject
                ?.get("parameters")
                ?.jsonObject
                ?.get("position")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { value -> value == "bottom" }
                ?: "top"
        return PsiFindingRenderer.renderEach(
            findings.filter { finding ->
                when (position) {
                    "bottom" -> finding.intDetail("position") != finding.intDetail("lastPosition")
                    else -> finding.intDetail("position") != 0
                }
            },
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
                    override fun visitClass(klass: KtClass) {
                        super.visitClass(klass)
                        val body = klass.getBody()
                        if (body != null) {
                            val declarations = body.declarations.filter { declaration -> declaration !is KtEnumEntry }
                            declarations.forEachIndexed { index, declaration ->
                                if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                                    add(
                                        PsiFinding(
                                            rule = category,
                                            file = PsiRuleSupport.relativeFilePath(file, root),
                                            line = PsiRuleSupport.lineOf(ktFile, declaration.node?.startOffset),
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
                },
            )
        }
    }
}
