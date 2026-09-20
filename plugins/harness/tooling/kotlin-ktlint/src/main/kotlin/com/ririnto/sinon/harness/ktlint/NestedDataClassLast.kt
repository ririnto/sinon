package com.ririnto.sinon.harness.ktlint

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
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

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
                            with(LiteralTypeInference) {
                                !declarations.any { declaration -> declaration.containsMultilineRawString() }
                            }
                        ).ifAutocorrectAllowed {
                            val rewrittenText =
                                classOrObject.text.substring(
                                    0,
                                    (
                                        (
                                            when (classOrObject) {
                                                is KtClass -> classOrObject.body
                                                else -> (classOrObject as KtObjectDeclaration).body
                                            }
                                        )?.node?.findChildByType(KtTokens.LBRACE)?.startOffset ?: classOrObject.node.startOffset
                                    ) -
                                        classOrObject.node.startOffset +
                                        1
                                ) + """
${declarations
                                    .partition { candidate -> candidate !is KtClass || !candidate.isData() }
                                    .let { (nonData, data) -> nonData + data }
                                    .joinToString("\n\n") { declaration -> declaration.blockText() }}
}"""
                            classOrObject.node.replaceWith(
                                KtPsiFactory
                                    .contextual(classOrObject, false)
                                    .let { factory ->
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

        private fun KtDeclaration.blockText(): String {
            val declarationText = text
            val normalizedDeclarationText =
                when {
                    !declarationText.startsWith("//") && !declarationText.startsWith("/*") -> {
                        declarationText
                    }

                    else -> {
                        val lines = declarationText.lines()
                        val declarationLineIndex =
                            (this as? KtClass)
                                ?.nameIdentifier
                                ?.let { identifier -> identifier.node.startOffset - node.startOffset }
                                ?.let { identifierOffset ->
                                    declarationText.take(identifierOffset).count { character -> character == '\n' }
                                }
                        val baseIndent =
                            declarationLineIndex
                                ?.let(lines::getOrNull)
                                ?.takeWhile { character -> character == ' ' || character == '\t' }
                                .orEmpty()
                        lines
                            .mapIndexed { index, line ->
                                when (0 < index && line.startsWith(baseIndent)) {
                                    true -> {
                                        line.removePrefix(baseIndent)
                                    }

                                    false -> {
                                        line
                                    }
                                }
                            }.joinToString("\n")
                    }
                }
            return (
                buildList {
                    var sibling: ASTNode? = this@blockText.node.treePrev
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
                }.asReversed() + normalizedDeclarationText
            ).joinToString("\n").prependIndent("    ")
        }
    }
}
