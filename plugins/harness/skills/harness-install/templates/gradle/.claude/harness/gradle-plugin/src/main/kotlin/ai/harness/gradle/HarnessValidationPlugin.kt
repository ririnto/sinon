package ai.harness.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files

/**
 * Registers the harness validation task on the root project.
 */
class HarnessValidationPlugin : Plugin<Project> {
    /**
     * Applies the plugin to the root project.
     */
    override fun apply(project: Project) {
        if (project == project.rootProject) {
            project.tasks.register("harnessValidate", HarnessValidationTask::class.java) { task ->
                task.group = "verification"
                task.description = "Validate Claude repository harness assets."
            }
        }
    }

    /**
     * Gradle task that validates installed Claude repository harness assets.
     */
    abstract class HarnessValidationTask : DefaultTask() {
        /**
         * Validates required harness files, generated artifact policy, templates, hooks, and executable shebangs.
         */
        @TaskAction
        fun validate() {
            val root = project.rootDir
            val failures = mutableListOf<String>()
            validateManifestParity(root, failures)
            requiredFiles.forEach { requiredFile ->
                if (!isSafeFile(root, File(root, requiredFile), failures)) {
                    failures += "missing file: $requiredFile"
                }
            }
            requiredDirectories.forEach { requiredDirectory ->
                if (!isSafeDirectory(root, File(root, requiredDirectory), failures)) {
                    failures += "missing directory: $requiredDirectory"
                }
            }
            validateKeepFiles(root, failures)
            validateDocs(root, failures)
            val agentsText = read(root, "AGENTS.md")
            val claudeText = read(root, "CLAUDE.md")
            val generatedText = listOf(
                agentsText,
                claudeText,
                read(root, "ARCHITECTURE.md"),
            ).joinToString("\n")
            val evolutionText = listOf(
                agentsText,
                claudeText,
                read(root, ".claude/harness/evolution-log.md"),
            ).joinToString("\n")
            failures += validateRequiredContent(agentsText, claudeText, generatedText, evolutionText)
            validateAgents(root, failures)
            validateSkills(root, failures)
            templateGroups.forEach { templateGroup ->
                if (!isSafeDirectory(root, File(root, ".claude/harness/templates/$templateGroup"), failures)) {
                    failures += "missing template group: .claude/harness/templates/$templateGroup"
                }
            }
            validateActiveAssets(root, failures)
            validateHook(root, failures)
            validateEnvShebangs(root, failures)
            if (failures.isNotEmpty()) {
                throw GradleException(
                    "Harness validation failed:" +
                        System.lineSeparator() +
                        failures.distinct().joinToString(System.lineSeparator()),
                )
            }
            logger.lifecycle("Harness validation passed")
        }

        private fun read(root: File, path: String): String {
            val file = File(root, path)
            val target = allowedRootContractTarget(root, file)
            if (Files.isSymbolicLink(file.toPath()) && target == null) {
                return ""
            }
            return (target ?: file).takeIf { candidateFile ->
                candidateFile.isFile
            }?.readText() ?: ""
        }

        private fun validateRequiredContent(
            agentsText: String,
            claudeText: String,
            generatedText: String,
            evolutionText: String,
        ): List<String> = buildList {
            if (!agentsText.contains("Repository Harness Contract")) {
                add("AGENTS.md must contain Repository Harness Contract")
            }
            if (!claudeText.contains("Claude Code Entry Point")) {
                add("CLAUDE.md must contain Claude Code Entry Point")
            }
            if (!claudeText.contains("AGENTS.md")) {
                add("CLAUDE.md must reference AGENTS.md")
            }
            if (!agentsText.contains("docs/generated/")) {
                add("AGENTS.md must describe docs/generated/ semantics")
            }
            if (!generatedText.contains("docs/generated/db-schema.md")) {
                add(
                    "repository docs must state that docs/generated/db-schema.md is only an example, " +
                        "not a required scaffold file",
                )
            }
            if (
                !generatedText.contains("source command") ||
                    !generatedText.contains("regeneration trigger")
            ) {
                add(
                    "repository docs must describe generated-artifact source command and " +
                        "regeneration trigger metadata",
                )
            }
            if (
                !evolutionText.contains("discovery") ||
                    !evolutionText.contains("maintenance")
            ) {
                add("repository docs must state that the harness may evolve across development phases")
            }
        }

