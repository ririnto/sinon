package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Flags inline fully qualified Kotlin names that could be imported instead.
 */
class ImportOverFqn :
    Rule(
        ruleId = RuleId("code:import-over-fqn"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            val imports = ktFile.importDirectives
            val importNamesToPaths: Map<String, List<String>> =
                imports
                    .mapNotNull { directive ->
                        directive.importedName?.asString()?.let { name ->
                            directive.importPath?.pathStr?.let { path -> name to path }
                        }
                    }.groupBy({ pair -> pair.first }, { pair -> pair.second })
            val aliasNames: Set<String> = imports.mapNotNull { directive -> directive.aliasName }.toSet()
            val aliasedImportPaths: Set<String> =
                imports
                    .filter { directive -> directive.aliasName !== null }
                    .mapNotNull { directive -> directive.importPath?.pathStr }
                    .toSet()
            val declaredNames: Set<String> =
                PsiTreeUtil
                    .findChildrenOfType(ktFile, KtNamedDeclaration::class.java)
                    .mapNotNull { declaration -> declaration.name }
                    .toSet()
            val existingPaths = imports.mapNotNull { directive -> directive.importPath?.pathStr }.toSet()
            val findings = mutableListOf<FqnFinding>()
            ktFile.accept(
                Visitor { nameParts, replacementElement ->
                    if (3 <= nameParts.size &&
                        nameParts[0].firstOrNull()?.isLowerCase() == true &&
                        nameParts[1].firstOrNull()?.isLowerCase() == true &&
                        nameParts.last().firstOrNull()?.isUpperCase() == true
                    ) {
                        findings.add(
                            FqnFinding(
                                nameParts = nameParts,
                                replacementElement = replacementElement,
                                importPath = nameParts.joinToString("."),
                                simpleName = nameParts.last()
                            )
                        )
                    }
                }
            )
            val candidatePathsBySimpleName =
                findings
                    .groupBy { finding -> finding.simpleName }
                    .mapValues { (_, group) -> group.map { finding -> finding.importPath }.toSet() }
            val newImports =
                buildSet {
                    findings.forEach { finding ->
                        val sameNamePaths = importNamesToPaths[finding.simpleName].orEmpty()
                        val candidatePaths = candidatePathsBySimpleName[finding.simpleName].orEmpty()
                        val resolvesUnambiguously =
                            candidatePaths.all { path -> path == finding.importPath } &&
                                sameNamePaths.all { path -> path == finding.importPath }
                        emit(
                            finding.replacementElement.textOffset,
                            "fully qualified name `${finding.nameParts.joinToString(
                                "."
                            )}` used inline; add an import and use the simple name",
                            resolvesUnambiguously &&
                                finding.importPath !in aliasedImportPaths &&
                                finding.simpleName !in aliasNames &&
                                finding.simpleName !in declaredNames &&
                                imports.none { directive -> directive.isAllUnder } &&
                                ktFile.packageFqName.asString() != finding.nameParts.dropLast(1).joinToString(".")
                        ).ifAutocorrectAllowed {
                            finding.replacementElement.node.replaceWith(
                                KtPsiFactory
                                    .contextual(finding.replacementElement, false)
                                    .createExpression(
                                        finding.replacementElement.text.replaceFirst(
                                            finding.nameParts.joinToString("."),
                                            finding.simpleName
                                        )
                                    ).node
                            )
                            add(finding.importPath)
                        }
                    }
                }.filter { path -> path !in existingPaths }.sorted()
            ktFile.importList?.let { importList ->
                val importsAreSorted =
                    importList.imports
                        .mapNotNull { directive -> directive.importPath?.pathStr }
                        .let { paths -> paths == paths.sorted() }
                newImports.forEach { path ->
                    val importNode =
                        KtPsiFactory
                            .contextual(ktFile, false)
                            .createImportDirective(ImportPath(FqName(path), false, null))
                            .node
                    val anchor =
                        when (importsAreSorted) {
                            true -> {
                                importList.imports
                                    .firstOrNull { directive ->
                                        path < directive.importPath?.pathStr.orEmpty()
                                    }?.node
                            }

                            else -> {
                                null
                            }
                        }
                    importList.node.addChild(importNode, anchor)
                    importList.node.addChild(
                        KtPsiFactory.contextual(ktFile, false).createWhiteSpace("\n").node,
                        anchor
                    )
                }
                if (imports.isEmpty() && newImports.isNotEmpty()) {
                    importList.node.addChild(
                        KtPsiFactory.contextual(ktFile, false).createWhiteSpace("\n").node,
                        null
                    )
                }
            } ?: run {
                val anchor = ktFile.declarations.firstOrNull()?.node
                newImports.asReversed().forEach { path ->
                    val importNode =
                        KtPsiFactory
                            .contextual(ktFile, false)
                            .createImportDirective(ImportPath(FqName(path), false, null))
                            .node
                    ktFile.node.addChild(importNode, anchor)
                    ktFile.node.addChild(
                        KtPsiFactory.contextual(ktFile, false).createWhiteSpace("\n").node,
                        anchor
                    )
                }
                if (newImports.isNotEmpty()) {
                    ktFile.node.addChild(
                        KtPsiFactory.contextual(ktFile, false).createWhiteSpace("\n\n").node,
                        anchor
                    )
                }
            }
        }
    }

    private data class FqnFinding(
        val nameParts: List<String>,
        val replacementElement: PsiElement,
        val importPath: String,
        val simpleName: String
    )

    private class Visitor(
        private val onFqnFinding: (
            nameParts: List<String>,
            replacementElement: PsiElement
        ) -> Unit
    ) : KtTreeVisitorVoid() {
        override fun visitUserType(userType: KtUserType) {
            super.visitUserType(userType)
            if (
                generateSequence(userType as PsiElement?) { element -> element.parent }.none { element ->
                    element is KtImportDirective
                } &&
                userType.parent !is KtUserType
            ) {
                val fqnParts =
                    generateSequence(userType) { parent -> parent.qualifier }
                        .mapNotNull { ut -> ut.referencedName }
                        .toList()
                        .asReversed()
                if (2 <= fqnParts.size) {
                    onFqnFinding(fqnParts, userType)
                }
            }
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            super.visitDotQualifiedExpression(expression)
            if (
                generateSequence(expression as PsiElement?) { element -> element.parent }.none { element ->
                    element is KtImportDirective
                } &&
                expression.parent !is KtDotQualifiedExpression
            ) {
                val parts = expression.expressionParts()
                val classIndex = parts.indexOfFirst { part -> part.firstOrNull()?.isUpperCase() == true }
                if (2 <= classIndex) {
                    onFqnFinding(parts.take(classIndex + 1), expression)
                }
            }
        }

        private fun KtExpression.expressionParts(): List<String> =
            when (this) {
                is KtNameReferenceExpression -> {
                    listOf(getReferencedName())
                }

                is KtDotQualifiedExpression -> {
                    receiverExpression.expressionParts() +
                        selectorExpression?.expressionParts().orEmpty()
                }

                is KtCallExpression -> {
                    calleeExpression?.expressionParts().orEmpty()
                }

                else -> {
                    emptyList()
                }
            }
    }
}
