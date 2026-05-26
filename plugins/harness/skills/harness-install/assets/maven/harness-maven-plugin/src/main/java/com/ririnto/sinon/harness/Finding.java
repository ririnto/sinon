package com.ririnto.sinon.harness;

/**
 * Record representing a validation finding with severity, category, message, and
 * optional structured location and fix metadata. Line and column values are
 * 1-indexed.
 */
public record Finding(
        String severity,
        String category,
        String message,
        String file,
        Integer startLine,
        Integer startColumn,
        Integer endLine,
        Integer endColumn,
        FindingFix fix) {
    /**
     * Convenience factory for the legacy 3-argument shape.
     *
     * @param severity severity level
     * @param category manifest category key
     * @param message  human-readable violation message
     * @return finding with no location or fix metadata
     */
    public static Finding of(String severity, String category, String message) {
        return new Finding(severity, category, message, null, null, null, null, null, null);
    }
}
