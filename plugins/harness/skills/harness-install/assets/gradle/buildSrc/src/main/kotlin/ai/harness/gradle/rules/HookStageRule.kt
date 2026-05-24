package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Rule that requires hooks to contain stage markers.
 */
object HookStageRule : HarnessCheckRule() {
    override val category: String = "hookStage"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        val stagesObj = parametersObj?.get("stages")?.jsonObject
        val gradleStages = stagesObj?.get("gradle")?.jsonObject
        return when {
            catObj == null || parametersObj == null || messagesObj == null || stagesObj == null ||
                gradleStages == null -> {
                emptyList()
            }

            else -> {
                val markerTemplate = HarnessCheck.stringFrom(parametersObj, "markerTemplate")
                val preCommitStage = HarnessCheck.stringFrom(gradleStages, "pre-commit")
                val prePushStage = HarnessCheck.stringFrom(gradleStages, "pre-push")
                val preCommitHook = root / "docs/harness/git-hooks/pre-commit"
                val prePushHook = root / "docs/harness/git-hooks/pre-push"
                buildList {
                    if (preCommitHook.isRegularFile()) {
                        val marker = markerTemplate.replace("{stage}", preCommitStage)
                        if (!preCommitHook.readText().contains(marker)) {
                            add(
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    HarnessCheck.stringFrom(messagesObj, "default").takeIf { message ->
                                        message.isNotEmpty()
                                    }
                                        ?: "pre-commit must contain stage marker '$marker'",
                                ),
                            )
                        }
                    }
                    if (prePushHook.isRegularFile()) {
                        val marker = markerTemplate.replace("{stage}", prePushStage)
                        if (!prePushHook.readText().contains(marker)) {
                            add(
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    HarnessCheck.stringFrom(messagesObj, "default").takeIf { message ->
                                        message.isNotEmpty()
                                    }
                                        ?: "pre-push must contain stage marker '$marker'",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun renderPsiFindings(
        findings: List<ai.harness.gradle.PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> = emptyList()

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: org.jetbrains.kotlin.psi.KtPsiFactory?,
    ): List<ai.harness.gradle.PsiFinding> = emptyList()
}
