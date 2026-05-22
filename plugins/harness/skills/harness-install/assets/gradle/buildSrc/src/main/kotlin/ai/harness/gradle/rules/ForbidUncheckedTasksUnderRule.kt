package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that forbids unchecked tasks in completed plans.
 */
object ForbidUncheckedTasksUnderRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "forbidUncheckedTasksUnder"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "forbidUncheckedTasksUnder"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		if (catObj == null || parametersObj == null || messagesObj == null) {
			return emptyList()
		}
		val directory = HarnessCheck.Companion.stringFrom(parametersObj, "directory")
		val uncheckedTaskPattern = HarnessCheck.Companion.stringFrom(parametersObj, "uncheckedTaskPattern")
		val dirPath = root / directory
		if (!dirPath.isDirectory()) {
			return emptyList()
		}
		val (files, _) = HarnessCheck.Companion.walkSafe(root, dirPath)
		val pattern = try {
			uncheckedTaskPattern.toRegex()
		} catch (_: Exception) {
			return emptyList()
		}
		return files.filter { it.name.endsWith(".md") }.mapNotNull { file ->
			if (pattern.containsMatchIn(file.readText())) {
				val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "completed plan has unchecked tasks: ${file.relativeTo(root)}"
				Finding(severity, category, msg)
			} else {
				null
			}
		}
	}
}
