package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink

/**
 * Rule that requires specified directories to exist.
 */
class RequireDirectoriesExistRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireDirectoriesExist"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireDirectoriesExist"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val paths = HarnessCheck.Companion.stringArrayFrom(parametersObj, "paths")
		return buildSet<Finding> {
			paths.forEach { path ->
				val p = root / path
				when {
					p.isSymbolicLink() -> add(Finding(Severity.ERROR, category, "symlink directory is not allowed: $path"))
					!p.isDirectory() -> add(Finding(severity, category, "missing directory: $path"))
				}
			}
		}.toList()
	}
}
