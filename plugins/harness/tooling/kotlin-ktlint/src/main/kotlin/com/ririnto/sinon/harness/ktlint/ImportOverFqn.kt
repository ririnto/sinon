package com.ririnto.sinon.harness.ktlint

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
import org.jetbrains.kotlin.psi.KtImportList
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
        ruleId = RuleId("harness:import-over-fqn"),
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
            val findings = collectFqnFindings(ktFile)
            val candidatePathsBySimpleName =
                findings
                    .groupBy { finding -> finding.simpleName }
                    .mapValues { (_, group) -> group.map { finding -> finding.importPath }.toSet() }
            val newImports =
                buildSet {
                    findings.forEach { finding ->
                        val resolvesUnambiguously =
                            candidatePathsBySimpleName[finding.simpleName].orEmpty().all { path -> path == finding.importPath } &&
                                importNamesToPaths[finding.simpleName].orEmpty().all { path -> path == finding.importPath }
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
            insertImports(ktFile, imports, newImports)
        }
    }

    private fun collectFqnFindings(ktFile: KtFile): List<FqnFinding> =
        buildList {
            ktFile.accept(
                Visitor { nameParts, replacementElement ->
                    if (3 <= nameParts.size &&
                        nameParts[0].firstOrNull()?.isLowerCase() == true &&
                        nameParts[1].firstOrNull()?.isLowerCase() == true &&
                        nameParts.last().firstOrNull()?.isUpperCase() == true
                    ) {
                        add(
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
        }

    /**
     * Inserts imports into the import list or creates the import block.
     *
     * An empty import list carries no separators of its own, so the surrounding whitespace has
     * to be added explicitly: a blank line after a preceding package directive, one newline
     * between consecutive imports, and a blank line before the following code. A sibling only
     * counts as an existing separator when it is whitespace that starts on a new line; in a
     * packageless script the next sibling is the code itself, which merely ends in a newline.
     */
    private fun insertImports(
        ktFile: KtFile,
        imports: List<KtImportDirective>,
        newImports: List<String>
    ) {
        ktFile.importList?.let { importList ->
            val importsAreSorted =
                importList.imports
                    .mapNotNull { directive -> directive.importPath?.pathStr }
                    .let { paths -> paths == paths.sorted() }
            if (imports.isEmpty() && newImports.isNotEmpty()) {
                addImportsToEmptyList(ktFile, importList, newImports)
            }
            if (imports.isNotEmpty()) {
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
                        when {
                            anchor == null && imports.isNotEmpty() -> importNode
                            else -> anchor
                        }
                    )
                }
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

    /**
     * Inserts imports into an import list that has no imports yet.
     *
     * An empty import list carries no separators of its own, so the surrounding whitespace has
     * to be added explicitly: a blank line after a preceding package directive, one newline
     * between consecutive imports, and a blank line before the following code. A sibling only
     * counts as an existing separator when it is whitespace that starts on a new line; in a
     * packageless script the next sibling is the code itself, which merely ends in a newline.
     */
    private fun addImportsToEmptyList(
        ktFile: KtFile,
        importList: KtImportList,
        newImports: List<String>
    ) {
        val factory = KtPsiFactory.contextual(ktFile, false)
        val preceding = importList.node.treePrev
        if (preceding?.text?.isNotBlank() == true && !preceding.text.endsWith("\n")) {
            importList.node.addChild(factory.createWhiteSpace("\n\n").node, null)
        }
        newImports.forEachIndexed { index, path ->
            importList.node.addChild(
                factory.createImportDirective(ImportPath(FqName(path), false, null)).node,
                null
            )
            if (index < newImports.lastIndex) {
                importList.node.addChild(factory.createWhiteSpace("\n").node, null)
            }
        }
        if (importList.node.treeNext
                ?.text
                ?.startsWith("\n") != true
        ) {
            importList.node.addChild(factory.createWhiteSpace("\n\n").node, null)
        }
    }

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

    private data class FqnFinding(
        val nameParts: List<String>,
        val replacementElement: PsiElement,
        val importPath: String,
        val simpleName: String
    )
}
