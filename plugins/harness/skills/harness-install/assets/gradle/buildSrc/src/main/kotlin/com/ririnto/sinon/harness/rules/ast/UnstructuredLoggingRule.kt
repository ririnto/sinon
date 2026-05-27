package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * Rule that reports forbidden unstructured Kotlin logging calls.
 */
object UnstructuredLoggingRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "unstructuredLogging"

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
            ktFile?.accept(
                Visitor({ finding -> add(finding) }, file, ctx, ktFile, forbiddenApis(ctx), allowedApis(ctx)),
            )
        }

    private fun forbiddenApis(ctx: RuleContext): Set<String> {
        val forbidden = JsonAccess.stringArrayFromObject(
            ctx.manifest
                .categoryObject(category)
                ?.get("parameters")
                ?.jsonObject,
            "forbiddenLoggingApis"
        )
        return forbidden.takeIf { values -> values.isNotEmpty() }?.toSet() ?: setOf("println")
    }

    private fun allowedApis(ctx: RuleContext): Set<String> {
        return JsonAccess.stringArrayFromObject(
            ctx.manifest
                .categoryObject(category)
                ?.get("parameters")
                ?.jsonObject,
            "allowedLoggingApis"
        ).toSet()
    }

    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
        private val forbiddenApis: Set<String>,
        private val allowedApis: Set<String>,
    ) : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)
            val callName = expression.calleeExpression?.text ?: return
            val qualifiedCall = qualifiedCallName(expression, callName)
            if (callName in allowedApis || qualifiedCall in allowedApis) {
                return
            }
            if (callName in forbiddenApis || qualifiedCall in forbiddenApis) {
                record(
                    AstFinding(
                        rule = UnstructuredLoggingRule.category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, expression.node?.startOffset),
                        details = mapOf("name" to qualifiedCall),
                    ),
                )
            }
        }

        private fun qualifiedCallName(
            expression: KtCallExpression,
            callName: String,
        ): String {
            return when {
                (expression.parent as? KtDotQualifiedExpression)?.selectorExpression == expression -> "${(expression.parent as KtDotQualifiedExpression).receiverExpression.text}.$callName"
                else -> callName
            }
        }
    }
}
