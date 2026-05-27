package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.HarnessCheck
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Rule that requires .gitkeep or real files in empty directories.
 */
object EmptyDirectoryPlaceholdersRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "emptyDirectoryPlaceholders"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        buildList {
            val catObj = ctx.manifest.categoryObject(category)
            val parametersObj = catObj?.get("parameters")?.jsonObject
            if (catObj != null && parametersObj != null) {
                ctx.manifest
                    .stringArray(category, "directories")
                    .filter { dirPath ->
                        val dir = ctx.root / dirPath
                        when {
                            !dir.isDirectory() -> {
                                false
                            }

                            else -> {
                                dir.listDirectoryEntries().none { entry -> entry.name != ".gitkeep" } &&
                                    !(ctx.root / dirPath / ".gitkeep").exists()
                            }
                        }
                    }.map { dirPath ->
                        Finding(
                            ctx.manifest.severityOf(category),
                            category,
                            "empty directory must keep placeholder or real files: $dirPath",
                        )
                    }.forEach { finding ->
                        add(finding)
                    }
            }
        }

    /**
     * Creates .gitkeep files in empty directories that lack placeholders.
     */
    override fun format(ctx: RuleContext): Collection<Path> =
        buildList {
            val catObj = ctx.manifest.categoryObject(category)
            val parametersObj = catObj?.get("parameters")?.jsonObject
            if (catObj != null && parametersObj != null) {
                ctx.manifest
                    .stringArray(category, "directories")
                    .filter { dirPath ->
                        val dir = ctx.root / dirPath
                        when {
                            !dir.isDirectory() -> {
                                false
                            }

                            else -> {
                                dir.listDirectoryEntries().none { entry -> entry.name != ".gitkeep" } &&
                                    !(ctx.root / dirPath / ".gitkeep").exists()
                            }
                        }
                    }.forEach { dirPath ->
                        val gitkeepPath = ctx.root / dirPath / ".gitkeep"
                        gitkeepPath.createFile()
                        add(gitkeepPath)
                    }
            }
        }
}
