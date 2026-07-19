package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
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
                        true
                    ).ifAutocorrectAllowed {
                        reorderDeclarations(classOrObject, declarations)
                    }
                }
        }

        private fun reorderDeclarations(
            classOrObject: KtClassOrObject,
            declarations: List<org.jetbrains.kotlin.psi.KtDeclaration>
        ) {
            val orderedDeclarations = declarations.partition { declaration ->
                declaration !is KtClass || !declaration.isData()
            }.let { (nonData, data) -> nonData + data }

            val header = classOrObject.text.substringBefore('{')
            val bodyIndent = "    "
            val bodyText = orderedDeclarations.joinToString("\n\n") { declaration ->
                declaration.text.prependIndent(bodyIndent)
            }
            val rewrittenText = "$header{\n$bodyText\n}"

            val factory = KtPsiFactory.contextual(classOrObject, false)
            val replacement = if (classOrObject is KtClass) {
                factory.createClass(rewrittenText)
            } else {
                factory.createObject(rewrittenText)
            }
            classOrObject.node.replaceWith(replacement.node)
        }
    }
}
