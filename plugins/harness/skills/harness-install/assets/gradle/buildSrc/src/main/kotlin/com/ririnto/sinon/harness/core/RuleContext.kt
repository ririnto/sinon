package com.ririnto.sinon.harness.core

import com.ririnto.sinon.harness.ast.HarnessAstResults
import java.nio.file.Path

/**
 * Validation context passed to all rule implementations.
 */
interface RuleContext {
    /**
     * The project root path.
     */
    val root: Path

    /**
     * The harness validation manifest.
     */
    val manifest: Manifest

    /**
     * Safely read file content.
     *
     * @param path The file path relative to root.
     * @return File content; empty string if file does not exist or symlink is not allowed.
     */
    fun readSafe(path: String): String

    /**
     * Safely walk a directory tree.
     *
     * @param base The base directory path.
     * @return A pair of (list of files, list of findings for violations).
     */
    fun walkSafe(base: Path): WalkResult

    /**
     * Check if a symlink at the repository root is allowed per contract rules.
     *
     * @param path The symlink path.
     * @return true if allowed, false otherwise.
     */
    fun isAllowedRootContractSymlink(path: Path): Boolean

    /**
     * Result of a safe directory walk.
     */
    data class WalkResult(
        /**
         * Paths of files found during the walk.
         */
        val paths: List<Path>,
        /**
         * Findings for symlink violations or access errors.
         */
        val findings: List<HarnessAstResults.Finding>,
    )
}
