package ai.harness.gradle

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.rules.LeafFunctionBlankLinesRule
import ai.harness.gradle.rules.EarlyReturnRule
import ai.harness.gradle.rules.EmptyCatchBlockRule
import ai.harness.gradle.rules.GreaterThanComparisonRule
import ai.harness.gradle.rules.ImplicitLambdaItRule
import ai.harness.gradle.rules.MutableCollectionRule
import ai.harness.gradle.rules.ScaffoldLeaksRule
import ai.harness.gradle.rules.SilentCatchRule
import ai.harness.gradle.rules.UncheckedTasksRule
import ai.harness.gradle.rules.SymlinkSafetyRule
import ai.harness.gradle.rules.UnstructuredLoggingRule
import ai.harness.gradle.rules.WildcardImportRule
import ai.harness.gradle.rules.HarnessCheckRule
import ai.harness.gradle.rules.AgentFrontmatterRule
import ai.harness.gradle.rules.IfStatementBracesRule
import ai.harness.gradle.rules.CiHookCommandParityRule
import ai.harness.gradle.rules.ClassMemberOrderingRule
import ai.harness.gradle.rules.CompanionObjectPositionRule
import ai.harness.gradle.rules.DirectoryPresenceRule
import ai.harness.gradle.rules.PublicDeclarationDocCommentRule
import ai.harness.gradle.rules.DocContentRule
import ai.harness.gradle.rules.DocHeadingsRule
import ai.harness.gradle.rules.EnvShebangUsageRule
import ai.harness.gradle.rules.FilePresenceRule
import ai.harness.gradle.rules.HookCommandRule
import ai.harness.gradle.rules.HookExecutableRule
import ai.harness.gradle.rules.HookGeneratedMarkerRule
import ai.harness.gradle.rules.HookShebangRule
import ai.harness.gradle.rules.HookStageRule
import ai.harness.gradle.rules.ImportOverFqnRule
import ai.harness.gradle.rules.EmptyDirectoryPlaceholdersRule
import ai.harness.gradle.rules.KotlinTopLevelDeclarationCountRule
import ai.harness.gradle.rules.SkillFrontmatterRule
import ai.harness.gradle.rules.TemplateGroupsRule
import ai.harness.gradle.rules.TerminalBranchWhenRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Enumeration of all harness validation checks.
 */
