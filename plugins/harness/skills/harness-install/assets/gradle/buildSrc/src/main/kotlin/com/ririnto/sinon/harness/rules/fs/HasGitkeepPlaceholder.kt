package com.ririnto.sinon.harness.rules.fs

import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * .gitkeep detector for placeholder policy.
 */
internal object HasGitkeepPlaceholder {
    /**
     * Checks whether a directory has a .gitkeep placeholder file.
     */
    fun check(path: Path): Boolean {
        return (path / ".gitkeep").exists()
    }
}
