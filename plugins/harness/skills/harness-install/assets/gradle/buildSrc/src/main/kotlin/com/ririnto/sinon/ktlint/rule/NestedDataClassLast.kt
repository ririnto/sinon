package com.ririnto.sinon.ktlint.rule

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
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags nested data classes that are not placed at the bottom of their enclosing class; keep value
 * models together after behavior so the reader sees operations first.
 */
class NestedDataClassLast :
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
                        val bodyPsi: KtClassBody? = when (classOrObject) {
                            is KtClass -> classOrObject.body
                            else -> (classOrObject as org.jetbrains.kotlin.psi.KtObjectDeclaration).body
                        }
                        val lbraceNode = bodyPsi?.node?.findChildByType(org.jetbrains.kotlin.lexer.KtTokens.LBRACE)
                        val lbraceOffset = lbraceNode?.startOffset ?: classOrObject.node.startOffset
                        val headerEnd = lbraceOffset - classOrObject.node.startOffset + 1
                        val header = classOrObject.text.substring(0, headerEnd)
                        val body = declarations.partition { candidate -> candidate !is KtClass || !candidate.isData() }
                            .let { (nonData, data) -> nonData + data }
                            .joinToString("\n\n") { candidate -> candidate.text.prependIndent("    ") }
                        val rewrittenText = "$header\n$body\n}"
                        classOrObject.node.replaceWith(
                            KtPsiFactory.contextual(classOrObject, false).let { factory ->
                                when (classOrObject) {
                                    is KtClass -> factory.createClass(rewrittenText)
                                    else -> factory.createObject(rewrittenText)
                                }
                            }.node
                        )
                    }
                }
        }
    }
}
