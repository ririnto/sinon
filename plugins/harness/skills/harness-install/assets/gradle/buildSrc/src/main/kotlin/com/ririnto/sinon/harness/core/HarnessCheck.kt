package com.ririnto.sinon.harness.core

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.HarnessAstResults
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import com.ririnto.sinon.harness.rules.ast.CompanionObjectPositionRule
import com.ririnto.sinon.harness.rules.ast.EarlyReturnRule
import com.ririnto.sinon.harness.rules.ast.EmptyCatchBlockRule
import com.ririnto.sinon.harness.rules.ast.GreaterThanComparisonRule
import com.ririnto.sinon.harness.rules.ast.IfStatementBracesRule
import com.ririnto.sinon.harness.rules.ast.ImplicitLambdaItRule
import com.ririnto.sinon.harness.rules.ast.ImportOverFqnRule
import com.ririnto.sinon.harness.rules.ast.KotlinTopLevelDeclarationCountRule
import com.ririnto.sinon.harness.rules.ast.LeadingUnderscoreRule
import com.ririnto.sinon.harness.rules.ast.LeafFunctionBlankLinesRule
import com.ririnto.sinon.harness.rules.ast.MultilineDocStyleRule
import com.ririnto.sinon.harness.rules.ast.MutableCollectionRule
import com.ririnto.sinon.harness.rules.ast.NonNullAssertionRule
import com.ririnto.sinon.harness.rules.ast.PublicDeclarationDocCommentRule
import com.ririnto.sinon.harness.rules.ast.SilentCatchRule
import com.ririnto.sinon.harness.rules.ast.TerminalBranchWhenRule
import com.ririnto.sinon.harness.rules.ast.UncheckedCastSuppressionRule
import com.ririnto.sinon.harness.rules.ast.UnstructuredLoggingRule
import com.ririnto.sinon.harness.rules.ast.WildcardImportRule
import com.ririnto.sinon.harness.rules.fs.DirectoryPresenceRule
import com.ririnto.sinon.harness.rules.fs.EmptyDirectoryPlaceholdersRule
import com.ririnto.sinon.harness.rules.fs.FilePresenceRule
import com.ririnto.sinon.harness.rules.fs.ScaffoldLeaksRule
import com.ririnto.sinon.harness.rules.fs.SymlinkSafetyRule
import com.ririnto.sinon.harness.rules.fs.TemplateGroupsRule
import com.ririnto.sinon.harness.rules.fs.UncheckedTasksRule
import com.ririnto.sinon.harness.rules.text.AgentFrontmatterRule
import com.ririnto.sinon.harness.rules.text.CiHookCommandParityRule
import com.ririnto.sinon.harness.rules.text.DocContentRule
import com.ririnto.sinon.harness.rules.text.DocHeadingsRule
import com.ririnto.sinon.harness.rules.text.EnvShebangUsageRule
import com.ririnto.sinon.harness.rules.text.HookCommandRule
import com.ririnto.sinon.harness.rules.text.HookExecutableRule
import com.ririnto.sinon.harness.rules.text.HookGeneratedMarkerRule
import com.ririnto.sinon.harness.rules.text.HookShebangRule
import com.ririnto.sinon.harness.rules.text.HookStageRule
import com.ririnto.sinon.harness.rules.text.SkillFrontmatterRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/**
 * Enumeration of all harness validation checks.
 */
