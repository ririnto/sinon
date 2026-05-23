package ai.harness.gradle.rules

import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessPsiResults
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/**
 * Rule that requires documentation files to contain specified headings.
 */
object RequireDocHeadingsRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "requireDocHeadings"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "requireDocHeadings"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        return if (catObj == null || parametersObj == null) {
            emptyList()
        } else {
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
            sourceFiles.filter { sourceFile -> sourceFile.startsWith(prefix) && sourceFile.endsWith(suffix) }.flatMap { docPath ->
                headings
                    .filter { heading ->
                        !HarnessCheck.readSafe(root, docPath).contains(heading)
                    }.map { heading ->
                        Finding(HarnessCheck.severityOf(manifest, category), category, "doc missing $heading: $docPath")
                    }
            }
        }
    }
}
