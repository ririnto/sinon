package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that forbids unchecked tasks in completed plans.
 */
class ForbidUncheckedTasksUnderRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "forbidUncheckedTasksUnder"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "forbidUncheckedTasksUnder"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val directory = HarnessCheck.Companion.stringFrom(parametersObj, "directory")
		val uncheckedTaskPattern = HarnessCheck.Companion.stringFrom(parametersObj, "uncheckedTaskPattern")

		val dirPath = root / directory
		if (!dirPath.isDirectory()) {
			return@buildSet
		}

		val (files, _) = HarnessCheck.Companion.walkSafe(root, dirPath)
		val pattern = try {
			uncheckedTaskPattern.toRegex()
		} catch (_: Exception) {
			return@buildSet
		}

		return buildSet<Finding> {
			files.filter { it.name.endsWith(".md") }.forEach { file ->
				if (pattern.containsMatchIn(file.readText())) {
					val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "completed plan has unchecked tasks: ${file.relativeTo(root)}"
					add(Finding(severity, category, msg))
				}
			}
		}.toList()
	}
}
