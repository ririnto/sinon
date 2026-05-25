package com.ririnto.sinon.harness.rules

import com.ririnto.sinon.harness.core.RuleContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Base class for rules scoped to manifest stack-source configuration.
 *
 * A stack-scope rule applies only when the manifest declares the rule's category
 * with a non-empty `parameters.sourceRoots` array.
 */
abstract class HarnessStackScopeRule : HarnessCheckRule() {
    /**
     * Check whether this rule applies.
     *
     * Returns false if the manifest does not declare `parameters.sourceRoots`
     * for this rule's category, or the declared list is empty.
     *
     * @param ctx The rule context.
     * @return true if the rule is enabled and source roots are configured.
     */
    override fun applies(ctx: RuleContext): Boolean {
        if (!ctx.manifest.isEnabled(category)) {
            return false
        }
        val catObj = ctx.manifest.categoryObject(category) ?: return false
        val parametersObj = catObj["parameters"]?.jsonObject ?: return false
        val sourceRoots = parametersObj["sourceRoots"]?.jsonArray ?: return false
        return sourceRoots.isNotEmpty()
    }
}
