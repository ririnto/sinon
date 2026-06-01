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
import java.util.concurrent.ConcurrentHashMap
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
            !isSafeWalkRoot(base) -> {
                RuleContext.WalkResult(
                    emptyList(),
                    listOf(
                        HarnessAstResults.Finding(
                            Severity.ERROR,
                            "symlinkSafety",
                            "symlink scan root is not allowed: ${safePathMessage(base)}",
                        ),
                    ),
                )
            }

            !base.exists() -> {
                RuleContext.WalkResult(emptyList<Path>(), emptyList<HarnessAstResults.Finding>())
            }

            base.isRegularFile() -> {
                RuleContext.WalkResult(listOf(base), emptyList<HarnessAstResults.Finding>())
            }

            base.isDirectory() -> {
                val entries = base.listDirectoryEntries().filterNot { entry -> isWorktreeOrDescendant(entry) }
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

    private fun isSafeWalkRoot(base: Path): Boolean =
        runCatching {
            if (hasTraversalSegment(base)) {
                return@runCatching false
            }
            val normalized = base.toAbsolutePath().normalize()
            val rootAbsolute = root.toAbsolutePath().normalize()
            if (!normalized.startsWith(rootAbsolute)) {
                return@runCatching false
            }
            if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                return@runCatching true
            }
            val rootReal = root.toRealPath()
            val baseReal = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS)
            baseReal.startsWith(rootReal) && !hasSymlinkSegment(normalized)
        }.getOrElse { false }

    private fun safePathMessage(base: Path): String =
        runCatching { base.normalize().relativeTo(root).pathString }
            .getOrElse { base.pathString }

    private fun hasTraversalSegment(path: Path): Boolean =
        (0..<path.nameCount).any { i -> path.getName(i).pathString == ".." }

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

    private val stackSourceCache = ConcurrentHashMap<String, StackSourceResult>()

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
        val includeFindings = buildList {
            for (pattern in includePaths) {
                try {
                    pathMatcher.getPathMatcher("glob:$pattern")
                } catch (ignored: Exception) {
                    add(sourceRootFinding("symlinkSafety", "invalid includePaths glob pattern: $pattern: ${ignored.message}"))
                }
            }
        }
        val excludeFindings = buildList {
            for (pattern in excludePaths) {
                try {
                    pathMatcher.getPathMatcher("glob:$pattern")
                } catch (ignored: Exception) {
                    add(sourceRootFinding("symlinkSafety", "invalid excludePaths glob pattern: $pattern: ${ignored.message}"))
                }
            }
        }
        val includeMatchers = includePaths.mapNotNull { pattern ->
            runCatching { pathMatcher.getPathMatcher("glob:$pattern") }.getOrNull()
        }
        val excludeMatchers = excludePaths.mapNotNull { pattern ->
            runCatching { pathMatcher.getPathMatcher("glob:$pattern") }.getOrNull()
        }

        /**
         * Validated source pattern result: either a glob matcher, a concrete source root, or a finding.
         */
        data class ValidatedPattern(val matcher: PathMatcher? = null, val sourceRoot: Path? = null, val finding: HarnessAstResults.Finding? = null)

        val validated = buildList {
            for (pattern in sourcePatterns) {
                val patternPath = Path.of(pattern)
                if (pattern.isBlank()) {
                    continue
                }

                if (patternPath.isAbsolute) {
                    add(ValidatedPattern(finding = sourceRootFinding("symlinkSafety", "absolute source root is not allowed: $pattern")))
                    continue
                }

                if ((0..<patternPath.nameCount).any { index -> patternPath.getName(index).pathString == ".." }) {
                    add(ValidatedPattern(finding = sourceRootFinding("symlinkSafety", "source root traversal is not allowed: $pattern")))
                    continue
                }

                val patternHasGlob = hasGlobTokens(pattern)
                if (patternHasGlob) {
                    val matcher = try {
                        pathMatcher.getPathMatcher("glob:$pattern")
                    } catch (ignored: Exception) {
                        add(ValidatedPattern(finding = sourceRootFinding("symlinkSafety", "invalid glob source root pattern: $pattern: ${ignored.message}")))
                        continue
                    }
                    add(ValidatedPattern(matcher = matcher))
                    continue
                }

                val sourceRoot = root / pattern
                if (!Files.exists(sourceRoot, LinkOption.NOFOLLOW_LINKS)) {
                    continue
                }

                if (!isContainedDirectory(sourceRoot)) {
                    add(ValidatedPattern(finding = sourceRootFinding("symlinkSafety", "source root is not contained or is a symlink: $pattern")))
                    continue
                }

                add(ValidatedPattern(sourceRoot = sourceRoot))
            }
        }

        val findings = (includeFindings + excludeFindings + validated.mapNotNull { vp -> vp.finding }).toSet()
        val paths = buildSet<Path> {
            for (vp in validated) {
                if (vp.matcher != null) {
                    val directories = buildList {
                        root.walk().filter { file -> isContainedDirectory(file) }.filter { dir -> !isWorktreeOrDescendant(dir) }.forEach { dir ->
                            if (
                                vp.matcher.matches(root.relativeTo(root).resolve(dir.relativeTo(root))) ||
                                vp.matcher.matches(dir.relativeTo(root))
                            ) {
                                add(dir)
                            }
                        }
                    }
                    for (dir in directories) {
                        addAll(collectStackSourcePaths(dir, extensions, includeMatchers, excludeMatchers))
                    }
                }
                if (vp.sourceRoot != null) {
                    addAll(collectStackSourcePaths(vp.sourceRoot, extensions, includeMatchers, excludeMatchers))
                }
            }
        }

        return StackSourceResult(paths.toList(), findings.toList())
    }

    private fun collectStackSourcePaths(
        dir: Path,
        extensions: Set<String>,
        includeMatchers: List<PathMatcher>,
        excludeMatchers: List<PathMatcher>,
    ): Set<Path> =
        dir
            .walk()
            .filter { file -> isContainedRegularFile(file) }
            .filter { file -> !isWorktreeOrDescendant(file) }
            .filter { file -> file.extension in extensions }
            .filter { file ->
                val relative = file.relativeTo(root)
                (includeMatchers.isEmpty() || includeMatchers.any { matcher -> matcher.matches(relative) }) &&
                    excludeMatchers.none { matcher -> matcher.matches(relative) }
            }.toSet()

    private fun hasGlobTokens(value: String): Boolean {
        return "*" in value || "?" in value || "[" in value || "{" in value
    }

    private fun isWorktreeOrDescendant(path: Path): Boolean =
        runCatching {
            val relative = path.relativeTo(root)
            relative.startsWith(Path.of(".claude", "worktrees"))
        }.getOrDefault(false)

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

    private fun isContainedPath(path: Path): Boolean =
        runCatching {
            val rootReal = root.toRealPath()
            val pathReal = path.toRealPath(LinkOption.NOFOLLOW_LINKS)
            pathReal.startsWith(rootReal) && !hasSymlinkSegment(path)
        }.getOrDefault(false)

    private fun hasSymlinkSegment(path: Path): Boolean =
        runCatching {
            val relative = path.relativeTo(root)
            var current = root
            for (segment in relative) {
                if (segment.pathString == ".") {
                    continue
                }
                if (segment.pathString == "..") {
                    return@runCatching true
                }
                current /= segment
                if (current.isSymbolicLink()) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(true)
}
