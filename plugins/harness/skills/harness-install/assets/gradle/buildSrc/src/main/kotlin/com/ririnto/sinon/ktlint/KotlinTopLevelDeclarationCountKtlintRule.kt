package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeAlias

/**
 * Requires each non-script Kotlin source file to declare exactly one top-level type declaration.
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
                val declaration = ktFile.declarations.singleOrNull()
                if (declaration !is KtClassOrObject && declaration !is KtTypeAlias) {
                    emit(0, "file must have a single top-level type declaration", false)
                }
            }
    }
}
