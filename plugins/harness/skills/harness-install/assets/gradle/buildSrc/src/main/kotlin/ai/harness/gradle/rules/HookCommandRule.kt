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
 * Rule that requires hooks to declare and run validation commands.
 */
object HookCommandRule : HarnessCheckRule() {
    override val category: String = "hookCommand"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val severity = HarnessCheck.severityOf(manifest, category)
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        return when {
            catObj == null || parametersObj == null || messagesObj == null -> {
                emptyList()
            }

            else -> {
                val allowedCmdObj = parametersObj["allowedCommands"]?.jsonObject
                val allowedCmds = HarnessCheck.stringArrayFrom(allowedCmdObj, "gradle")
                val allowedPreCommitCmdObj = parametersObj["allowedPreCommitCommands"]?.jsonObject
                val allowedPreCommitCmds = HarnessCheck.stringArrayFrom(allowedPreCommitCmdObj, "gradle")
                val prePushPath = HarnessCheck.stringFrom(parametersObj, "prePushHook")
                val preCommitPath = HarnessCheck.stringFrom(parametersObj, "preCommitHook")
                val prePushHook = root / prePushPath
                val preCommitHook = root / preCommitPath
                val prePushFindings =
                    if (prePushHook.isRegularFile()) {
                        val text = prePushHook.readText()
                        val command =
                            text
                                .lineSequence()
                                .firstOrNull { line ->
                                    line.startsWith("# Harness validation command: ")
                                }?.removePrefix("# Harness validation command: ")
                                ?.trim()
                                ?: ""
                        buildList {
                            if (command.isEmpty()) {
                                add(
                                    Finding(
                                        severity,
                                        category,
                                        HarnessCheck.stringFrom(messagesObj, "missingDeclaration").takeIf { message ->
                                            message.isNotEmpty()
                                        } ?: "pre-push hook must declare Harness validation command",
                                    ),
                                )
                            }
                            if (command.isNotEmpty() && command !in allowedCmds) {
                                add(
                                    Finding(
                                        severity,
                                        category,
                                        HarnessCheck.stringFrom(messagesObj, "unsupportedCommand").takeIf { message ->
                                            message.isNotEmpty()
                                        } ?: "pre-push hook declares unsupported validation command: $command",
                                    ),
                                )
                            }
                            if (command.isNotEmpty() && !text.contains(command)) {
                                add(
                                    Finding(
                                        severity,
                                        category,
                                        HarnessCheck.stringFrom(messagesObj, "commandNotRun").takeIf { message ->
                                            message.isNotEmpty()
                                        } ?: "pre-push hook must run the declared validation command",
                                    ),
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }
                val preCommitFindings =
                    if (preCommitHook.isRegularFile()) {
                        val text = preCommitHook.readText()
                        val command =
                            text
                                .lineSequence()
                                .firstOrNull { line ->
                                    line.startsWith("# Harness validation command: ")
                                }?.removePrefix("# Harness validation command: ")
                                ?.trim()
                                ?: ""
                        buildList {
                            if (command.isEmpty() && allowedPreCommitCmds.isNotEmpty()) {
                                add(
                                    Finding(
                                        severity,
                                        category,
                                        HarnessCheck.stringFrom(messagesObj, "missingDeclaration").takeIf { message ->
                                            message.isNotEmpty()
                                        } ?: "pre-commit hook must declare validation command",
                                    ),
                                )
                            }
                            if (command.isNotEmpty() && command !in allowedPreCommitCmds) {
                                add(
                                    Finding(
                                        severity,
                                        category,
                                        HarnessCheck.stringFrom(messagesObj, "unsupportedCommand").takeIf { message ->
                                            message.isNotEmpty()
                                        } ?: "pre-commit hook declares unsupported validation command: $command",
                                    ),
                                )
                            }
                            if (command.isNotEmpty() && !text.contains(command)) {
                                add(
                                    Finding(
                                        severity,
                                        category,
                                        HarnessCheck.stringFrom(messagesObj, "commandNotRun").takeIf { message ->
                                            message.isNotEmpty()
                                        } ?: "pre-commit hook must run the declared validation command",
                                    ),
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }
                prePushFindings + preCommitFindings
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
