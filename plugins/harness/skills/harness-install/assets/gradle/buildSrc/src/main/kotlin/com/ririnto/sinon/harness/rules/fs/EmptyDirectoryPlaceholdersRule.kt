package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.jsonObject
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Rule that requires .gitkeep or real files in empty directories.
 */
object EmptyDirectoryPlaceholdersRule : HarnessCheckRule() {
    override val category = "emptyDirectoryPlaceholders"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        buildList {
            val parametersObj = ctx.manifest.categoryObject(category)?.get("parameters")?.jsonObject ?: return@buildList

            val categorySeverity = ctx.manifest.severityOf(category)
            val pathSeverity = Severity.ERROR
            JsonAccess
                .stringArrayFromObject(parametersObj, "directories")
                .forEach { dirPath ->
                    val directory = safeDirectoryPath(ctx.root, dirPath, pathSeverity) { finding ->
                        add(finding)
                    } ?: return@forEach
                    if (!directory.isDirectory()) return@forEach

                    if (isEmptyDirectory(directory) && !hasGitkeepPlaceholder(directory)) {
                        add(
                            Finding(
                                categorySeverity,
                                category,
                                "empty directory must keep placeholder or real files: $dirPath",
                            ),
                        )
                    }
                }
        }

    /**
     * Creates .gitkeep files in empty directories that lack placeholders.
     */
    override fun format(ctx: RuleContext): Collection<Path> =
        buildList {
            val parametersObj = ctx.manifest.categoryObject(category)?.get("parameters")?.jsonObject ?: return@buildList

            val pathSeverity = Severity.ERROR
            JsonAccess.stringArrayFromObject(parametersObj, "directories").forEach { dirPath ->
                val directory = safeDirectoryPath(ctx.root, dirPath, pathSeverity) { _ ->
                    // no-op: format mode should only emit paths
                } ?: return@forEach
                if (!directory.isDirectory()) return@forEach
                val gitkeepPath = directory / ".gitkeep"

                if (!isContainedByRoot(ctx.root, gitkeepPath)) {
                    return@forEach
                }

                if (isEmptyDirectory(directory) && !hasGitkeepPlaceholder(directory)) {
                    gitkeepPath.createFile()
                    add(gitkeepPath)
                }
            }
        }
}

private fun safeDirectoryPath(
    root: Path,
    directoryPath: String,
    addFindingSeverity: Severity,
    addFinding: (Finding) -> Unit,
): Path? {
    if (directoryPath.isBlank()) {
        addFinding(Finding(addFindingSeverity, "emptyDirectoryPlaceholders", " is not a safe relative directory path"))
        return null
    }

    val path = Path.of(directoryPath)
    if (path.isAbsolute || path.nameCount == 0) {
        addFinding(
            Finding(
                addFindingSeverity,
                "emptyDirectoryPlaceholders",
                "$directoryPath is not a safe relative directory path",
            ),
        )
        return null
    }

    if ((0..<path.nameCount).any { idx ->
            val segment = path.getName(idx).pathString
            segment == "." || segment == ".." || segment.startsWith("-")
        }) {
        addFinding(
            Finding(
                addFindingSeverity,
                "emptyDirectoryPlaceholders",
                "$directoryPath is not a safe relative directory path",
            ),
        )
        return null
    }

    val resolvedDirectory = root / path
    if (hasSymlinkComponent(root, resolvedDirectory)) {
        addFinding(
            Finding(
                Severity.ERROR,
                "symlinkSafety",
                "symlink directory is not allowed: $directoryPath",
            ),
        )
        return null
    }

    if (!isContainedByRoot(root, resolvedDirectory)) {
        addFinding(
            Finding(
                addFindingSeverity,
                "emptyDirectoryPlaceholders",
                "$directoryPath is not a safe relative directory path",
            ),
        )
        return null
    }

    return resolvedDirectory
}

private fun isContainedByRoot(root: Path, candidate: Path): Boolean {
    return try {
        val rootReal = root.toRealPath(LinkOption.NOFOLLOW_LINKS)
        candidate.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(rootReal)
    } catch (_: Exception) {
        candidate.normalize().startsWith(root.toRealPath(LinkOption.NOFOLLOW_LINKS).normalize())
    }
}

private fun hasSymlinkComponent(root: Path, path: Path): Boolean {
    val relativePath = try {
        root.relativize(path)
    } catch (_: Exception) {
        return true
    }
    var current = root
    for (segmentIndex in 0 until relativePath.nameCount) {
        val segment = relativePath.getName(segmentIndex)
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
    return false
}

private fun isEmptyDirectory(path: Path): Boolean {
    return path.listDirectoryEntries().none { entry -> entry.name != ".gitkeep" }
}

private fun hasGitkeepPlaceholder(path: Path): Boolean {
    return (path / ".gitkeep").exists()
}
