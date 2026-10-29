package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias

/**
 * Requires each non-script Kotlin source file to declare exactly one top-level type declaration.
 *
 * Files without declarations, private top-level functions, properties, and type aliases, and one top-level `fun main` do not count.
 */
class KotlinTopLevelDeclarationCount :
    Rule(
        ruleId = RuleId("harness:kotlin-top-level-declaration-count"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.takeUnless(KtFile::isScript)
            ?.let { ktFile ->
                val declarations = ktFile.declarations
                val entryPoints = declarations.filter { declaration -> declaration.isMainFunction() }
                val counted = declarations.filterNot { declaration -> declaration.isPrivateHelper() || declaration in entryPoints }
                if (
                    !(declarations.isEmpty() ||
                        (
                            entryPoints.size <= 1 &&
                                when (counted.size) {
                                    0 -> {
                                        entryPoints.size == 1
                                    }

                                    1 -> {
                                        counted.single().let { declaration ->
                                            declaration is KtClassOrObject || declaration is KtTypeAlias
                                        }
                                    }

                                    else -> {
                                        false
                                    }
                                }
                        ))
                ) {
                    emit(0, "declare a single top-level type declaration in this file", false)
                }
            }
    }

    private fun KtDeclaration.isMainFunction(): Boolean = this is KtNamedFunction && name == "main"

    private fun KtDeclaration.isPrivateHelper(): Boolean =
        (this is KtNamedFunction || this is KtProperty || this is KtTypeAlias) && hasModifier(KtTokens.PRIVATE_KEYWORD)
}
