package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import java.nio.file.Path

/**
 * Rule that delegates mutable-collection findings to source AST/PSI analysis.
 */
object MutableCollectionRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "mutableCollection"

    override fun renderAstFindings(ctx: RuleContext, findings: Collection<AstFinding>): Collection<Finding> {
        return buildList {
            findings.forEach { finding ->
                add(
                    Finding(
                        ctx.manifest.severityOf(category),
                        category,
                        "${finding.file}:${finding.line}: mutable collection `${finding.detail("name")}` is forbidden; " +
                            "use immutable alternatives",
                    ),
                )
            }
        }
    }

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> = buildSet {
        val ktFile = AstSupport.parse(file, astFactory)
        val mutableFactories =
            setOf("mutableListOf", "mutableSetOf", "mutableMapOf", "ArrayList", "HashSet", "HashMap", "LinkedHashMap", "LinkedHashSet")
        ktFile?.accept(Visitor(::add, file, ctx, ktFile, mutableFactories))
    }

    /**
     * AST visitor for mutable collection analysis.
     *
     * Identifies uses of mutable collection factories and types throughout the AST.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
        private val mutableFactories: Set<String>,
    ) : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)
            val calleeName = expression.calleeExpression?.text ?: ""
            if (calleeName in mutableFactories) {
                record(
                    AstFinding(
                        rule = category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, expression.node?.startOffset),
                        details = mapOf("name" to calleeName),
                    ),
                )
            }
        }

        override fun visitUserType(type: KtUserType) {
            super.visitUserType(type)
            val typeName = type.text.substringBefore("<")
            if (typeName in mutableFactories) {
                record(
                    AstFinding(
                        rule = category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, type.node?.startOffset),
                        details = mapOf("name" to typeName),
                    ),
                )
            }
        }
    }
}
