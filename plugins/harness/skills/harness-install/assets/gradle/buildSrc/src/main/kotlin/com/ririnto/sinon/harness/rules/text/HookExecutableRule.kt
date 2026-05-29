package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.div
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * Rule that requires hooks to be executable.
 *
 * Operates on file metadata (executable bit); no text parsing or AST parser required.
 */
object HookExecutableRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "hookExecutable"

    override fun validate(ctx: RuleContext): Collection<Finding> {
        if (ctx.manifest.categoryObject(category) == null) {
            return emptyList()
        }
        return ctx.manifest
            .stringArray(category, "hooks")
            .mapNotNull { hookPath -> safeHookPath(ctx.root, hookPath) }
            .filter { hook -> !hook.isExecutable() }
            .map { hook ->
                Finding(
                    ctx.manifest.severityOf(category),
                    category,
                    ctx.manifest.stringValue(category, "default").takeIf { defaultMsg -> defaultMsg.isNotEmpty() }
                        ?: "${hook.relativeTo(ctx.root)} must be executable",
                )
            }
    }

    override fun format(ctx: RuleContext): Collection<Path> {
        if (ctx.manifest.categoryObject(category) == null) {
            return emptyList()
        }
        return ctx.manifest
            .stringArray(category, "hooks")
            .mapNotNull { hookPath -> safeHookPath(ctx.root, hookPath) }
            .filter { hook -> !hook.isExecutable() }
            .mapNotNull { hook ->
                val currentPerms = Files.getPosixFilePermissions(hook)
                val newPerms = currentPerms + setOf(
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_EXECUTE
                )
                Files.setPosixFilePermissions(hook, newPerms)
                hook
            }
    }
}

/**
 * Validates that a hook path is safe for read/write operations.
 *
 * Rejects absolute paths, traversal segments, leading-dash components,
 * symlink components, and paths that resolve outside the root.
 *
 * @param root the project root directory.
 * @param hookPath the hook path string from manifest.
 * @return the resolved Path if safe, null otherwise.
 */
private fun safeHookPath(root: Path, hookPath: String): Path? {
    val path = Path.of(hookPath)
    if (path.isAbsolute) return null
    if (path.nameCount == 0) return null
    if ((0..<path.nameCount).any { segment ->
        val s = path.getName(segment).pathString
        s == ".." || s == "." || s.startsWith("-")
    }) return null
    val resolved = root / path
    if (!resolved.isRegularFile()) return null
    if (hasSymlinkComponent(root, resolved)) return null
    return try {
        val rootReal = root.toRealPath()
        val resolvedReal = resolved.toRealPath()
        if (resolvedReal.startsWith(rootReal)) resolvedReal else null
    } catch (_: Exception) {
        null
    }
}

/**
 * Checks whether any path segment between root and target is a symbolic link.
 */
private fun hasSymlinkComponent(root: Path, target: Path): Boolean {
    val relative = try { target.relativeTo(root) } catch (_: Exception) { return true }
    var current = root
    for (segmentIndex in 0 until relative.nameCount) {
        val segment = relative.getName(segmentIndex)
        if (segment.pathString == ".") continue
        if (segment.pathString == "..") return true
        current /= segment
        if (java.nio.file.Files.isSymbolicLink(current)) return true
    }
    return false
}
