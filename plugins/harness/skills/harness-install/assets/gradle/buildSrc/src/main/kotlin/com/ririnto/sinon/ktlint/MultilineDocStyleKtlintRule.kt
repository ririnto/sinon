package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import org.ec4j.core.model.PropertyType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Requires KDoc comments to use multiline block style `/** */` instead of single-line.
 */
class MultilineDocStyleKtlintRule : KtlintRule(
    ruleId = RuleId("code:multiline-doc-style"),
    usesEditorConfigProperties = setOf(DOC_STYLE_MODE),
) {
    companion object {
        private val DOC_STYLE_MODE: EditorConfigProperty<String> =
            EditorConfigProperty(
                type = PropertyType("ktlint_multiline_doc_style_mode", "Doc style mode (multiline or other)", PropertyType.PropertyValueParser.IDENTITY_VALUE_PARSER),
                defaultValue = "multiline",
            )
    }

    private var docStyleMode: String = "multiline"

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        docStyleMode = editorConfig[DOC_STYLE_MODE]
    }

    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (docStyleMode != "multiline") {
            return
        }
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitDeclaration(declaration: KtDeclaration) {
                        super.visitDeclaration(declaration)
                        val docComment = declaration.docComment ?: return
                        if (!docComment.text.contains('\n')) {
                            emit(docComment.textOffset, "documentation comment must use multiline KDoc style", false)
                        }
                    }
                },
            )
        }
    }
}
