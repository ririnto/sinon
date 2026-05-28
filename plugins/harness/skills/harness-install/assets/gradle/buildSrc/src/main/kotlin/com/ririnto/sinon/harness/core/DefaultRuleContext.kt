package com.ririnto.sinon.harness.core

import com.ririnto.sinon.harness.ast.HarnessAstResults
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
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

    private val stackSourceCache = mutableMapOf<String, StackSourceResult>()

    override fun stackSourceFindings(category: String): List<HarnessAstResults.Finding> =
        stackSourceCache.getOrPut(category) { collectStackSources(category) }.findings

    override fun stackSources(category: String): List<Path> =
        stackSourceCache.getOrPut(category) { collectStackSources(category) }.paths

    private fun collectStackSources(category: String): StackSourceResult {
        val parametersObj = (manifest.categoryObject(category) ?: return StackSourceResult(emptyList(), emptyList()))["parameters"]?.jsonObject
            ?: return StackSourceResult(emptyList(), emptyList())
        val sourcePatterns =
            parametersObj["sourceRoots"]?.jsonArray?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }
                ?: return StackSourceResult(emptyList(), emptyList())
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

        val paths = mutableSetOf<Path>()
        val findings = mutableSetOf<HarnessAstResults.Finding>()

        for (pattern in sourcePatterns) {
            val patternPath = Path.of(pattern)
            if (pattern.isBlank()) {
                continue
            }

            if (patternPath.isAbsolute) {
                findings.add(sourceRootFinding("symlinkSafety", "absolute source root is not allowed: $pattern"))
                continue
            }

            if ((0..<patternPath.nameCount).any { patternPath.getName(it).pathString == ".." }) {
                findings.add(sourceRootFinding("symlinkSafety", "source root traversal is not allowed: $pattern"))
                continue
            }

            val patternHasGlob = hasGlobTokens(pattern)
            if (patternHasGlob) {
                val matcher = try {
                    pathMatcher.getPathMatcher("glob:$pattern")
                } catch (_: Exception) {
                    findings.add(
                        sourceRootFinding(
                            "symlinkSafety",
                            "invalid glob source root pattern: $pattern",
                        ),
                    )
                    continue
                }

                val directories = buildList {
                    root.walk().filter { file -> isContainedDirectory(file) }.forEach { dir ->
                        if (
                            matcher.matches(root.relativeTo(root).resolve(dir.relativeTo(root))) ||
                            matcher.matches(dir.relativeTo(root))
                        ) {
                            add(dir)
                        }
                    }
                }

                for (dir in directories) {
                    addStackSources(paths, dir, extensions, includeMatchers, excludeMatchers)
                }
                continue
            }

            val sourceRoot = root / pattern
            if (!Files.exists(sourceRoot, LinkOption.NOFOLLOW_LINKS)) {
                continue
            }

            if (!isContainedDirectory(sourceRoot)) {
                findings.add(
                    sourceRootFinding(
                        "symlinkSafety",
                        "source root is not contained or is a symlink: $pattern",
                    ),
                )
                continue
            }

            addStackSources(paths, sourceRoot, extensions, includeMatchers, excludeMatchers)
        }

        return StackSourceResult(paths.toList(), findings.toList())
    }

    private fun addStackSources(
        paths: MutableCollection<Path>,
        dir: Path,
        extensions: Set<String>,
        includeMatchers: List<PathMatcher>,
        excludeMatchers: List<PathMatcher>,
    ) {
        dir
            .walk()
            .filter { file -> isContainedRegularFile(file) }
            .filter { file -> file.extension in extensions }
            .filter { file ->
                val relative = file.relativeTo(root)
                (includeMatchers.isEmpty() || includeMatchers.any { matcher -> matcher.matches(relative) }) &&
                    excludeMatchers.none { matcher -> matcher.matches(relative) }
            }.forEach { file -> paths.add(file) }
    }

    private fun hasGlobTokens(value: String): Boolean {
        return "*" in value || "?" in value || "[" in value || "{" in value
    }

    private fun sourceRootFinding(category: String, message: String): HarnessAstResults.Finding {
        return HarnessAstResults.Finding(Severity.ERROR, category, message)
    }

    private data class StackSourceResult(
        val paths: List<Path>,
        val findings: List<HarnessAstResults.Finding>,
    )

    private fun isContainedDirectory(path: Path): Boolean {
        return isContainedPath(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
    }

    private fun isContainedRegularFile(path: Path): Boolean {
        return isContainedPath(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
    }

    private fun isContainedPath(path: Path): Boolean {
        return try {
            val rootReal = root.toRealPath()
            val pathReal = path.toRealPath(LinkOption.NOFOLLOW_LINKS)
            pathReal.startsWith(rootReal) && !hasSymlinkSegment(path)
        } catch (_: Exception) {
            false
        }
    }

    private fun hasSymlinkSegment(path: Path): Boolean {
        return try {
            val relative = path.relativeTo(root)
            var current = root
            for (segment in relative) {
                if (segment.pathString == ".") {
                    continue
                }
                if (segment.pathString == "..") {
                    return true
                }
                current /= segment
                if (current.isSymbolicLink()) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            true
        }
    }
}