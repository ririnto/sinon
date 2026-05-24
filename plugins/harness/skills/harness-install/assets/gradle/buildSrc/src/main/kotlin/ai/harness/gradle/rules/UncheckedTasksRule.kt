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
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that forbids unchecked tasks in completed plans.
 */
object UncheckedTasksRule : HarnessCheckRule() {
    override val category: String = "uncheckedTasks"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> = buildList {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        if (catObj != null && parametersObj != null && messagesObj != null) {
            val directory = HarnessCheck.stringFrom(parametersObj, "directory")
            val uncheckedTaskPattern = HarnessCheck.stringFrom(parametersObj, "uncheckedTaskPattern")
            val dirPath = root / directory
            if (dirPath.isDirectory()) {
                val (files, _) = HarnessCheck.walkSafe(root, dirPath)
                val pattern = uncheckedTaskPattern.toRegex()
                files
                    .filter { file ->
                        file.name.endsWith(".md") && pattern.containsMatchIn(file.readText())
                    }.forEach { file ->
                        add(
                            Finding(
                                HarnessCheck.severityOf(manifest, category),
                                category,
                                HarnessCheck.stringFrom(messagesObj, "default").takeIf { message -> message.isNotEmpty() }
                                    ?: "completed plan has unchecked tasks: ${file.relativeTo(root)}",
                            ),
                        )
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
