package com.ririnto.sinon.harness.rules

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.core.RuleContext
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Path

/**
 * Base class for AST-based harness validation rules.
 *
 * AST rules are stack-scoped and process source files based on manifest configuration
 * for sourceRoots, extensions, includePaths, and excludePaths.
 */
abstract class HarnessAstRule : HarnessStackScopeRule() {
    /**
     * Render AST scanner details owned by this rule into final findings.
     */
    abstract fun renderAstFindings(
        ctx: RuleContext,
        findings: Collection<AstFinding>,
    ): Collection<Finding>

    /**
     * Return raw AST findings produced directly by this rule.
     */
    abstract fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding>

    override fun validate(ctx: RuleContext): Collection<Finding> = emptyList()
}
