package ai.harness.maven;

/**
 * Record to represent a validation finding with severity, category, and message.
 */
public record Finding(String severity, String category, String message) {}
