package com.ririnto.sinon.harness.rules.text

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * Shared safe-path resolver for generated hook validators.
 */
internal object HookPathSupport {
    /**
     * Resolve a manifest hook path only when it stays inside the project root.
     */
    fun safeHookPath(root: Path, hookPath: String): Path? {
        val path = Path.of(hookPath)
        if (path.isAbsolute) {
            return null
        }
        if (path.nameCount == 0) {
            return null
        }
        if (hasUnsafeSegment(path)) {
            return null
        }
        val resolved = root / path
        if (!resolved.isRegularFile()) {
            return null
        }
        if (hasSymlinkComponent(root, resolved)) {
            return null
        }
        return runCatching {
            val rootReal = root.toRealPath()
            val resolvedReal = resolved.toRealPath()
            when {
                resolvedReal.startsWith(rootReal) -> resolvedReal
                else -> null
            }
        }.getOrNull()
    }

    private fun hasUnsafeSegment(path: Path): Boolean =
        (0..<path.nameCount).any { segmentIndex ->
            val segment = path.getName(segmentIndex).pathString
            segment == ".." || segment == "." || segment.startsWith("-")
        }

    private fun hasSymlinkComponent(root: Path, target: Path): Boolean {
        val relativeResult = runCatching { target.relativeTo(root) }
        if (relativeResult.isFailure) {
            return true
        }
        val relative = relativeResult.getOrThrow()
        var current = root
        for (segmentIndex in 0 until relative.nameCount) {
            val segment = relative.getName(segmentIndex)
            if (segment.pathString == ".") {
                continue
            }
            if (segment.pathString == "..") {
                return true
            }
            current /= segment
            if (Files.isSymbolicLink(current)) {
                return true
            }
        }
        return false
    }
}
