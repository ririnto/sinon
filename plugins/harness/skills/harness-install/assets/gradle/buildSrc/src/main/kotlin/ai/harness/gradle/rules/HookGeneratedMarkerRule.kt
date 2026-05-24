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
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Rule that requires hooks to have generated marker and no packaging placeholders.
 */
object HookGeneratedMarkerRule : HarnessCheckRule() {
    override val category: String = "hookGeneratedMarker"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        return when {
            catObj == null || parametersObj == null || messagesObj == null -> {
                emptyList()
            }

            else -> {
                val hooks = HarnessCheck.stringArrayFrom(parametersObj, "hooks")
                val markerTemplate = HarnessCheck.stringFrom(parametersObj, "markerTemplate")
                val placeholderForbidden = HarnessCheck.stringFrom(parametersObj, "placeholderForbidden")
                hooks
                    .filter { hookPath ->
                        (root / hookPath).isRegularFile()
                    }.flatMap { hookPath ->
                        val hook = root / hookPath
                        val text = hook.readText()
                        val marker = markerTemplate.replace("{name}", hook.name)
                        buildList {
                            if (!text.contains(marker)) {
                                add(
                                    Finding(
                                        HarnessCheck.severityOf(manifest, category),
                                        category,
                                        HarnessCheck.stringFrom(messagesObj, "missingMarker").takeIf { message ->
                                            message.isNotEmpty()
                                        }
                                            ?: "$hookPath must contain generated marker '$marker'",
                                    ),
                                )
                            }
                            if (text.contains(placeholderForbidden)) {
                                add(
                                    Finding(
                                        HarnessCheck.severityOf(manifest, category),
                                        category,
                                        HarnessCheck.stringFrom(messagesObj, "placeholderPresent").takeIf { message ->
                                            message.isNotEmpty()
                                        }
                                            ?: "$hookPath still contains packaging placeholder text",
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
