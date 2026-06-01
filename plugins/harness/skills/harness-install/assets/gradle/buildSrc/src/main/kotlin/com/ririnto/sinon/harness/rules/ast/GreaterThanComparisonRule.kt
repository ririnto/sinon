package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import java.nio.file.Path

/**
 * Rule that delegates greater-than comparison findings to source AST/PSI analysis.
 */
object GreaterThanComparisonRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "greaterThanComparison"

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
            ktFile?.accept(Visitor(::add, file, ctx, ktFile))
        }

    override fun formatAst(
        file: Path,
        ktFile: KtFile,
        ctx: RuleContext,
    ): String? {
        val edits = buildSet {
            ktFile.accept(FormatVisitor { edit -> add(edit) })
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

    private fun replacementFor(expression: KtBinaryExpression): String? {
        val left = expression.left ?: return null
        val right = expression.right ?: return null
        if (!left.isSafeOperand() || !right.isSafeOperand()) {
            return null
        }
        if (left is KtNameReferenceExpression && right is KtNameReferenceExpression) {
            return null
        }
        val operator = when (expression.operationToken) {
            KtTokens.GT -> "<"
            KtTokens.GTEQ -> "<="
            else -> return null
        }
        return "${right.text} $operator ${left.text}"
    }

    private fun KtExpression.isSafeOperand(): Boolean =
        this is KtNameReferenceExpression || this is KtConstantExpression

    /**
     * AST visitor for greater-than comparison analysis.
     *
     * Identifies binary expressions using greater-than (>) or
     * greater-than-or-equal (>=) operators.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            super.visitBinaryExpression(expression)
            if (expression.operationToken == KtTokens.GT || expression.operationToken == KtTokens.GTEQ) {
                record(
                    AstFinding(
                        rule = category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, expression.node?.startOffset),
                    ),
                )
            }
        }
    }

    /**
     * Checks whether the given expression contains any PSI comment descendants.
     * Comment-bearing expressions are skipped during formatting to prevent comment loss.
     */
    private fun hasCommentDescendant(expression: KtBinaryExpression): Boolean =
        PsiTreeUtil.findChildOfType(expression, PsiComment::class.java) != null

    /**
     * Text edit to be applied during source formatting.
     */
    private data class TextEdit(
        val startOffset: Int,
        val endOffset: Int,
        val replacement: String,
    )

    /**
     * AST visitor for simple greater-than comparison formatting.
     */
    private class FormatVisitor(
        private val record: (TextEdit) -> Unit,
    ) : KtTreeVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            super.visitBinaryExpression(expression)
            val replacement = replacementFor(expression) ?: return
            if (hasCommentDescendant(expression)) {
                return
            }
            record(
                TextEdit(
                    startOffset = expression.textRange.startOffset,
                    endOffset = expression.textRange.endOffset,
                    replacement = replacement,
                ),
            )
        }
    }
}
