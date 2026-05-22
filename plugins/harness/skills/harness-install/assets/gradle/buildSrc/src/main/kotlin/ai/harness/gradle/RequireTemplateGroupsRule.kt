package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink

/**
 * Rule that requires template groups to exist as directories.
 */
class RequireTemplateGroupsRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireTemplateGroups"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): List<Finding> {
		val category = "requireTemplateGroups"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val targetRoot = HarnessCheck.Companion.stringFrom(parametersObj, "targetRoot")
		val groups = HarnessCheck.Companion.stringArrayFrom(parametersObj, "groups")
		return buildSet<Finding> {
			groups.forEach { group ->
				val p = root / "$targetRoot/$group"
				when {
					p.isSymbolicLink() -> add(Finding(Severity.ERROR, category, "symlink directory is not allowed: $targetRoot/$group"))
					!p.isDirectory() -> add(Finding(severity, category, "missing template group: $targetRoot/$group"))
				}
			}
		}.toList()
	}
}
