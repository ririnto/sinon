package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

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

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireDocHeadings"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val sourceCategory = HarnessCheck.Companion.stringFrom(parametersObj, "sourceFilesFromCategory")
		val sourceFiles = HarnessCheck.Companion.stringArrayFrom(manifest[sourceCategory]?.jsonObject?.get("parameters")?.jsonObject, "paths")
		val sourceFilterObj = parametersObj["sourceFilter"]?.jsonObject
		val prefix = HarnessCheck.Companion.stringFrom(sourceFilterObj, "prefix")
		val suffix = HarnessCheck.Companion.stringFrom(sourceFilterObj, "suffix")
		val headings = HarnessCheck.Companion.stringArrayFrom(parametersObj, "headings")
		val filtered = sourceFiles.filter { it.startsWith(prefix) && it.endsWith(suffix) }
		return filtered.flatMap { docPath ->
			val content = HarnessCheck.Companion.readSafe(root, docPath)
			headings.mapNotNull { heading ->
				if (!content.contains(heading)) {
					Finding(severity, category, "doc missing $heading: $docPath")
				} else {
					null
				}
			}
		}
	}
}
