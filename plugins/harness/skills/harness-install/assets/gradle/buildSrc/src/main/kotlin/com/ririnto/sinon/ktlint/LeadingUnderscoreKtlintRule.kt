package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import kotlin.io.path.nameWithoutExtension

/**
 * Flags leading underscores in Kotlin file basenames and declarations, including parameters.
 */
class LeadingUnderscoreKtlintRule : KtlintRule(
    ruleId = RuleId("code:leading-underscore"),
) {
    companion object {
        private val allowedNames = setOf("_")
        private val allowedPatterns: List<Regex> = emptyList()
    }

    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            val basename = ktFile.virtualFile?.nameWithoutExtension ?: return@let
            if (isForbidden(basename)) {
                emit(ktFile.textOffset, "declaration `$basename` uses a leading underscore", false)
            }
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                        super.visitNamedDeclaration(declaration)
                        val name = declaration.name ?: return
                        if (isForbidden(name)) {
                            emit(declaration.textOffset, "declaration `$name` uses a leading underscore", false)
                        }
                    }
                },
            )
        }
    }

    private fun isForbidden(name: String): Boolean =
        name.startsWith("_") &&
            name !in allowedNames &&
            allowedPatterns.none { pattern -> pattern.matches(name) }
}
