package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Rule that requires hooks to contain stage markers.
 */
class RequireHookStageRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireHookStage"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "requireHookStage"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val markerTemplate = HarnessCheck.Companion.stringFrom(parametersObj, "markerTemplate")
		val stagesObj = parametersObj["stages"]?.jsonObject ?: return@buildSet
		val gradleStages = stagesObj["gradle"]?.jsonObject ?: return@buildSet
		val preCommitStage = HarnessCheck.Companion.stringFrom(gradleStages, "pre-commit")
		val prePushStage = HarnessCheck.Companion.stringFrom(gradleStages, "pre-push")

		return buildSet<Finding> {
			val preCommitHook = root / "docs/harness/git-hooks/pre-commit"
			if (preCommitHook.isRegularFile()) {
				val marker = markerTemplate.replace("{stage}", preCommitStage)
				if (!preCommitHook.readText().contains(marker)) {
					val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "pre-commit must contain stage marker '$marker'"
					add(Finding(severity, category, msg))
				}
			}

			val prePushHook = root / "docs/harness/git-hooks/pre-push"
			if (prePushHook.isRegularFile()) {
				val marker = markerTemplate.replace("{stage}", prePushStage)
				if (!prePushHook.readText().contains(marker)) {
					val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "pre-push must contain stage marker '$marker'"
					add(Finding(severity, category, msg))
				}
			}
		}.toList()
	}
}
