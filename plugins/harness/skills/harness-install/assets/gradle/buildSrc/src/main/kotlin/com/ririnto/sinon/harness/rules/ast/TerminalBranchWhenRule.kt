package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.rules.HarnessAstRule
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Rule that delegates terminal-if findings to source AST/PSI analysis.
 */
object TerminalBranchWhenRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "terminalBranchWhen"

    override fun renderAstFindings(
        ctx: RuleContext,
        findings: Collection<AstFinding>,
    ): Collection<Finding> =
        buildList {
            findings.forEach { finding ->
                add(
                    Finding(
                        ctx.manifest.severityOf(category),
                        category,
                        "${finding.file}:${finding.line}: terminal if expression in ${finding.detail(
                            "context",
                        )}; use when instead",
                    ),
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
            ktFile?.accept(Visitor(::add, file, ctx, ktFile))
        }

    private fun terminalIfElseExpressions(expression: PsiElement?): List<KtIfExpression> =
        buildList {
            when (expression) {
                is KtIfExpression -> {
                    if (expression.`else` != null) {
                        add(expression)
                    }
                }
                is KtReturnExpression -> addAll(terminalIfElseExpressions(expression.returnedExpression))
            }
        }

    /**
     * AST visitor for terminal branch when analysis.
     *
     * Identifies terminal if-else expressions that should use when instead.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
    ) : KtTreeVisitorVoid() {
        override fun visitBlockExpression(expression: KtBlockExpression) {
            super.visitBlockExpression(expression)
            terminalIfElseExpressions(expression.statements.lastOrNull()).forEach { terminalIf ->
                record(
                    AstFinding(
                        rule = "terminalBranchWhen",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, terminalIf.node?.startOffset),
                        details = mapOf("context" to "block"),
                    ),
                )
            }
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            terminalIfElseExpressions(function.bodyExpression).forEach { terminalIf ->
                record(
                    AstFinding(
                        rule = "terminalBranchWhen",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, terminalIf.node?.startOffset),
                        details = mapOf("context" to "function `${function.name ?: "unknown"}`"),
                    ),
                )
            }
        }

        override fun visitProperty(property: KtProperty) {
            super.visitProperty(property)
            if (property.isLocal) {
                return
            }
            terminalIfElseExpressions(property.initializer).forEach { terminalIf ->
                record(
                    AstFinding(
                        rule = "terminalBranchWhen",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, terminalIf.node?.startOffset),
                        details = mapOf("context" to "property `${property.name ?: "property"}`"),
                    ),
                )
            }
        }
    }
}
