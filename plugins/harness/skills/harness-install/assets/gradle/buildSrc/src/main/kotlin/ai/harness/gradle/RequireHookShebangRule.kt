package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import java.io.File

/**
 * Rule that requires hooks to have correct shebang.
 */
class RequireHookShebangRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireHookShebang"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireHookShebang"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
		val hooks = HarnessCheck.Companion.stringArrayFrom(parametersObj, "hooks")
		val expectedShebang = HarnessCheck.Companion.stringFrom(parametersObj, "expectedShebang")

		return buildSet<Finding> {
			hooks.forEach { hookPath ->
				val hook = root / hookPath
				if (hook.isRegularFile()) {
					val first = hook.toFile().readLines().firstOrNull() ?: ""
					if (first != expectedShebang) {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$hookPath must start with $expectedShebang"
						add(Finding(severity, category, msg))
					}
				}
			}
		}.toList()
	}
}
