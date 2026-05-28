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
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that reports inline fully qualified Kotlin names.
 */
object ImportOverFqnRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "importOverFqn"

    /**
     * Finds Kotlin PSI import-over-FQN findings for one source file.
     */
    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> =
        buildSet {
            val ktFile = astFactory?.createFile("temp", file.readText())
            ktFile?.run {
                val importedNames =
                    importDirectives
                        .mapNotNull { directive ->
                            directive.importedName?.asString()
                        }.toSet()

                fun addFqnFinding(
                    nameParts: List<String>,
                    element: PsiElement,
                ) {
                    if (isPackageQualifiedName(nameParts) && nameParts.lastOrNull() !in importedNames) {
                        add(
                            AstFinding(
                                rule = category,
                                file = file.relativeTo(ctx.root).invariantSeparatorsPathString,
                                line = AstSupport.lineOf(this, element.node?.startOffset),
                                details = mapOf("name" to nameParts.joinToString(".")),
                            ),
                        )
                    }
                }
                accept(Visitor(importedNames, ::addFqnFinding))
            }
        }

    override fun renderAstFindings(
        ctx: RuleContext,
        findings: Collection<AstFinding>,
    ): Collection<Finding> = AstFindingRenderer.renderEach(findings.toList(), ctx.manifest.raw)

    /**
     * Checks if the given name looks like a package-qualified name.
     *
     * A package-qualified name has at least 3 parts separated by dots,
     * with the first two parts being lowercase (package parts) and the
     * last part being uppercase (class or type name).
     *
     * @param parts Qualified name segments to check.
     * @return True if the name matches the package-qualified pattern.
     */
    private fun isPackageQualifiedName(parts: List<String>): Boolean =
        3 <= parts.size && parts[0].firstOrNull()?.isLowerCase() == true &&
            parts[1].firstOrNull()?.isLowerCase() == true &&
            parts.last().firstOrNull()?.isUpperCase() == true

    private class Visitor(
        private val importedNames: Set<String>,
        private val addFqnFinding: (List<String>, PsiElement) -> Unit,
    ) : KtTreeVisitorVoid() {
        override fun visitUserType(userType: KtUserType) {
            super.visitUserType(userType)
            if (generateSequence(userType as PsiElement?) { element -> element.parent }.any { element ->
                    element is KtImportDirective
                }
            ) {
                return
            }
            if (userType.parent is KtUserType) {
                return
            }
            val fqnParts =
                generateSequence(userType) { parent -> parent.qualifier }
                    .mapNotNull { ut ->
                        ut.referencedName
                    }.toList()
                    .asReversed()
            if (2 <= fqnParts.size && fqnParts.first() !in importedNames) {
                addFqnFinding(fqnParts, userType)
            }
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            super.visitDotQualifiedExpression(expression)
            if (generateSequence(expression as PsiElement?) { element -> element.parent }.any { element ->
                    element is KtImportDirective
                }
            ) {
                return
            }
            if (expression.parent is KtDotQualifiedExpression) {
                return
            }
            addFqnFinding(expressionParts(expression.receiverExpression), expression.receiverExpression)
        }

        private fun expressionParts(expression: KtExpression): List<String> =
            (when (expression) {
                is KtNameReferenceExpression -> listOf(expression.getReferencedName())
                is KtDotQualifiedExpression -> expressionParts(expression.receiverExpression) +
                    expression.selectorExpression?.let(::expressionParts).orEmpty()
                else -> emptyList()
            })
    }
}
