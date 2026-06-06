package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags Kotlin member properties without explicit type declarations.
 */
class ExplicitPropertyTypeKtlintRule : KtlintRule(
    ruleId = RuleId("code:explicit-property-type"),
) {
    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitProperty(property: KtProperty) {
                        super.visitProperty(property)
                        if (property.isLocal || property.parent !is KtClassBody || property.typeReference != null) {
                            return
                        }
                        emit(
                            property.textOffset,
                            "${
                                when {
                                    (property.parent?.parent as? KtObjectDeclaration)?.isCompanion() == true -> "companion object property"
                                    else -> "member property"
                                }
                            } `${property.name ?: "property"}` must declare an explicit type",
                            false,
                        )
                    }
                },
            )
        }
    }
}
