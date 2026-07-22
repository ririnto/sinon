package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.TokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags nested data classes that are not placed at the bottom of their enclosing class.
 *
 * Keep value models together after behavior so the reader sees operations first.
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
            if (classOrObject !is KtClass || !classOrObject.isEnum()) {
                val declarations = classOrObject.declarations
                declarations
                    .filterIsInstance<KtClass>()
                    .filter { declaration -> declaration.isData() }
                    .filter { declaration ->
                        declarations
                            .dropWhile { candidate -> candidate != declaration }
                            .drop(1)
                            .any { candidate -> candidate !is KtClass || !candidate.isData() }
                    }.forEach { declaration ->
                        emit(
                            declaration.textOffset,
                            "move the nested data class `${declaration.name ?: "data class"}` to the bottom of its enclosing class",
                            !declarations.any(::containsMultilineRawString)
                        ).ifAutocorrectAllowed {
                            val rewrittenText = classOrObject.text.substring(
                                0,
                                ((when (classOrObject) {
                                    is KtClass -> classOrObject.body
                                    else -> (classOrObject as KtObjectDeclaration).body
                                })?.node?.findChildByType(KtTokens.LBRACE)?.startOffset ?: classOrObject.node.startOffset) - classOrObject.node.startOffset + 1
                            ) + "\n" +
                                declarations.partition { candidate -> candidate !is KtClass || !candidate.isData() }
                                    .let { (nonData, data) -> nonData + data }
                                    .map(::blockText)
                                    .joinToString("\n\n") + "\n}"
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

        private fun blockText(declaration: KtDeclaration): String =
            (buildList {
                var sibling: ASTNode? = declaration.node.treePrev
                while (sibling !== null) {
                    when (sibling.elementType) {
                        KtTokens.EOL_COMMENT, KtTokens.BLOCK_COMMENT -> {
                            add(sibling.text)
                            sibling = sibling.treePrev
                        }
                        TokenType.WHITE_SPACE -> {
                            val newlineCount = sibling.text.count { character -> character == '\n' }
                            when {
                                2 <= newlineCount -> break
                                newlineCount == 0 -> break
                                else -> sibling = sibling.treePrev
                            }
                        }
                        else -> {
                            break
                        }
                    }
                }
            }.asReversed() + declaration.text).joinToString("\n").prependIndent("    ")

        private fun containsMultilineRawString(declaration: KtDeclaration): Boolean =
            declaration.collectDescendantsOfType<KtStringTemplateExpression>().any { template ->
                template.node.findChildByType(KtTokens.OPEN_QUOTE)?.text == "\"\"\"" &&
                    template.text.contains('\n')
            }
    }
}
