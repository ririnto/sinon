package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Requires exactly one blank line between sibling Kotest tests and lifecycle hooks.
 *
 * The rule applies only inside directly imported StringSpec and FunSpec scopes and leaves unproven lambdas, comments, and same-line registrations unchanged.
 */
class KotestSiblingSpacing :
    Rule(
        ruleId = RuleId("harness:kotest-sibling-spacing"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        val newlineCount = node.text.count { character -> character == '\n' }
        if (with(ElementType) { node.elementType == WHITE_SPACE } &&
            newlineCount != 0 &&
            newlineCount != 2 &&
            KotestRegistrations.isRegistrationSeparator(node)
        ) {
            emit(
                node.startOffset,
                "use exactly one blank line between sibling Kotest tests and lifecycle hooks",
                true
            ).ifAutocorrectAllowed {
                node.replaceTextWith("\n\n${node.text.substringAfterLast('\n')}")
            }
        }
    }
}
