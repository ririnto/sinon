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
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Rule that requires skills to have proper frontmatter.
 */
object SkillFrontmatterRule : HarnessCheckRule() {
    override val category: String = "skillFrontmatter"
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
            val rootDirectory = HarnessCheck.stringFrom(parametersObj, "rootDirectory")
            val filename = HarnessCheck.stringFrom(parametersObj, "filename")
            val dirPath = root / rootDirectory
            val severity = HarnessCheck.severityOf(manifest, category)
            if (!dirPath.isDirectory()) {
                add(
                    Finding(
                        severity,
                        category,
                        HarnessCheck.stringFrom(messagesObj, "missingDirectory").takeIf { message ->
                            message.isNotEmpty()
                        } ?: ".claude/skills must contain at least one SKILL.md",
                    ),
                )
            } else {
                val files =
                    dirPath
                        .walk()
                        .filter { file -> file.isRegularFile() }
                        .filter { file -> file.name == filename }
                        .toList()
                if (files.isEmpty()) {
                    add(
                        Finding(
                            severity,
                            category,
                            HarnessCheck.stringFrom(messagesObj, "missingSkill").takeIf { message ->
                                message.isNotEmpty()
                            } ?: ".claude/skills must contain at least one SKILL.md",
                        ),
                    )
                }
                files.forEach { file ->
                    val text = file.readText()
                    if (!text.startsWith("---")) {
                        add(
                            Finding(
                                severity,
                                category,
                                HarnessCheck
                                    .stringFrom(
                                        messagesObj,
                                        "missingFrontmatter",
                                    ).takeIf { message -> message.isNotEmpty() }
                                    ?: "skill missing frontmatter: ${file.relativeTo(root)}",
                            ),
                        )
                    } else if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
                        add(
                            Finding(
                                severity,
                                category,
                                HarnessCheck
                                    .stringFrom(messagesObj, "missingDescription")
                                    .takeIf { message -> message.isNotEmpty() }
                                    ?: "skill missing description: ${file.relativeTo(root)}",
                            ),
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
