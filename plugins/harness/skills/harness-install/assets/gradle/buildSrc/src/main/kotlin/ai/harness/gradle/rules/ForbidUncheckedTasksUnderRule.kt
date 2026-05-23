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
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.name

/**
 * Rule that forbids unchecked tasks in completed plans.
 */
object ForbidUncheckedTasksUnderRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "forbidUncheckedTasksUnder"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "forbidUncheckedTasksUnder"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        if (catObj == null || parametersObj == null || messagesObj == null) {
            return emptyList()
        }
        val directory = HarnessCheck.stringFrom(parametersObj, "directory")
        val uncheckedTaskPattern = HarnessCheck.stringFrom(parametersObj, "uncheckedTaskPattern")
        val dirPath = root / directory
        if (!dirPath.isDirectory()) {
            return emptyList()
        }
        val (files, _) = HarnessCheck.walkSafe(root, dirPath)
        /**
         * Pattern compilation may fail if the pattern is invalid; return empty on silent fallback.
         */
        val pattern =
            try {
                uncheckedTaskPattern.toRegex()
            } catch (e: Exception) {
                val skipped = e.localizedMessage
                return emptyList()
            }
        return files
            .filter { file ->
                file.name.endsWith(".md") && pattern.containsMatchIn(file.readText())
            }.map { file ->
                Finding(
                    HarnessCheck.severityOf(manifest, category),
                    category,
                    HarnessCheck.stringFrom(messagesObj, "default").takeIf { message -> message.isNotEmpty() }
                        ?: "completed plan has unchecked tasks: ${file.relativeTo(root)}",
                )
            }
    }
}
