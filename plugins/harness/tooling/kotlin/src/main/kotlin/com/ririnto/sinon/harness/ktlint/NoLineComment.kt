package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFunctionLiteral

/**
 * Forbids line and block comments inside function, lambda, accessor, initializer, and constructor bodies.
 *
 * Comments outside bodies and KDoc are not checked.
 * Formatter directives such as `@formatter:off` stay allowed because tools consume them.
 */
class NoLineComment :
    Rule(
        ruleId = RuleId("harness:no-line-comment"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        const val FORMATTER_DIRECTIVE_PREFIX: String = "@formatter:"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if ((node.elementType == KtTokens.EOL_COMMENT || node.elementType == KtTokens.BLOCK_COMMENT) &&
            !node.isFormatterDirective() &&
            node.psi.isInsideFunctionBody()
        ) {
            emit(
                node.psi.textOffset,
                "remove the comment from the function body; put the rationale in KDoc on the declaration",
                false
            )
        }
    }

    private fun ASTNode.isFormatterDirective(): Boolean =
        text
            .removePrefix("//")
            .removePrefix("/*")
            .trimStart()
            .startsWith(FORMATTER_DIRECTIVE_PREFIX)

    private fun PsiElement.isInsideFunctionBody(): Boolean =
        generateSequence(parent, PsiElement::getParent).any { scope ->
            bodyOf(scope)?.let { body -> PsiTreeUtil.isAncestor(body, this, false) } == true
        }

    private fun bodyOf(scope: PsiElement): PsiElement? =
        when (scope) {
            is KtFunctionLiteral -> scope
            is KtDeclarationWithBody -> scope.bodyExpression
            is KtAnonymousInitializer -> scope.body
            else -> null
        }
}
