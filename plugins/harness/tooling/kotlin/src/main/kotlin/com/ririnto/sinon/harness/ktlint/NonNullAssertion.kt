package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Flags every Kotlin non-null assertion operator (`!!`) without rewriting it.
 *
 * Guard replacements can change the exception type, and syntax alone cannot prove a guard call's identity or return type.
 */
class NonNullAssertion :
    Rule(
        ruleId = RuleId("harness:non-null-assertion"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (with(ElementType) { node.elementType == OPERATION_REFERENCE } && node.firstChildNode?.elementType == KtTokens.EXCLEXCL) {
            emit(
                node.startOffset,
                "avoid non-null assertion `${KtTokens.EXCLEXCL.value}`; use safe call (${KtTokens.SAFE_ACCESS.value}), " +
                    "Elvis (${KtTokens.ELVIS.value}), or an explicit `requireNotNull` or `checkNotNull` guard",
                false
            )
        }
    }
}