        private fun validateManifestParity(root: File, failures: MutableList<String>) {
            val manifest = read(root, ".claude/harness/manifest.json")
            if (Files.isSymbolicLink(File(root, ".claude/harness/manifest.json").toPath())) {
                failures += "symlink file is not allowed: .claude/harness/manifest.json"
                return
            }
            if (manifest.isBlank()) {
                failures += "missing file: .claude/harness/manifest.json"
                return
            }
            compareManifestList(manifest, "requiredFiles", requiredFiles, failures)
            compareManifestList(manifest, "requiredDirectories", requiredDirectories, failures)
            compareManifestList(manifest, "emptyDirectoryKeepFiles", emptyDirectoryKeepFiles, failures)
            compareManifestList(manifest, "optionalSeedFiles", optionalSeedFiles, failures)
            compareManifestList(manifest, "templateGroups", templateGroups, failures)
        }

        private fun compareManifestList(
            manifest: String,
            key: String,
            expected: List<String>,
            failures: MutableList<String>,
        ) {
            val actual = Regex(
                """"$key"\s*:\s*\[(.*?)]""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(manifest)
                ?.groupValues
                ?.get(1)
                ?.let { manifestListBody ->
                    Regex(""""([^"\\]+)"""")
                        .findAll(manifestListBody)
                        .map { match ->
                            match.groupValues[1]
                        }
                        .toList()
                }.orEmpty()
            if (actual.sorted() != expected.sorted()) {
                failures += "manifest $key must match validator constants"
            }
        }

        private fun validateKeepFiles(root: File, failures: MutableList<String>) {
            emptyDirectoryKeepFiles.forEach { keepFilePath ->
                val keepFile = File(root, keepFilePath)
                val directory = keepFile.parentFile
                if (directory == null || !isSafeDirectory(root, directory, failures)) {
                    return@forEach
                }
                val realFiles = directory.listFiles()?.filter { candidateFile ->
                    candidateFile.name != ".gitkeep"
                }.orEmpty()
                if (realFiles.isEmpty() && !isSafeFile(root, keepFile, failures)) {
                    failures +=
                        "empty directory must keep placeholder or real files: " +
                            directory.relativeTo(root)
                }
            }
        }

        private fun validateDocs(root: File, failures: MutableList<String>) {
            requiredAuthoredDocs.forEach { authoredDocPath ->
                val file = File(root, authoredDocPath)
                if (!isSafeFile(root, file, failures)) {
                    return@forEach
                }
                val text = file.readText()
                requiredDocHeadings.forEach { heading ->
                    if (!text.contains(heading)) {
                        failures += "doc missing $heading: $authoredDocPath"
                    }
                }
            }
        }

        private fun validateAgents(root: File, failures: MutableList<String>) {
            val dir = File(root, ".claude/agents")
            val files = safeFiles(root, dir, failures)
                .filter { candidateFile ->
                    candidateFile.parentFile == dir && candidateFile.extension == "md"
                }
            if (files.isEmpty()) {
                failures += ".claude/agents must contain at least one .md agent"
            }
            files.forEach { file ->
                val text = file.readText()
                if (!text.startsWith("---")) {
                    failures += "agent missing frontmatter: ${file.relativeTo(root)}"
                }
                if (!Regex("""(?m)^name:\s*[-a-z0-9]+\s*$""").containsMatchIn(text)) {
                    failures += "agent missing name: ${file.relativeTo(root)}"
                }
                if (!Regex("""(?m)^description:\s*.+$""").containsMatchIn(text)) {
                    failures += "agent missing description: ${file.relativeTo(root)}"
                }
            }
        }

        private fun validateSkills(root: File, failures: MutableList<String>) {
            val dir = File(root, ".claude/skills")
            val files = safeFiles(root, dir, failures).filter { candidateFile ->
                candidateFile.name == "SKILL.md"
            }
            if (files.isEmpty()) {
                failures += ".claude/skills must contain at least one SKILL.md"
            }
            files.forEach { file ->
                val text = file.readText()
                if (!text.startsWith("---")) {
                    failures += "skill missing frontmatter: ${file.relativeTo(root)}"
                }
                if (!Regex("""(?m)^description:\s*.+$""").containsMatchIn(text)) {
                    failures += "skill missing description: ${file.relativeTo(root)}"
                }
            }
        }

        private fun validateActiveAssets(root: File, failures: MutableList<String>) {
            val excluded = File(root, ".claude/harness/templates")
            val bases = listOf(
                "AGENTS.md",
                "CLAUDE.md",
                "ARCHITECTURE.md",
                "docs",
                ".claude/agents",
                ".claude/skills",
                ".claude/harness",
                ".github",
            ).map { templateName ->
                File(root, templateName)
            }
            bases.flatMap { candidateBase ->
                safeFileOrWalk(root, candidateBase, failures)
            }.forEach { file ->
                if (
                    file.startsWith(excluded) ||
                        file.extension !in setOf("md", "txt", "json", "yml", "yaml")
                ) {
                    return@forEach
                }
                val text = file.readText()
                leakPatterns.forEach { (pattern, label) ->
                    if (pattern.containsMatchIn(text)) {
                        failures += "$label in active asset: ${file.relativeTo(root)}"
                    }
                }
            }
        }

        private fun validateHook(root: File, failures: MutableList<String>) {
            val hook = File(root, ".claude/harness/git-hooks/pre-commit")
            if (isSafeFile(root, hook, failures)) {
                val hookText = hook.readText()
                if (hookText.lineSequence().firstOrNull() != "#!/usr/bin/env sh") {
                    failures += "pre-commit hook must use #!/usr/bin/env sh"
                }
                if (!hook.canExecute()) {
                    failures += "pre-commit hook must be executable: ${hook.relativeTo(root)}"
                }
                if (hookText.contains("packaged placeholder is replaced during harness installation")) {
                    failures += "pre-commit hook must be installer-generated selected-mode content"
                }
            }
        }

        private fun validateEnvShebangs(root: File, failures: MutableList<String>) {
            listOf(File(root, ".claude/harness"), File(root, ".claude/skills")).forEach { base ->
                if (!isSafeDirectory(root, base, failures)) {
                    return@forEach
                }
                safeFiles(root, base, failures).filter { candidateFile ->
                    candidateFile.canExecute()
                }.forEach { file ->
                    val line = file.readLines().firstOrNull() ?: ""
                    if (line.startsWith("#!") && !line.startsWith("#!/usr/bin/env ")) {
                        failures +=
                            "executable script should use /usr/bin/env shebang: " +
                                file.relativeTo(root)
                    }
                }
            }
        }

        private fun safeFiles(root: File, base: File, failures: MutableList<String>): List<File> {
            if (!base.exists()) {
                return emptyList()
            }
            if (Files.isSymbolicLink(base.toPath())) {
                failures += "symlink scan root is not allowed: ${base.relativeTo(root)}"
                return emptyList()
            }
            if (base.isFile) {
                return listOf(base)
            }
            val output = mutableListOf<File>()
            base.listFiles().orEmpty().forEach { child ->
                if (Files.isSymbolicLink(child.toPath())) {
                    failures += "symlink scan entry is not allowed: ${child.relativeTo(root)}"
                } else if (child.isDirectory) {
                    output += safeFiles(root, child, failures)
                } else if (child.isFile) {
                    output += child
                }
            }
            return output
        }

        private fun safeFileOrWalk(root: File, base: File, failures: MutableList<String>): List<File> {
            if (
                Files.isSymbolicLink(base.toPath()) &&
                    allowedRootContractTarget(root, base) == null
            ) {
                failures += "symlink path is not allowed: ${base.relativeTo(root)}"
                return emptyList()
            }
            if (isSafeFile(root, base, failures)) {
                return listOf(base)
            }
            return safeFiles(root, base, failures)
        }

        private fun allowedRootContractTarget(root: File, file: File): File? {
            if (
                file.parentFile != root ||
                    file.name !in setOf("AGENTS.md", "CLAUDE.md")
            ) {
                return null
            }
            val expected = if (file.name == "AGENTS.md") {
                "CLAUDE.md"
            } else {
                "AGENTS.md"
            }
            val targetName = try {
                Files.readSymbolicLink(file.toPath()).toString()
            } catch (_: Exception) {
                return null
            }
            if (targetName != expected) {
                return null
            }
            val target = File(root, expected)
            return target.takeIf { candidateFile ->
                !Files.isSymbolicLink(candidateFile.toPath()) &&
                    candidateFile.isFile
            }
        }

        private fun isSafeFile(root: File, file: File, failures: MutableList<String>): Boolean {
            if (Files.isSymbolicLink(file.toPath())) {
                if (allowedRootContractTarget(root, file) != null) {
                    return true
                }
                failures += "symlink file is not allowed: ${file.relativeTo(root)}"
                return false
            }
            return file.isFile
        }

        private fun isSafeDirectory(root: File, file: File, failures: MutableList<String>): Boolean {
            if (Files.isSymbolicLink(file.toPath())) {
                failures += "symlink directory is not allowed: ${file.relativeTo(root)}"
                return false
            }
            return file.isDirectory
        }

        private companion object {
            private val requiredFiles = listOf(
                "AGENTS.md",
                "ARCHITECTURE.md",
                "CLAUDE.md",
                "docs/design-docs/index.md",
                "docs/design-docs/core-beliefs.md",
                "docs/exec-plans/tech-debt-tracker.md",
                "docs/product-specs/index.md",
                "docs/DESIGN.md",
                "docs/FRONTEND.md",
                "docs/PLANS.md",
                "docs/PRODUCT_SENSE.md",
                "docs/QUALITY_SCORE.md",
                "docs/RELIABILITY.md",
                "docs/SECURITY.md",
                ".claude/harness/git-hooks/pre-commit",
            )
            private val requiredDirectories = listOf(
                "docs",
                "docs/design-docs",
                "docs/exec-plans",
                "docs/exec-plans/active",
                "docs/exec-plans/completed",
                "docs/generated",
                "docs/product-specs",
                "docs/references",
                ".claude/agents",
                ".claude/skills",
                ".claude/harness/templates",
            )
            private val emptyDirectoryKeepFiles = listOf(
                "docs/exec-plans/active/.gitkeep",
                "docs/exec-plans/completed/.gitkeep",
                "docs/generated/.gitkeep",
            )
            private val optionalSeedFiles = listOf(
                "docs/product-specs/new-user-onboarding.md",
                "docs/references/design-system-reference-llms.txt",
                "docs/references/nixpacks-llms.txt",
                "docs/references/uv-llms.txt",
            )
            private val templateGroups = listOf(
                "agent",
                "skill",
                "workflow",
                "ci",
                "docs",
            )
            private val requiredDocHeadings = listOf(
                "## Purpose",
                "## When To Update",
                "## Required Evidence",
                "## Validation Link",
            )
            private val requiredAuthoredDocs = requiredFiles
                .filter { requiredFile ->
                    requiredFile.startsWith("docs/") && requiredFile.endsWith(".md")
                }
            private val leakPatterns = listOf(
                Regex("""\{\{""") to "unresolved template token",
                Regex("""(?m)^name:\s*example-""") to "example frontmatter name",
                Regex("Describe ") to "scaffold prompt text",
                Regex("""\bTODO\b|\bTBD\b""") to "TODO/TBD placeholder",
                Regex("replace-with-stack-specific") to "stack placeholder",
            )
        }
    }
}
