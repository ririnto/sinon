package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that requires documentation files to contain specified content.
 */
object RequireDocContentRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireDocContent"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireDocContent"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val checks = parametersObj?.get("checks")?.jsonArray
		return if (catObj == null || parametersObj == null || checks == null) {
			emptyList()
		} else {
			checks.mapNotNull { checkElem ->
				val checkObj = checkElem.jsonObject
				val files = HarnessCheck.Companion.stringArrayFrom(checkObj, "files")
				val containsAll = HarnessCheck.Companion.stringArrayFrom(checkObj, "containsAll")
				val failureMessage = HarnessCheck.Companion.stringFrom(checkObj, "failureMessage")
				val content = files.map { HarnessCheck.Companion.readSafe(root, it) }.joinToString("\n")
				if (!containsAll.all { content.contains(it) }) {
					Finding(severity, category, failureMessage)
				} else {
					null
				}
			}
		}
	}
}
