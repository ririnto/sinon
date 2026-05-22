package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

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

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "requireKeepfileInEmptyDirectories"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val directories = HarnessCheck.Companion.stringArrayFrom(parametersObj, "directories")
		directories.forEach { dirPath ->
			val dir = root / dirPath
			if (!dir.isDirectory()) {
				return@forEach
			}
			val realFiles = try {
				dir.listDirectoryEntries().filter { it.name != ".gitkeep" }
			} catch (_: Exception) {
				emptyList()
			}
			if (realFiles.isEmpty() && !(root / "$dirPath/.gitkeep").exists()) {
				add(Finding(severity, category, "empty directory must keep placeholder or real files: $dirPath"))
			}
		}
	}.toList()
}
