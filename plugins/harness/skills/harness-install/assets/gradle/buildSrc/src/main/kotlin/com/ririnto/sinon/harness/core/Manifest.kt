package com.ririnto.sinon.harness.core

import kotlinx.serialization.json.JsonObject

/**
 * Abstraction over harness validation manifest configuration.
 */
interface Manifest {
    /**
     * The raw JSON manifest object.
     */
    val raw: JsonObject

    /**
     * Check if a category is enabled in the manifest.
     *
     * @param category The category name.
     * @return true if enabled, false otherwise.
     */
    fun isEnabled(category: String): Boolean

    /**
     * Get the severity level for a category.
     *
     * @param category The category name.
     * @return The severity level, defaulting to ERROR if not found.
     */
    fun severityOf(category: String): Severity

    /**
     * Extract a string array from a category's parameters.
     *
     * @param category The category name.
     * @param key The parameter key.
     * @return List of strings; empty if not found.
     */
    fun stringArray(
        category: String,
        key: String,
    ): List<String>

    /**
     * Extract a string value from a category's parameters.
     *
     * @param category The category name.
     * @param key The parameter key.
     * @return The string value; empty string if not found.
     */
    fun stringValue(
        category: String,
        key: String,
    ): String

    /**
     * Get the category's JSON object.
     *
     * @param category The category name.
     * @return The category object; null if not found.
     */
    fun categoryObject(category: String): JsonObject?
}
