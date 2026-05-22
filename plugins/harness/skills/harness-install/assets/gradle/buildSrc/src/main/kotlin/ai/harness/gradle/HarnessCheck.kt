package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.extension
import kotlin.io.path.isExecutable
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.toPath
import java.nio.file.Path

/**
 * Enumeration of all harness validation checks.
 */
enum class HarnessCheck {
	REQUIRE_FILES_EXIST {
		override val category = "requireFilesExist"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val paths = stringArrayFrom(parametersObj, "paths")
			return buildSet<Finding> {
				paths.forEach { path ->
					val p = java.io.File(root, path)
					when {
						p.toPath().isSymbolicLink() && !isAllowedRootContractSymlink(root, p) -> add(Finding(Severity.ERROR, category, "symlink file is not allowed: $path"))
						!p.isFile -> add(Finding(severity, category, "missing file: $path"))
					}
				}
			}.toList()
		}
	},
	REQUIRE_DIRECTORIES_EXIST {
		override val category = "requireDirectoriesExist"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val paths = stringArrayFrom(parametersObj, "paths")
			return buildSet<Finding> {
				paths.forEach { path ->
					val p = java.io.File(root, path)
					when {
						p.toPath().isSymbolicLink() -> add(Finding(Severity.ERROR, category, "symlink directory is not allowed: $path"))
						!p.isDirectory -> add(Finding(severity, category, "missing directory: $path"))
					}
				}
			}.toList()
		}
	},
	REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES {
		override val category = "requireKeepfileInEmptyDirectories"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val directories = stringArrayFrom(parametersObj, "directories")
			return buildSet<Finding> {
				directories.forEach { dirPath ->
					val dir = java.io.File(root, dirPath)
					if (!dir.isDirectory) return@forEach
					val realFiles = dir.listFiles().orEmpty().filter { it.name != ".gitkeep" }
					if (realFiles.isEmpty() && !java.io.File(root, "$dirPath/.gitkeep").exists()) {
						add(Finding(severity, category, "empty directory must keep placeholder or real files: $dirPath"))
					}
				}
			}.toList()
		}
	},
	REQUIRE_TEMPLATE_GROUPS {
		override val category = "requireTemplateGroups"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val targetRoot = stringFrom(parametersObj, "targetRoot")
			val groups = stringArrayFrom(parametersObj, "groups")
			return buildSet<Finding> {
				groups.forEach { group ->
					val p = java.io.File(root, "$targetRoot/$group")
					when {
						p.toPath().isSymbolicLink() -> add(Finding(Severity.ERROR, category, "symlink directory is not allowed: $targetRoot/$group"))
						!p.isDirectory -> add(Finding(severity, category, "missing template group: $targetRoot/$group"))
					}
				}
			}.toList()
		}
	},
	REQUIRE_DOC_HEADINGS {
		override val category = "requireDocHeadings"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val sourceCategory = stringFrom(parametersObj, "sourceFilesFromCategory")
			val sourceFiles = stringArrayFrom(manifest[sourceCategory]?.jsonObject?.get("parameters")?.jsonObject, "paths")
			val sourceFilterObj = parametersObj["sourceFilter"]?.jsonObject
			val prefix = stringFrom(sourceFilterObj, "prefix")
			val suffix = stringFrom(sourceFilterObj, "suffix")
			val headings = stringArrayFrom(parametersObj, "headings")

			val filtered = sourceFiles.filter { it.startsWith(prefix) && it.endsWith(suffix) }
			return buildSet<Finding> {
				filtered.forEach { docPath ->
					val content = readSafe(root, docPath)
					headings.forEach { heading ->
						if (!content.contains(heading)) {
							add(Finding(severity, category, "doc missing $heading: $docPath"))
						}
					}
				}
			}.toList()
		}
	},
	REQUIRE_DOC_CONTENT {
		override val category = "requireDocContent"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val checks = parametersObj["checks"]?.jsonArray ?: return emptyList()

			return buildSet<Finding> {
				checks.forEach { checkElem ->
					val checkObj = checkElem.jsonObject
					val files = stringArrayFrom(checkObj, "files")
					val containsAll = stringArrayFrom(checkObj, "containsAll")
					val failureMessage = stringFrom(checkObj, "failureMessage")

					val content = files.map { readSafe(root, it) }.joinToString("\n")
					val allPresent = containsAll.all { content.contains(it) }
					if (!allPresent) {
						add(Finding(severity, category, failureMessage))
					}
				}
			}.toList()
		}
	},
	REQUIRE_AGENT_FRONTMATTER {
		override val category = "requireAgentFrontmatter"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val directory = stringFrom(parametersObj, "directory")
			val dirPath = java.io.File(root, directory)

			if (!dirPath.isDirectory) {
				val msg = stringFrom(messagesObj, "missingDirectory").takeIf { it.isNotEmpty() } ?: ".claude/agents must contain at least one .md agent"
				return listOf(Finding(severity, category, msg))
			}

			val files = dirPath.listFiles().orEmpty().filter { it.isFile && it.extension == "md" }
			if (files.isEmpty()) {
				val msg = stringFrom(messagesObj, "missingAgent").takeIf { it.isNotEmpty() } ?: ".claude/agents must contain at least one .md agent"
				return listOf(Finding(severity, category, msg))
			}

			return buildSet<Finding> {
				files.forEach { file ->
					val text = file.readText()
					if (!text.startsWith("---")) {
						val msg = stringFrom(messagesObj, "missingFrontmatter").takeIf { it.isNotEmpty() } ?: "agent missing frontmatter: ${file.relativeTo(root)}"
						add(Finding(severity, category, msg))
					} else {
						if (!"""(?m)^name:\s*[-a-z0-9]+\s*$""".toRegex().containsMatchIn(text)) {
							val msg = stringFrom(messagesObj, "missingName").takeIf { it.isNotEmpty() } ?: "agent missing name: ${file.relativeTo(root)}"
							add(Finding(severity, category, msg))
						}
						if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
							val msg = stringFrom(messagesObj, "missingDescription").takeIf { it.isNotEmpty() } ?: "agent missing description: ${file.relativeTo(root)}"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	REQUIRE_SKILL_FRONTMATTER {
		override val category = "requireSkillFrontmatter"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val rootDirectory = stringFrom(parametersObj, "rootDirectory")
			val filename = stringFrom(parametersObj, "filename")

			val dirPath = java.io.File(root, rootDirectory)
			if (!dirPath.isDirectory) {
				val msg = stringFrom(messagesObj, "missingDirectory").takeIf { it.isNotEmpty() } ?: ".claude/skills must contain at least one SKILL.md"
				return listOf(Finding(severity, category, msg))
			}

			val files = dirPath.walk().filter { it.isFile && it.name == filename }.toList()
			if (files.isEmpty()) {
				val msg = stringFrom(messagesObj, "missingSkill").takeIf { it.isNotEmpty() } ?: ".claude/skills must contain at least one SKILL.md"
				return listOf(Finding(severity, category, msg))
			}

			return buildSet<Finding> {
				files.forEach { file ->
					val text = file.readText()
					if (!text.startsWith("---")) {
						val msg = stringFrom(messagesObj, "missingFrontmatter").takeIf { it.isNotEmpty() } ?: "skill missing frontmatter: ${file.relativeTo(root)}"
						add(Finding(severity, category, msg))
					} else {
						if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
							val msg = stringFrom(messagesObj, "missingDescription").takeIf { it.isNotEmpty() } ?: "skill missing description: ${file.relativeTo(root)}"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	FORBID_SCAFFOLD_LEAKS {
		override val category = "forbidScaffoldLeaks"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val scopeObj = parametersObj["scope"]?.jsonObject ?: return emptyList()
			val bases = stringArrayFrom(scopeObj, "bases")
			val excludedSubtrees = stringArrayFrom(scopeObj, "excludedSubtrees")
			val extensions = stringArrayFrom(scopeObj, "extensions")
			val patterns = parametersObj["patterns"]?.jsonArray?.mapNotNull { patternElem ->
				val obj = patternElem.jsonObject
				val pattern = stringFrom(obj, "pattern")
				val label = stringFrom(obj, "label")
				if (pattern.isEmpty() || label.isEmpty()) null else pattern to label
			} ?: emptyList()

			val excludedPaths = excludedSubtrees.map { java.io.File(root, it) }
			val regexes = patterns.mapNotNull { (pattern, label) ->
				try {
					pattern.toRegex() to label
				} catch (_: Exception) {
					null
				}
			}

			return buildSet<Finding> {
				bases.forEach { basePath ->
					val base = java.io.File(root, basePath)
					val (files, _) = walkSafe(root, base)
					files.forEach { file ->
						val ext = file.extension
						if (ext in extensions && excludedPaths.none { file.startsWith(it) }) {
							val text = file.readText()
							regexes.forEach { (regex, label) ->
								if (regex.containsMatchIn(text)) {
									val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$label in active asset: ${file.relativeTo(root)}"
									add(Finding(severity, category, msg))
								}
							}
						}
					}
				}
			}.toList()
		}
	},
	REQUIRE_HOOK_SHEBANG {
		override val category = "requireHookShebang"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val hooks = stringArrayFrom(parametersObj, "hooks")
			val expectedShebang = stringFrom(parametersObj, "expectedShebang")

			return buildSet<Finding> {
				hooks.forEach { hookPath ->
					val hook = java.io.File(root, hookPath)
					if (hook.isFile) {
						val first = hook.readLines().firstOrNull() ?: ""
						if (first != expectedShebang) {
							val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$hookPath must start with $expectedShebang"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	REQUIRE_HOOK_EXECUTABLE {
		override val category = "requireHookExecutable"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val hooks = stringArrayFrom(parametersObj, "hooks")

			return buildSet<Finding> {
				hooks.forEach { hookPath ->
					val hook = java.io.File(root, hookPath)
					if (hook.isFile && !hook.toPath().isExecutable()) {
						val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$hookPath must be executable"
						add(Finding(severity, category, msg))
					}
				}
			}.toList()
		}
	},
	REQUIRE_HOOK_GENERATED_MARKER {
		override val category = "requireHookGeneratedMarker"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val hooks = stringArrayFrom(parametersObj, "hooks")
			val markerTemplate = stringFrom(parametersObj, "markerTemplate")
			val placeholderForbidden = stringFrom(parametersObj, "placeholderForbidden")

			return buildSet<Finding> {
				hooks.forEach { hookPath ->
					val hook = java.io.File(root, hookPath)
					if (hook.isFile) {
						val text = hook.readText()
						val marker = markerTemplate.replace("{name}", hook.name)
						if (!text.contains(marker)) {
							val msg = stringFrom(messagesObj, "missingMarker").takeIf { it.isNotEmpty() } ?: "$hookPath must contain generated marker '$marker'"
							add(Finding(severity, category, msg))
						}
						if (text.contains(placeholderForbidden)) {
							val msg = stringFrom(messagesObj, "placeholderPresent").takeIf { it.isNotEmpty() } ?: "$hookPath still contains packaging placeholder text"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	REQUIRE_HOOK_STAGE {
		override val category = "requireHookStage"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val markerTemplate = stringFrom(parametersObj, "markerTemplate")
			val stagesObj = parametersObj["stages"]?.jsonObject ?: return emptyList()
			val gradleStages = stagesObj["gradle"]?.jsonObject ?: return emptyList()
			val preCommitStage = stringFrom(gradleStages, "pre-commit")
			val prePushStage = stringFrom(gradleStages, "pre-push")

			return buildSet<Finding> {
				val preCommitHook = java.io.File(root, "docs/harness/git-hooks/pre-commit")
				if (preCommitHook.isFile) {
					val marker = markerTemplate.replace("{stage}", preCommitStage)
					if (!preCommitHook.readText().contains(marker)) {
						val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "pre-commit must contain stage marker '$marker'"
						add(Finding(severity, category, msg))
					}
				}

				val prePushHook = java.io.File(root, "docs/harness/git-hooks/pre-push")
				if (prePushHook.isFile) {
					val marker = markerTemplate.replace("{stage}", prePushStage)
					if (!prePushHook.readText().contains(marker)) {
						val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "pre-push must contain stage marker '$marker'"
						add(Finding(severity, category, msg))
					}
				}
			}.toList()
		}
	},
	REQUIRE_HOOK_COMMAND {
		override val category = "requireHookCommand"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val allowedCmdObj = parametersObj["allowedCommands"]?.jsonObject
			val allowedCmds = stringArrayFrom(allowedCmdObj, "gradle")
			val allowedPreCommitCmdObj = parametersObj["allowedPreCommitCommands"]?.jsonObject
			val allowedPreCommitCmds = stringArrayFrom(allowedPreCommitCmdObj, "gradle")
			val prePushPath = stringFrom(parametersObj, "prePushHook")
			val preCommitPath = stringFrom(parametersObj, "preCommitHook")

			return buildSet<Finding> {
				val prePushHook = java.io.File(root, prePushPath)
				if (prePushHook.isFile) {
					val text = prePushHook.readText()
					val command = text.lineSequence().firstOrNull { it.startsWith("# Harness validation command: ") }?.removePrefix("# Harness validation command: ")?.trim() ?: ""
					when {
						command.isEmpty() -> {
							val msg = stringFrom(messagesObj, "missingDeclaration").takeIf { it.isNotEmpty() } ?: "pre-push hook must declare Harness validation command"
							add(Finding(severity, category, msg))
						}
						command !in allowedCmds -> {
							val msg = stringFrom(messagesObj, "unsupportedCommand").takeIf { it.isNotEmpty() } ?: "pre-push hook declares unsupported validation command: $command"
							add(Finding(severity, category, msg))
						}
						!text.contains(command) -> {
							val msg = stringFrom(messagesObj, "commandNotRun").takeIf { it.isNotEmpty() } ?: "pre-push hook must run the declared validation command"
							add(Finding(severity, category, msg))
						}
					}
				}

				val preCommitHook = java.io.File(root, preCommitPath)
				if (preCommitHook.isFile) {
					val text = preCommitHook.readText()
					val command = text.lineSequence().firstOrNull { it.startsWith("# Harness validation command: ") }?.removePrefix("# Harness validation command: ")?.trim() ?: ""
					when {
						command.isEmpty() && allowedPreCommitCmds.isNotEmpty() -> {
							val msg = stringFrom(messagesObj, "missingDeclaration").takeIf { it.isNotEmpty() } ?: "pre-commit hook must declare validation command"
							add(Finding(severity, category, msg))
						}
						command.isNotEmpty() && command !in allowedPreCommitCmds -> {
							val msg = stringFrom(messagesObj, "unsupportedCommand").takeIf { it.isNotEmpty() } ?: "pre-commit hook declares unsupported validation command: $command"
							add(Finding(severity, category, msg))
						}
						command.isNotEmpty() && !text.contains(command) -> {
							val msg = stringFrom(messagesObj, "commandNotRun").takeIf { it.isNotEmpty() } ?: "pre-commit hook must run the declared validation command"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	REQUIRE_CI_COMMAND_MATCHES_HOOK {
		override val category = "requireCiCommandMatchesHook"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val ciFiles = stringArrayFrom(parametersObj, "ciFiles")
			val referenceHookPath = stringFrom(parametersObj, "referenceHook")

			val referenceHook = java.io.File(root, referenceHookPath)
			val command = if (referenceHook.isFile) {
				referenceHook.readText().lineSequence().firstOrNull { it.startsWith("# Harness validation command: ") }?.removePrefix("# Harness validation command: ")?.trim() ?: ""
			} else {
				""
			}

			if (command.isEmpty()) return emptyList()

			return buildSet<Finding> {
				ciFiles.forEach { ciFile ->
					val ciPath = java.io.File(root, ciFile)
					if (ciPath.isFile && !ciPath.readText().contains(command)) {
						val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$ciFile: CI command mismatch — expected $command"
						add(Finding(severity, category, msg))
					}
				}
			}.toList()
		}
	},
	REQUIRE_ENV_SHEBANG_UNDER {
		override val category = "requireEnvShebangUnder"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val directories = stringArrayFrom(parametersObj, "directories")
			val expectedPrefix = stringFrom(parametersObj, "expectedPrefix")

			return buildSet<Finding> {
				directories.forEach { dirPath ->
					val dir = java.io.File(root, dirPath)
					if (!dir.isDirectory) return@forEach

					val (files, _) = walkSafe(root, dir)
					files.filter { it.toPath().isExecutable() }.forEach { file ->
						val firstLine = file.readLines().firstOrNull() ?: ""
						if (firstLine.startsWith("#!") && !firstLine.startsWith(expectedPrefix)) {
							val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "executable script should use /usr/bin/env shebang: ${file.relativeTo(root)}"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	FORBID_UNCHECKED_TASKS_UNDER {
		override val category = "forbidUncheckedTasksUnder"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val directory = stringFrom(parametersObj, "directory")
			val uncheckedTaskPattern = stringFrom(parametersObj, "uncheckedTaskPattern")

			val dirPath = java.io.File(root, directory)
			if (!dirPath.isDirectory) return emptyList()

			val (files, _) = walkSafe(root, dirPath)
			val pattern = try {
				uncheckedTaskPattern.toRegex()
			} catch (_: Exception) {
				return emptyList()
			}

			return buildSet<Finding> {
				files.filter { it.name.endsWith(".md") }.forEach { file ->
					if (pattern.containsMatchIn(file.readText())) {
						val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "completed plan has unchecked tasks: ${file.relativeTo(root)}"
						add(Finding(severity, category, msg))
					}
				}
			}.toList()
		}
	},
	FORBID_UNSAFE_SYMLINKS {
		override val category = "forbidUnsafeSymlinks"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val allowedPairs = parametersObj["allowedSymlinkPairs"]?.jsonArray?.mapNotNull { pairElem ->
				val pair = pairElem.jsonArray
				if (pair.size < 2) null else {
					val a = pair[0].jsonPrimitive.contentOrNull ?: return@mapNotNull null
					val b = pair[1].jsonPrimitive.contentOrNull ?: return@mapNotNull null
					a to b
				}
			} ?: emptyList()

			val allowed = buildSet<Pair<String, String>> {
				allowedPairs.forEach { (a, b) ->
					add(a to b)
					add(b to a)
				}
			}

			return buildSet<Finding> {
				val rootFiles = root.listFiles().orEmpty()
				rootFiles.filter { it.toPath().isSymbolicLink() }.forEach { file ->
					val target = try {
						file.toPath().readSymbolicLink().toString()
					} catch (_: Exception) {
						""
					}
					if (file.name to target !in allowed) {
						val msg = stringFrom(messagesObj, "fileNotAllowed").takeIf { it.isNotEmpty() } ?: "symlink file is not allowed: ${file.relativeTo(root)}"
						add(Finding(Severity.ERROR, category, msg))
					}
				}
			}.toList()
		}
	},
	FORBID_GREATER_THAN_COMPARISON {
		override val category = "forbidGreaterThanComparison"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val sourceRootsPerStack = parametersObj["sourceRootsPerStack"]?.jsonObject ?: return emptyList()
			val extensionsPerStack = parametersObj["extensionsPerStack"]?.jsonObject ?: return emptyList()
			val kotlinDirs = stringArrayFrom(sourceRootsPerStack, "kotlin")
			val kotlinExts = stringArrayFrom(extensionsPerStack, "kotlin")

			val results = psiResults?.greaterThanComparisons ?: emptyList()

			return buildSet<Finding> {
				kotlinDirs.forEach { dirPattern ->
					val dir = java.io.File(root, dirPattern)
					if (!dir.exists()) return@forEach

					val (files, _) = walkSafe(root, dir)
					files.filter { file ->
						val ext = file.toPath().extension
						ext in kotlinExts
					}.forEach { file ->
						results.filter { it.file == file.name }.forEach { hit ->
							val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}:${hit.line}: forbidden `>`/`>=`; use `<`/`<=`"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	FORBID_BLANK_LINE_IN_LEAF_FUNCTION {
		override val category = "forbidBlankLineInLeafFunction"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val sourceRootsPerStack = parametersObj["sourceRootsPerStack"]?.jsonObject ?: return emptyList()
			val extensionsPerStack = parametersObj["extensionsPerStack"]?.jsonObject ?: return emptyList()
			val kotlinDirs = stringArrayFrom(sourceRootsPerStack, "kotlin")
			val kotlinExts = stringArrayFrom(extensionsPerStack, "kotlin")

			val results = psiResults?.blankLinesInLeafFunctions ?: emptyList()

			return buildSet<Finding> {
				kotlinDirs.forEach { dirPattern ->
					val dir = java.io.File(root, dirPattern)
					if (!dir.exists()) return@forEach

					val (files, _) = walkSafe(root, dir)
					files.filter { file ->
						val ext = file.toPath().extension
						ext in kotlinExts
					}.forEach { file ->
						results.filter { it.file == file.name }.forEach { hit ->
							val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}:${hit.line}: blank line in leaf function"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	FORBID_IMPLICIT_LAMBDA_IT {
		override val category = "forbidImplicitLambdaIt"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val sourceRootsPerStack = parametersObj["sourceRootsPerStack"]?.jsonObject ?: return emptyList()
			val extensionsPerStack = parametersObj["extensionsPerStack"]?.jsonObject ?: return emptyList()
			val kotlinDirs = stringArrayFrom(sourceRootsPerStack, "kotlin")
			val kotlinExts = stringArrayFrom(extensionsPerStack, "kotlin")

			val results = psiResults?.implicitLambdaIt ?: emptyList()

			return buildSet<Finding> {
				kotlinDirs.forEach { dirPattern ->
					val dir = java.io.File(root, dirPattern)
					if (!dir.exists()) return@forEach

					val (files, _) = walkSafe(root, dir)
					files.filter { file ->
						val ext = file.toPath().extension
						ext in kotlinExts
					}.forEach { file ->
						results.filter { it.file == file.name }.forEach { hit ->
							val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}:${hit.line}: implicit lambda parameter 'it' is forbidden"
							add(Finding(severity, category, msg))
						}
					}
				}
			}.toList()
		}
	},
	REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION {
		override val category = "requireSingleTopLevelKotlinDeclaration"
		override fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults?): List<Finding> {
			val severity = severityOf(manifest, category)
			val catObj = manifest[category]?.jsonObject ?: return emptyList()
			val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
			val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
			val sourceRootsPerStack = parametersObj["sourceRootsPerStack"]?.jsonObject ?: return emptyList()
			val extensionsPerStack = parametersObj["extensionsPerStack"]?.jsonObject ?: return emptyList()
			val kotlinDirs = stringArrayFrom(sourceRootsPerStack, "kotlin")
			val kotlinExts = stringArrayFrom(extensionsPerStack, "kotlin")
			val allowedDeclarations = stringArrayFrom(parametersObj, "allowedDeclarations")

			val results = psiResults?.topLevelDeclarations ?: emptyList()

			return buildSet<Finding> {
				kotlinDirs.forEach { dirPattern ->
					val dir = java.io.File(root, dirPattern)
					if (!dir.exists()) return@forEach

					val (files, _) = walkSafe(root, dir)
					files.filter { file ->
						val ext = file.toPath().extension
						ext in kotlinExts
					}.forEach { file ->
						val info = results.find { it.file == file.name }
						if (info != null) {
							when {
								info.count != 1 -> {
									val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}: file must have single top-level declaration, found ${info.count}"
									add(Finding(severity, category, msg))
								}
								else -> {
									val declType = info.firstKind
									if (declType != "unknown" && declType !in allowedDeclarations) {
										val msg = stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}: top-level $declType is not allowed"
										add(Finding(severity, category, msg))
									}
								}
							}
						}
					}
				}
			}.toList()
		}
	},
	;

	abstract val category: String
	abstract fun validate(manifest: JsonObject, root: java.io.File, psiResults: HarnessPsiResults? = null): List<Finding>

	fun applies(manifest: JsonObject): Boolean {
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	private companion object {
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

		fun readSafe(root: java.io.File, path: String): String {
			val p = java.io.File(root, path)
			return when {
				p.toPath().isSymbolicLink() && isAllowedRootContractSymlink(root, p) -> {
					val target = if (p.name == "AGENTS.md") "CLAUDE.md" else "AGENTS.md"
					val resolved = java.io.File(root, target)
					if (resolved.isFile) resolved.readText() else ""
				}
				p.toPath().isSymbolicLink() -> ""
				p.isFile -> p.readText()
				else -> ""
			}
		}

		fun isAllowedRootContractSymlink(root: java.io.File, p: java.io.File): Boolean {
			if (p.parentFile != root || p.name !in setOf("AGENTS.md", "CLAUDE.md")) return false
			val expected = if (p.name == "AGENTS.md") "CLAUDE.md" else "AGENTS.md"
			return try {
				p.toPath().readSymbolicLink().toString() == expected && java.io.File(root, expected).isFile
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

		fun walkSafe(root: java.io.File, base: java.io.File): Pair<List<java.io.File>, List<Finding>> {
			return when {
				!base.exists() -> emptyList<java.io.File>() to emptyList()
				base.toPath().isSymbolicLink() && !isAllowedRootContractSymlink(root, base) ->
					emptyList<java.io.File>() to listOf(Finding(Severity.ERROR, "forbidUnsafeSymlinks", "symlink scan root is not allowed: ${base.relativeTo(root)}"))
				base.isFile -> listOf(base) to emptyList()
				base.isDirectory -> {
					val entries = base.listFiles().orEmpty()
					val warnings = buildSet<Finding> {
						entries.filter { it.toPath().isSymbolicLink() && !isAllowedRootContractSymlink(root, it) }.forEach {
							add(Finding(Severity.ERROR, "forbidUnsafeSymlinks", "symlink path is not allowed: ${it.relativeTo(root)}"))
						}
					}.toList()

					val files = entries.filter { !it.toPath().isSymbolicLink() }.flatMap { entry ->
						when {
							entry.isDirectory -> walkSafe(root, entry).first
							entry.isFile -> listOf(entry)
							else -> emptyList()
						}
					}

					files to warnings
				}
				else -> emptyList<java.io.File>() to emptyList()
			}
		}
	}
}
