package com.ririnto.sinon.harness.core

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.rules.HarnessCheckRule
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
