package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.Severity
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.pathString

/**
 * Directory path resolver for manifest-controlled empty-directory checks.
 */
internal object SafeDirectoryPath {
    /**
     * Validates and resolves a directory path from manifest configuration.
     *
     * @param root Project root directory.
     * @param directoryPath Relative directory path string from manifest.
     * @param addFindingSeverity Severity for path-validation findings.
     * @param addFinding Callback to record findings.
     * @return Resolved directory path, or null if the path is invalid.
     */
    fun resolve(
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

        if ((0..<path.nameCount).any { index ->
                val segment = path.getName(index).pathString
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
        if (HasSymlinkComponent.check(root, resolvedDirectory)) {
            addFinding(
                Finding(
                    Severity.ERROR,
                    "symlinkSafety",
                    "symlink directory is not allowed: $directoryPath",
                ),
            )
            return null
        }

        if (!IsContainedByRoot.check(root, resolvedDirectory)) {
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
}
