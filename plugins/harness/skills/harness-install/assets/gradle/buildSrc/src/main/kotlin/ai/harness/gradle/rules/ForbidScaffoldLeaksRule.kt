package ai.harness.gradle.rules

import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessPsiResults
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that forbids scaffold placeholder patterns in active assets.
 */
object ForbidScaffoldLeaksRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "forbidScaffoldLeaks"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "forbidScaffoldLeaks"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        val scopeObj = parametersObj?.get("scope")?.jsonObject
        return if (catObj == null || parametersObj == null || messagesObj == null || scopeObj == null) {
            emptyList()
        } else {
            val bases = HarnessCheck.stringArrayFrom(scopeObj, "bases")
            val excludedSubtrees = HarnessCheck.stringArrayFrom(scopeObj, "excludedSubtrees")
            val extensions = HarnessCheck.stringArrayFrom(scopeObj, "extensions")
            val patterns =
                parametersObj["patterns"]
                    ?.jsonArray
                    ?.filter { patternElem ->
                        val obj = patternElem.jsonObject
                        val pattern = HarnessCheck.stringFrom(obj, "pattern")
                        val label = HarnessCheck.stringFrom(obj, "label")
                        pattern.isNotEmpty() && label.isNotEmpty()
                    }?.map { patternElem ->
                        val obj = patternElem.jsonObject
                        val pattern = HarnessCheck.stringFrom(obj, "pattern")
                        val label = HarnessCheck.stringFrom(obj, "label")
                        pattern to label
                    } ?: emptyList()
            val excludedPaths = excludedSubtrees.map { excludedPath -> root / excludedPath }
            /**
             * Filter out patterns that don't compile; invalid regexes silently fail without raising findings.
             */
            val regexes =
                patterns
                    .filter { (pattern, label) ->
                        try {
                            pattern.toRegex()
                            true
                        } catch (e: Exception) {
                            val skipped = e.localizedMessage
                            false
                        }
                    }.map { (pattern, label) ->
                        pattern.toRegex() to label
                    }
            bases.flatMap { basePath ->
                val (files, _) = HarnessCheck.walkSafe(root, root / basePath)
                files
                    .filter { file ->
                        file.extension in extensions && excludedPaths.none { excludedPath -> file.toString().startsWith(excludedPath.toString()) }
                    }.flatMap { file ->
                        regexes
                            .filter { (regex, label) ->
                                regex.containsMatchIn(file.readText())
                            }.map { (regex, label) ->
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    HarnessCheck.stringFrom(messagesObj, "default").takeIf { message -> message.isNotEmpty() }
                                        ?: "$label in active asset: ${file.relativeTo(root)}",
                                )
                            }
                    }
            }
        }
    }
}
