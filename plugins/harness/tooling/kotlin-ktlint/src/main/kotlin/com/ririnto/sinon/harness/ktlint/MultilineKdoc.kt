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
 * A single-line comment is expanded without changing its documented text.
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
            declaration.docComment?.takeIf { comment -> !comment.text.contains('\n') }?.let { comment ->
                if (emit(comment.textOffset, "use multiline KDoc for this declaration", true) == AutocorrectDecision.ALLOW_AUTOCORRECT) {
                    comment.node.replaceWith(
                        KtPsiFactory
                            .contextual(declaration)
                            .createComment("/**\n * ${comment.text.substring(3, comment.text.length - 2).trim()}\n */")
                            .node
                    )
                }
            }
        }
    }
}
