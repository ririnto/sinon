package com.ririnto.sinon.harness.ast

import kotlinx.serialization.Serializable
import com.ririnto.sinon.harness.core.Severity

/**
 * Aggregated validation findings produced by source AST/PSI analysis.
 */
@Serializable
data class HarnessAstResults(
    val findings: List<Finding>,
) {
    /**
     * A concrete text edit that a fix would apply.
     */
    @Serializable
    data class FindingEdit(
        val file: String,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
        val replacement: String,
    )

    /**
     * Describes how a rule violation would be fixed.
     */
    @Serializable
    data class FindingFix(
        val description: String,
        val safety: FixSafety,
        val edits: List<FindingEdit> = emptyList(),
    )

    /**
     * Represents a validation finding with severity, category, message, and optional
     * biome-lint-style location/fix metadata. Line and column values are 1-indexed.
     */
    @Serializable
    data class Finding(
        val severity: Severity,
        val category: String,
        val message: String,
        val file: String? = null,
        val startLine: Int? = null,
        val startColumn: Int? = null,
        val endLine: Int? = null,
        val endColumn: Int? = null,
        val fix: FindingFix? = null,
    )

    /**
     * Safety classification of a fix associated with a finding.
     */
    @Serializable
    enum class FixSafety { SAFE, UNSAFE, MANUAL }
}
