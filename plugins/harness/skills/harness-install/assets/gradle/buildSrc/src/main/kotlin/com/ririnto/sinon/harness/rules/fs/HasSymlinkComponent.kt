package com.ririnto.sinon.harness.rules.fs

import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.pathString

/**
 * Symlink detector for manifest-controlled filesystem paths.
 */
internal object HasSymlinkComponent {
    /**
     * Checks whether any path segment is a symbolic link.
     */
    fun check(root: Path, path: Path): Boolean {
        val relativePath = runCatching { root.relativize(path) }.getOrNull() ?: return true
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
}
