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
            val harnessValidate =
                project.tasks.register("harnessValidate", HarnessValidationTask::class.java) { task ->
                    task.group = "verification"
                    task.description = "Validate Claude repository harness assets."
                }
            project.pluginManager.apply("base")
            project.tasks.named("check") { task ->
                task.dependsOn(harnessValidate)
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
            val manifestLoad = loadManifest(root)
            val manifest = manifestLoad.manifest
            val findings: List<Finding> =
                buildList {
                    addAll(manifestLoad.findings)
                    if (manifest == null) {
                        return@buildList
                    }
                    addAll(validateStructure(root, manifest))
                    addAll(validateDocs(root, manifest))
                    addAll(validateContentChecks(root, manifest))
                    addAll(validateAgents(root))
                    addAll(validateSkills(root))
                    addAll(validateActiveAssets(root, manifest))
                    addAll(validateHooks(root, manifest))
                    addAll(validateEnvShebangs(root, manifest))
                    addAll(validateCompletedPlans(root, manifest))
                }.distinct()
            val sortedFindings = findings.sortedWith(compareBy({ it.severity.ordinal }, { findings.indexOf(it) }))
            val errors = sortedFindings.filter { it.severity == Severity.ERROR }
            sortedFindings.forEach { finding ->
                System.err.println("[${finding.severity}] ${finding.message}")
            }
            when {
                errors.isNotEmpty() -> throw GradleException("Harness validation failed")
                else -> logger.lifecycle("Harness validation passed")
            }
        }

        private fun loadManifest(
            root: File,
        ): ManifestLoad {
            val manifestFile = File(root, "docs/harness/manifest.json")
            val failuresList: List<Finding> =
                when {
                    Files.isSymbolicLink(manifestFile.toPath()) ->
                        listOf(Finding(Severity.ERROR, "symlinkSafety", "symlink file is not allowed: docs/harness/manifest.json"))
                    else -> emptyList()
                }
            if (failuresList.isNotEmpty()) {
                return ManifestLoad(null, failuresList)
            }
            val manifest = read(root, "docs/harness/manifest.json")
            return when {
                manifest.isBlank() ->
                    ManifestLoad(null, listOf(Finding(Severity.ERROR, "requiredFiles", "missing file: docs/harness/manifest.json")))
                else -> ManifestLoad(manifest, emptyList())
            }
        }

        private fun parseSeverity(manifest: String, category: String): Severity {
            val severityStr = """"$category"\s*:\s*\{[^}]*?"severity"\s*:\s*"([^"\\]+)"""".toRegex(RegexOption.DOT_MATCHES_ALL)
                .find(manifest)
                ?.groupValues
                ?.get(1)
                .orEmpty()
            return try {
                Severity.valueOf(severityStr)
            } catch (_: Exception) {
                Severity.ERROR
            }
        }

        private fun read(
            root: File,
            path: String,
        ): String {
            val file = File(root, path)
            val target = allowedRootContractTarget(root, file)
            if (Files.isSymbolicLink(file.toPath()) && target == null) {
                return ""
            }
            return (target ?: file)
                .takeIf { candidateFile ->
                    candidateFile.isFile
                }?.readText() ?: ""
        }

        private fun extractObjectBody(manifest: String, key: String): String? =
            """"$key"\s*:\s*\{((?:[^{}]|\{[^{}]*\})*)\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
                .find(manifest)
                ?.groupValues
                ?.get(1)

        private fun parseStringArray(
            manifest: String,
            key: String,
        ): List<String> {
            val body = extractObjectBody(manifest, key)
            if (body == null) {
                return """"$key"\s*:\s*\[(.*?)]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    .find(manifest)
                    ?.groupValues
                    ?.get(1)
                    ?.let { manifestListBody ->
                        """"([^"\\]+)"""".toRegex()
                            .findAll(manifestListBody)
                            .map { it.groupValues[1] }
                            .toList()
                    }
                    .orEmpty()
            }
            return """"items"\s*:\s*\[(.*?)]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                .find(body)
                ?.groupValues
                ?.get(1)
                ?.let { itemsBody ->
                    """"([^"\\]+)"""".toRegex()
                        .findAll(itemsBody)
                        .map { it.groupValues[1] }
                        .toList()
                }
                .orEmpty()
        }

        private fun parseString(
            manifest: String,
            key: String,
        ): String {
            val body = extractObjectBody(manifest, key)
            if (body == null) {
                return """"$key"\s*:\s*"([^"\\]*)"""".toRegex()
                    .find(manifest)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
            }
            return """"value"\s*:\s*"([^"\\]*)"""".toRegex()
                .find(body)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        }

        private fun parseContentChecks(
            manifest: String,
        ): List<ContentCheck> =
            buildList {
                extractObjectBody(manifest, "requiredContentChecks")?.let { checksBody ->
                    """"items"\s*:\s*\[(.*?)]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                        .find(checksBody)
                        ?.groupValues
                        ?.get(1)
                        ?.let { itemsBody ->
                            """\{[^{}]*?"files"\s*:\s*\[(.*?)]\s*,\s*"containsAll"\s*:\s*\[(.*?)]\s*,\s*"failureMessage"\s*:\s*"([^"\\]*)"""".toRegex(RegexOption.DOT_MATCHES_ALL)
                                .findAll(itemsBody).forEach { match ->
                                    val files = """"([^"\\]+)"""".toRegex()
                                        .findAll(match.groupValues[1])
                                        .map { it.groupValues[1] }
                                        .toList()
                                    val containsAll = """"([^"\\]+)"""".toRegex()
                                        .findAll(match.groupValues[2])
                                        .map { it.groupValues[1] }
                                        .toList()
                                    add(ContentCheck(files, containsAll, match.groupValues[3]))
                                }
                        }
                }
            }

        private fun parseLeakPatterns(
            manifest: String,
        ): List<Pair<Regex, String>> =
            buildList {
                extractObjectBody(manifest, "leakPatterns")?.let { patternsBody ->
                    """"items"\s*:\s*\[(.*?)]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                        .find(patternsBody)
                        ?.groupValues
                        ?.get(1)
                        ?.let { itemsBody ->
                            """\{\s*"pattern"\s*:\s*"([^"\\]*)"\s*,\s*"label"\s*:\s*"([^"\\]*)"""".toRegex(RegexOption.DOT_MATCHES_ALL)
                                .findAll(itemsBody).forEach { match ->
                                    try {
                                        add(match.groupValues[1].toRegex() to match.groupValues[2])
                                    } catch (_: Exception) {
                                    }
                                }
                        }
                }
            }

        private fun parseHookStages(
            manifest: String,
        ): Pair<String, String> {
            extractObjectBody(manifest, "hookStages")?.let { stagesBody ->
                """"gradle"\s*:\s*\{(.*?)}""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    .find(stagesBody)
                    ?.groupValues
                    ?.get(1)
                    ?.let { gradleBody ->
                        val preCommit = """"preCommit"\s*:\s*"([^"\\]+)"""".toRegex()
                            .find(gradleBody)
                            ?.groupValues
                            ?.get(1)
                            .orEmpty()
                        val prePush = """"prePush"\s*:\s*"([^"\\]+)"""".toRegex()
                            .find(gradleBody)
                            ?.groupValues
                            ?.get(1)
                            .orEmpty()
                        return preCommit to prePush
                    }
            }
            return "" to ""
        }

        private fun validateStructure(
            root: File,
            manifest: String,
        ): List<Finding> =
            buildList {
                val requiredFiles = parseStringArray(manifest, "requiredFiles")
                val requiredDirectories = parseStringArray(manifest, "requiredDirectories")
                val emptyDirectoryKeepFiles = parseStringArray(manifest, "emptyDirectoryKeepFiles")
                val templateGroups = parseStringArray(manifest, "templateGroups")
                requiredFiles.forEach { requiredFile ->
                    val check = isSafeFile(root, File(root, requiredFile))
                    addAll(check.warnings)
                    if (!check.ok) {
                        add(Finding(Severity.ERROR, "requiredFiles", "missing file: $requiredFile"))
                    }
                }
                requiredDirectories.forEach { requiredDirectory ->
                    val check = isSafeDirectory(root, File(root, requiredDirectory))
                    addAll(check.warnings)
                    if (!check.ok) {
                        add(Finding(Severity.ERROR, "requiredDirectories", "missing directory: $requiredDirectory"))
                    }
                }
                emptyDirectoryKeepFiles.forEach { keepFilePath ->
                    val keepFile = File(root, keepFilePath)
                    val directory = keepFile.parentFile
                    if (directory == null) {
                        return@forEach
                    }
                    val dirCheck = isSafeDirectory(root, directory)
                    addAll(dirCheck.warnings)
                    if (!dirCheck.ok) {
                        return@forEach
                    }
                    val realFiles =
                        directory
                            .listFiles()
                            ?.filter { candidateFile ->
                                candidateFile.name != ".gitkeep"
                            }.orEmpty()
                    if (realFiles.isEmpty()) {
                        val fileCheck = isSafeFile(root, keepFile)
                        addAll(fileCheck.warnings)
                        if (!fileCheck.ok) {
                            add(
                                Finding(
                                    Severity.ERROR,
                                    "emptyDirectoryKeepFiles",
                                    "empty directory must keep placeholder or real files: ${directory.relativeTo(root)}"
                                )
                            )
                        }
                    }
                }
                templateGroups.forEach { templateGroup ->
                    val check = isSafeDirectory(root, File(root, "docs/harness/templates/$templateGroup"))
                    addAll(check.warnings)
                    if (!check.ok) {
                        add(Finding(Severity.ERROR, "templateGroups", "missing template group: docs/harness/templates/$templateGroup"))
                    }
                }
            }

        private fun validateDocs(
            root: File,
            manifest: String,
        ): List<Finding> =
            buildList {
                val requiredFiles = parseStringArray(manifest, "requiredFiles")
                val requiredDocHeadings = parseStringArray(manifest, "requiredDocHeadings")
                val requiredAuthoredDocs =
                    requiredFiles
                        .filter { requiredFile ->
                            requiredFile.startsWith("docs/") && requiredFile.endsWith(".md")
                        }
                requiredAuthoredDocs.forEach { authoredDocPath ->
                    val file = File(root, authoredDocPath)
                    val check = isSafeFile(root, file)
                    addAll(check.warnings)
                    if (!check.ok) {
                        return@forEach
                    }
                    val text = file.readText()
                    requiredDocHeadings.forEach { heading ->
                        if (!text.contains(heading)) {
                            add(Finding(Severity.ERROR, "requiredDocHeadings", "doc missing $heading: $authoredDocPath"))
                        }
                    }
                }
            }

        private fun validateContentChecks(
            root: File,
            manifest: String,
        ): List<Finding> =
            buildList {
                val checks = parseContentChecks(manifest)
                checks.forEach { check ->
                    val texts =
                        check.files.map { filePath ->
                            read(root, filePath)
                        }
                    val combined = texts.joinToString("\n")
                    val allPresent = check.containsAll.all { substring ->
                        combined.contains(substring)
                    }
                    if (!allPresent) {
                        add(Finding(Severity.ERROR, "requiredContentChecks", check.failureMessage))
                    }
                }
            }

        private fun validateAgents(
            root: File,
        ): List<Finding> =
            buildList {
                val dir = File(root, ".claude/agents")
                val scan = safeFiles(root, dir)
                addAll(scan.warnings)
                val files = scan.files.filter { it.parentFile == dir && it.extension == "md" }
                if (files.isEmpty()) {
                    add(Finding(Severity.ERROR, "agentFrontmatter", ".claude/agents must contain at least one .md agent"))
                }
                files.forEach { file ->
                    val text = file.readText()
                    if (!text.startsWith("---")) {
                        add(Finding(Severity.ERROR, "agentFrontmatter", "agent missing frontmatter: ${file.relativeTo(root)}"))
                    }
                    if (!"""(?m)^name:\s*[-a-z0-9]+\s*$""".toRegex().containsMatchIn(text)) {
                        add(Finding(Severity.ERROR, "agentFrontmatter", "agent missing name: ${file.relativeTo(root)}"))
                    }
                    if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
                        add(Finding(Severity.ERROR, "agentFrontmatter", "agent missing description: ${file.relativeTo(root)}"))
                    }
                }
            }

        private fun validateSkills(
            root: File,
        ): List<Finding> =
            buildList {
                val dir = File(root, ".claude/skills")
                val scan = safeFiles(root, dir)
                addAll(scan.warnings)
                val files = scan.files.filter { it.name == "SKILL.md" }
                if (files.isEmpty()) {
                    add(Finding(Severity.ERROR, "skillFrontmatter", ".claude/skills must contain at least one SKILL.md"))
                }
                files.forEach { file ->
                    val text = file.readText()
                    if (!text.startsWith("---")) {
                        add(Finding(Severity.ERROR, "skillFrontmatter", "skill missing frontmatter: ${file.relativeTo(root)}"))
                    }
                    if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
                        add(Finding(Severity.ERROR, "skillFrontmatter", "skill missing description: ${file.relativeTo(root)}"))
                    }
                }
            }

        private fun validateActiveAssets(
            root: File,
            manifest: String,
        ): List<Finding> =
            buildList {
                val bases = parseStringArray(manifest, "activeAssetBases")
                val excludedSubtrees = parseStringArray(manifest, "excludedActiveAssetSubtrees")
                val extensions = parseStringArray(manifest, "activeAssetExtensions")
                val leakPatterns = parseLeakPatterns(manifest)
                val excludedDirs = excludedSubtrees.map { File(root, it) }
                bases
                    .map { templateName ->
                        File(root, templateName)
                    }
                    .forEach { candidateBase ->
                        val scan = safeFileOrWalk(root, candidateBase)
                        addAll(scan.warnings)
                        scan.files.forEach { file ->
                            if (
                                excludedDirs.any { file.startsWith(it) } ||
                                file.extension !in extensions
                            ) {
                                return@forEach
                            }
                            val text = file.readText()
                            leakPatterns.forEach { (pattern, label) ->
                                if (pattern.containsMatchIn(text)) {
                                    add(Finding(Severity.ERROR, "leakPatterns", "$label in active asset: ${file.relativeTo(root)}"))
                                }
                            }
                        }
                    }
            }

        private fun hookCommand(prePushText: String): String =
            prePushText
                .lineSequence()
                .firstOrNull { line ->
                    line.startsWith("# Harness validation command: ")
                }?.removePrefix("# Harness validation command: ")
                ?.trim()
                .orEmpty()

        private fun validateOneHook(
            root: File,
            name: String,
            stage: String,
        ): HookCheck {
            val hook = File(root, "docs/harness/git-hooks/$name")
            val check = isSafeFile(root, hook)
            var hookText = ""
            val findings: List<Finding> =
                buildList {
                    addAll(check.warnings)
                    if (check.ok) {
                        hookText = hook.readText()
                        if (hookText.lineSequence().firstOrNull() != "#!/usr/bin/env sh") {
                            add(Finding(Severity.ERROR, "hookFirstLine", "$name hook must use #!/usr/bin/env sh"))
                        }
                        if (!hook.canExecute()) {
                            add(Finding(Severity.ERROR, "hookExecutable", "$name hook must be executable: ${hook.relativeTo(root)}"))
                        }
                        if (!hookText.contains("Harness generated hook: $name")) {
                            add(Finding(Severity.ERROR, "hookGeneratedMarker", "$name hook must contain generated marker"))
                        }
                        if (!hookText.contains("Harness stage: $stage")) {
                            add(Finding(Severity.ERROR, "hookStage", "$name hook must contain $stage stage marker"))
                        }
                        if (hookText.contains("packaged placeholder is replaced during harness installation")) {
                            add(Finding(Severity.ERROR, "hookValidationCommand", "$name hook must be installer-generated selected-mode content"))
                        }
                    }
                }
            return HookCheck(hookText, findings)
        }

        private fun validateHooks(
            root: File,
            manifest: String,
        ): List<Finding> =
            buildList {
                val (preCommitStage, prePushStage) = parseHookStages(manifest)
                val allCommands = parseStringArray(manifest, "expectedValidationCommands")
                val allowedPreCommitCommands = allCommands.filter { it.contains("harnessValidate") }
                val allowedValidationCommands = allCommands.filter { it.contains("check") }
                val preCommitCheck = validateOneHook(root, "pre-commit", preCommitStage)
                addAll(preCommitCheck.findings)
                val prePushCheck = validateOneHook(root, "pre-push", prePushStage)
                addAll(prePushCheck.findings)
                val preCommitCommand = hookCommand(preCommitCheck.text)
                if (preCommitCommand.isNotEmpty() && preCommitCommand !in allowedPreCommitCommands) {
                    add(Finding(Severity.ERROR, "hookValidationCommand", "pre-commit hook must declare Gradle harness validation command"))
                } else if (preCommitCommand.isNotEmpty() && preCommitCommand !in preCommitCheck.text.lineSequence().toSet()) {
                    add(Finding(Severity.ERROR, "hookValidationCommand", "pre-commit hook must run the declared validation command"))
                }
                val command = hookCommand(prePushCheck.text)
                if (command.isBlank()) {
                    add(Finding(Severity.ERROR, "hookValidationCommand", "pre-push hook must declare Harness validation command"))
                    return@buildList
                }
                if (command !in allowedValidationCommands) {
                    add(Finding(Severity.ERROR, "hookValidationCommand", "pre-push hook declares unsupported validation command: $command"))
                    return@buildList
                }
                if (command !in prePushCheck.text.lineSequence().toSet()) {
                    add(Finding(Severity.ERROR, "hookValidationCommand", "pre-push hook must run the declared validation command"))
                }
                listOf(".github/workflows/harness.yml", ".gitlab-ci.yml").forEach { ciFile ->
                    val path = File(root, ciFile)
                    if (path.exists()) {
                        val check = isSafeFile(root, path)
                        addAll(check.warnings)
                        if (check.ok && !path.readText().contains(command)) {
                            add(Finding(Severity.ERROR, "ciCommandMatch", "$ciFile: CI command mismatch - expected $command"))
                        }
                    }
                }
            }

        private fun validateEnvShebangs(
            root: File,
            manifest: String,
        ): List<Finding> =
            buildList {
                val envShebangBases = parseStringArray(manifest, "envShebangBases")
                envShebangBases.forEach { basePath ->
                    val base = File(root, basePath)
                    val dirCheck = isSafeDirectory(root, base)
                    addAll(dirCheck.warnings)
                    if (!dirCheck.ok) {
                        return@forEach
                    }
                    val scan = safeFiles(root, base)
                    addAll(scan.warnings)
                    scan.files
                        .filter { candidateFile ->
                            candidateFile.canExecute()
                        }.forEach { file ->
                            val line = file.readLines().firstOrNull() ?: ""
                            if (line.startsWith("#!") && !line.startsWith("#!/usr/bin/env ")) {
                                add(
                                    Finding(
                                        Severity.ERROR,
                                        "envShebang",
                                        "executable script should use /usr/bin/env shebang: ${file.relativeTo(root)}"
                                    )
                                )
                            }
                        }
                }
            }

        private fun validateCompletedPlans(
            root: File,
            manifest: String,
        ): List<Finding> =
            buildList {
                val completedDir = parseString(manifest, "completedPlanDirectory")
                val unfinishedPattern = parseString(manifest, "unfinishedTaskPattern")
                if (completedDir.isEmpty() || unfinishedPattern.isEmpty()) {
                    return@buildList
                }
                val dir = File(root, completedDir)
                val dirCheck = isSafeDirectory(root, dir)
                addAll(dirCheck.warnings)
                if (!dirCheck.ok) {
                    return@buildList
                }
                val scan = safeFiles(root, dir)
                addAll(scan.warnings)
                val regex = try {
                    unfinishedPattern.toRegex()
                } catch (_: Exception) {
                    return@buildList
                }
                scan.files.filter { it.extension == "md" }.forEach { file ->
                    if (regex.containsMatchIn(file.readText())) {
                        add(Finding(Severity.ERROR, "completedPlanUnfinishedTask", "completed plan has unchecked tasks: ${file.relativeTo(root)}"))
                    }
                }
            }

        private fun safeFiles(
            root: File,
            base: File,
        ): ScanResult {
            if (!base.exists()) {
                return ScanResult(emptyList(), emptyList())
            }
            if (Files.isSymbolicLink(base.toPath())) {
                return ScanResult(
                    emptyList(),
                    listOf(Finding(Severity.WARN, "symlinkSafety", "symlink scan root is not allowed: ${base.relativeTo(root)}"))
                )
            }
            if (base.isFile) {
                return ScanResult(listOf(base), emptyList())
            }
            val children = base.listFiles().orEmpty()
            val warnings: List<Finding> =
                buildList {
                    children.forEach { child ->
                        if (Files.isSymbolicLink(child.toPath())) {
                            add(Finding(Severity.WARN, "symlinkSafety", "symlink scan entry is not allowed: ${child.relativeTo(root)}"))
                        }
                    }
                }
            val files: List<File> =
                children
                    .filter { child ->
                        !Files.isSymbolicLink(child.toPath())
                    }
                    .flatMap { child ->
                        when {
                            child.isDirectory -> safeFiles(root, child).files
                            child.isFile -> listOf(child)
                            else -> emptyList()
                        }
                    }
            val allWarnings: List<Finding> =
                buildList {
                    addAll(warnings)
                    children
                        .filter { child ->
                            !Files.isSymbolicLink(child.toPath()) && child.isDirectory
                        }
                        .forEach { child ->
                            addAll(safeFiles(root, child).warnings)
                        }
                }
            return ScanResult(files, allWarnings)
        }

        private fun safeFileOrWalk(
            root: File,
            base: File,
        ): ScanResult {
            if (
                Files.isSymbolicLink(base.toPath()) &&
                allowedRootContractTarget(root, base) == null
            ) {
                return ScanResult(
                    emptyList(),
                    listOf(Finding(Severity.WARN, "symlinkSafety", "symlink path is not allowed: ${base.relativeTo(root)}"))
                )
            }
            val fileCheck = isSafeFile(root, base)
            if (fileCheck.ok) {
                return ScanResult(listOf(base), fileCheck.warnings)
            }
            val scan = safeFiles(root, base)
            return ScanResult(scan.files, fileCheck.warnings + scan.warnings)
        }

        private fun allowedRootContractTarget(
            root: File,
            file: File,
        ): File? {
            if (
                file.parentFile != root ||
                file.name !in setOf("AGENTS.md", "CLAUDE.md")
            ) {
                return null
            }
            val expected =
                when (file.name) {
                    "AGENTS.md" -> "CLAUDE.md"
                    else -> "AGENTS.md"
                }
            val targetName =
                try {
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

        private fun isSafeFile(
            root: File,
            file: File,
        ): SafetyCheck {
            if (Files.isSymbolicLink(file.toPath())) {
                if (allowedRootContractTarget(root, file) != null) {
                    return SafetyCheck(true, emptyList())
                }
                return SafetyCheck(
                    false,
                    listOf(Finding(Severity.ERROR, "symlinkSafety", "symlink file is not allowed: ${file.relativeTo(root)}"))
                )
            }
            return SafetyCheck(file.isFile, emptyList())
        }

        private fun isSafeDirectory(
            root: File,
            file: File,
        ): SafetyCheck {
            if (Files.isSymbolicLink(file.toPath())) {
                return SafetyCheck(
                    false,
                    listOf(Finding(Severity.ERROR, "symlinkSafety", "symlink directory is not allowed: ${file.relativeTo(root)}"))
                )
            }
            return SafetyCheck(file.isDirectory, emptyList())
        }

        private enum class Severity {
            ERROR,
            WARN,
            INFO,
        }

        private data class Finding(
            val severity: Severity,
            val category: String,
            val message: String,
        )

        private data class ContentCheck(
            val files: List<String>,
            val containsAll: List<String>,
            val failureMessage: String,
        )

        private data class SafetyCheck(
            val ok: Boolean,
            val warnings: List<Finding>,
        )

        private data class ScanResult(
            val files: List<File>,
            val warnings: List<Finding>,
        )

        private data class HookCheck(
            val text: String,
            val findings: List<Finding>,
        )

        private data class ManifestLoad(
            val manifest: String?,
            val findings: List<Finding>,
        )
    }
}
