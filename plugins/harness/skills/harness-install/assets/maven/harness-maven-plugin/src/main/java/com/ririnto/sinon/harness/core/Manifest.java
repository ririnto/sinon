package com.ririnto.sinon.harness.core;

import tools.jackson.databind.JsonNode;
import java.util.List;

/**
 * Typed access facade for the harness manifest.
 */
public interface Manifest {
    /**
     * Returns the raw manifest JSON node for advanced rule queries.
     *
     * @return raw manifest JSON node
     */
    JsonNode raw();

    /**
     * Returns whether a category is enabled in the manifest.
     *
     * @param category manifest category key
     * @return true when the category is present and not disabled
     */
    boolean isEnabled(String category);

    /**
     * Returns the configured severity for a category.
     *
     * @param category manifest category key
     * @return severity value, defaulting to ERROR
     */
    Severity severityOf(String category);

    /**
     * Returns a string array parameter from a category.
     *
     * @param category manifest category key
     * @param key parameter key
     * @return string values, or an empty list when missing or invalid
     */
    List<String> stringArray(String category, String key);

    /**
     * Returns a string parameter from a category.
     *
     * @param category manifest category key
     * @param key parameter key
     * @return string value, or an empty string when missing or invalid
     */
    String stringValue(String category, String key);

    /**
     * Returns a category JSON object.
     *
     * @param category manifest category key
     * @return category object, or null when missing or invalid
     */
    JsonNode categoryObject(String category);
}
