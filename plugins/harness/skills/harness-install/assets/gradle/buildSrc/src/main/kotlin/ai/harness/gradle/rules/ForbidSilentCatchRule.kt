package ai.harness.gradle.rules

import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessPsiResults
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * Rule that forbids catch blocks that silently ignore exceptions.
 */
object ForbidSilentCatchRule : HarnessCheckRule {
    /**
     * PSI detection result for a silent catch block.
     */
    @Serializable
    data class Result(
        val file: String,
        val line: Int,
    )

    override fun applies(manifest: JsonObject): Boolean {
        val category = "forbidSilentCatch"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "forbidSilentCatch"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        val sourceRootsPerStack = parametersObj?.get("sourceRootsPerStack")?.jsonObject
        val extensionsPerStack = parametersObj?.get("extensionsPerStack")?.jsonObject
        return if (catObj == null || parametersObj == null || messagesObj == null || sourceRootsPerStack == null ||
            extensionsPerStack == null
        ) {
            emptyList()
        } else {
            val kotlinDirs = HarnessCheck.stringArrayFrom(sourceRootsPerStack, "kotlin")
            val kotlinExts = HarnessCheck.stringArrayFrom(extensionsPerStack, "kotlin")
            val results = psiResults?.silentCatches ?: emptyList()
            kotlinDirs
                .filter { dirPattern ->
                    (root / dirPattern).exists()
                }.flatMap { dirPattern ->
                    val dir = root / dirPattern
                    val (files, _) = HarnessCheck.walkSafe(root, dir)
                    files
                        .filter { file ->
                            file.extension in kotlinExts
                        }.flatMap { file ->
                            results.filter { result -> result.file == file.relativeTo(root).toString().replace("\\", "/") }.map { hit ->
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    HarnessCheck.stringFrom(messagesObj, "default")
                                        .replace("{file}", file.relativeTo(root).toString())
                                        .replace("{line}", hit.line.toString())
                                        .takeIf { message -> message.isNotEmpty() }
                                        ?: "${file.relativeTo(
                                            root,
                                        )}:${hit.line}: catch block must handle the exception; log, rethrow, or convert to a Finding",
                                )
                            }
                        }
                }
        }
    }
}
