package ai.harness.gradle

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
enum class HarnessCheck(val category: String, val rule: HarnessCheckRule) {
	REQUIRE_FILES_EXIST("requireFilesExist", RequireFilesExistRule()),
	REQUIRE_DIRECTORIES_EXIST("requireDirectoriesExist", RequireDirectoriesExistRule()),
	REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES("requireKeepfileInEmptyDirectories", RequireKeefileInEmptyDirectoriesRule()),
	REQUIRE_TEMPLATE_GROUPS("requireTemplateGroups", RequireTemplateGroupsRule()),
	REQUIRE_DOC_HEADINGS("requireDocHeadings", RequireDocHeadingsRule()),
	REQUIRE_DOC_CONTENT("requireDocContent", RequireDocContentRule()),
	REQUIRE_AGENT_FRONTMATTER("requireAgentFrontmatter", RequireAgentFrontmatterRule()),
	REQUIRE_SKILL_FRONTMATTER("requireSkillFrontmatter", RequireSkillFrontmatterRule()),
	FORBID_SCAFFOLD_LEAKS("forbidScaffoldLeaks", ForbidScaffoldLeaksRule()),
	REQUIRE_HOOK_SHEBANG("requireHookShebang", RequireHookShebangRule()),
	REQUIRE_HOOK_EXECUTABLE("requireHookExecutable", RequireHookExecutableRule()),
	REQUIRE_HOOK_GENERATED_MARKER("requireHookGeneratedMarker", RequireHookGeneratedMarkerRule()),
	REQUIRE_HOOK_STAGE("requireHookStage", RequireHookStageRule()),
	REQUIRE_HOOK_COMMAND("requireHookCommand", RequireHookCommandRule()),
	REQUIRE_CI_COMMAND_MATCHES_HOOK("requireCiCommandMatchesHook", RequireCiCommandMatchesHookRule()),
	REQUIRE_ENV_SHEBANG_UNDER("requireEnvShebangUnder", RequireEnvShebangUnderRule()),
	FORBID_UNCHECKED_TASKS_UNDER("forbidUncheckedTasksUnder", ForbidUncheckedTasksUnderRule()),
	FORBID_UNSAFE_SYMLINKS("forbidUnsafeSymlinks", ForbidUnsafeSymlinksRule()),
	FORBID_GREATER_THAN_COMPARISON("forbidGreaterThanComparison", ForbidGreaterThanComparisonRule()),
	FORBID_BLANK_LINE_IN_LEAF_FUNCTION("forbidBlankLineInLeafFunction", ForbidBlankLineInLeafFunctionRule()),
	FORBID_IMPLICIT_LAMBDA_IT("forbidImplicitLambdaIt", ForbidImplicitLambdaItRule()),
	REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION("requireSingleTopLevelKotlinDeclaration", RequireSingleTopLevelKotlinDeclarationRule()),
	FORBID_WILDCARD_IMPORT("forbidWildcardImport", ForbidWildcardImportRule()),
	FORBID_EMPTY_CATCH_BLOCK("forbidEmptyCatchBlock", ForbidEmptyCatchBlockRule()),
	REQUIRE_BRACES_ON_IF("requireBracesOnIf", RequireBracesOnIfRule());

	fun applies(manifest: JsonObject): Boolean {
		return rule.applies(manifest)
	}

	fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults? = null): List<Finding> {
		return rule.validate(manifest, root, psiResults)
	}

	companion object {
		fun severityOf(manifest: JsonObject, category: String): Severity {
			val severity = manifest[category]
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

		fun readSafe(root: Path, path: String): String {
			val p = root / path
			return when {
				p.isSymbolicLink() && isAllowedRootContractSymlink(root, p) -> {
					val target = if (p.name == "AGENTS.md") "CLAUDE.md" else "AGENTS.md"
					val resolved = root / target
					if (resolved.isRegularFile()) resolved.readText() else ""
				}
				p.isSymbolicLink() -> ""
				p.isRegularFile() -> p.readText()
				else -> ""
			}
		}

		fun isAllowedRootContractSymlink(root: Path, p: Path): Boolean {
			if (p.parent != root || p.name !in setOf("AGENTS.md", "CLAUDE.md")) {
				return false
			}
			val expected = if (p.name == "AGENTS.md") "CLAUDE.md" else "AGENTS.md"
			return try {
				p.readSymbolicLink().toString() == expected && (root / expected).isRegularFile()
			} catch (_: Exception) {
				false
			}
		}

		fun stringArrayFrom(obj: JsonObject?, key: String): List<String> {
			return obj?.get(key)?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
		}

		fun stringFrom(obj: JsonObject?, key: String): String {
			return obj?.get(key)?.jsonPrimitive?.contentOrNull ?: ""
		}

		fun walkSafe(root: Path, base: Path): Pair<List<Path>, List<Finding>> {
			return when {
				!base.exists() -> emptyList<Path>() to emptyList()
				base.isSymbolicLink() && !isAllowedRootContractSymlink(root, base) ->
					emptyList<Path>() to listOf(Finding(Severity.ERROR, "forbidUnsafeSymlinks", "symlink scan root is not allowed: ${base.relativeTo(root)}"))
				base.isRegularFile() -> listOf(base) to emptyList()
				base.isDirectory() -> {
					val entries = try {
						base.listDirectoryEntries()
					} catch (_: Exception) {
						emptyList()
					}
					val warnings = buildSet<Finding> {
						entries.filter { it.isSymbolicLink() && !isAllowedRootContractSymlink(root, it) }.forEach {
							add(Finding(Severity.ERROR, "forbidUnsafeSymlinks", "symlink path is not allowed: ${it.relativeTo(root)}"))
						}
					}.toList()

					val files = entries.filter { !it.isSymbolicLink() }.flatMap { entry ->
						when {
							entry.isDirectory() -> walkSafe(root, entry).first
							entry.isRegularFile() -> listOf(entry)
							else -> emptyList()
						}
					}

					files to warnings
				}
				else -> emptyList<Path>() to emptyList()
			}
		}
	}
}
