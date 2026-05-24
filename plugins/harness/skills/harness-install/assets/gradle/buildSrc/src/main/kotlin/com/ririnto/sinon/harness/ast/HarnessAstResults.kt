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
     * Represents a validation finding with severity, category, and message.
     */
    @Serializable
    data class Finding(
        val severity: Severity,
        val category: String,
        val message: String,
    )
}
