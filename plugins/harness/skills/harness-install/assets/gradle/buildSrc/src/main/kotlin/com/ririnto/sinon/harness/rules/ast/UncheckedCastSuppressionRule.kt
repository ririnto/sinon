package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Verifies that @Suppress("UNCHECKED_CAST") annotations are not used in Kotlin code.
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
    ): Collection<AstFinding> = buildSet {
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
    internal fun extractStringValue(expr: KtStringTemplateExpression): String {
        return when {
            expr.entries.all { entry -> entry is KtLiteralStringTemplateEntry } -> {
                expr.entries.joinToString("") { entry -> entry.text }
            }
            else -> ""
        }
    }

    /**
     * AST visitor for finding @Suppress("UNCHECKED_CAST") annotations.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitAnnotationEntry(annotation: KtAnnotationEntry) {
            super.visitAnnotationEntry(annotation)
            if (annotation.shortName?.asString() == "Suppress") {
                for (arg in annotation.valueArguments) {
                    val argExpr = arg.getArgumentExpression()
                    if (argExpr is KtStringTemplateExpression) {
                        val stringValue = UncheckedCastSuppressionRule.extractStringValue(argExpr)
                        if (stringValue == "UNCHECKED_CAST") {
                            record(
                                AstFinding(
                                    rule = UncheckedCastSuppressionRule.category,
                                    file = AstSupport.relativeFilePath(file, ctx.root),
                                    line = AstSupport.lineOf(ktFile, annotation.node?.startOffset),
                                    details = mapOf(
                                        "snippet" to annotation.text,
                                    ),
                                ),
                            )
                            return
                        }
                    }
                }
            }
        }
    }
}
