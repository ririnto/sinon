package ai.harness.gradle

import ai.harness.gradle.rules.ForbidBlankLineInLeafFunctionRule
import ai.harness.gradle.rules.ForbidEarlyReturnRule
import ai.harness.gradle.rules.ForbidEmptyCatchBlockRule
import ai.harness.gradle.rules.ForbidGreaterThanComparisonRule
import ai.harness.gradle.rules.ForbidImplicitLambdaItRule
import ai.harness.gradle.rules.ForbidMutableCollectionRule
import ai.harness.gradle.rules.ForbidScaffoldLeaksRule
import ai.harness.gradle.rules.ForbidSilentCatchRule
import ai.harness.gradle.rules.ForbidUncheckedTasksUnderRule
import ai.harness.gradle.rules.ForbidUnsafeSymlinksRule
import ai.harness.gradle.rules.ForbidUnstructuredLoggingRule
import ai.harness.gradle.rules.ForbidWildcardImportRule
import ai.harness.gradle.rules.HarnessCheckRule
import ai.harness.gradle.rules.RequireAgentFrontmatterRule
import ai.harness.gradle.rules.RequireBracesOnIfRule
import ai.harness.gradle.rules.RequireCiCommandMatchesHookRule
import ai.harness.gradle.rules.RequireCompanionObjectPositionRule
import ai.harness.gradle.rules.RequireDirectoriesExistRule
import ai.harness.gradle.rules.RequireDocCommentOnPublicDeclarationRule
import ai.harness.gradle.rules.RequireDocContentRule
import ai.harness.gradle.rules.RequireDocHeadingsRule
import ai.harness.gradle.rules.RequireEnvShebangUnderRule
import ai.harness.gradle.rules.RequireFilesExistRule
import ai.harness.gradle.rules.RequireHookCommandRule
import ai.harness.gradle.rules.RequireHookExecutableRule
import ai.harness.gradle.rules.RequireHookGeneratedMarkerRule
import ai.harness.gradle.rules.RequireHookShebangRule
import ai.harness.gradle.rules.RequireHookStageRule
import ai.harness.gradle.rules.RequireImportOverFqnRule
import ai.harness.gradle.rules.RequireKeefileInEmptyDirectoriesRule
import ai.harness.gradle.rules.RequireSingleTopLevelKotlinDeclarationRule
import ai.harness.gradle.rules.RequireSkillFrontmatterRule
import ai.harness.gradle.rules.RequireTemplateGroupsRule
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
    val category: String,
    val rule: HarnessCheckRule,
) {

    /** Verifies that required files exist at the repository root. */
    REQUIRE_FILES_EXIST("requireFilesExist", RequireFilesExistRule),
    /** Verifies that required directories exist at the repository root. */
    REQUIRE_DIRECTORIES_EXIST("requireDirectoriesExist", RequireDirectoriesExistRule),
    /** Verifies that empty directories contain a .keepfile placeholder. */
    REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES("requireKeepfileInEmptyDirectories", RequireKeefileInEmptyDirectoriesRule),
    /** Verifies that command and agent groups are properly organized. */
    REQUIRE_TEMPLATE_GROUPS("requireTemplateGroups", RequireTemplateGroupsRule),
    /** Verifies that Markdown documents have proper heading structure. */
    REQUIRE_DOC_HEADINGS("requireDocHeadings", RequireDocHeadingsRule),
    /** Verifies that Markdown documents contain substantive content. */
    REQUIRE_DOC_CONTENT("requireDocContent", RequireDocContentRule),
    /** Verifies that agent files declare required frontmatter fields. */
    REQUIRE_AGENT_FRONTMATTER("requireAgentFrontmatter", RequireAgentFrontmatterRule),
    /** Verifies that skill files declare required frontmatter fields. */
    REQUIRE_SKILL_FRONTMATTER("requireSkillFrontmatter", RequireSkillFrontmatterRule),
    /** Verifies that scaffold artifacts are not included in tracked files. */
    FORBID_SCAFFOLD_LEAKS("forbidScaffoldLeaks", ForbidScaffoldLeaksRule),
    /** Verifies that shell scripts have proper shebangs. */
    REQUIRE_HOOK_SHEBANG("requireHookShebang", RequireHookShebangRule),
    /** Verifies that hook scripts are executable. */
    REQUIRE_HOOK_EXECUTABLE("requireHookExecutable", RequireHookExecutableRule),
    /** Verifies that hook scripts contain the harness-generated marker. */
    REQUIRE_HOOK_GENERATED_MARKER("requireHookGeneratedMarker", RequireHookGeneratedMarkerRule),
    /** Verifies that hook scripts declare a valid stage. */
    REQUIRE_HOOK_STAGE("requireHookStage", RequireHookStageRule),
    /** Verifies that hook scripts declare a command to execute. */
    REQUIRE_HOOK_COMMAND("requireHookCommand", RequireHookCommandRule),
    /** Verifies that CI commands match their corresponding hook definitions. */
    REQUIRE_CI_COMMAND_MATCHES_HOOK("requireCiCommandMatchesHook", RequireCiCommandMatchesHookRule),
    /** Verifies that shebangs within files use the env form. */
    REQUIRE_ENV_SHEBANG_UNDER("requireEnvShebangUnder", RequireEnvShebangUnderRule),
    /** Verifies that only approved Gradle task categories are used. */
    FORBID_UNCHECKED_TASKS_UNDER("forbidUncheckedTasksUnder", ForbidUncheckedTasksUnderRule),
    /** Verifies that symbolic links conform to allowed patterns. */
    FORBID_UNSAFE_SYMLINKS("forbidUnsafeSymlinks", ForbidUnsafeSymlinksRule),
    /** Verifies that length comparisons do not use the greater-than operator. */
    FORBID_GREATER_THAN_COMPARISON("forbidGreaterThanComparison", ForbidGreaterThanComparisonRule),
    /** Verifies that leaf functions do not contain blank lines in their body. */
    FORBID_BLANK_LINE_IN_LEAF_FUNCTION("forbidBlankLineInLeafFunction", ForbidBlankLineInLeafFunctionRule),
    /** Verifies that lambda parameters are not used implicitly. */
    FORBID_IMPLICIT_LAMBDA_IT("forbidImplicitLambdaIt", ForbidImplicitLambdaItRule),
    /** Verifies that Kotlin files declare only a single top-level declaration. */
    REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION(
        "requireSingleTopLevelKotlinDeclaration",
        RequireSingleTopLevelKotlinDeclarationRule,
    ),
    /** Verifies that wildcard imports are not used. */
    FORBID_WILDCARD_IMPORT("forbidWildcardImport", ForbidWildcardImportRule),
    /** Verifies that catch blocks contain at least one statement. */
    FORBID_EMPTY_CATCH_BLOCK("forbidEmptyCatchBlock", ForbidEmptyCatchBlockRule),
    /** Verifies that if statements are wrapped in braces. */
    REQUIRE_BRACES_ON_IF("requireBracesOnIf", RequireBracesOnIfRule),
    /** Verifies that early returns are avoided in functions. */
    FORBID_EARLY_RETURN("forbidEarlyReturn", ForbidEarlyReturnRule),
    /** Verifies that catch blocks do not silently ignore exceptions. */
    FORBID_SILENT_CATCH("forbidSilentCatch", ForbidSilentCatchRule),
    /** Verifies that mutable collection builders are not used. */
    FORBID_MUTABLE_COLLECTION("forbidMutableCollection", ForbidMutableCollectionRule),
    /** Verifies that logging conforms to structured patterns. */
    FORBID_UNSTRUCTURED_LOGGING("forbidUnstructuredLogging", ForbidUnstructuredLoggingRule),
    /** Verifies that imports are used instead of fully qualified names. */
    REQUIRE_IMPORT_OVER_FQN("requireImportOverFqn", RequireImportOverFqnRule),
    /** Verifies that public declarations include documentation comments. */
    REQUIRE_DOC_COMMENT_ON_PUBLIC_DECLARATION(
        "requireDocCommentOnPublicDeclaration",
        RequireDocCommentOnPublicDeclarationRule,
    ),
    /** Verifies that companion objects are positioned first in the class body. */
    REQUIRE_COMPANION_OBJECT_POSITION("requireCompanionObjectPosition", RequireCompanionObjectPositionRule),
    ;
    /** Companion object providing utility functions for harness validation. */
    companion object {
        /** Returns the severity level for a given category in the manifest. */
        fun severityOf(
            manifest: JsonObject,
            category: String,
        ): Severity {
            val severity = manifest[category]?.jsonObject?.get("severity")?.jsonPrimitive?.contentOrNull
                ?: return Severity.ERROR
            return try {
                Severity.valueOf(severity.uppercase())
            } catch (e: Exception) {
                val skipped = e.localizedMessage
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
                    val target = if (p.name == "AGENTS.md") {
                        "CLAUDE.md"
                    } else {
                        "AGENTS.md"
                    }
                    (root / target).let { path ->
                        if (path.isRegularFile()) {
                            path.readText()
                        } else {
                            ""
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
            return try {
                p.readSymbolicLink().toString() == (if (p.name == "AGENTS.md") {
                    "CLAUDE.md"
                } else {
                    "AGENTS.md"
                }) &&
                    (root / (if (p.name == "AGENTS.md") {
                        "CLAUDE.md"
                    } else {
                        "AGENTS.md"
                    })).isRegularFile()
            } catch (e: Exception) {
                val skipped = e.localizedMessage
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
                    emptyList<Path>() to listOf(
                        Finding(
                            Severity.ERROR,
                            "forbidUnsafeSymlinks",
                            "symlink scan root is not allowed: ${base.relativeTo(root)}",
                        ),
                    )
                }
                base.isRegularFile() -> {
                    listOf(base) to emptyList()
                }
                base.isDirectory() -> {
                    val entries = try {
                        base.listDirectoryEntries()
                    } catch (e: Exception) {
                        val skipped = e.localizedMessage
                        emptyList()
                    }
                    val warnings = buildSet {
                        entries.filter { entry ->
                            entry.isSymbolicLink() && !isAllowedRootContractSymlink(root, entry)
                        }.forEach { entry ->
                            add(
                                Finding(
                                    Severity.ERROR,
                                    "forbidUnsafeSymlinks",
                                    "symlink path is not allowed: ${entry.relativeTo(root)}",
                                ),
                            )
                        }
                    }.toList()
                    entries.filter { entry ->
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

    /** Checks whether this rule applies to the given manifest. */
    fun applies(manifest: JsonObject): Boolean = rule.applies(manifest)

    /** Validates the manifest and root against this rule. */
    fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults? = null,
    ): Collection<Finding> = rule.validate(manifest, root, psiResults)
}
