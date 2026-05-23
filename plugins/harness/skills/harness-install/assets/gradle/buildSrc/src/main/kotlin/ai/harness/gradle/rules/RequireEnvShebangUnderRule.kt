package ai.harness.gradle.rules

import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessPsiResults
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
object RequireEnvShebangUnderRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "requireEnvShebangUnder"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "requireEnvShebangUnder"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        return if (catObj == null || parametersObj == null || messagesObj == null) {
            emptyList()
        } else {
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
                                HarnessCheck.stringFrom(messagesObj, "default").takeIf { message -> message.isNotEmpty() }
                                    ?: "executable script should use /usr/bin/env shebang: ${file.relativeTo(root)}",
                            )
                        }
                }
        }
    }
}
