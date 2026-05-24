package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that requires agents to have proper frontmatter.
 */
object AgentFrontmatterRule : HarnessCheckRule() {
    override val category: String = "agentFrontmatter"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> = buildList {
        val severity = HarnessCheck.severityOf(manifest, category)
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        if (catObj != null && parametersObj != null && messagesObj != null) {
            val directory = HarnessCheck.stringFrom(parametersObj, "directory")
            val dirPath = root / directory
            if (!dirPath.isDirectory()) {
                add(
                    Finding(
                        severity,
                        category,
                        HarnessCheck.stringFrom(messagesObj, "missingDirectory").takeIf { message ->
                            message.isNotEmpty()
                        } ?: ".claude/agents must contain at least one .md agent",
                    ),
                )
            } else {
                val files =
                    dirPath
                        .listDirectoryEntries()
                        .filter { entry -> entry.isRegularFile() }
                        .filter { entry -> entry.extension == "md" }
                if (files.isEmpty()) {
                    add(
                        Finding(
                            severity,
                            category,
                            HarnessCheck.stringFrom(messagesObj, "missingAgent").takeIf { message ->
                                message.isNotEmpty()
                            } ?: ".claude/agents must contain at least one .md agent",
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
                                    ).takeIf { message ->
                                        message.isNotEmpty()
                                    } ?: "agent missing frontmatter: ${file.relativeTo(root)}",
                            ),
                        )
                    } else {
                        if (!"""(?m)^name:\s*[-a-z0-9]+\s*$"""
                                .toRegex()
                                .containsMatchIn(text)
                        ) {
                            add(
                                Finding(
                                    severity,
                                    category,
                                    HarnessCheck
                                        .stringFrom(messagesObj, "missingName")
                                        .takeIf { message -> message.isNotEmpty() }
                                        ?: "agent missing name: ${file.relativeTo(root)}",
                                ),
                            )
                        }
                        if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
                            add(
                                Finding(
                                    severity,
                                    category,
                                    HarnessCheck
                                        .stringFrom(messagesObj, "missingDescription")
                                        .takeIf { message -> message.isNotEmpty() }
                                        ?: "agent missing description: ${file.relativeTo(root)}",
                                ),
                            )
                        }
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
