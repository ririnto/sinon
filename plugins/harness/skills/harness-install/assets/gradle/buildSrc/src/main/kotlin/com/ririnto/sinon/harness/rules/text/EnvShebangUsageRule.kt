package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

/**
 * Rule that requires executable scripts under certain directories to use /usr/bin/env shebang.
 *
 * Operates on plain text; the check targets a fixed-position first line and requires no AST parser.
 */
object EnvShebangUsageRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "envShebangUsage"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        buildList {
            val catObj = ctx.manifest.categoryObject(category)
            val parametersObj = catObj?.get("parameters")?.jsonObject
            if (catObj != null && parametersObj != null) {
                ctx.manifest
                    .stringArray(category, "directories")
                    .filter { dirPath ->
                        (ctx.root / dirPath).isDirectory()
                    }.flatMap { dirPath ->
                        ctx
                            .walkSafe(ctx.root / dirPath)
                            .paths
                            .filter { file -> file.isExecutable() }
                            .filter { file ->
                                val firstLine = file.readText().lineSequence().firstOrNull() ?: ""
                                firstLine.startsWith("#!") &&
                                    !firstLine.startsWith(JsonAccess.stringFromObject(parametersObj, "expectedPrefix"))
                            }.map { file ->
                                Finding(
                                    ctx.manifest.severityOf(category),
                                    category,
                                    ctx.manifest.stringValue(category, "default").takeIf { message ->
                                        message.isNotEmpty()
                                    }
                                        ?: "executable script should use /usr/bin/env shebang: ${file.relativeTo(
                                            ctx.root,
                                        )}",
                                )
                            }
                    }.forEach { finding -> add(finding) }
            }
        }

    /**
     * Auto-fix executable scripts by correcting their shebang to use /usr/bin/env format.
     *
     * Extracts the interpreter token from the existing shebang and rewrites the first line
     * to use the normalized /usr/bin/env prefix.
     */
    override fun format(ctx: RuleContext): Collection<Path> =
        buildList {
            val catObj = ctx.manifest.categoryObject(category)
            val parametersObj = catObj?.get("parameters")?.jsonObject
            if (catObj != null && parametersObj != null) {
                val expectedPrefix = JsonAccess.stringFromObject(parametersObj, "expectedPrefix")
                ctx.manifest
                    .stringArray(category, "directories")
                    .filter { dirPath ->
                        (ctx.root / dirPath).isDirectory()
                    }.flatMap { dirPath ->
                        ctx
                            .walkSafe(ctx.root / dirPath)
                            .paths
                            .filter { file -> file.isExecutable() }
                            .filter { file ->
                                val firstLine = file.readText().lineSequence().firstOrNull() ?: ""
                                firstLine.startsWith("#!") &&
                                    !firstLine.startsWith(expectedPrefix)
                            }.map { file ->
                                val originalText = file.readText()
                                val lines = originalText.split("\n")
                                val currentShebang = lines.getOrNull(0) ?: ""
                                val interpreterLine = currentShebang.removePrefix("#!").trim()
                                val interpreter = interpreterLine.split(Regex("\\s+")).lastOrNull() ?: "sh"
                                val newShebang = expectedPrefix.trimEnd() + " " + interpreter
                                val newLines = listOf(newShebang) + lines.drop(1)
                                val newText = newLines.joinToString("\n") + if (originalText.endsWith("\n")) "\n" else ""
                                file.writeText(newText)
                                file
                            }
                    }.forEach { file -> add(file) }
            }
        }
}