enum class HarnessCheck(
    val rule: HarnessCheckRule,
) {
    /** Verifies that required files exist at the repository root. */
    FILE_PRESENCE(FilePresenceRule),

    /** Verifies that required directories exist at the repository root. */
    DIRECTORY_PRESENCE(DirectoryPresenceRule),

    /** Verifies that empty directories contain a .keepfile placeholder. */
    EMPTY_DIRECTORY_PLACEHOLDERS(EmptyDirectoryPlaceholdersRule),

    /** Verifies that command and agent groups are properly organized. */
    TEMPLATE_GROUPS(TemplateGroupsRule),

    /** Verifies that Markdown documents have proper heading structure. */
    DOC_HEADINGS(DocHeadingsRule),

    /** Verifies that Markdown documents contain substantive content. */
    DOC_CONTENT(DocContentRule),

    /** Verifies that agent files declare required frontmatter fields. */
    AGENT_FRONTMATTER(AgentFrontmatterRule),

    /** Verifies that skill files declare required frontmatter fields. */
    SKILL_FRONTMATTER(SkillFrontmatterRule),

    /** Verifies that scaffold artifacts are not included in tracked files. */
    SCAFFOLD_LEAKS(ScaffoldLeaksRule),

    /** Verifies that shell scripts have proper shebangs. */
    HOOK_SHEBANG(HookShebangRule),

    /** Verifies that hook scripts are executable. */
    HOOK_EXECUTABLE(HookExecutableRule),

    /** Verifies that hook scripts contain the harness-generated marker. */
    HOOK_GENERATED_MARKER(HookGeneratedMarkerRule),

    /** Verifies that hook scripts declare a valid stage. */
    HOOK_STAGE(HookStageRule),

    /** Verifies that hook scripts declare a command to execute. */
    HOOK_COMMAND(HookCommandRule),

    /** Verifies that CI commands match their corresponding hook definitions. */
    CI_HOOK_COMMAND_PARITY(CiHookCommandParityRule),

    /** Verifies that shebangs within files use the env form. */
    ENV_SHEBANG_USAGE(EnvShebangUsageRule),

    /** Verifies that only approved Gradle task categories are used. */
    UNCHECKED_TASKS(UncheckedTasksRule),

    /** Verifies that symbolic links conform to allowed patterns. */
    SYMLINK_SAFETY(SymlinkSafetyRule),

    /** Verifies that length comparisons do not use the greater-than operator. */
    GREATER_THAN_COMPARISON(GreaterThanComparisonRule),

    /** Verifies that leaf functions do not contain blank lines in their body. */
    LEAF_FUNCTION_BLANK_LINES(LeafFunctionBlankLinesRule),

    /** Verifies that lambda parameters are not used implicitly. */
    IMPLICIT_LAMBDA_IT(ImplicitLambdaItRule),

    /** Verifies that Kotlin files declare only a single top-level declaration. */
    KOTLIN_TOP_LEVEL_DECLARATION_COUNT(KotlinTopLevelDeclarationCountRule),

    /** Verifies that wildcard imports are not used. */
    WILDCARD_IMPORT(WildcardImportRule),

    /** Verifies that catch blocks contain at least one statement. */
    EMPTY_CATCH_BLOCK(EmptyCatchBlockRule),

    /** Verifies that if statements are wrapped in braces. */
    IF_STATEMENT_BRACES(IfStatementBracesRule),

    /** Verifies that terminal Kotlin branches use when instead of if. */
    TERMINAL_BRANCH_WHEN(TerminalBranchWhenRule),

    /** Verifies that early returns are avoided in functions. */
    EARLY_RETURN(EarlyReturnRule),

    /** Verifies that catch blocks do not silently ignore exceptions. */
    SILENT_CATCH(SilentCatchRule),

    /** Verifies that mutable collection builders are not used. */
    MUTABLE_COLLECTION(MutableCollectionRule),

    /** Verifies that logging conforms to structured patterns. */
    UNSTRUCTURED_LOGGING(UnstructuredLoggingRule),

    /** Verifies that imports are used instead of fully qualified names. */
    IMPORT_OVER_FQN(ImportOverFqnRule),

    /** Verifies that public declarations include documentation comments. */
    PUBLIC_DECLARATION_DOC_COMMENT(PublicDeclarationDocCommentRule),

    /** Verifies that companion objects are positioned first in the class body. */
    COMPANION_OBJECT_POSITION(CompanionObjectPositionRule),

    /** Verifies that JVM class members follow the configured order. */
    CLASS_MEMBER_ORDERING(ClassMemberOrderingRule),
    ;

    /** Companion object providing utility functions for harness validation. */
    companion object {
        /** Returns the severity level for a given category in the manifest. */
        fun severityOf(
            manifest: JsonObject,
            category: String,
        ): Severity {
            val severity =
                manifest[category]
                    ?.jsonObject
                    ?.get("severity")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: return Severity.ERROR
            return try {
                Severity.valueOf(severity.uppercase())
            } catch (e: Exception) {
                Severity.ERROR
            }
        }

        /** Safely reads file content; returns empty string if symlink is not allowed or file does not exist. */
        fun readSafe(
            root: Path,
            path: String,
        ): String {
            val p = root / path
            return when {
                p.isSymbolicLink() && isAllowedRootContractSymlink(root, p) -> {
                    val target =
                        when (p.name) {
                            "AGENTS.md" -> "CLAUDE.md"
                            else -> "AGENTS.md"
                        }
                    (root / target).let { path ->
                        when {
                            path.isRegularFile() -> path.readText()
                            else -> ""
                        }
                    }
                }

                p.isSymbolicLink() -> {
                    ""
                }

                p.isRegularFile() -> {
                    p.readText()
                }

                else -> {
                    ""
                }
            }
        }

        /** Checks if a symlink at the repository root is allowed per contract rules. */
        fun isAllowedRootContractSymlink(
            root: Path,
            p: Path,
        ): Boolean {
            if (p.parent != root || p.name !in setOf("AGENTS.md", "CLAUDE.md")) {
                return false
            }
            val expected =
                when (p.name) {
                    "AGENTS.md" -> "CLAUDE.md"
                    else -> "AGENTS.md"
                }
            return try {
                p.readSymbolicLink().toString() == expected && (root / expected).isRegularFile()
            } catch (e: Exception) {
                false
            }
        }

        /** Extracts a string array from a JSON object by key. */
        fun stringArrayFrom(
            obj: JsonObject?,
            key: String,
        ): List<String> =
            obj
                ?.get(key)
                ?.jsonArray
                ?.filter { elem ->
                    elem.jsonPrimitive.contentOrNull != null
                }?.map { elem -> elem.jsonPrimitive.contentOrNull!! }
                ?: emptyList()

        /** Extracts a string value from a JSON object by key. */
        fun stringFrom(
            obj: JsonObject?,
            key: String,
        ): String = obj?.get(key)?.jsonPrimitive?.contentOrNull ?: ""

        /** Render raw PSI findings through rule-owned renderers. */
        fun renderPsiFindings(
            rawFindings: List<PsiFinding>,
            manifest: JsonObject,
        ): List<Finding> =
            entries
                .filter { check -> check.applies(manifest) }
                .flatMap { check ->
                    check.rule.renderPsiFindings(
                        rawFindings.filter { finding -> finding.rule == check.category() },
                        manifest,
                    )
                }

        /** Safely walks the directory tree, returning files and symlink violations. */
        fun walkSafe(
            root: Path,
            base: Path,
        ): Pair<List<Path>, List<Finding>> =
            when {
                !base.exists() -> {
                    emptyList<Path>() to emptyList()
                }

                base.isSymbolicLink() && !isAllowedRootContractSymlink(root, base) -> {
                    emptyList<Path>() to
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "symlinkSafety",
                                "symlink scan root is not allowed: ${base.relativeTo(root)}",
                            ),
                        )
                }

                base.isRegularFile() -> {
                    listOf(base) to emptyList()
                }

                base.isDirectory() -> {
                    val entries =
                        try {
                            base.listDirectoryEntries()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    val warnings =
                        buildSet {
                            entries
                                .filter { entry ->
                                    entry.isSymbolicLink() && !isAllowedRootContractSymlink(root, entry)
                                }.forEach { entry ->
                                    add(
                                        Finding(
                                            Severity.ERROR,
                                            "symlinkSafety",
                                            "symlink path is not allowed: ${entry.relativeTo(root)}",
                                        ),
                                    )
                                }
                        }.toList()
                    entries
                        .filter { entry ->
                            !entry.isSymbolicLink()
                        }.flatMap { entry ->
                            when {
                                entry.isDirectory() -> {
                                    walkSafe(root, entry).first
                                }

                                entry.isRegularFile() -> {
                                    listOf(entry)
                                }

                                else -> {
                                    emptyList()
                                }
                            }
                        } to warnings
                }

                else -> {
                    emptyList<Path>() to emptyList()
                }
            }
    }

    /** Gets the category name for this check. */
    fun category(): String = rule.category

    /** Checks whether this rule applies to the given manifest. */
    fun applies(manifest: JsonObject): Boolean = rule.applies(manifest)

    /** Validates the manifest and root against this rule. */
    fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults? = null,
    ): Collection<Finding> =
        rule.validate(manifest, root) +
            (psiResults?.findings?.filter { finding -> finding.category == category() } ?: emptyList())
}
