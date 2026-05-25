package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;

/**
 * Common base for AST-based validation rules. Provides a default applies()
 * implementation that consults manifest stack-source configuration so concrete
 * rules need only provide category() and validate().
 */
public interface AstRule extends HarnessCheckRule {
    @Override
    default boolean applies(RuleContext ctx) {
        final String cat = category();
        if (!ctx.manifest().isEnabled(cat)) {
            return false;
        }
        final var categoryNode = ctx.manifest().raw().get(cat);
        if (categoryNode == null || !categoryNode.has("parameters")) {
            return false;
        }
        final var parameters = categoryNode.get("parameters");
        final var roots = parameters.get("sourceRoots");
        return roots != null && roots.isArray() && !roots.isEmpty();
    }
}
