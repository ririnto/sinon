package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that requires hooks to be executable.
 */
object RequireHookExecutableRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireHookExecutable"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireHookExecutable"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		return if (catObj == null || parametersObj == null || messagesObj == null) {
			emptyList()
		} else {
			val hooks = HarnessCheck.Companion.stringArrayFrom(parametersObj, "hooks")
			hooks.mapNotNull { hookPath ->
				val hook = root / hookPath
				if (hook.isRegularFile() && !hook.isExecutable()) {
					val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$hookPath must be executable"
					Finding(severity, category, msg)
				} else {
					null
				}
			}
		}
	}
}
