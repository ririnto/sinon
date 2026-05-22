package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink

/**
 * Rule that requires specified files to exist.
 */
class RequireFilesExistRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireFilesExist"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireFilesExist"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val paths = HarnessCheck.Companion.stringArrayFrom(parametersObj, "paths")
		return buildSet<Finding> {
			paths.forEach { path ->
				val p = root / path
				when {
					p.isSymbolicLink() && !HarnessCheck.Companion.isAllowedRootContractSymlink(root, p) -> add(Finding(Severity.ERROR, category, "symlink file is not allowed: $path"))
					!p.isRegularFile() -> add(Finding(severity, category, "missing file: $path"))
				}
			}
		}.toList()
	}
}
