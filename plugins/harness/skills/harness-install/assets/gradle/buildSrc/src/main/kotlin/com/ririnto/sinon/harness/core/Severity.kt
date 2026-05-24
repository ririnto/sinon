package com.ririnto.sinon.harness.core

import kotlinx.serialization.Serializable

/**
 * Represents the severity level of a harness validation finding.
 */
@Serializable
enum class Severity {
    /**
     * Critical validation failure.
     */
    ERROR,

    /**
     * Non-critical validation issue.
     */
    WARN,

    /**
     * Informational validation message.
     */
    INFO,
}
