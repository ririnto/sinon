package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.ririnto.sinon.harness.core.HarnessTextEdit
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.ktlint.HarnessKotlinRules.RuleSpec
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

/**
 * Runs the harness Kotlin rules through the ktlint rule engine and maps lint errors to findings.
 *
 * Rules are grouped by their manifest source-scope so that each rule only inspects the files
 * declared for its own category, matching the manifest's per-category scoping while sharing a
 * single parse pass within each scope group. ktlint owns parsing, AST traversal, and reporting;
 * the harness manifest still owns severity, message templates, and rule parameters.
 */
object HarnessKtlintEngine {
    /**
     * Analyze all enabled Kotlin rules for the context and return harness findings.
     */
    fun analyze(ctx: RuleContext): List<Finding> {
        val ruleIdToCategory =
            HarnessKotlinRules.specs.associate { spec -> HarnessKtlintRule.ruleIdValue(spec.category) to spec.category }
        return HarnessKotlinRules.specs
            .filter { spec -> ctx.manifest.isEnabled(spec.category) }
            .groupBy { spec -> scopeKey(ctx, spec.category) }
            .flatMap { (_, specs) -> analyzeScope(ctx, specs, ruleIdToCategory) }
    }

    /**
     * Format all enabled Kotlin rules for the context and return list of changed file paths.
     */
    fun format(ctx: RuleContext): List<Path> {
        return HarnessKotlinRules.specs
            .filter { spec -> ctx.manifest.isEnabled(spec.category) }
            .groupBy { spec -> scopeKey(ctx, spec.category) }
            .flatMap { (_, specs) -> formatScope(ctx, specs) }
    }

    private fun analyzeScope(
        ctx: RuleContext,
        specs: List<RuleSpec>,
        ruleIdToCategory: Map<String, String>,
    ): List<Finding> {
        val files = ctx.stackSources(specs.first().category)
        if (files.isEmpty()) {
            return emptyList()
        }
        val engine =
            KtLintRuleEngine(
                ruleProviders = specs.map { spec -> RuleProvider { spec.create(ctx) } }.toSet(),
            )
        return files.flatMap { file -> lintFile(engine, ctx, file, ruleIdToCategory) }
    }

    private fun lintFile(
        engine: KtLintRuleEngine,
        ctx: RuleContext,
        file: Path,
        ruleIdToCategory: Map<String, String>,
    ): List<Finding> {
        val relative = file.relativeTo(ctx.root).invariantSeparatorsPathString
        return buildList {
            runCatching {
                engine.lint(Code.fromFile(file.toFile())) { error ->
                    add(toFinding(error, ctx, relative, ruleIdToCategory))
                }
            }.onFailure { throwable ->
                add(
                    Finding(
                        severity = Severity.ERROR,
                        category = "parseError",
                        message = "kotlin parse error: ${throwable.message ?: throwable::class.simpleName}",
                        file = relative,
                        startLine = 1,
                        startColumn = 1,
                    ),
                )
            }
        }
    }

    private fun toFinding(
        error: LintError,
        ctx: RuleContext,
        relative: String,
        ruleIdToCategory: Map<String, String>,
    ): Finding {
        val category = ruleIdToCategory[error.ruleId.value] ?: error.ruleId.value
        val message =
            error.detail
                .replace("{file}", relative)
                .replace("{line}", error.line.toString())
        return Finding(
            severity = ctx.manifest.severityOf(category),
            category = category,
            message = message,
            file = relative,
            startLine = error.line,
            startColumn = error.col,
        )
    }

    private fun formatScope(
        ctx: RuleContext,
        specs: List<RuleSpec>,
    ): List<Path> {
        val files = ctx.stackSources(specs.first().category)
        if (files.isEmpty()) {
            return emptyList()
        }
        val engine =
            KtLintRuleEngine(
                ruleProviders = specs.map { spec -> RuleProvider { spec.create(ctx) } }.toSet(),
            )
        return files.mapNotNull { file -> formatFile(engine, ctx, file) }
    }

    private fun formatFile(
        engine: KtLintRuleEngine,
        ctx: RuleContext,
        file: Path,
    ): Path? {
        val original = file.readText()
        val edits =
            buildList {
                ctx.fixEdits = this
                try {
                    runCatching {
                        engine.lint(Code.fromFile(file.toFile())) { }
                    }
                } finally {
                    ctx.fixEdits = null
                }
            }
        if (edits.isEmpty()) {
            return null
        }
        val sortedEdits = edits.sortedByDescending { edit -> edit.startOffset }
        var result = original
        var lastAppliedEnd = original.length
        for (edit in sortedEdits) {
            if (lastAppliedEnd < edit.endOffsetExclusive) {
                continue
            }
            result =
                result.substring(0, edit.startOffset) + edit.replacement + result.substring(edit.endOffsetExclusive)
            lastAppliedEnd = edit.startOffset
        }
        if (result != original) {
            file.writeText(result)
            return file
        }
        return null
    }

    private fun scopeKey(
        ctx: RuleContext,
        category: String,
    ): String {
        val parameters = ctx.manifest.categoryObject(category)?.get("parameters")?.jsonObject
        return listOf("sourceRoots", "extensions", "includePaths", "excludePaths")
            .joinToString("|") { key -> parameters?.get(key)?.toString() ?: "" }
    }
}
