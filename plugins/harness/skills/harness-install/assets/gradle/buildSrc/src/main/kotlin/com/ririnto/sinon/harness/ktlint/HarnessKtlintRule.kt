package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.ririnto.sinon.harness.core.RuleContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Base class for harness Kotlin validation rules implemented on the ktlint rule engine.
 *
 * Each rule maps to a harness manifest category. The category supplies the severity, the
 * message template, and rule-specific parameters; this base renders the manifest message
 * template by substituting rule-supplied tokens, while ktlint owns parsing and AST traversal.
 * The `{file}` and `{line}` tokens are resolved later from the ktlint lint-error location.
 */
abstract class HarnessKtlintRule(
    val category: String,
    private val messageTemplate: String,
) : Rule(
        ruleId = RuleId(ruleIdValue(category)),
        about = About(),
    ),
    RuleAutocorrectApproveHandler {
    /**
     * Shared harness rule-set identifier and category-to-rule-id naming helpers.
     */
    companion object {
        /**
         * ktlint rule-set identifier under which every harness Kotlin rule is registered.
         */
        const val RULE_SET_ID: String = "harness"

        /**
         * Compute the fully qualified ktlint rule id for a harness manifest category.
         */
        fun ruleIdValue(category: String): String = "$RULE_SET_ID:${category.toKebabCase()}"

        /**
         * Read the `messages.<key>` template for a category from the manifest.
         */
        fun messageTemplate(
            ctx: RuleContext,
            category: String,
            key: String = "default",
        ): String =
            ctx.manifest
                .categoryObject(category)
                ?.get("messages")
                ?.jsonObject
                ?.get(key)
                ?.jsonPrimitive
                ?.contentOrNull
                ?: ""

        private fun String.toKebabCase(): String =
            replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()
    }

    /**
     * Render the manifest message template by replacing rule-supplied tokens.
     */
    protected fun message(tokens: Map<String, String> = emptyMap()): String =
        tokens.entries.fold(messageTemplate) { acc, entry -> acc.replace("{${entry.key}}", entry.value) }
}
