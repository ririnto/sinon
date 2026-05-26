package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/**
 * Rule that requires documentation files to contain specified content.
 */
object DocContentRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "docContent"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        ctx.manifest.categoryObject(category)?.get("parameters")?.jsonObject?.get("checks")?.jsonArray?.mapNotNull { checkElem ->
            runCatching { checkElem.jsonObject }.getOrNull()?.let { checkObj ->
                val content = JsonAccess.stringArrayFromObject(checkObj, "files").joinToString("\n") { filePath ->
                    ctx.readSafe(filePath)
                }
                if (!conditionMatches(checkObj, content)) {
                    Finding(
                        ctx.manifest.severityOf(category),
                        category,
                        JsonAccess.stringFromObject(checkObj, "failureMessage"),
                    )
                } else {
                    null
                }
            }
        } ?: emptyList()

    private fun conditionMatches(
        checkObj: JsonObject,
        content: String,
    ): Boolean {
        val condition = checkObj["condition"] ?: checkObj["when"]
        return when {
            condition != null -> evaluateCondition(condition, content)
            else -> false
        }
    }

    private fun evaluateCondition(
        condition: JsonElement,
        content: String,
    ): Boolean {
        runCatching { condition.jsonPrimitive.contentOrNull }.getOrNull()?.let { value ->
            return content.contains(value)
        }
        runCatching { condition.jsonArray }.getOrNull()?.let { items ->
            return items.all { item -> evaluateCondition(item, content) }
        }
        val asObject = runCatching { condition.jsonObject }.getOrNull() ?: return false
        val hasAll = "allOf" in asObject
        val hasAny = "anyOf" in asObject
        val hasContains = "contains" in asObject
        val hasNot = "not" in asObject
        if (!(hasAll || hasAny || hasContains || hasNot)) {
            return false
        }
        val allOfValue = asObject["allOf"]
        val anyOfValue = asObject["anyOf"]
        val allOf = conditionArray(allOfValue)
        val anyOf = conditionArray(anyOfValue)
        val contains = stringArray(asObject["contains"])
        val andMatches = allOf.isEmpty() || allOf.all { item -> evaluateCondition(item, content) }
        val orMatches = !hasAny || (anyOf.isNotEmpty() && anyOf.any { item -> evaluateCondition(item, content) })
        val containsMatches = contains.all { item -> content.contains(item) }
        val notCondition = asObject["not"]
        val notMatches = !hasNot || notCondition?.let { item -> !evaluateCondition(item, content) } == true
        return andMatches && orMatches && containsMatches && notMatches
    }

    private fun conditionArray(value: JsonElement?): List<JsonElement> {
        val asArray = value?.let { item -> runCatching { item.jsonArray }.getOrNull() }
        return when {
            asArray != null -> asArray.toList()
            value != null -> listOf(value)
            else -> emptyList()
        }
    }

    private fun stringArray(value: JsonElement?): List<String> {
        val primitive = value?.let { item -> runCatching { item.jsonPrimitive.contentOrNull }.getOrNull() }
        val array = value?.let { item -> runCatching { item.jsonArray }.getOrNull() }
        return when {
            primitive != null -> listOf(primitive)
            array != null -> array.mapNotNull { item -> runCatching { item.jsonPrimitive.contentOrNull }.getOrNull() }
            else -> emptyList()
        }
    }
}
