package com.ririnto.sinon.harness.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Default implementation of the Manifest interface.
 */
class DefaultManifest(override val raw: JsonObject) : Manifest {
    override fun isEnabled(category: String): Boolean =
        categoryField(category, "enabled")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBooleanStrictOrNull()
            ?: false

    override fun severityOf(category: String): Severity {
        val severityName =
            categoryField(category, "severity")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: return Severity.ERROR
        return Severity.entries.firstOrNull { entry -> entry.name.equals(severityName, ignoreCase = true) } ?: Severity.ERROR
    }

    override fun stringArray(category: String, key: String): List<String> =
        categoryField(category, key)
            ?.jsonArray
            ?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }
            ?: emptyList()

    override fun stringValue(category: String, key: String): String =
        categoryField(category, key)
            ?.jsonPrimitive
            ?.contentOrNull
            ?: ""

    override fun categoryObject(category: String): JsonObject? =
        raw[category]?.jsonObject

    /**
     * Retrieves a field from a category's JSON object.
     *
     * @param category The category name to access.
     * @param key The field key within the category.
     * @return The field as a JsonElement, or null if the category or field does not exist.
     */
    private fun categoryField(category: String, key: String): JsonElement? =
        raw[category]?.jsonObject?.get(key)
}
