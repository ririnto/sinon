package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Rule that requires hooks to have generated marker and no packaging placeholders.
 */
class RequireHookGeneratedMarkerRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireHookGeneratedMarker"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): List<Finding> {
		val category = "requireHookGeneratedMarker"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
		val hooks = HarnessCheck.Companion.stringArrayFrom(parametersObj, "hooks")
		val markerTemplate = HarnessCheck.Companion.stringFrom(parametersObj, "markerTemplate")
		val placeholderForbidden = HarnessCheck.Companion.stringFrom(parametersObj, "placeholderForbidden")

		return buildSet<Finding> {
			hooks.forEach { hookPath ->
				val hook = root / hookPath
				if (hook.isRegularFile()) {
					val text = hook.readText()
					val marker = markerTemplate.replace("{name}", hook.name)
					if (!text.contains(marker)) {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingMarker").takeIf { it.isNotEmpty() } ?: "$hookPath must contain generated marker '$marker'"
						add(Finding(severity, category, msg))
					}
					if (text.contains(placeholderForbidden)) {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "placeholderPresent").takeIf { it.isNotEmpty() } ?: "$hookPath still contains packaging placeholder text"
						add(Finding(severity, category, msg))
					}
				}
			}
		}.toList()
	}
}
