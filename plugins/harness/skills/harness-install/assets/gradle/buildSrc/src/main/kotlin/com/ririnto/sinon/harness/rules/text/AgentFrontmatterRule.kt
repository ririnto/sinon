package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import kotlinx.serialization.json.jsonObject
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.parser.Parser
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
 *
 * @category agentFrontmatter
 */
object AgentFrontmatterRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "agentFrontmatter"

    private val namePattern: Regex = "[-a-z0-9]+".toRegex()

    private val markdownParser: Parser =
        Parser.builder()
            .extensions(listOf(YamlFrontMatterExtension.create()))
            .build()

    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val catObj = ctx.manifest.categoryObject(category)
        val parametersObj = catObj?.get("parameters")?.jsonObject
        if (catObj != null && parametersObj != null) {
            val severity = ctx.manifest.severityOf(category)
            val dirPath = ctx.root / JsonAccess.stringFromObject(parametersObj, "directory")
            when {
                !dirPath.isDirectory() -> add(
                    Finding(
                        severity,
                        category,
                        ctx.manifest.stringValue(category, "missingDirectory").takeIf { message ->
                            message.isNotEmpty()
                        } ?: ".claude/agents must contain at least one .md agent",
                    ),
                )
                else -> {
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
                                ctx.manifest.stringValue(category, "missingAgent").takeIf { message ->
                                    message.isNotEmpty()
                                } ?: ".claude/agents must contain at least one .md agent",
                            ),
                        )
                    }
                    files.forEach { file ->
                        val text = file.readText()
                        when {
                            !text.startsWith("---") -> add(
                                Finding(
                                    severity,
                                    category,
                                    ctx.manifest.stringValue(category, "missingFrontmatter").takeIf { message ->
                                        message.isNotEmpty()
                                    } ?: "agent missing frontmatter: ${file.relativeTo(ctx.root)}",
                                ),
                            )
                            else -> {
                                val frontmatter = extractFrontmatter(text)
                                if (frontmatter["name"]?.firstOrNull()?.matches(namePattern) != true) {
                                    add(
                                        Finding(
                                            severity,
                                            category,
                                            ctx.manifest.stringValue(category, "missingName")
                                                .takeIf { message -> message.isNotEmpty() }
                                                ?: "agent missing name: ${file.relativeTo(ctx.root)}",
                                        ),
                                    )
                                }
                                if (frontmatter["description"]?.firstOrNull()?.isNotBlank() != true) {
                                    add(
                                        Finding(
                                            severity,
                                            category,
                                            ctx.manifest.stringValue(category, "missingDescription")
                                                .takeIf { message -> message.isNotEmpty() }
                                                ?: "agent missing description: ${file.relativeTo(ctx.root)}",
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractFrontmatter(content: String): Map<String, List<String>> =
        YamlFrontMatterVisitor()
            .also { visitor -> markdownParser.parse(content).accept(visitor) }
            .data
}
