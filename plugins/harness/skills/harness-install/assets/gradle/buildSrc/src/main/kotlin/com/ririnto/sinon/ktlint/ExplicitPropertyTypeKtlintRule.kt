package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags Kotlin member properties without explicit type declarations.
 */
class ExplicitPropertyTypeKtlintRule :
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
            if (!property.isLocal && property.parent is KtClassBody && property.typeReference == null) {
                val typeName = property.initializer?.let(::literalTypeName)
                emit(
                    property.textOffset,
                    "${
                        when ((property.parent?.parent as? KtObjectDeclaration)?.isCompanion()) {
                            true -> "companion object property"
                            else -> "member property"
                        }
                    } `${property.name ?: "property"}` must declare an explicit type",
                    typeName != null
                ).ifAutocorrectAllowed {
                    val name = property.name
                    if (typeName != null && name != null) {
                        val currentText = property.text
                        val nameEnd = currentText.indexOf(name) + name.length
                        val newText = currentText.substring(0, nameEnd) + ": $typeName" + currentText.substring(nameEnd)
                        val newProperty = KtPsiFactory.contextual(property, false).createProperty(newText)
                        property.node.replaceWith(newProperty.node)
                    }
                }
            }
        }

        private fun literalTypeName(initializer: org.jetbrains.kotlin.psi.KtExpression): String? =
            when (initializer) {
                is KtStringTemplateExpression -> "String"
                is KtConstantExpression -> constantTypeName(initializer)
                else -> null
            }

        private fun constantTypeName(expression: KtConstantExpression): String? {
            val text = expression.text
            return when (expression.node.elementType) {
                ElementType.BOOLEAN_CONSTANT -> "Boolean"
                ElementType.CHARACTER_CONSTANT -> "Char"
                ElementType.INTEGER_CONSTANT -> integerType(text)
                ElementType.FLOAT_CONSTANT -> when {
                    text.endsWith('f', ignoreCase = true) -> "Float"
                    else -> "Double"
                }
                else -> null
            }
        }

        private fun integerType(text: String): String? {
            val suffix = text.takeLastWhile { it in "uUlL" }
            val hasUnsigned = suffix.any { it in "uU" }
            val hasLong = suffix.any { it in "lL" }
            return when {
                hasUnsigned -> null
                hasLong -> "Long"
                else -> "Int"
            }
        }
    }
}
