package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that requires .gitkeep or real files in empty directories.
 */
object RequireKeefileInEmptyDirectoriesRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireKeepfileInEmptyDirectories"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireKeepfileInEmptyDirectories"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		return if (catObj == null || parametersObj == null) {
			emptyList()
		} else {
			val directories = HarnessCheck.Companion.stringArrayFrom(parametersObj, "directories")
			directories.mapNotNull { dirPath ->
				val dir = root / dirPath
				if (!dir.isDirectory()) {
					null
				} else {
					val realFiles = try {
						dir.listDirectoryEntries().filter { it.name != ".gitkeep" }
					} catch (_: Exception) {
						emptyList()
					}
					if (realFiles.isEmpty() && !(root / "$dirPath/.gitkeep").exists()) {
						Finding(severity, category, "empty directory must keep placeholder or real files: $dirPath")
					} else {
						null
					}
				}
			}
		}
	}
}
