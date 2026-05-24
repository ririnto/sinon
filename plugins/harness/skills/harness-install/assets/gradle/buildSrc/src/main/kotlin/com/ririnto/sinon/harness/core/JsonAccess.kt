package com.ririnto.sinon.harness.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Utilities for extracting values from JSON objects.
 */
object JsonAccess {
    /**
     * Extracts a string value from a JSON object by key.
     */
    fun stringFromObject(obj: JsonObject?, key: String): String =
        obj?.get(key)?.jsonPrimitive?.contentOrNull ?: ""

    /**
     * Extracts a string array from a JSON object by key.
     */
    fun stringArrayFromObject(obj: JsonObject?, key: String): List<String> =
        obj
            ?.get(key)
            ?.jsonArray
            ?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }
            ?: emptyList()
}
