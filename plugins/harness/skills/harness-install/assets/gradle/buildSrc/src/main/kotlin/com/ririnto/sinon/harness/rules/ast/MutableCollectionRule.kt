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
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
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
        ktFile?.accept(
            Visitor(
                { finding -> add(finding) },
                file,
                ctx,
                ktFile,
                MutableConfig(
                    configuredSet(
                        ctx,
                        "forbiddenConstructors",
                        listOf(
                            "mutableListOf",
                            "mutableSetOf",
                            "mutableMapOf",
                            "arrayListOf",
                            "hashSetOf",
                            "hashMapOf",
                            "linkedSetOf",
                            "linkedMapOf",
                            "ArrayList",
                            "HashSet",
                            "HashMap",
                            "LinkedHashMap",
                            "LinkedHashSet",
                            "LinkedList",
                            "TreeMap",
                            "TreeSet",
                        ),
                    ),
                    configuredSet(
                        ctx,
                        "forbiddenTypes",
                        listOf(
                            "MutableList",
                            "MutableSet",
                            "MutableMap",
                            "ArrayList",
                            "HashSet",
                            "HashMap",
                            "LinkedHashMap",
                            "LinkedHashSet",
                            "LinkedList",
                            "TreeMap",
                            "TreeSet",
                        ),
                    ),
                    configuredSet(
                        ctx,
                        "forbiddenFqns",
                        listOf(
                            "java.util.ArrayList",
                            "java.util.HashMap",
                            "java.util.HashSet",
                            "java.util.LinkedHashMap",
                            "java.util.LinkedHashSet",
                            "java.util.LinkedList",
                            "java.util.TreeMap",
                            "java.util.TreeSet",
                            "kotlin.collections.MutableList",
                            "kotlin.collections.MutableMap",
                            "kotlin.collections.MutableSet",
                        ),
                    ),
                    configuredSet(ctx, "accumulationMethods", listOf("add", "addAll", "put", "putAll")),
                    configuredSet(
                        ctx,
                        "allowedBuilders",
                        listOf(
                            "buildList",
                            "buildMap",
                            "buildSet",
                            "dependencies",
                        ),
                    ),
                ),
            ),
        )
    }

    private fun configuredSet(ctx: RuleContext, key: String, defaults: List<String>): Set<String> {
        val configured = ctx.manifest.stringArray(category, key)
        return (configured.ifEmpty { defaults }).toSet()
    }

    private data class MutableConfig(
        val constructors: Set<String>,
        val types: Set<String>,
        val forbiddenFqns: Set<String>,
        val accumulationMethods: Set<String>,
        val allowedBuilders: Set<String>,
    )

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
        private val config: MutableConfig,
    ) : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)
            val calleeName = expression.calleeExpression?.text ?: ""
            val qualifiedName = expression.getQualifiedExpressionForSelector()?.text?.substringBefore("(") ?: calleeName
            val receiverName = qualifiedName.substringBeforeLast(".", "")
            val findingName = when {
                calleeName in config.constructors -> calleeName
                qualifiedName in config.forbiddenFqns -> qualifiedName
                calleeName in config.accumulationMethods && receiverName !in config.allowedBuilders -> calleeName
                else -> ""
            }
            if (findingName.isNotEmpty()) {
                recordFinding(expression, findingName)
            }
        }

        override fun visitUserType(type: KtUserType) {
            super.visitUserType(type)
            val typeName = type.text.substringBefore("<")
            val findingName = when {
                typeName in config.types -> typeName
                typeName in config.forbiddenFqns -> typeName
                else -> ""
            }
            if (findingName.isNotEmpty()) {
                record(
                    AstFinding(
                        rule = category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, type.node?.startOffset),
                        details = mapOf("name" to findingName),
                    ),
                )
            }
        }

        private fun recordFinding(expression: KtCallExpression, name: String) {
            record(
                AstFinding(
                    rule = category,
                    file = AstSupport.relativeFilePath(file, ctx.root),
                    line = AstSupport.lineOf(ktFile, expression.node?.startOffset),
                    details = mapOf("name" to name),
                ),
            )
        }
    }
}
