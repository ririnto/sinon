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
 * Renders validation findings in biome+ruff hybrid diagnostic format.
 */
object FindingReporter {
    /**
     * Renders a list of findings to diagnostic output lines.
     *
     * Returns a list of output lines following biome+ruff style:
     * - File location header with severity and rule ID
     * - Code snippet with context lines (if file is readable)
     * - Fix metadata (safety, help, before/after diffs)
     * - Summary line at the end
     *
     * @param root Root directory path for resolving relative file paths.
     * @param findings List of validation findings to render.
     * @return List of formatted output lines.
     */
    fun renderFindings(root: Path, findings: List<Finding>): List<String> {
        if (findings.isEmpty()) return listOf("OK")
        return buildList {
            findings.sortedWith(compareBy({ finding -> finding.severity.ordinal }, { findings.indexOf(it) }))
                .forEach { finding ->
                    renderFinding(root, finding).forEach(::add)
                }
            val (fileCount, errorCount, warnCount, infoCount, fixableCount) = computeSummary(findings)
            add("")
            val fixableLabel = if (fixableCount > 0) " [*] $fixableCount fixable." else ""
            add("Checked $fileCount file(s). ${findings.size} violation(s): $errorCount error, $warnCount warn, $infoCount info.$fixableLabel")
        }
    }

    private fun renderFinding(root: Path, finding: Finding): List<String> = buildList {
        val headerLine = renderHeader(finding)
        add(headerLine)
        add("")
        if (finding.file != null && finding.startLine != null) {
            val filePath = root / finding.file
            if (filePath.isRegularFile() && filePath.exists()) {
                renderSnippet(filePath, finding.startLine, finding.startColumn).forEach(::add)
            }
        }
        if (finding.fix != null) {
            add("")
            val safetyLabel = finding.fix.safety.name
            add("Safety: $safetyLabel")
            add("Help: ${finding.fix.description}")
            val firstEdit = finding.fix.edits.firstOrNull()
            if (firstEdit != null && finding.file != null) {
                add("")
                add("Before:")
                extractRemovedText(root / finding.file, firstEdit).forEach { line -> add("- $line") }
                add("After:")
                extractAddedText(firstEdit).forEach { line -> add("+ $line") }
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
        val column = finding.startColumn ?: 1
        return "${finding.file}:${finding.startLine}:$column [$severity] ${finding.category}: ${finding.message}"
    }

    private fun renderSnippet(filePath: Path, startLine: Int, startColumn: Int?): List<String> {
        val lines = filePath.readLines()
        val lineNum = startLine - 1
        if (lineNum < 0 || lineNum >= lines.size) return emptyList()
        return buildList {
            val numWidth = lines.size.toString().length
            val beforeLine = lineNum - 1
            if (beforeLine >= 0) {
                add("   ${(beforeLine + 1).toString().padStart(numWidth)} │ ${lines[beforeLine]}")
            }
            add("  > ${(lineNum + 1).toString().padStart(numWidth)}  │ ${lines[lineNum]}")
            val afterLine = lineNum + 1
            if (afterLine < lines.size) {
                add("   ${(afterLine + 1).toString().padStart(numWidth)} │ ${lines[afterLine]}")
            }
        }
    }

    private fun extractRemovedText(filePath: Path, edit: HarnessAstResults.FindingEdit): List<String> {
        if (!filePath.isRegularFile() || !filePath.exists()) return emptyList()
        val lines = filePath.readLines()
        val startIdx = edit.startLine - 1
        val endIdx = edit.endLine - 1
        if (startIdx < 0 || endIdx >= lines.size) return emptyList()
        return buildList {
            for (i in startIdx..endIdx) {
                val line = lines[i]
                val trimmedLine = when {
                    i == startIdx && i == endIdx ->
                        line.substring(minOf(edit.startColumn - 1, line.length), minOf(edit.endColumn, line.length))
                    i == startIdx -> line.substring(minOf(edit.startColumn - 1, line.length))
                    i == endIdx -> line.substring(0, minOf(edit.endColumn, line.length))
                    else -> line
                }
                add(trimmedLine)
            }
        }
    }

    private fun extractAddedText(edit: HarnessAstResults.FindingEdit): List<String> {
        val lines = edit.replacement.split("\n")
        return if (lines.isEmpty()) listOf("") else lines
    }

    private fun computeSummary(findings: List<Finding>): Tuple5<Int, Int, Int, Int, Int> {
        val distinctFiles = findings.mapNotNull { it.file }.distinct().count()
        val errorCount = findings.count { it.severity == Severity.ERROR }
        val warnCount = findings.count { it.severity == Severity.WARN }
        val infoCount = findings.count { it.severity == Severity.INFO }
        val fixableCount = findings.count { it.fix?.safety == HarnessAstResults.FixSafety.SAFE }
        return Tuple5(distinctFiles, errorCount, warnCount, infoCount, fixableCount)
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
