package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that requires hooks to have generated marker and no packaging placeholders.
 */
object RequireHookGeneratedMarkerRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireHookGeneratedMarker"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireHookGeneratedMarker"
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		return if (catObj == null || parametersObj == null || messagesObj == null) {
			emptyList()
		} else {
			val hooks = HarnessCheck.stringArrayFrom(parametersObj, "hooks")
			val markerTemplate = HarnessCheck.stringFrom(parametersObj, "markerTemplate")
			val placeholderForbidden = HarnessCheck.stringFrom(parametersObj, "placeholderForbidden")
			hooks.flatMap { hookPath ->
				val hook = root / hookPath
				if (!hook.isRegularFile()) {
					emptyList()
				} else {
					val text = hook.readText()
					val marker = markerTemplate.replace("{name}", hook.name)
					listOfNotNull(
						if (!text.contains(marker)) {
							Finding(HarnessCheck.severityOf(manifest, category), category, HarnessCheck.stringFrom(messagesObj, "missingMarker").takeIf { it.isNotEmpty() } ?: "$hookPath must contain generated marker '$marker'")
						} else null,
						if (text.contains(placeholderForbidden)) {
							Finding(HarnessCheck.severityOf(manifest, category), category, HarnessCheck.stringFrom(messagesObj, "placeholderPresent").takeIf { it.isNotEmpty() } ?: "$hookPath still contains packaging placeholder text")
						} else null
					)
				}
			}
		}
	}
}
