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
 * Rule that requires hooks to contain stage markers.
 */
object RequireHookStageRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireHookStage"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireHookStage"
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		val stagesObj = parametersObj?.get("stages")?.jsonObject
		val gradleStages = stagesObj?.get("gradle")?.jsonObject
		return if (catObj == null || parametersObj == null || messagesObj == null || stagesObj == null || gradleStages == null) {
			emptyList()
		} else {
			val markerTemplate = HarnessCheck.Companion.stringFrom(parametersObj, "markerTemplate")
			val preCommitStage = HarnessCheck.Companion.stringFrom(gradleStages, "pre-commit")
			val prePushStage = HarnessCheck.Companion.stringFrom(gradleStages, "pre-push")
			val preCommitHook = root / "docs/harness/git-hooks/pre-commit"
			val prePushHook = root / "docs/harness/git-hooks/pre-push"
			listOfNotNull(
				if (preCommitHook.isRegularFile()) {
					val marker = markerTemplate.replace("{stage}", preCommitStage)
					if (!preCommitHook.readText().contains(marker)) {
						Finding(HarnessCheck.Companion.severityOf(manifest, category), category, HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "pre-commit must contain stage marker '$marker'")
					} else null
				} else null,
				if (prePushHook.isRegularFile()) {
					val marker = markerTemplate.replace("{stage}", prePushStage)
					if (!prePushHook.readText().contains(marker)) {
						Finding(HarnessCheck.Companion.severityOf(manifest, category), category, HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "pre-push must contain stage marker '$marker'")
					} else null
				} else null
			)
		}
	}
}