enum class HarnessCheck(
    val rule: HarnessCheckRule,
) {
    /**
     * Verifies that required files exist at the repository root.
     */
    FILE_PRESENCE(FilePresenceRule),

    /**
     * Verifies that required directories exist at the repository root.
     */
    DIRECTORY_PRESENCE(DirectoryPresenceRule),

    /**
     * Verifies that empty directories contain a .keepfile placeholder.
     */
    EMPTY_DIRECTORY_PLACEHOLDERS(EmptyDirectoryPlaceholdersRule),

    /**
     * Verifies that command and agent groups are properly organized.
     */
    TEMPLATE_GROUPS(TemplateGroupsRule),

    /**
     * Verifies that Markdown documents have proper heading structure.
     */
    DOC_HEADINGS(DocHeadingsRule),

    /**
     * Verifies that Markdown documents contain substantive content.
     */
    DOC_CONTENT(DocContentRule),

    /**
     * Verifies that agent files declare required frontmatter fields.
     */
    AGENT_FRONTMATTER(AgentFrontmatterRule),

    /**
     * Verifies that skill files declare required frontmatter fields.
     */
    SKILL_FRONTMATTER(SkillFrontmatterRule),

    /**
     * Verifies that scaffold artifacts are not included in tracked files.
     */
    SCAFFOLD_LEAKS(ScaffoldLeaksRule),

    /**
     * Verifies that shell scripts have proper shebangs.
     */
    HOOK_SHEBANG(HookShebangRule),

    /**
     * Verifies that hook scripts are executable.
     */
    HOOK_EXECUTABLE(HookExecutableRule),

    /**
     * Verifies that hook scripts contain the harness-generated marker.
     */
    HOOK_GENERATED_MARKER(HookGeneratedMarkerRule),

    /**
     * Verifies that hook scripts declare a valid stage.
     */
    HOOK_STAGE(HookStageRule),

    /**
     * Verifies that hook scripts declare a command to execute.
     */
    HOOK_COMMAND(HookCommandRule),

    /**
     * Verifies that CI commands match their corresponding hook definitions.
     */
    CI_HOOK_COMMAND_PARITY(CiHookCommandParityRule),

    /**
     * Verifies that shebangs within files use the env form.
     */
    ENV_SHEBANG_USAGE(EnvShebangUsageRule),

    /**
     * Verifies that only approved Gradle task categories are used.
     */
    UNCHECKED_TASKS(UncheckedTasksRule),

    /**
     * Verifies that symbolic links conform to allowed patterns.
     */
    SYMLINK_SAFETY(SymlinkSafetyRule),

    /**
     * Verifies that length comparisons do not use the greater-than operator.
     */
    GREATER_THAN_COMPARISON(GreaterThanComparisonRule),

    /**
     * Verifies that leaf functions do not contain blank lines in their body.
     */
    LEAF_FUNCTION_BLANK_LINES(LeafFunctionBlankLinesRule),

    /**
     * Verifies that lambda parameters are not used implicitly.
     */
    IMPLICIT_LAMBDA_IT(ImplicitLambdaItRule),

    /**
     * Verifies that Kotlin files declare only a single top-level declaration.
     */
    KOTLIN_TOP_LEVEL_DECLARATION_COUNT(KotlinTopLevelDeclarationCountRule),

    /**
     * Verifies that wildcard imports are not used.
     */
    WILDCARD_IMPORT(WildcardImportRule),

    /**
     * Verifies that catch blocks contain at least one statement.
     */
    EMPTY_CATCH_BLOCK(EmptyCatchBlockRule),

    /**
     * Verifies that if statements are wrapped in braces.
     */
    IF_STATEMENT_BRACES(IfStatementBracesRule),

    /**
     * Verifies that terminal Kotlin branches use when instead of if.
     */
    TERMINAL_BRANCH_WHEN(TerminalBranchWhenRule),

    /**
     * Verifies that early returns are avoided in functions.
     */
    EARLY_RETURN(EarlyReturnRule),

    /**
     * Verifies that catch blocks do not silently ignore exceptions.
     */
    SILENT_CATCH(SilentCatchRule),

    /**
     * Verifies that mutable collection builders are not used.
     */
    MUTABLE_COLLECTION(MutableCollectionRule),

    /**
     * Verifies that non-null assertions are not used.
     */
    NON_NULL_ASSERTION(NonNullAssertionRule),

    /**
     * Verifies that @Suppress("UNCHECKED_CAST") annotations are not used.
     */
    UNCHECKED_CAST_SUPPRESSION(UncheckedCastSuppressionRule),

    /**
     * Verifies that logging conforms to structured patterns.
     */
    UNSTRUCTURED_LOGGING(UnstructuredLoggingRule),

    /**
     * Verifies that imports are used instead of fully qualified names.
     */
    IMPORT_OVER_FQN(ImportOverFqnRule),

    /**
     * Verifies that public declarations include documentation comments.
     */
    PUBLIC_DECLARATION_DOC_COMMENT(PublicDeclarationDocCommentRule),

    /**
     * Verifies that declarations and file basenames do not use leading underscores.
     */
    LEADING_UNDERSCORE(LeadingUnderscoreRule),

    /**
     * Verifies that documentation comments use multiline native style.
     */
    MULTILINE_DOC_STYLE(MultilineDocStyleRule),

    /**
     * Verifies that companion objects are positioned first in the class body.
     */
    COMPANION_OBJECT_POSITION(CompanionObjectPositionRule),
    ;

    /**
     * Companion object providing utility functions for harness validation.
     */
    companion object {
        /**
         * Returns the severity level for a given category in the manifest.
         */
        fun severityOf(
            manifest: JsonObject,
            category: String,
        ): Severity {
            val severityName = manifest[category]?.jsonObject?.get("severity")?.jsonPrimitive?.contentOrNull
            return Severity.entries.firstOrNull { entry -> entry.name.equals(severityName, ignoreCase = true) }
                ?: Severity.ERROR
        }
    }

    /**
     * Gets the category name for this check.
     */
    fun category(): String = rule.category
}
