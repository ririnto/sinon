package ai.harness.gradle.rules

import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.relativeTo

/**
 * Rule that forbids implicit lambda parameter 'it'.
 */
object ForbidImplicitLambdaItRule : HarnessCheckRule {
    /**
     * PSI detection result for a single implicit lambda 'it' parameter.
     */
    @Serializable
    data class Result(
        val file: String,
        val line: Int,
    )

    override fun applies(manifest: JsonObject): Boolean {
        val category = "forbidImplicitLambdaIt"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "forbidImplicitLambdaIt"
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
            val results = psiResults?.implicitLambdaIt ?: emptyList()
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
                            results.filter { it.file == file.name }.map { hit ->
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    HarnessCheck.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() }
                                        ?: "${file.relativeTo(
                                            root,
                                        )}:${hit.line}: implicit lambda parameter 'it' is forbidden",
                                )
                            }
                        }
                }
        }
    }
}
