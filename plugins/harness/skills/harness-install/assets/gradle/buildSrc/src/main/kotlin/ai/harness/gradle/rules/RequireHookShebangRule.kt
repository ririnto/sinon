package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import java.io.File
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that requires hooks to have correct shebang.
 */
object RequireHookShebangRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireHookShebang"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "requireHookShebang"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val hooks = HarnessCheck.Companion.stringArrayFrom(parametersObj, "hooks")
		val expectedShebang = HarnessCheck.Companion.stringFrom(parametersObj, "expectedShebang")
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
