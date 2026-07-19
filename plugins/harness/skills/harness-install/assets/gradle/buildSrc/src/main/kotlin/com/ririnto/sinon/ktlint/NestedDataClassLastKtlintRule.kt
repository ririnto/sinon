package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags nested data classes that are not placed at the bottom of their enclosing class; keep value
 * models together after behavior so the reader sees operations first.
 */
class NestedDataClassLastKtlintRule :
    Rule(
        ruleId = RuleId("code:nested-data-class-last"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(DataClassLastVisitor(emit))
    }

    private class DataClassLastVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitClassOrObject(classOrObject: KtClassOrObject) {
            super.visitClassOrObject(classOrObject)
            val declarations = classOrObject.declarations
            declarations
                .filterIsInstance<KtClass>()
                .filter { declaration -> declaration.isData() }
                .filter { declaration ->
                    declarations
                        .dropWhile { candidate -> candidate !== declaration }
                        .drop(1)
                        .any { candidate -> candidate !is KtClass || !candidate.isData() }
                }.forEach { declaration ->
                    emit(
                        declaration.textOffset,
                        "nested data class `${declaration.name ?: "data class"}` must sit at the bottom of its enclosing class",
                        false
                    )
                }
        }
    }
}
