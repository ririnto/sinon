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
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Rule that requires skills to have proper frontmatter.
 */
object RequireSkillFrontmatterRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "requireSkillFrontmatter"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "requireSkillFrontmatter"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        return if (catObj == null || parametersObj == null || messagesObj == null) {
            emptyList()
        } else {
            val rootDirectory = HarnessCheck.stringFrom(parametersObj, "rootDirectory")
            val filename = HarnessCheck.stringFrom(parametersObj, "filename")
            val dirPath = root / rootDirectory
            if (!dirPath.isDirectory()) {
                listOf(
                    Finding(
                        HarnessCheck.severityOf(manifest, category),
                        category,
                        HarnessCheck.stringFrom(messagesObj, "missingDirectory").takeIf { message -> message.isNotEmpty() }
                            ?: ".claude/skills must contain at least one SKILL.md",
                    ),
                )
            } else {
                /**
                 * Tree walk may fail due to permission or I/O issues; return empty on silent fallback.
                 */
                val files =
                    try {
                        dirPath.walk().filter { file -> file.isRegularFile() && file.name == filename }.toList()
                    } catch (e: Exception) {
                        val skipped = e.localizedMessage
                        emptyList()
                    }
                if (files.isEmpty()) {
                    listOf(
                        Finding(
                            HarnessCheck.severityOf(manifest, category),
                            category,
                            HarnessCheck.stringFrom(messagesObj, "missingSkill").takeIf { message -> message.isNotEmpty() }
                                ?: ".claude/skills must contain at least one SKILL.md",
                        ),
                    )
                } else {
                    files.flatMap { file ->
                        val text = file.readText()
                        if (!text.startsWith("---")) {
                            listOf(
                                Finding(
                                    HarnessCheck.severityOf(manifest, category),
                                    category,
                                    HarnessCheck
                                        .stringFrom(
                                            messagesObj,
                                            "missingFrontmatter",
                                        ).takeIf { message -> message.isNotEmpty() }
                                        ?: "skill missing frontmatter: ${file.relativeTo(root)}",
                                ),
                            )
                        } else {
                            listOfNotNull(
                                if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
                                    Finding(
                                        HarnessCheck.severityOf(manifest, category),
                                        category,
                                        HarnessCheck
                                            .stringFrom(
                                                messagesObj,
                                                "missingDescription",
                                            ).takeIf { message -> message.isNotEmpty() }
                                            ?: "skill missing description: ${file.relativeTo(root)}",
                                    )
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
