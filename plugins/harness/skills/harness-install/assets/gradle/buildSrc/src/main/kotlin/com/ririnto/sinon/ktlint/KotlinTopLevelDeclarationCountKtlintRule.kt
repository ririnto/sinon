package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile

/**
 * Requires each Kotlin source file to declare exactly one top-level declaration.
 */
class KotlinTopLevelDeclarationCountKtlintRule :
    Rule(
        ruleId = RuleId("code:kotlin-top-level-declaration-count"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.takeUnless { ktFile -> ktFile.isScript() }
            ?.let { ktFile ->
                if (ktFile.declarations.size != 1) {
                    emit(0, "file must have single top-level declaration", false)
                }
            }
    }
}
