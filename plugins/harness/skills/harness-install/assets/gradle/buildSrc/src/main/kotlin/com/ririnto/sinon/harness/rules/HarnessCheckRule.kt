package com.ririnto.sinon.harness.rules

import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding

/**
 * Base class for harness validation rules.
 */
abstract class HarnessCheckRule {
    /**
     * Category key used in the harness manifest and findings.
     */
    abstract val category: String

    /**
     * Check whether this rule applies based on manifest configuration.
     *
     * @param ctx The rule context.
     * @return true if the rule applies, false otherwise.
     */
    open fun applies(ctx: RuleContext): Boolean = ctx.manifest.isEnabled(category)

    /**
     * Validate and return a collection of findings.
     *
     * @param ctx The rule context.
     * @return Collection of findings; empty collection if no issues found.
     */
    abstract fun validate(ctx: RuleContext): Collection<Finding>
}
