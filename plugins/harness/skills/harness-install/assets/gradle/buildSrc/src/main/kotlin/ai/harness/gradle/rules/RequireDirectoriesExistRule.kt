package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults
import ai.harness.gradle.Severity

/**
 * Rule that requires specified directories to exist.
 */
object RequireDirectoriesExistRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireDirectoriesExist"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireDirectoriesExist"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		return if (catObj == null || parametersObj == null) {
			emptyList()
		} else {
			val paths = HarnessCheck.Companion.stringArrayFrom(parametersObj, "paths")
			paths.mapNotNull { path ->
				val p = root / path
				when {
					p.isSymbolicLink() -> Finding(Severity.ERROR, category, "symlink directory is not allowed: $path")
					!p.isDirectory() -> Finding(severity, category, "missing directory: $path")
					else -> null
				}
			}
		}
	}
}
