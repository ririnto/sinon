package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile

/**
 * Requires each Kotlin source file to declare exactly one top-level declaration.
 */
class KotlinTopLevelDeclarationCountKtlintRule : KtlintRule(
    ruleId = RuleId("code:kotlin-top-level-declaration-count"),
) {
    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            if (
                ktFile.declarations
                    .filter { decl -> decl.javaClass.simpleName !in setOf("KtImportDirective", "KtPackageDirective", "KtScript") }
                    .size != 1
            ) {
                emit(0, "file must have single top-level declaration", false)
            }
        }
    }
}
