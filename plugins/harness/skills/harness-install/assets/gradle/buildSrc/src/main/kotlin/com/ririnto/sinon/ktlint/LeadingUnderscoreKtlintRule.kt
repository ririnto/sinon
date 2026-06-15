package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags leading underscores in Kotlin file basenames and declarations, including parameters.
 */
class LeadingUnderscoreKtlintRule :
    Rule(
        ruleId = RuleId("code:leading-underscore"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        fun isForbidden(name: String): Boolean =
            name.startsWith("_") && name != "_"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        val ktFile = node.psi as? KtFile ?: return
        val basename = ktFile.virtualFile?.nameWithoutExtension
        if (basename != null && isForbidden(basename)) {
            emit(ktFile.textOffset, "declaration `$basename` uses a leading underscore", false)
        }
        ktFile.accept(LeadingUnderscoreVisitor(emit))
    }

    private class LeadingUnderscoreVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
            super.visitNamedDeclaration(declaration)
            val name = declaration.name ?: return
            if (isForbidden(name)) {
                emit(declaration.textOffset, "declaration `$name` uses a leading underscore", false)
            }
        }
    }
}
