package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Forbids line and block comments.
 *
 * Directs users to use KDoc instead.
 */
class NoLineComment :
    Rule(
        ruleId = RuleId("code:no-line-comment"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (node.elementType == KtTokens.EOL_COMMENT || node.elementType == KtTokens.BLOCK_COMMENT) {
            emit(node.psi.textOffset, "use KDoc (/** ... */) instead of // or /* */ comments", false)
        }
    }
}
