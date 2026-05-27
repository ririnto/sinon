package com.ririnto.sinon.harness.rules

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Path

/**
 * Base class for AST-based harness validation rules.
 *
 * AST rules are stack-scoped and process source files based on manifest configuration
 * for sourceRoots, extensions, includePaths, and excludePaths.
 */
abstract class HarnessAstRule : HarnessStackScopeRule() {
    override fun validate(ctx: RuleContext): Collection<Finding> = emptyList()

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

    /**
     * Compute formatted source text for this file, when this rule supports auto-formatting.
     *
     * Rules without an automatic AST-based fix MUST keep this default and return null.
     *
     * @param file Absolute path of the source file (read-only; do not write).
     * @param ktFile Parsed Kotlin PSI file.
     * @param ctx Rule execution context.
     * @return New source text when changes are needed, or null when the file is already conformant.
     */
    open fun formatAst(
        file: Path,
        ktFile: KtFile,
        ctx: RuleContext,
    ): String? = null
}
