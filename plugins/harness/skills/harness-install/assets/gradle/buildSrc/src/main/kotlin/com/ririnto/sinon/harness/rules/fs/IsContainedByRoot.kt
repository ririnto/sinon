package com.ririnto.sinon.harness.rules.fs

import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Root containment checker for manifest-controlled filesystem paths.
 */
internal object IsContainedByRoot {
    /**
     * Checks whether a candidate path is contained within the project root.
     */
    fun check(root: Path, candidate: Path): Boolean =
        runCatching {
            val rootReal = root.toRealPath(LinkOption.NOFOLLOW_LINKS)
            candidate.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(rootReal)
        }.getOrDefault(false) || runCatching {
            candidate.normalize().startsWith(root.toRealPath(LinkOption.NOFOLLOW_LINKS).normalize())
        }.getOrDefault(false)
}
