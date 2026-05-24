package com.ririnto.sinon.harness.core

import com.ririnto.sinon.harness.ast.HarnessAstResults
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Default implementation of RuleContext.
 */
class DefaultRuleContext(
    override val root: Path,
    override val manifest: Manifest,
) : RuleContext {
    override fun readSafe(path: String): String {
        val p = root / path
        return when {
            p.isSymbolicLink() && isAllowedRootContractSymlink(p) -> {
                val target =
                    when (p.name) {
                        "AGENTS.md" -> "CLAUDE.md"
                        else -> "AGENTS.md"
                    }
                (root / target).let { targetPath ->
                    when {
                        targetPath.isRegularFile() -> targetPath.readText()
                        else -> ""
                    }
                }
            }

            p.isSymbolicLink() -> ""

            p.isRegularFile() -> p.readText()

            else -> ""
        }
    }

    override fun walkSafe(base: Path): RuleContext.WalkResult {
        return when {
            !base.exists() -> RuleContext.WalkResult(emptyList<Path>(), emptyList<HarnessAstResults.Finding>())

            base.isSymbolicLink() && !isAllowedRootContractSymlink(base) ->
                RuleContext.WalkResult(
                    emptyList(),
                    listOf(
                        HarnessAstResults.Finding(
                            Severity.ERROR,
                            "symlinkSafety",
                            "symlink scan root is not allowed: ${base.relativeTo(root)}",
                        ),
                    ),
                )

            base.isRegularFile() -> RuleContext.WalkResult(listOf(base), emptyList<HarnessAstResults.Finding>())

            base.isDirectory() -> {
                val entries = base.listDirectoryEntries()
                RuleContext.WalkResult(
                    entries
                        .filter { entry -> !entry.isSymbolicLink() }
                        .flatMap { entry ->
                            when {
                                entry.isDirectory() -> walkSafe(entry).paths
                                entry.isRegularFile() -> listOf(entry)
                                else -> emptyList()
                            }
                        },
                    buildSet {
                        entries
                            .filter { entry -> entry.isSymbolicLink() }
                            .filter { entry -> !isAllowedRootContractSymlink(entry) }
                            .forEach { entry ->
                                add(
                                    HarnessAstResults.Finding(
                                        Severity.ERROR,
                                        "symlinkSafety",
                                        "symlink path is not allowed: ${entry.relativeTo(root)}",
                                    ),
                                )
                            }
                    }.toList(),
                )
            }

            else -> RuleContext.WalkResult(emptyList<Path>(), emptyList<HarnessAstResults.Finding>())
        }
    }

    override fun isAllowedRootContractSymlink(path: Path): Boolean {
        if (path.parent != root || path.name !in setOf("AGENTS.md", "CLAUDE.md") || !path.isSymbolicLink()) {
            return false
        }
        val expected =
            when (path.name) {
                "AGENTS.md" -> "CLAUDE.md"
                else -> "AGENTS.md"
            }
        return path.readSymbolicLink().fileName.pathString == expected && (root / expected).isRegularFile()
    }
}
