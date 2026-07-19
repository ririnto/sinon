package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration

/**
 * Flags companion objects that are not the first non-enum-entry declaration in their class or object body.
 */
class CompanionObjectPositionKtlintRule :
    Rule(
        ruleId = RuleId("code:companion-object-position"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtClassOrObject)
            ?.declarations
            ?.filterNot { declaration -> declaration is KtEnumEntry }
            ?.let { declarations ->
                declarations
                    .filterIsInstance<KtObjectDeclaration>()
                    .filter { declaration -> declaration.isCompanion() }
                    .filter { declaration -> declarations.firstOrNull() !== declaration }
                    .forEach { declaration ->
                        emit(
                            declaration.textOffset,
                            "Place the companion object before other class members",
                            false
                        )
                    }
            }
    }
}
