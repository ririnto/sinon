package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.AstSupport.hasDescendantOfType
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that delegates leaf-function blank-line findings to source AST/PSI analysis.
 */
object LeafFunctionBlankLinesRule : HarnessAstRule() {
    /**
     * Category identifier for this rule.
     */
    override val category: String = "leafFunctionBlankLines"

    override fun renderAstFindings(
        ctx: RuleContext,
        findings: Collection<AstFinding>,
    ): Collection<Finding> {
        val maxBlankLines =
            ctx.manifest
                .categoryObject(category)
                ?.get("parameters")
                ?.jsonObject
                ?.get("maxConsecutiveBlankLines")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toIntOrNull()
                ?.coerceAtLeast(0)
                ?: 1
        return findings
            .filter { finding -> maxBlankLines < finding.intDetail("blankLineCount") }
            .map { finding ->
                Finding(
                    ctx.manifest.severityOf(category),
                    category,
                    "${finding.file}:${finding.line}: leaf function `${finding.detail("function")}` " +
                        "contains too many blank lines; remove or extract the section",
                )
            }
    }

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> =
        buildSet {
            val ktFile = AstSupport.parse(file, astFactory)
            ktFile?.accept(Visitor({ finding -> add(finding) }, file, ctx, ktFile))
        }

    override fun formatAst(
        file: Path,
        ktFile: KtFile,
        ctx: RuleContext,
    ): String? {
        val maxBlankLines = readMaxBlankLines(ctx)
        val edits = buildSet {
            ktFile.accept(FormatVisitor({ edit -> add(edit) }, maxBlankLines))
        }
        if (edits.isEmpty()) {
            return null
        }
        val builder = StringBuilder(ktFile.text)
        edits
            .sortedByDescending { edit -> edit.startOffset }
            .forEach { edit -> builder.replace(edit.startOffset, edit.endOffset, edit.replacement) }
        return builder.toString()
    }

    private fun readMaxBlankLines(ctx: RuleContext): Int =
        ctx.manifest
            .categoryObject(category)
            ?.get("parameters")
            ?.jsonObject
            ?.get("maxConsecutiveBlankLines")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 1

    /**
     * AST visitor for leaf function blank line analysis.
     *
     * Identifies consecutive blank lines within leaf functions that exceed configured limits.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        /**
         * Visit named function declarations.
         */
        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (function.hasDescendantOfType<KtNamedFunction> { nestedFunc -> nestedFunc !== function } ||
                function.hasDescendantOfType<KtLambdaExpression> { true }
            ) {
                return
            }
            function.bodyExpression?.firstChild?.run {
                generateSequence(this) { element -> element.nextSibling }
                    .filter { child -> child is PsiWhiteSpace }
                    .map { child -> child to child.text.count { char -> char == '\n' } - 1 }
                    .filter { pair -> 0 < pair.second }
                    .forEach { pair ->
                        record(
                            AstFinding(
                                rule = "leafFunctionBlankLines",
                                file = AstSupport.relativeFilePath(file, ctx.root),
                                line = AstSupport.lineOf(ktFile, pair.first.node?.startOffset),
                                details =
                                    mapOf(
                                        "function" to (function.name ?: "unknown"),
                                        "blankLineCount" to pair.second.toString(),
                                    ),
                            ),
                        )
                    }
            }
        }
    }

    /**
     * Text edit to be applied during source formatting.
     */
    private data class TextEdit(
        val startOffset: Int,
        val endOffset: Int,
        val replacement: String,
    )

    /**
     * AST visitor for leaf function blank line formatting.
     *
     * Collects text edits to reduce consecutive blank lines exceeding the threshold.
     */
    private class FormatVisitor(
        private val record: (TextEdit) -> Unit,
        private val maxBlankLines: Int,
    ) : KtTreeVisitorVoid() {
        /**
         * Visit named function declarations.
         */
        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (function.hasDescendantOfType<KtNamedFunction> { nestedFunc -> nestedFunc !== function } ||
                function.hasDescendantOfType<KtLambdaExpression> { true }
            ) {
                return
            }
            function.bodyExpression?.firstChild?.run {
                generateSequence(this) { element -> element.nextSibling }
                    .filter { child -> child is PsiWhiteSpace }
                    .forEach { child ->
                        val blankLines = child.text.count { c -> c == '\n' } - 1
                        if (blankLines > maxBlankLines) {
                            val newText = "\n".repeat(maxBlankLines + 1)
                            record(
                                TextEdit(
                                    startOffset = child.textRange.startOffset,
                                    endOffset = child.textRange.endOffset,
                                    replacement = newText,
                                ),
                            )
                        }
                    }
            }
        }
    }
}
