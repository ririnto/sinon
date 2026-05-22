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
import kotlin.io.path.walk

/**
 * Enumeration of all harness validation checks.
 */
enum class HarnessCheck(
    val category: String,
    val rule: HarnessCheckRule,
) {
    REQUIRE_FILES_EXIST("requireFilesExist", RequireFilesExistRule),
    REQUIRE_DIRECTORIES_EXIST("requireDirectoriesExist", RequireDirectoriesExistRule),
    REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES("requireKeepfileInEmptyDirectories", RequireKeefileInEmptyDirectoriesRule),
    REQUIRE_TEMPLATE_GROUPS("requireTemplateGroups", RequireTemplateGroupsRule),
    REQUIRE_DOC_HEADINGS("requireDocHeadings", RequireDocHeadingsRule),
    REQUIRE_DOC_CONTENT("requireDocContent", RequireDocContentRule),
    REQUIRE_AGENT_FRONTMATTER("requireAgentFrontmatter", RequireAgentFrontmatterRule),
    REQUIRE_SKILL_FRONTMATTER("requireSkillFrontmatter", RequireSkillFrontmatterRule),
    FORBID_SCAFFOLD_LEAKS("forbidScaffoldLeaks", ForbidScaffoldLeaksRule),
    REQUIRE_HOOK_SHEBANG("requireHookShebang", RequireHookShebangRule),
    REQUIRE_HOOK_EXECUTABLE("requireHookExecutable", RequireHookExecutableRule),
    REQUIRE_HOOK_GENERATED_MARKER("requireHookGeneratedMarker", RequireHookGeneratedMarkerRule),
    REQUIRE_HOOK_STAGE("requireHookStage", RequireHookStageRule),
    REQUIRE_HOOK_COMMAND("requireHookCommand", RequireHookCommandRule),
    REQUIRE_CI_COMMAND_MATCHES_HOOK("requireCiCommandMatchesHook", RequireCiCommandMatchesHookRule),
    REQUIRE_ENV_SHEBANG_UNDER("requireEnvShebangUnder", RequireEnvShebangUnderRule),
    FORBID_UNCHECKED_TASKS_UNDER("forbidUncheckedTasksUnder", ForbidUncheckedTasksUnderRule),
    FORBID_UNSAFE_SYMLINKS("forbidUnsafeSymlinks", ForbidUnsafeSymlinksRule),
    FORBID_GREATER_THAN_COMPARISON("forbidGreaterThanComparison", ForbidGreaterThanComparisonRule),
    FORBID_BLANK_LINE_IN_LEAF_FUNCTION("forbidBlankLineInLeafFunction", ForbidBlankLineInLeafFunctionRule),
    FORBID_IMPLICIT_LAMBDA_IT("forbidImplicitLambdaIt", ForbidImplicitLambdaItRule),
    REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION(
        "requireSingleTopLevelKotlinDeclaration",
        RequireSingleTopLevelKotlinDeclarationRule,
    ),
    FORBID_WILDCARD_IMPORT("forbidWildcardImport", ForbidWildcardImportRule),
    FORBID_EMPTY_CATCH_BLOCK("forbidEmptyCatchBlock", ForbidEmptyCatchBlockRule),
    REQUIRE_BRACES_ON_IF("requireBracesOnIf", RequireBracesOnIfRule),
    FORBID_EARLY_RETURN("forbidEarlyReturn", ForbidEarlyReturnRule),
    FORBID_SILENT_CATCH("forbidSilentCatch", ForbidSilentCatchRule),
    FORBID_MUTABLE_COLLECTION("forbidMutableCollection", ForbidMutableCollectionRule),
    FORBID_UNSTRUCTURED_LOGGING("forbidUnstructuredLogging", ForbidUnstructuredLoggingRule),
    REQUIRE_IMPORT_OVER_FQN("requireImportOverFqn", RequireImportOverFqnRule),
    REQUIRE_DOC_COMMENT_ON_PUBLIC_DECLARATION(
        "requireDocCommentOnPublicDeclaration",
        RequireDocCommentOnPublicDeclarationRule,
    ),
    REQUIRE_COMPANION_OBJECT_POSITION("requireCompanionObjectPosition", RequireCompanionObjectPositionRule),
    ;

    fun applies(manifest: JsonObject): Boolean = rule.applies(manifest)

    fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults? = null,
    ): Collection<Finding> = rule.validate(manifest, root, psiResults)

    companion object {
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
            } catch (_: Exception) {
                Severity.ERROR
            }
        }

        fun readSafe(
            root: Path,
            path: String,
        ): String {
            val p = root / path
            return when {
                p.isSymbolicLink() && isAllowedRootContractSymlink(root, p) -> {
                    val target = if (p.name == "AGENTS.md") "CLAUDE.md" else "AGENTS.md"
                    (root / target).let { if (it.isRegularFile()) it.readText() else "" }
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

        fun isAllowedRootContractSymlink(
            root: Path,
            p: Path,
        ): Boolean {
            if (p.parent != root || p.name !in setOf("AGENTS.md", "CLAUDE.md")) {
                return false
            }
            return try {
                p.readSymbolicLink().toString() == (if (p.name == "AGENTS.md") "CLAUDE.md" else "AGENTS.md") &&
                    (root / (if (p.name == "AGENTS.md") "CLAUDE.md" else "AGENTS.md")).isRegularFile()
            } catch (_: Exception) {
                false
            }
        }

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

        fun stringFrom(
            obj: JsonObject?,
            key: String,
        ): String = obj?.get(key)?.jsonPrimitive?.contentOrNull ?: ""

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
                                "forbidUnsafeSymlinks",
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
                        } catch (_: Exception) {
                            emptyList()
                        }
                    val warnings =
                        buildSet {
                            entries.filter { it.isSymbolicLink() && !isAllowedRootContractSymlink(root, it) }.forEach {
                                add(
                                    Finding(
                                        Severity.ERROR,
                                        "forbidUnsafeSymlinks",
                                        "symlink path is not allowed: ${it.relativeTo(root)}",
                                    ),
                                )
                            }
                        }.toList()

                    entries.filter { !it.isSymbolicLink() }.flatMap { entry ->
                        when {
                            entry.isDirectory() -> walkSafe(root, entry).first
                            entry.isRegularFile() -> listOf(entry)
                            else -> emptyList()
                        }
                    } to warnings
                }

                else -> {
                    emptyList<Path>() to emptyList()
                }
            }
    }
}
