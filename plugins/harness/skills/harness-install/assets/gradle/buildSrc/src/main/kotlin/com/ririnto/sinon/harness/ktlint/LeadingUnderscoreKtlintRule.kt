package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import kotlin.io.path.nameWithoutExtension

/**
 * Flags leading underscores in Kotlin file basenames and declarations.
 */
class LeadingUnderscoreKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
    category = CATEGORY,
    messageTemplate = messageTemplate(ctx, CATEGORY),
) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "leadingUnderscore"
    }

    private val allowedNames: Set<String>
    private val allowedPatterns: List<Regex>

    init {
        val parameters = ctx.manifest
            .categoryObject(CATEGORY)
            ?.get("parameters")
            ?.jsonObject
        allowedNames = (JsonAccess.stringArrayFromObject(parameters, "allowedNames") + "_").toSet()
        allowedPatterns = JsonAccess.stringArrayFromObject(parameters, "allowedPatterns")
            .mapNotNull { pattern -> runCatching { pattern.toRegex() }.getOrNull() }
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            val basename = ktFile.virtualFile?.nameWithoutExtension ?: return@let
            if (isForbidden(basename)) {
                emit(ktFile.textOffset, message(mapOf("name" to basename)), false)
            }
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                        super.visitNamedDeclaration(declaration)
                        val name = declaration.name ?: return
                        if (isForbidden(name)) {
                            emit(declaration.textOffset, message(mapOf("name" to name)), false)
                        }
                    }
                },
            )
        }
    }

    /**
     * Determines if a name violates the leading underscore rule.
     *
     * A name is forbidden if it starts with an underscore and is not in the allowed names set
     * or matched by allowed patterns.
     */
    private fun isForbidden(name: String): Boolean =
        name.startsWith("_") &&
            name !in allowedNames &&
            allowedPatterns.none { pattern -> pattern.matches(name) }
}
