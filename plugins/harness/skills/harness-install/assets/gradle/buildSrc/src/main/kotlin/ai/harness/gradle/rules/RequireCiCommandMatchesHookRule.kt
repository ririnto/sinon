package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that requires CI files to match hook validation commands.
 */
object RequireCiCommandMatchesHookRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireCiCommandMatchesHook"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireCiCommandMatchesHook"
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		return if (catObj == null || parametersObj == null || messagesObj == null) {
			emptyList()
		} else {
			val ciFiles = HarnessCheck.stringArrayFrom(parametersObj, "ciFiles")
			val referenceHookPath = HarnessCheck.stringFrom(parametersObj, "referenceHook")
			val referenceHook = root / referenceHookPath
			val command = if (referenceHook.isRegularFile()) {
				referenceHook.readText().lineSequence().firstOrNull { it.startsWith("# Harness validation command: ") }?.removePrefix("# Harness validation command: ")?.trim() ?: ""
			} else {
				""
			}
			if (command.isEmpty()) {
				emptyList()
			} else {
				ciFiles.mapNotNull { ciFile ->
					if ((root / ciFile).isRegularFile() && !(root / ciFile).readText().contains(command)) {
						Finding(HarnessCheck.severityOf(manifest, category), category, HarnessCheck.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$ciFile: CI command mismatch — expected $command")
					} else {
						null
					}
				}
			}
		}
	}
}
