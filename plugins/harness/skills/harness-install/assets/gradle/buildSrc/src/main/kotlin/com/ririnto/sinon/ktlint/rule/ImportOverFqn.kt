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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil

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
        (node.psi as? KtFile)
            ?.let { ktFile ->
                Visitor(ktFile, emit).visit()
            }
    }

    private class Visitor(
        private val ktFile: KtFile,
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        private val importsToAdd = linkedSetOf<String>()
        private lateinit var simpleNames: Set<String>

        fun visit() {
            val imports = ktFile.importDirectives
            simpleNames = imports.mapNotNull { directive ->
                directive.importedName?.asString()?.let { name ->
                    directive.importPath?.pathStr?.let { path -> name to path }
                }
            }.toMap().keys + imports.mapNotNull { directive -> directive.aliasName }.toSet() + PsiTreeUtil.findChildrenOfType(
                ktFile,
                KtNamedDeclaration::class.java
            )
                .mapNotNull { declaration -> declaration.name }
                .toSet()
            ktFile.accept(this)
            val existing = imports.mapNotNull { directive -> directive.importPath?.pathStr }.toSet()
            val newImports = importsToAdd.filter { path -> path !in existing }.sorted()
            val importList = ktFile.importList
            when {
                importList !== null -> {
                    val existingPaths = imports.mapNotNull { directive -> directive.importPath?.pathStr }
                    val importsAreSorted = existingPaths == existingPaths.sorted()
                    newImports.forEach { path ->
                        val importNode = KtPsiFactory.contextual(ktFile, false)
                            .createImportDirective(ImportPath(FqName(path), false, null))
                            .node
                        val anchor = when {
                            importsAreSorted -> importList.imports.firstOrNull { directive ->
                                path < directive.importPath?.pathStr.orEmpty()
                            }?.node
                            else -> null
                        }
                        importList.node.addChild(importNode, anchor)
                        importList.node.addChild(
                            KtPsiFactory.contextual(ktFile, false).createWhiteSpace("\n").node,
                            null
                        )
                    }
                    if (imports.isEmpty() && newImports.isNotEmpty()) {
                        importList.node.addChild(
                            KtPsiFactory.contextual(ktFile, false).createWhiteSpace("\n").node,
                            null
                        )
                    }
                }
                else -> {
                    val anchor = ktFile.declarations.firstOrNull()?.node
                    newImports.asReversed().forEach { path ->
                        val importNode = KtPsiFactory.contextual(ktFile, false)
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
                    addFqnFinding(fqnParts, userType, userType)
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
                val parts = expressionParts(expression)
                val classIndex = parts.indexOfFirst { part -> part.firstOrNull()?.isUpperCase() == true }
                if (2 <= classIndex) {
                    addFqnFinding(parts.take(classIndex + 1), expression, expression)
                }
            }
        }

        private fun expressionParts(expression: KtExpression): List<String> =
            when (expression) {
                is KtNameReferenceExpression -> {
                    listOf(expression.getReferencedName())
                }

                is KtDotQualifiedExpression -> {
                    expressionParts(expression.receiverExpression) +
                            expression.selectorExpression?.let(::expressionParts).orEmpty()
                }

                is KtCallExpression -> {
                    expression.calleeExpression?.let(::expressionParts).orEmpty()
                }

                else -> {
                    emptyList()
                }
            }

        private fun addFqnFinding(
            nameParts: List<String>,
            element: PsiElement,
            replacementElement: PsiElement
        ) {
            if (3 <= nameParts.size &&
                nameParts[0].firstOrNull()?.isLowerCase() == true &&
                nameParts[1].firstOrNull()?.isLowerCase() == true &&
                nameParts.last().firstOrNull()?.isUpperCase() == true
            ) {
                val simpleName = nameParts.last()
                val importPath = nameParts.dropLast(1).joinToString(".") + "." + simpleName
                val canCorrect = simpleName !in simpleNames &&
                        ktFile.importDirectives.none { directive -> directive.isAllUnder } &&
                        ktFile.importDirectives.none { directive ->
                            directive.importedName?.asString() == simpleName && directive.importPath?.pathStr != importPath
                        } &&
                        ktFile.packageFqName.asString() != nameParts.dropLast(1).joinToString(".")
                emit(
                    element.textOffset,
                    "fully qualified name `${nameParts.joinToString(".")}` used inline; add an import and use the simple name",
                    canCorrect
                ).ifAutocorrectAllowed {
                    if (canCorrect) {
                        val fqn = nameParts.joinToString(".")
                        val replacementText = replacementElement.text.replaceFirst(fqn, simpleName)
                        replacementElement.node.replaceWith(
                            KtPsiFactory.contextual(replacementElement, false)
                                .createExpression(replacementText)
                                .node
                        )
                        importsToAdd += importPath
                    }
                }
            }
        }
    }
}
