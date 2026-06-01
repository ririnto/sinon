package com.ririnto.sinon.harness.rules.fs

import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Empty-directory checker for placeholder policy.
 */
internal object IsEmptyDirectory {
    /**
     * Checks whether a directory contains no real files, allowing only .gitkeep.
     */
    fun check(path: Path): Boolean {
        return path.listDirectoryEntries().none { entry -> entry.name != ".gitkeep" }
    }
}
