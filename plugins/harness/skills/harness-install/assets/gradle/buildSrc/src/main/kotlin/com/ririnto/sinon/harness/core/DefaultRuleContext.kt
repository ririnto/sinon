package com.ririnto.sinon.harness.core

import com.ririnto.sinon.harness.ast.HarnessAstResults
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Default implementation of RuleContext.
 */
class DefaultRuleContext(
    override val root: Path,
    override val manifest: Manifest,
    override val stack: String = "kotlin",
) : RuleContext {
    override fun readSafe(path: String): String {
        return when {
            (root / path).isSymbolicLink() && isAllowedRootContractSymlink(root / path) -> {
                (root / when ((root / path).name) {
                    "AGENTS.md" -> "CLAUDE.md"
                    else -> "AGENTS.md"
                }).let { targetPath ->
                    when {
                        targetPath.isRegularFile() -> targetPath.readText()
                        else -> ""
                    }
                }
            }

            (root / path).isSymbolicLink() -> {
                ""
            }

            (root / path).isRegularFile() -> {
                (root / path).readText()
            }

            else -> {
                ""
            }
        }
    }

    override fun walkSafe(base: Path): RuleContext.WalkResult =
        when {
            !base.exists() -> {
                RuleContext.WalkResult(emptyList<Path>(), emptyList<HarnessAstResults.Finding>())
            }

            base.isSymbolicLink() && !isAllowedRootContractSymlink(base) -> {
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
            }

            base.isRegularFile() -> {
                RuleContext.WalkResult(listOf(base), emptyList<HarnessAstResults.Finding>())
            }

            base.isDirectory() -> {
                val entries = base.listDirectoryEntries()
                RuleContext.WalkResult(
                    buildList {
                        entries
                            .filter { entry -> !entry.isSymbolicLink() }
                            .forEach { entry ->
                                when {
                                    entry.isDirectory() -> addAll(walkSafe(entry).paths)
                                    entry.isRegularFile() -> add(entry)
                                }
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

            else -> {
                RuleContext.WalkResult(emptyList<Path>(), emptyList<HarnessAstResults.Finding>())
            }
        }

    override fun isAllowedRootContractSymlink(path: Path): Boolean {
        if (path.parent != root || path.name !in setOf("AGENTS.md", "CLAUDE.md") || !path.isSymbolicLink()) {
            return false
        }
        val targetName = when (path.name) {
            "AGENTS.md" -> "CLAUDE.md"
            else -> "AGENTS.md"
        }
        return path.readSymbolicLink().fileName.pathString == targetName && (root / targetName).isRegularFile()
    }

    override fun stackSources(category: String): List<Path> {
        val parametersObj = (manifest.categoryObject(category) ?: return emptyList())["parameters"]?.jsonObject ?: return emptyList()
        val sourcePatterns =
            parametersObj["sourceRoots"]?.jsonArray?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }
                ?: return emptyList()
        val extensions =
            parametersObj["extensions"]?.jsonArray?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }?.toSet()
                ?: emptySet()
        val includePaths =
            parametersObj["includePaths"]?.jsonArray?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }
                ?: emptyList()
        val excludePaths =
            parametersObj["excludePaths"]?.jsonArray?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }
                ?: emptyList()
        val pathMatcher = FileSystems.getDefault()
        val includeMatchers: List<PathMatcher> =
            buildList {
                if (includePaths.isNotEmpty()) {
                    addAll(includePaths.map { pattern -> pathMatcher.getPathMatcher("glob:$pattern") })
                }
            }
        val excludeMatchers: List<PathMatcher> =
            buildList {
                addAll(excludePaths.map { pattern -> pathMatcher.getPathMatcher("glob:$pattern") })
            }

        return buildList {
            for (pattern in sourcePatterns) {
                for (dir in
                    when {
                        "*" in pattern || "?" in pattern -> {
                            root
                                .walk()
                                .filter { file -> !file.isSymbolicLink() }
                                .filter { file -> file.isDirectory() }
                                .filter { dir ->
                                    val matcher = pathMatcher.getPathMatcher("glob:$pattern")
                                    matcher.matches(root.relativeTo(root).resolve(dir.relativeTo(root))) ||
                                        matcher.matches(dir.relativeTo(root))
                                }.toList()
                        }

                        else -> buildList {
                            if ((root / pattern).isDirectory()) {
                                add(root / pattern)
                            }
                        }
                    }
                ) {
                    dir
                        .walk()
                        .filter { file -> !file.isSymbolicLink() }
                        .filter { file -> file.isRegularFile() }
                        .filter { file -> file.extension in extensions }
                        .filter { file ->
                            val relative = file.relativeTo(root)
                            (includeMatchers.isEmpty() || includeMatchers.any { matcher -> matcher.matches(relative) }) &&
                                excludeMatchers.none { matcher -> matcher.matches(relative) }
                        }.forEach { file -> add(file) }
                }
            }
        }
    }
}
