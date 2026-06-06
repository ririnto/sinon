package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import org.ec4j.core.model.PropertyType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags companion objects positioned outside the configured location; reposition to top or bottom of class body.
 */
class CompanionObjectPositionKtlintRule : KtlintRule(
    ruleId = RuleId("code:companion-object-position"),
    usesEditorConfigProperties = setOf(COMPANION_POSITION),
) {
    companion object {
        private val COMPANION_POSITION: EditorConfigProperty<String> =
            EditorConfigProperty(
                type = PropertyType("ktlint_companion_object_position_position", "Companion object position (top or bottom)", PropertyType.PropertyValueParser.IDENTITY_VALUE_PARSER),
                defaultValue = "top",
            )
    }

    private var position: String = "top"

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        position = editorConfig[COMPANION_POSITION].takeIf { value -> value == "bottom" } ?: "top"
    }

    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitClass(klass: KtClass) {
                        super.visitClass(klass)
                        klass.getBody()?.let { body ->
                            val declarations = body.declarations.filter { declaration -> declaration !is KtEnumEntry }
                            declarations.forEachIndexed { index, declaration ->
                                if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                                    if (
                                        when (position) {
                                            "bottom" -> index != declarations.lastIndex
                                            else -> index != 0
                                        }
                                    ) {
                                        emit(
                                            declaration.textOffset,
                                            "companion object position violates parameters.position=$position",
                                            false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
