package com.ririnto.sinon.harness.core

/**
 * Text-edit record for autofix application.
 */
data class HarnessTextEdit(
    /**
     * Start offset (inclusive) in the file text.
     */
    val startOffset: Int,
    /**
     * End offset (exclusive) in the file text.
     */
    val endOffsetExclusive: Int,
    /**
     * Replacement text.
     */
    val replacement: String,
)
