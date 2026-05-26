package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Verifies that @Suppress annotations do not use forbidden suppression tokens.
 *
 * Detects all argument forms:
 * - @Suppress("TOKEN") — positional single
 * - @Suppress("TOKEN", "OTHER") — positional multiple
 * - @Suppress(value = ["TOKEN"]) — named array single
 * - @Suppress(value = ["TOKEN", "OTHER"]) — named array multiple
 *
 * Configurable via parameters.forbiddenSuppressions and parameters.allowedSuppressions.
 */
object UncheckedCastSuppressionRule : HarnessAstRule() {
    /**
     * Category key for unchecked cast suppression findings.
     */
    override val category: String = "uncheckedCastSuppression"

    override fun renderAstFindings(
        ctx: RuleContext,
        findings: Collection<AstFinding>,
    ): Collection<Finding> = AstFindingRenderer.renderEach(findings.toList(), ctx.manifest.raw)

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> =
        buildSet {
            val ktFile = AstSupport.parse(file, astFactory)
            ktFile?.accept(Visitor({ finding -> add(finding) }, file, ctx, ktFile))
        }

    /**
     * Extracts the string value from a Kotlin string template expression.
     *
     * Returns the concatenated text of all literal string template entries,
     * or an empty string if the expression contains any non-literal entries.
     *
     * @param expr The string template expression to extract from.
     * @return The string value, or empty string if expression is not purely literal.
     */
    internal fun extractStringValue(expr: KtStringTemplateExpression): String =
        when {
            expr.entries.all { entry -> entry is KtLiteralStringTemplateEntry } -> {
                expr.entries.joinToString("") { entry -> entry.text }
            }

            else -> {
                ""
            }
        }

    /**
     * Resolves forbiddenSuppressions from manifest parameters.
     *
     * Reads parameters.forbiddenSuppressions from the manifest section,
     * defaulting to ["UNCHECKED_CAST"] when missing.
     *
     * @param ctx Rule execution context carrying manifest payload.
     * @return Set of forbidden suppression tokens.
     */
    internal fun resolveForbiddenSuppressions(ctx: RuleContext): Set<String> {
        val manifest = ctx.manifest.raw
        val section = manifest[category] as? Map<*, *> ?: return setOf("UNCHECKED_CAST")
        val params = section["parameters"] as? Map<*, *> ?: return setOf("UNCHECKED_CAST")
        val tokens = params["forbiddenSuppressions"] as? List<*> ?: return setOf("UNCHECKED_CAST")
        return tokens.filterIsInstance<String>().toSet()
    }

    /**
     * Resolves allowedSuppressions from manifest parameters.
     *
     * @param ctx Rule execution context carrying manifest payload.
     * @return Set of allowed suppression tokens.
     */
    internal fun resolveAllowedSuppressions(ctx: RuleContext): Set<String> {
        val manifest = ctx.manifest.raw
        val section = manifest[category] as? Map<*, *> ?: return emptySet()
        val params = section["parameters"] as? Map<*, *> ?: return emptySet()
        val tokens = params["allowedSuppressions"] as? List<*> ?: return emptySet()
        return tokens.filterIsInstance<String>().toSet()
    }

    /**
     * AST visitor for finding @Suppress annotations with forbidden tokens.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        private val forbiddenTokens = resolveForbiddenSuppressions(ctx)
        private val allowedTokens = resolveAllowedSuppressions(ctx)

        override fun visitAnnotationEntry(annotation: KtAnnotationEntry) {
            super.visitAnnotationEntry(annotation)
            if (annotation.shortName?.asString() == "Suppress") {
                val foundTokens = extractSuppressTokens(annotation)
                val matchingTokens = foundTokens.intersect(forbiddenTokens - allowedTokens)
                if (matchingTokens.isNotEmpty()) {
                    record(
                        AstFinding(
                            rule = UncheckedCastSuppressionRule.category,
                            file = AstSupport.relativeFilePath(file, ctx.root),
                            line = AstSupport.lineOf(ktFile, annotation.node?.startOffset),
                            details =
                                mapOf(
                                    "snippet" to annotation.text,
                                ),
                        ),
                    )
                }
            }
        }

        /**
         * Extracts all string literals from a @Suppress annotation argument list.
         *
         * Handles:
         * - Positional args: @Suppress("TOKEN1", "TOKEN2")
         * - Named array: @Suppress(value = ["TOKEN1", "TOKEN2"])
         * - Named single: @Suppress(value = "TOKEN1")
         *
         * @param annotation The @Suppress annotation entry.
         * @return Set of extracted string values.
         */
        private fun extractSuppressTokens(annotation: KtAnnotationEntry): Set<String> =
            buildSet {
                for (arg in annotation.valueArguments) {
                    val argExpr = arg.getArgumentExpression()
                    when {
                        argExpr is KtStringTemplateExpression -> {
                            val stringValue = extractStringValue(argExpr)
                            if (stringValue.isNotEmpty()) {
                                add(stringValue)
                            }
                        }

                        argExpr != null && argExpr.text.startsWith("[") -> {
                            for (token in arrayLiteralTokens(argExpr)) {
                                add(token)
                            }
                        }
                    }
                }
            }

        /**
         * Extracts string literals from an array literal (e.g., ["TOKEN1", "TOKEN2"]).
         *
         * @param arrayExpr The array expression AST node.
         * @return List of extracted string values in source order.
         */
        private fun arrayLiteralTokens(arrayExpr: Any): List<String> =
            arrayExpr
                .toString()
                .trim()
                .removeSurrounding("[", "]")
                .split(Regex(",\\s*"))
                .map { elemText -> elemText.trim().removeSurrounding("\"") }
                .filter { trimmed -> trimmed.isNotEmpty() }
    }
}
