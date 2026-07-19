package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.ec4j.core.model.PropertyType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Requires KDoc comments to use multiline block style `/** */` instead of single-line.
 * Disabled by default; set `ktlint_multiline_doc_style_mode = multiline` (or `on`) in `.editorconfig` to enable.
 */
class MultilineDocStyleKtlintRule :
    Rule(
        ruleId = RuleId("code:multiline-doc-style"),
        about = About(),
        usesEditorConfigProperties = setOf(DOC_STYLE_MODE)
    ),
    RuleAutocorrectApproveHandler {
    companion object {
        private val ENABLED_MODES: Set<String> = setOf("multiline", "on")

        private val DOC_STYLE_MODE: EditorConfigProperty<String> =
            EditorConfigProperty(
                type =
                    PropertyType(
                        "ktlint_multiline_doc_style_mode",
                        "Doc style mode (`multiline` or `on` to enable; any other value disables)",
                        PropertyType.PropertyValueParser.IDENTITY_VALUE_PARSER
                    ),
                defaultValue = "off"
            )
    }

    private lateinit var docStyleMode: String

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        docStyleMode = editorConfig[DOC_STYLE_MODE]
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (docStyleMode in ENABLED_MODES) {
            (node.psi as? KtFile)?.accept(DocStyleVisitor(emit))
        }
    }

    private class DocStyleVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitDeclaration(declaration: KtDeclaration) {
            super.visitDeclaration(declaration)
            declaration.docComment?.let { docComment ->
                if (!docComment.text.contains('\n')) {
                    emit(
                        docComment.textOffset,
                        "documentation comment must use multiline KDoc style",
                        true
                    ).ifAutocorrectAllowed {
                        val content = docComment.text.substring(3, docComment.text.length - 2).trim()
                        docComment.node.replaceWith(
                            KtPsiFactory.contextual(declaration)
                                .createComment("/**\n * $content\n */")
                                .node
                        )
                    }
                }
            }
        }
    }
}
