package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.jsonObject
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.parser.Parser
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
 *
 * @category skillFrontmatter
 */
object SkillFrontmatterRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "skillFrontmatter"

    private val markdownParser: Parser =
        Parser
            .builder()
            .extensions(listOf(YamlFrontMatterExtension.create()))
            .build()

    override fun validate(ctx: RuleContext): Collection<Finding> =
        buildList {
            val catObj = ctx.manifest.categoryObject(category)
            val parametersObj = catObj?.get("parameters")?.jsonObject
            if (catObj != null && parametersObj != null) {
                val dirPath = ctx.root / JsonAccess.stringFromObject(parametersObj, "rootDirectory")
                val severity = ctx.manifest.severityOf(category)
                when {
                    !dirPath.isDirectory() -> {
                        add(
                            Finding(
                                severity,
                                category,
                                ctx.manifest.stringValue(category, "missingDirectory").takeIf { message ->
                                    message.isNotEmpty()
                                } ?: ".claude/skills must contain at least one SKILL.md",
                            ),
                        )
                    }

                    else -> {
                        val files =
                            dirPath
                                .walk()
                                .filter { file -> file.isRegularFile() }
                                .filter { file -> file.name == JsonAccess.stringFromObject(parametersObj, "filename") }
                                .toList()
                        when {
                            files.isEmpty() -> {
                                add(
                                    Finding(
                                        severity,
                                        category,
                                        ctx.manifest.stringValue(category, "missingSkill").takeIf { message ->
                                            message.isNotEmpty()
                                        } ?: ".claude/skills must contain at least one SKILL.md",
                                    ),
                                )
                            }

                            else -> {
                                files.forEach { file ->
                                    val text = file.readText()
                                    when {
                                        !text.startsWith("---") -> {
                                            add(
                                                Finding(
                                                    severity,
                                                    category,
                                                    ctx.manifest
                                                        .stringValue(category, "missingFrontmatter")
                                                        .takeIf { message -> message.isNotEmpty() }
                                                        ?: "skill missing frontmatter: ${file.relativeTo(ctx.root)}",
                                                ),
                                            )
                                        }

                                        else -> {
                                            val frontmatter = extractFrontmatter(text)
                                            if (frontmatter["description"]?.firstOrNull()?.isNotBlank() != true) {
                                                add(
                                                    Finding(
                                                        severity,
                                                        category,
                                                        ctx.manifest
                                                            .stringValue(
                                                                category,
                                                                "missingDescription",
                                                            ).takeIf { message ->
                                                                message.isNotEmpty()
                                                            }
                                                            ?: "skill missing description: ${file.relativeTo(
                                                                ctx.root,
                                                            )}",
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
            }
        }

    private fun extractFrontmatter(content: String): Map<String, List<String>> =
        YamlFrontMatterVisitor()
            .also { visitor -> markdownParser.parse(content).accept(visitor) }
            .data
}
