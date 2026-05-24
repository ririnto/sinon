package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that requires executable scripts under certain directories to use /usr/bin/env shebang.
 */
object EnvShebangUsageRule : HarnessCheckRule() {
    override val category: String = "envShebangUsage"
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
                val directories = HarnessCheck.stringArrayFrom(parametersObj, "directories")
                val expectedPrefix = HarnessCheck.stringFrom(parametersObj, "expectedPrefix")
                directories
                    .filter { dirPath ->
                        (root / dirPath).isDirectory()
                    }.flatMap { dirPath ->
                        val dir = root / dirPath
                        val (files, _) = HarnessCheck.walkSafe(root, dir)
                        files
                            .filter { file -> file.isExecutable() }
                            .filter { file ->
                                val firstLine = file.readText().lineSequence().firstOrNull() ?: ""
                                firstLine.startsWith("#!") && !firstLine.startsWith(expectedPrefix)
                            }.map { file ->
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    HarnessCheck.stringFrom(messagesObj, "default").takeIf { message ->
                                        message.isNotEmpty()
                                    }
                                        ?: "executable script should use /usr/bin/env shebang: ${file.relativeTo(
                                            root,
                                        )}",
                                )
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
