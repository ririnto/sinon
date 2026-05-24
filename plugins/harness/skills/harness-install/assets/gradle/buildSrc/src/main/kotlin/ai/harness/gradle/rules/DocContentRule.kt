package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
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
    override val category: String = "docContent"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val checks = parametersObj?.get("checks")?.jsonArray
        return when {
            catObj == null || parametersObj == null || checks == null -> {
                emptyList()
            }

            else -> {
                checks
                    .mapNotNull { checkElem -> runCatching { checkElem.jsonObject }.getOrNull() }
                    .filter { checkObj ->
                        val files = HarnessCheck.stringArrayFrom(checkObj, "files")
                        val content =
                            files
                                .map { filePath ->
                                    HarnessCheck.readSafe(root, filePath)
                                }.joinToString("\n")
                        !conditionMatches(checkObj, content)
                    }.map { checkObj ->
                        Finding(
                            HarnessCheck.severityOf(manifest, category),
                            category,
                            HarnessCheck.stringFrom(checkObj, "failureMessage"),
                        )
                    }
            }
        }
    }

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
        val asString = runCatching { condition.jsonPrimitive.contentOrNull }.getOrNull()
        if (asString != null) {
            return content.contains(asString)
        }
        val asArray = runCatching { condition.jsonArray }.getOrNull()
        if (asArray != null) {
            return asArray.all { item -> evaluateCondition(item, content) }
        }
        val asObject = runCatching { condition.jsonObject }.getOrNull() ?: return false
        val hasAll = "allOf" in asObject
        val hasAny = "anyOf" in asObject
        val hasContains = "contains" in asObject
        val hasNot = "not" in asObject
        if (!hasAll && !hasAny && !hasContains && !hasNot) {
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

    override fun renderPsiFindings(
        findings: List<ai.harness.gradle.PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> = emptyList()

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: org.jetbrains.kotlin.psi.KtPsiFactory?,
    ): List<ai.harness.gradle.PsiFinding> = emptyList()
}
