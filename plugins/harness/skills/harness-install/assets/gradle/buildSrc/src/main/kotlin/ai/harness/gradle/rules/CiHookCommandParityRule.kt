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
 * Rule that requires CI files to match hook validation commands.
 */
object CiHookCommandParityRule : HarnessCheckRule() {
    override val category: String = "ciHookCommandParity"
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
                val ciFiles = HarnessCheck.stringArrayFrom(parametersObj, "ciFiles")
                val referenceHookPath = HarnessCheck.stringFrom(parametersObj, "referenceHook")
                val referenceHook = root / referenceHookPath
                val command =
                    if (referenceHook.isRegularFile()) {
                        referenceHook
                            .readText()
                            .lineSequence()
                            .firstOrNull { hookLine ->
                                hookLine.startsWith("# Harness validation command: ")
                            }?.removePrefix("# Harness validation command: ")
                            ?.trim()
                            ?: ""
                    } else {
                        ""
                    }
                when {
                    command.isEmpty() -> {
                        emptyList()
                    }

                    else -> {
                        ciFiles
                            .filter { ciFile -> (root / ciFile).isRegularFile() }
                            .filter { ciFile -> !(root / ciFile).readText().contains(command) }
                            .map { ciFile ->
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    HarnessCheck.stringFrom(messagesObj, "default").takeIf { message ->
                                        message.isNotEmpty()
                                    }
                                        ?: "$ciFile: CI command mismatch — expected $command",
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
