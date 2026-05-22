package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

/**
 * Rule that requires documentation files to contain specified headings.
 */
class RequireDocHeadingsRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireDocHeadings"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "requireDocHeadings"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val sourceCategory = HarnessCheck.Companion.stringFrom(parametersObj, "sourceFilesFromCategory")
		val sourceFiles = HarnessCheck.Companion.stringArrayFrom(manifest[sourceCategory]?.jsonObject?.get("parameters")?.jsonObject, "paths")
		val sourceFilterObj = parametersObj["sourceFilter"]?.jsonObject
		val prefix = HarnessCheck.Companion.stringFrom(sourceFilterObj, "prefix")
		val suffix = HarnessCheck.Companion.stringFrom(sourceFilterObj, "suffix")
		val headings = HarnessCheck.Companion.stringArrayFrom(parametersObj, "headings")

		val filtered = sourceFiles.filter { it.startsWith(prefix) && it.endsWith(suffix) }
		return buildSet<Finding> {
			filtered.forEach { docPath ->
				val content = HarnessCheck.Companion.readSafe(root, docPath)
				headings.forEach { heading ->
					if (!content.contains(heading)) {
						add(Finding(severity, category, "doc missing $heading: $docPath"))
					}
				}
			}
		}.toList()
	}
}
