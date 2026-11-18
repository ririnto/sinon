package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Flags nested data classes that are not placed at the bottom of their enclosing class.
 *
 * Keep value models together after behavior so the reader sees operations first.
 */
class NestedDataClassLast :
    Rule(
        ruleId = RuleId("harness:nested-data-class-last"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        val owner = node.psi as? KtClassOrObject ?: return
        if (owner !is KtClass || !owner.isEnum()) {
            val members = owner.declarations
            val lastOtherMember = members.indexOfLast { member -> member !is KtClass || !member.isData() }
            members.forEachIndexed { index, member ->
                if (index < lastOtherMember && member is KtClass && member.isData()) {
                    emit(
                        member.textOffset,
                        "move the nested data class `${member.name ?: "data class"}` to the bottom of its enclosing class",
                        false
                    )
                }
            }
        }
    }
}
