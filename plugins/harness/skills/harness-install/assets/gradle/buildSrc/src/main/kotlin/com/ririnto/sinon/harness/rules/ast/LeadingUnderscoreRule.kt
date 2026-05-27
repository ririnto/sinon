package com.ririnto.sinon.harness.rules.ast

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

/**
 * Rule that forbids leading underscores in Kotlin file basenames and declarations.
 */
object LeadingUnderscoreRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "leadingUnderscore"

    override fun renderAstFindings(
        ctx: RuleContext,
        findings: Collection<AstFinding>,
    ): Collection<Finding> = AstFindingRenderer.renderEach(findings.toList(), ctx.manifest.raw)

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> =
        buildSet {
            val ruleConfig = RuleConfig.from(ctx)
            val basename = file.nameWithoutExtension
            if (ruleConfig.isForbidden(basename)) {
                add(
                    AstFinding(
                        rule = category,
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = 1,
                        details = mapOf("name" to basename),
                    ),
                )
            }
            val ktFile = AstSupport.parse(file, astFactory)
            ktFile?.accept(Visitor(::add, file, ctx, ktFile, ruleConfig))
        }

    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
        private val ruleConfig: RuleConfig,
    ) : KtTreeVisitorVoid() {
        override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
            super.visitNamedDeclaration(declaration)
            val name = declaration.name ?: return
            if (ruleConfig.isForbidden(name)) {
                record(
                    AstFinding(
                        rule = "leadingUnderscore",
                        file = AstSupport.relativeFilePath(file, ctx.root),
                        line = AstSupport.lineOf(ktFile, declaration.node?.startOffset),
                        details = mapOf("name" to name),
                    ),
                )
            }
        }
    }

    private data class RuleConfig(
        val allowedNames: Set<String>,
        val allowedPatterns: List<Regex>,
    ) {
        companion object {
            /**
             * Creates a RuleConfig from manifest parameters.
             *
             * Loads allowed names and patterns from the leadingUnderscore
             * manifest category configuration.
             */
            fun from(ctx: RuleContext): RuleConfig {
                val parameters =
                    ctx.manifest
                        .categoryObject("leadingUnderscore")
                        ?.get("parameters")
                        ?.jsonObject
                return RuleConfig(
                    allowedNames = (JsonAccess.stringArrayFromObject(parameters, "allowedNames") + "_").toSet(),
                    allowedPatterns = JsonAccess.stringArrayFromObject(parameters, "allowedPatterns").mapNotNull { pattern ->
                        runCatching { pattern.toRegex() }.getOrNull()
                    },
                )
            }
        }

        /**
         * Determines if a name violates the leading underscore rule.
         *
         * A name is forbidden if it starts with an underscore and is not
         * in the allowed names set or matched by allowed patterns.
         */
        fun isForbidden(name: String): Boolean =
            name.startsWith("_") &&
                name !in allowedNames &&
                allowedPatterns.none { pattern -> pattern.matches(name) }
    }
}
