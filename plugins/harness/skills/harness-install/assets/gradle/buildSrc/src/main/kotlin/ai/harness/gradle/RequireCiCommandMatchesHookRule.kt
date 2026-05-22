package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Rule that requires CI files to match hook validation commands.
 */
class RequireCiCommandMatchesHookRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireCiCommandMatchesHook"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireCiCommandMatchesHook"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
		val ciFiles = HarnessCheck.Companion.stringArrayFrom(parametersObj, "ciFiles")
		val referenceHookPath = HarnessCheck.Companion.stringFrom(parametersObj, "referenceHook")

		val referenceHook = root / referenceHookPath
		val command = if (referenceHook.isRegularFile()) {
			referenceHook.readText().lineSequence().firstOrNull { it.startsWith("# Harness validation command: ") }?.removePrefix("# Harness validation command: ")?.trim() ?: ""
		} else {
			""
		}

		if (command.isEmpty()) {
			return emptyList()
		}

		return buildSet<Finding> {
			ciFiles.forEach { ciFile ->
				val ciPath = root / ciFile
				if (ciPath.isRegularFile() && !ciPath.readText().contains(command)) {
					val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$ciFile: CI command mismatch — expected $command"
					add(Finding(severity, category, msg))
				}
			}
		}.toList()
	}
}
