package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Requires KDoc comments to use the multiline form.
 *
 * Delimiters occupy separate lines without changing the documented text.
 */
class MultilineKdoc :
    Rule(
        ruleId = RuleId("harness:multiline-kdoc"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(DocVisitor(emit))
    }

    private class DocVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitDeclaration(declaration: KtDeclaration) {
            super.visitDeclaration(declaration)
            declaration.docComment?.let { comment ->
                val source = comment.containingFile.text
                if (
                    source.substring(0, comment.textOffset).substringAfterLast('\n').isNotBlank() ||
                    source.substring(comment.textOffset + comment.textLength).substringBefore('\n').isNotBlank()
                ) {
                    emit(comment.textOffset, "put KDoc delimiters on separate source lines", false)
                } else if (
                    comment.text
                        .lineSequence()
                        .first()
                        .trim() != "/**" ||
                    comment.text
                        .lineSequence()
                        .last()
                        .trim() != "*/"
                ) {
                    val openingDelimiterHasOwnLine = comment.text.substringBefore('\n').trim() == "/**"
                    val documentedLines =
                        comment.text
                            .substring(3, comment.text.length - 2)
                            .trimEnd()
                            .let { text ->
                                if (openingDelimiterHasOwnLine) {
                                    text.trimStart()
                                } else {
                                    text.removePrefix(" ")
                                }
                            }.lines()
                    val canCorrect =
                        (
                            openingDelimiterHasOwnLine ||
                                !documentedLines.first().takeWhile(Char::isWhitespace).contains('\t')
                        ) &&
                            documentedLines
                                .filterIndexed { index, _ -> index != 0 || openingDelimiterHasOwnLine }
                                .all { line -> line.isBlank() || line.trimStart() == "*" || line.trimStart().startsWith("* ") }
                    if (
                        emit(comment.textOffset, "use multiline KDoc for this declaration", canCorrect) ==
                        AutocorrectDecision.ALLOW_AUTOCORRECT && canCorrect
                    ) {
                        comment.node.replaceWith(
                            KtPsiFactory
                                .contextual(declaration)
                                .createComment(
                                    """/**
${
                                        documentedLines
                                            .mapIndexed { index, line ->
                                                if (index == 0 && !openingDelimiterHasOwnLine) {
                                                    line
                                                } else {
                                                    line.trimStart().removePrefix("*").removePrefix(" ")
                                                }
                                            }.joinToString("\n") { line ->
                                                " *${line.takeIf(String::isNotEmpty)?.let { text ->
                                                    " $text"
                                                }.orEmpty()}"
                                            }
                                    }
 */"""
                                ).node
                        )
                    }
                }
            }
        }
    }
}
