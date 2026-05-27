package com.ririnto.sinon.harness.reporter

import com.ririnto.sinon.harness.ast.HarnessAstResults
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.Severity
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

/**
 * Renders validation findings in structured diagnostic format.
 */
object FindingReporter {
    /**
     * Renders a list of findings to diagnostic output lines.
     *
     * Returns a list of output lines following structured diagnostic style:
     *
     * - File location header with severity and rule ID
     * - Code snippet with context lines (if file is readable)
     * - Fix metadata (safety, help, before/after diffs)
     * - Summary line at the end
     *
     * @param root Root directory path for resolving relative file paths.
     * @param findings List of validation findings to render.
     * @return List of formatted output lines.
     */
    fun renderFindings(
        root: Path,
        findings: List<Finding>,
    ): List<String> {
        if (findings.isEmpty()) {
            return listOf("OK")
        }
        return buildList {
            findings
                .sortedWith(compareBy({ finding -> finding.severity.ordinal }, { f -> findings.indexOf(f) }))
                .forEach { finding ->
                    renderFinding(root, finding).forEach(::add)
                }
            val (fileCount, errorCount, warnCount, infoCount, fixableCount) = computeSummary(findings)
            add("")
            add(
                "Checked $fileCount file(s). ${findings.size} violation(s): " +
                    "$errorCount error, $warnCount warn, $infoCount info.${when (0 < fixableCount) {
                        true -> " [*] $fixableCount fixable."
                        else -> ""
                    }}",
            )
        }
    }

    private fun renderFinding(
        root: Path,
        finding: Finding,
    ): List<String> =
        buildList {
            add(renderHeader(finding))
            add("")
            if (finding.file != null && finding.startLine != null) {
                val filePath = root / finding.file
                if (filePath.isRegularFile() && filePath.exists()) {
                    renderSnippet(filePath, finding.startLine, finding.startColumn).forEach(::add)
                }
            }
            finding.fix?.let { fix ->
                add("")
                add("Safety: ${fix.safety.name}")
                add("Help: ${fix.description}")
                fix.edits.firstOrNull()?.let { firstEdit ->
                    if (finding.file != null) {
                        add("")
                        add("Before:")
                        extractRemovedText(root / finding.file, firstEdit).forEach { line -> add("- $line") }
                        add("After:")
                        extractAddedText(firstEdit).forEach { line -> add("+ $line") }
                    }
                }
            }
            add("")
        }

    private fun renderHeader(finding: Finding): String {
        val severity = finding.severity.name
        if (finding.file == null) {
            return "[$severity] ${finding.category}: ${finding.message}"
        }
        if (finding.startLine == null) {
            return "${finding.file} [$severity] ${finding.category}: ${finding.message}"
        }
        return "${finding.file}:${finding.startLine}:${finding.startColumn ?: 1} [$severity] ${finding.category}: ${finding.message}"
    }

    private fun renderSnippet(
        filePath: Path,
        startLine: Int,
        startColumn: Int?,
    ): List<String> {
        val lines = filePath.readLines()
        val lineNum = startLine - 1
        if (lineNum < 0 || lines.size <= lineNum) {
            return emptyList()
        }
        val numWidth = lines.size.toString().length
        return buildList {
            if (0 <= lineNum - 1) {
                add("   ${lineNum.toString().padStart(numWidth)} │ ${lines[lineNum - 1]}")
            }
            add("  > ${(lineNum + 1).toString().padStart(numWidth)}  │ ${lines[lineNum]}")
            if (lineNum + 1 < lines.size) {
                add("   ${(lineNum + 2).toString().padStart(numWidth)} │ ${lines[lineNum + 1]}")
            }
        }
    }

    private fun extractRemovedText(
        filePath: Path,
        edit: HarnessAstResults.FindingEdit,
    ): List<String> {
        if (!filePath.isRegularFile() || !filePath.exists()) {
            return emptyList()
        }
        val lines = filePath.readLines()
        val startIdx = edit.startLine - 1
        val endIdx = edit.endLine - 1
        if (startIdx < 0 || lines.size <= endIdx) {
            return emptyList()
        }
        return buildList {
            for (i in startIdx..endIdx) {
                add(
                    when {
                        i == startIdx && i == endIdx -> {
                            lines[i].substring(minOf(edit.startColumn - 1, lines[i].length), minOf(edit.endColumn, lines[i].length))
                        }

                        i == startIdx -> {
                            lines[i].substring(minOf(edit.startColumn - 1, lines[i].length))
                        }

                        i == endIdx -> {
                            lines[i].substring(0, minOf(edit.endColumn, lines[i].length))
                        }

                        else -> {
                            lines[i]
                        }
                    }
                )
            }
        }
    }

    private fun extractAddedText(edit: HarnessAstResults.FindingEdit): List<String> =
        edit.replacement.split("\n").takeIf { lines -> lines.isNotEmpty() } ?: listOf("")

    private fun computeSummary(findings: List<Finding>): Tuple5<Int, Int, Int, Int, Int> =
        Tuple5(
            findings.mapNotNull { finding -> finding.file }.distinct().count(),
            findings.count { finding -> finding.severity == Severity.ERROR },
            findings.count { finding -> finding.severity == Severity.WARN },
            findings.count { finding -> finding.severity == Severity.INFO },
            findings.count { finding -> finding.fix?.safety == HarnessAstResults.FixSafety.SAFE },
        )

    private data class Tuple5<A, B, C, D, E>(
        val a: A,
        val b: B,
        val c: C,
        val d: D,
        val e: E,
    )
}
