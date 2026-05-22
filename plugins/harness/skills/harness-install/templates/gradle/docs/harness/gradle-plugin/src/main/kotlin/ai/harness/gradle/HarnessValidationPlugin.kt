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
            val failures: List<String> =
                buildList {
                    val manifestLoad = loadManifest(root)
                    addAll(manifestLoad.failures)
                    val manifest = manifestLoad.manifest
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
                }
            if (failures.isNotEmpty()) {
                throw GradleException(
                    "Harness validation failed:" +
                        System.lineSeparator() +
                        failures.distinct().joinToString(System.lineSeparator()),
                )
            }
            logger.lifecycle("Harness validation passed")
        }

        private fun loadManifest(
            root: File,
        ): ManifestLoad {
            val manifestFile = File(root, "docs/harness/manifest.json")
            val failuresList: List<String> =
                if (Files.isSymbolicLink(manifestFile.toPath())) {
                    listOf("symlink file is not allowed: docs/harness/manifest.json")
                } else {
                    emptyList()
                }
            if (failuresList.isNotEmpty()) {
                return ManifestLoad(null, failuresList)
            }
            val manifest = read(root, "docs/harness/manifest.json")
            return if (manifest.isBlank()) {
                ManifestLoad(null, listOf("missing file: docs/harness/manifest.json"))
            } else {
                ManifestLoad(manifest, emptyList())
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

        private fun parseStringArray(
            manifest: String,
            key: String,
        ): List<String> =
            Regex(
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
                        }.toList()
                }.orEmpty()

        private fun parseString(
            manifest: String,
            key: String,
        ): String =
            Regex(
                """"$key"\s*:\s*"([^"\\]*)"""",
            ).find(manifest)
                ?.groupValues
                ?.get(1)
                .orEmpty()

        private fun parseContentChecks(
            manifest: String,
        ): List<ContentCheck> =
            buildList {
                val checksBody =
                    Regex(
                        """"requiredContentChecks"\s*:\s*\[(.*?)]""",
                        RegexOption.DOT_MATCHES_ALL,
                    ).find(manifest)
                        ?.groupValues
                        ?.get(1)
                        .orEmpty()
                Regex(
                    """\{[^{}]*?"files"\s*:\s*\[(.*?)]\s*,\s*"containsAll"\s*:\s*\[(.*?)]\s*,\s*"failureMessage"\s*:\s*"([^"\\]*)"""",
                    RegexOption.DOT_MATCHES_ALL,
                ).findAll(checksBody).forEach { match ->
                    val files =
                        Regex(""""([^"\\]+)"""")
                            .findAll(match.groupValues[1])
                            .map { it.groupValues[1] }
                            .toList()
                    val containsAll =
                        Regex(""""([^"\\]+)"""")
                            .findAll(match.groupValues[2])
                            .map { it.groupValues[1] }
                            .toList()
                    val message = match.groupValues[3]
                    add(ContentCheck(files, containsAll, message))
                }
            }

        private fun parseLeakPatterns(
            manifest: String,
        ): List<Pair<Regex, String>> =
            buildList {
                val patternsBody =
                    Regex(
                        """"leakPatterns"\s*:\s*\[(.*?)]""",
                        RegexOption.DOT_MATCHES_ALL,
                    ).find(manifest)
                        ?.groupValues
                        ?.get(1)
                        .orEmpty()
                Regex(
                    """\{\s*"pattern"\s*:\s*"([^"\\]*)"\s*,\s*"label"\s*:\s*"([^"\\]*)"""",
                    RegexOption.DOT_MATCHES_ALL,
                ).findAll(patternsBody).forEach { match ->
                    val patternStr = match.groupValues[1]
                    val label = match.groupValues[2]
                    try {
                        add(Regex(patternStr) to label)
                    } catch (_: Exception) {
                    }
                }
            }

        private fun parseHookStages(
            manifest: String,
        ): Pair<String, String> {
            val stagesBody =
                Regex(
                    """"hookStages"\s*:\s*\{(.*?)}\s*,""",
                    RegexOption.DOT_MATCHES_ALL,
                ).find(manifest)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
            val gradleBody =
                Regex(
                    """"gradle"\s*:\s*\{(.*?)}""",
                    RegexOption.DOT_MATCHES_ALL,
                ).find(stagesBody)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
            val preCommit =
                Regex(""""preCommit"\s*:\s*"([^"\\]+)"""")
                    .find(gradleBody)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
            val prePush =
                Regex(""""prePush"\s*:\s*"([^"\\]+)"""")
                    .find(gradleBody)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
            return preCommit to prePush
        }

        private fun validateStructure(
            root: File,
            manifest: String,
        ): List<String> =
            buildList {
                val requiredFiles = parseStringArray(manifest, "requiredFiles")
                val requiredDirectories = parseStringArray(manifest, "requiredDirectories")
                val emptyDirectoryKeepFiles = parseStringArray(manifest, "emptyDirectoryKeepFiles")
                val templateGroups = parseStringArray(manifest, "templateGroups")
                requiredFiles.forEach { requiredFile ->
                    val check = isSafeFile(root, File(root, requiredFile))
                    addAll(check.warnings)
                    if (!check.ok) {
                        add("missing file: $requiredFile")
                    }
                }
                requiredDirectories.forEach { requiredDirectory ->
                    val check = isSafeDirectory(root, File(root, requiredDirectory))
                    addAll(check.warnings)
                    if (!check.ok) {
                        add("missing directory: $requiredDirectory")
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
                                "empty directory must keep placeholder or real files: " +
                                    directory.relativeTo(root)
                            )
                        }
                    }
                }
                templateGroups.forEach { templateGroup ->
                    val check = isSafeDirectory(root, File(root, "docs/harness/templates/$templateGroup"))
                    addAll(check.warnings)
                    if (!check.ok) {
                        add("missing template group: docs/harness/templates/$templateGroup")
                    }
                }
            }

        private fun validateDocs(
            root: File,
            manifest: String,
        ): List<String> =
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
                            add("doc missing $heading: $authoredDocPath")
                        }
                    }
                }
            }

        private fun validateContentChecks(
            root: File,
            manifest: String,
        ): List<String> =
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
                        add(check.failureMessage)
                    }
                }
            }

        private fun validateAgents(
            root: File,
        ): List<String> =
            buildList {
                val dir = File(root, ".claude/agents")
                val scan = safeFiles(root, dir)
                addAll(scan.warnings)
                val files =
                    scan.files
                        .filter { candidateFile ->
                            candidateFile.parentFile == dir && candidateFile.extension == "md"
                        }
                if (files.isEmpty()) {
                    add(".claude/agents must contain at least one .md agent")
                }
                files.forEach { file ->
                    val text = file.readText()
                    if (!text.startsWith("---")) {
                        add("agent missing frontmatter: ${file.relativeTo(root)}")
                    }
                    if (!Regex("""(?m)^name:\s*[-a-z0-9]+\s*$""").containsMatchIn(text)) {
                        add("agent missing name: ${file.relativeTo(root)}")
                    }
                    if (!Regex("""(?m)^description:\s*.+$""").containsMatchIn(text)) {
                        add("agent missing description: ${file.relativeTo(root)}")
                    }
                }
            }

        private fun validateSkills(
            root: File,
        ): List<String> =
            buildList {
                val dir = File(root, ".claude/skills")
                val scan = safeFiles(root, dir)
                addAll(scan.warnings)
                val files = scan.files.filter { candidateFile ->
                    candidateFile.name == "SKILL.md"
                }
                if (files.isEmpty()) {
                    add(".claude/skills must contain at least one SKILL.md")
                }
                files.forEach { file ->
                    val text = file.readText()
                    if (!text.startsWith("---")) {
                        add("skill missing frontmatter: ${file.relativeTo(root)}")
                    }
                    if (!Regex("""(?m)^description:\s*.+$""").containsMatchIn(text)) {
                        add("skill missing description: ${file.relativeTo(root)}")
                    }
                }
            }

        private fun validateActiveAssets(
            root: File,
            manifest: String,
        ): List<String> =
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
                                    add("$label in active asset: ${file.relativeTo(root)}")
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
            val failures: List<String> =
                buildList {
                    addAll(check.warnings)
                    if (check.ok) {
                        hookText = hook.readText()
                        if (hookText.lineSequence().firstOrNull() != "#!/usr/bin/env sh") {
                            add("$name hook must use #!/usr/bin/env sh")
                        }
                        if (!hook.canExecute()) {
                            add("$name hook must be executable: ${hook.relativeTo(root)}")
                        }
                        if (!hookText.contains("Harness generated hook: $name")) {
                            add("$name hook must contain generated marker")
                        }
                        if (!hookText.contains("Harness stage: $stage")) {
                            add("$name hook must contain $stage stage marker")
                        }
                        if (hookText.contains("packaged placeholder is replaced during harness installation")) {
                            add("$name hook must be installer-generated selected-mode content")
                        }
                    }
                }
            return HookCheck(hookText, failures)
        }

        private fun validateHooks(
            root: File,
            manifest: String,
        ): List<String> =
            buildList {
                val (preCommitStage, prePushStage) = parseHookStages(manifest)
                val allowedPreCommitCommands = parseStringArray(manifest, "expectedValidationCommands")
                    .firstOrNull { it.contains("harnessValidate") }
                    ?.let { parseStringArray(manifest, "expectedValidationCommands") }
                    .orEmpty()
                    .filter { it.contains("harnessValidate") }
                val allowedValidationCommands = parseStringArray(manifest, "expectedValidationCommands")
                    .firstOrNull { it.contains("check") }
                    ?.let { parseStringArray(manifest, "expectedValidationCommands") }
                    .orEmpty()
                    .filter { it.contains("check") }
                val preCommitCheck = validateOneHook(root, "pre-commit", preCommitStage)
                addAll(preCommitCheck.failures)
                val preCommitText = preCommitCheck.text
                val prePushCheck = validateOneHook(root, "pre-push", prePushStage)
                addAll(prePushCheck.failures)
                val prePushText = prePushCheck.text
                val preCommitCommand = hookCommand(preCommitText)
                if (preCommitCommand.isNotEmpty() && preCommitCommand !in allowedPreCommitCommands) {
                    add("pre-commit hook must declare Gradle harness validation command")
                } else if (preCommitCommand.isNotEmpty() && preCommitCommand !in preCommitText.lineSequence().toSet()) {
                    add("pre-commit hook must run the declared validation command")
                }
                val command = hookCommand(prePushText)
                if (command.isBlank()) {
                    add("pre-push hook must declare Harness validation command")
                    return@buildList
                }
                if (command !in allowedValidationCommands) {
                    add("pre-push hook declares unsupported validation command: $command")
                    return@buildList
                }
                if (command !in prePushText.lineSequence().toSet()) {
                    add("pre-push hook must run the declared validation command")
                }
                listOf(".github/workflows/harness.yml", ".gitlab-ci.yml").forEach { ciFile ->
                    val path = File(root, ciFile)
                    if (path.exists()) {
                        val check = isSafeFile(root, path)
                        addAll(check.warnings)
                        if (check.ok && !path.readText().contains(command)) {
                            add("$ciFile: CI command mismatch - expected $command")
                        }
                    }
                }
            }

        private fun validateEnvShebangs(
            root: File,
            manifest: String,
        ): List<String> =
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
                                    "executable script should use /usr/bin/env shebang: " +
                                        file.relativeTo(root)
                                )
                            }
                        }
                }
            }

        private fun validateCompletedPlans(
            root: File,
            manifest: String,
        ): List<String> =
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
                val planFiles =
                    scan.files
                        .filter { candidateFile ->
                            candidateFile.extension == "md"
                        }
                val regex =
                    try {
                        Regex(unfinishedPattern)
                    } catch (_: Exception) {
                        return@buildList
                    }
                planFiles.forEach { file ->
                    val text = file.readText()
                    if (regex.containsMatchIn(text)) {
                        add("completed plan has unchecked tasks: ${file.relativeTo(root)}")
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
                    listOf("symlink scan root is not allowed: ${base.relativeTo(root)}")
                )
            }
            if (base.isFile) {
                return ScanResult(listOf(base), emptyList())
            }
            val children = base.listFiles().orEmpty()
            val warnings: List<String> =
                buildList {
                    children.forEach { child ->
                        if (Files.isSymbolicLink(child.toPath())) {
                            add("symlink scan entry is not allowed: ${child.relativeTo(root)}")
                        }
                    }
                }
            val files: List<File> =
                children
                    .filter { child ->
                        !Files.isSymbolicLink(child.toPath())
                    }
                    .flatMap { child ->
                        if (child.isDirectory) {
                            safeFiles(root, child).files
                        } else if (child.isFile) {
                            listOf(child)
                        } else {
                            emptyList()
                        }
                    }
            val allWarnings: List<String> =
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
                    listOf("symlink path is not allowed: ${base.relativeTo(root)}")
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
                if (file.name == "AGENTS.md") {
                    "CLAUDE.md"
                } else {
                    "AGENTS.md"
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
                    listOf("symlink file is not allowed: ${file.relativeTo(root)}")
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
                    listOf("symlink directory is not allowed: ${file.relativeTo(root)}")
                )
            }
            return SafetyCheck(file.isDirectory, emptyList())
        }

        private data class ContentCheck(
            val files: List<String>,
            val containsAll: List<String>,
            val failureMessage: String,
        )

        private data class SafetyCheck(
            val ok: Boolean,
            val warnings: List<String>,
        )

        private data class ScanResult(
            val files: List<File>,
            val warnings: List<String>,
        )

        private data class HookCheck(
            val text: String,
            val failures: List<String>,
        )

        private data class ManifestLoad(
            val manifest: String?,
            val failures: List<String>,
        )
    }
}
