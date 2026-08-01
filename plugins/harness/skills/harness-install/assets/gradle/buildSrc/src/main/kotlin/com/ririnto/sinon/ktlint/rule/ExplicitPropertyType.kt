package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags Kotlin member properties without explicit type declarations.
 */
class ExplicitPropertyType :
    Rule(
        ruleId = RuleId("code:explicit-property-type"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(PropertyTypeVisitor(emit))
    }

    private class PropertyTypeVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            super.visitProperty(property)
            if (!property.isLocal && property.parent is KtClassBody && property.typeReference === null) {
                val typeName =
                    property.initializer?.let { initializer ->
                        with(LiteralTypeInference) { initializer.inferLiteralType() }
                    }
                emit(
                    property.textOffset,
                    "declare an explicit type on ${
                        when ((property.parent?.parent as? KtObjectDeclaration)?.isCompanion()) {
                            true -> "companion object property"
                            else -> "member property"
                        }
                    } `${property.name ?: "property"}`",
                    typeName !== null
                ).ifAutocorrectAllowed {
                    val nameIdentifier = property.nameIdentifier
                    if (typeName !== null && nameIdentifier !== null) {
                        val currentText = property.text
                        val nameEnd = (nameIdentifier.node.startOffset - property.node.startOffset) + nameIdentifier.text.length
                        property.node.replaceWith(
                            KtPsiFactory
                                .contextual(
                                    property,
                                    false
                                ).createProperty(currentText.substring(0, nameEnd) + ": $typeName" + currentText.substring(nameEnd))
                                .node
                        )
                    }
                }
            }
        }
    }
}
