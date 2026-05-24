package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/**
 * Rule that requires documentation files to contain specified headings.
 */
object DocHeadingsRule : HarnessCheckRule() {
    override val category: String = "docHeadings"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        return when {
            catObj == null || parametersObj == null -> {
                emptyList()
            }

            else -> {
                val sourceCategory = HarnessCheck.stringFrom(parametersObj, "sourceFilesFromCategory")
                val sourceFiles =
                    HarnessCheck.stringArrayFrom(
                        manifest[sourceCategory]?.jsonObject?.get("parameters")?.jsonObject,
                        "paths",
                    )
                val sourceFilterObj = parametersObj["sourceFilter"]?.jsonObject
                val prefix = HarnessCheck.stringFrom(sourceFilterObj, "prefix")
                val suffix = HarnessCheck.stringFrom(sourceFilterObj, "suffix")
                val headings = HarnessCheck.stringArrayFrom(parametersObj, "headings")
                sourceFiles
                    .filter { sourceFile -> sourceFile.startsWith(prefix) }
                    .filter { sourceFile -> sourceFile.endsWith(suffix) }
                    .flatMap { docPath ->
                        headings
                            .filter { heading ->
                                !HarnessCheck.readSafe(root, docPath).contains(heading)
                            }.map { heading ->
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    "doc missing $heading: $docPath",
                                )
                            }
                    }
            }
        }
    }

    override fun renderPsiFindings(
        findings: List<ai.harness.gradle.PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> = emptyList()

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: org.jetbrains.kotlin.psi.KtPsiFactory?,
    ): List<ai.harness.gradle.PsiFinding> = emptyList()
}
