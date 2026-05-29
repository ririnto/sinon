package com.ririnto.sinon.harness.plugin

import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.DefaultManifest
import com.ririnto.sinon.harness.core.DefaultRuleContext
import com.ririnto.sinon.harness.core.HarnessCheck
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.reporter.FindingReporter
import com.ririnto.sinon.harness.rules.HarnessAstRule
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.relativeTo
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.io.path.pathString

/**
 * Registers the harness validation task on the root project.
 */
abstract class HarnessValidationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) {
            return
        }
        target.pluginManager.apply("base")
        val depScope = target.configurations.create("harnessKotlinCompilerDeps")
        target.dependencies.add(
            depScope.name,
            "${BuildConfig.KOTLIN_COMPILER_MODULE}:${BuildConfig.KOTLIN_COMPILER_VERSION}",
        )
        val compilerClasspath = target.configurations.create("harnessKotlinCompilerResolvable") {
            extendsFrom(depScope)
        }
        target.tasks.register("harnessCheck", HarnessCheckTask::class.java) {
            group = "verification"
            description = "Validate Claude repository harness assets."
            rootDirectory.set(target.layout.projectDirectory)
            kotlinCompiler.from(compilerClasspath)
        }
        target.tasks.register("harnessFormat", HarnessFormatTask::class.java) {
            group = "verification"
            description = "Auto-format Claude repository harness assets where rules support it."
            rootDirectory.set(target.layout.projectDirectory)
            kotlinCompiler.from(compilerClasspath)
        }
        target.tasks.named("check").configure {
            dependsOn("harnessCheck")
        }
        target.afterEvaluate {
            target.tasks.findByName("ktlintCheck")?.let { ktlintCheckTask ->
                target.tasks.named("harnessCheck") { dependsOn(ktlintCheckTask) }
            }
        }
    }

    /**
     * Work parameters for AST analysis in an isolated classloader.
     */
    interface HarnessAstWorkParameters : WorkParameters {
        /**
         * Absolute paths of Kotlin source files to scan.
         */
        val srcFilePaths: ListProperty<String>

        /**
         * Root directory path for computing relative file paths.
         */
        val rootDir: Property<String>

        /**
         * Serialized harness manifest used to render AST findings.
         */
        val manifestText: Property<String>

        /**
         * JSON output sink for serialized HarnessAstResults.
         */
        val outputFile: RegularFileProperty
    }

    /**
     * Work parameters for AST-based formatting in an isolated classloader.
     */
    interface HarnessAstFormatWorkParameters : WorkParameters {
        /**
         * Absolute paths of Kotlin source files to format.
         */
        val srcFilePaths: ListProperty<String>

        /**
         * Root directory path for computing relative file paths.
         */
        val rootDir: Property<String>

        /**
         * Serialized harness manifest used by formatting rules.
         */
        val manifestText: Property<String>

        /**
         * JSON output sink for list of changed file paths.
         */
        val outputFile: RegularFileProperty
    }

    /**
     * Gradle task that validates installed Claude repository harness assets.
     */
    abstract class HarnessCheckTask : DefaultTask() {
        /**
         * Gradle worker executor for running AST analysis in an isolated classloader.
         */
        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        /**
         * Classpath of the isolated Kotlin compiler used by the AST worker action.
         */
        @get:Classpath
        abstract val kotlinCompiler: ConfigurableFileCollection

        /**
         * Project root directory used by the validation pass.
         */
        @get:Internal
        abstract val rootDirectory: DirectoryProperty

        /**
         * Executes harness validation by scanning AST results and manifest integrity.
         */
        @TaskAction
        fun validate() {
            val root: Path = rootDirectory.get().asFile.toPath()
            val (manifest, manifestFindings) = loadManifest(root)
            val findings =
                buildSet {
                    addAll(manifestFindings)
                    manifest?.let { manifest ->
                        (
                            manifest.keys - HarnessCheck.entries.map { check -> check.category() }.toSet() -
                                setOf(
                                    "name",
                                    "description",
                                    $$"$schema",
                                    "seedFiles",
                                    "generatedArtifacts",
                                    "harnessEvolution",
                                    "teamPatterns",
                                )
                        ).forEach { key ->
                            logger.warn("unknown manifest key: $key")
                        }
                        val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
                        addAll(collectSourceRootFindings(ctx))
                        HarnessCheck.entries.filter { check -> check.rule.applies(ctx) }.forEach { check ->
                            addAll(check.rule.validate(ctx))
                        }
                        addAll(computeAstResults(root, manifest).findings)
                    }
                }
            FindingReporter.renderFindings(root, findings.toList()).forEach { line -> logger.lifecycle(line) }
            if (findings.any { finding -> finding.severity == Severity.ERROR }) {
                throw GradleException("Harness validation failed")
            }
        }

        private fun computeAstResults(
            root: Path,
            manifest: JsonObject?,
        ): HarnessAstResults =
            manifest?.let { manifest ->
                val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
                val astRules = HarnessCheck.entries
                    .map { it.rule }
                    .filter { rule -> rule.applies(ctx) }
                    .filterIsInstance<HarnessAstRule>()
                if (!astRules.any { rule -> ctx.stackSources(rule.category).isNotEmpty() }) {
                    return HarnessAstResults(emptyList())
                }
                val outputFile = temporaryDir.toPath() / "ast-results.json"
                workerExecutor
                    .classLoaderIsolation { classpath.from(kotlinCompiler) }
                    .apply {
                        submit(HarnessAstWorkAction::class.java) {
                            srcFilePaths.set(emptyList())
                            rootDir.set(root.invariantSeparatorsPathString)
                            manifestText.set(Json.encodeToString(manifest))
                            this.outputFile.set(outputFile.toFile())
                        }
                    }.await()
                Json.decodeFromString<HarnessAstResults>(outputFile.readText())
            } ?: HarnessAstResults(emptyList())

        private fun collectSourceRootFindings(ctx: DefaultRuleContext): List<Finding> =
            buildSet {
                HarnessCheck.entries
                    .filter { check -> check.rule.applies(ctx) }
                    .map { check -> check.rule.category }
                    .distinct()
                    .forEach { category -> addAll(ctx.stackSourceFindings(category)) }
            }.toList()

        private fun loadManifest(root: Path): ManifestLoadResult {
            val manifestFile = root / "docs" / "harness" / "manifest.json"
            return when {
                manifestFile.isSymbolicLink() -> {
                    ManifestLoadResult(
                        null,
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "symlinkSafety",
                                "symlink file is not allowed: docs/harness/manifest.json",
                            ),
                        ),
                    )
                }

                !manifestFile.isRegularFile() -> {
                    ManifestLoadResult(
                        null,
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "filePresence",
                                "missing file: docs/harness/manifest.json",
                            ),
                        ),
                    )
                }

                else -> {
                    ManifestLoadResult(Json.parseToJsonElement(manifestFile.readText()).jsonObject, emptyList())
                }
            }
        }
    }

    /**
     * Gradle task that auto-formats installed Claude repository harness assets.
     */
    abstract class HarnessFormatTask : DefaultTask() {
        /**
         * Gradle worker executor for running AST format in an isolated classloader.
         */
        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        /**
         * Classpath of the isolated Kotlin compiler used by the AST format worker action.
         */
        @get:Classpath
        abstract val kotlinCompiler: ConfigurableFileCollection

        /**
         * Project root directory used by the format pass.
         */
        @get:Internal
        abstract val rootDirectory: DirectoryProperty

        /**
         * Executes harness formatting by calling the format method on applicable rules.
         */
        @TaskAction
        fun format() {
            val root: Path = rootDirectory.get().asFile.toPath()
            val (manifest, manifestFindings) = loadManifest(root)
            if (manifestFindings.isNotEmpty()) {
                manifestFindings.forEach { finding ->
                    logger.error("[${finding.severity}] ${finding.message}")
                }
                throw GradleException("Harness manifest is invalid; cannot format")
            }
            if (manifest == null) {
                throw GradleException("Cannot load harness manifest; format aborted")
            }
            val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
            val sourceRootFindings = collectSourceRootFindings(ctx)
            if (sourceRootFindings.isNotEmpty()) {
                sourceRootFindings.forEach { finding ->
                    logger.error("[${finding.severity}] ${finding.message}")
                }
            }
            val applicableRules = HarnessCheck.entries
                .filter { check -> check.rule.applies(ctx) }
                .map { check -> check.rule }
            val textFsResults = applicableRules
                .filterNot { it is HarnessAstRule }
                .flatMap { rule -> rule.format(ctx) }
                .map { absolute -> root.relativize(absolute) }
            val astChangedPaths = formatAstRules(root, manifest, applicableRules)
            val relativePaths = (textFsResults + astChangedPaths)
                .map { path -> path.invariantSeparatorsPathString }
                .distinct()
                .sorted()
                .map { Path(it) }
            when {
                relativePaths.isEmpty() -> {
                    logger.lifecycle("no files formatted")
                }

                else -> {
                    logger.lifecycle("formatted: ${relativePaths.size}")
                    relativePaths.forEach { rel ->
                        logger.lifecycle("  ${rel.invariantSeparatorsPathString}")
                    }
                }
            }
            val remainingFindings = collectFindings(root, manifest, sourceRootFindings)
            logger.lifecycle("remaining findings after format:")
            FindingReporter.renderFindings(root, remainingFindings).forEach { line -> logger.lifecycle(line) }
            if (remainingFindings.any { finding -> finding.severity == Severity.ERROR }) {
                throw GradleException("Harness validation failed after format")
            }
        }

        private fun collectSourceRootFindings(ctx: DefaultRuleContext): List<Finding> =
            buildSet {
                HarnessCheck.entries
                    .filter { check -> check.rule.applies(ctx) }
                    .map { check -> check.rule.category }
                    .distinct()
                    .forEach { category -> addAll(ctx.stackSourceFindings(category)) }
            }.toList()
        private fun collectFindings(
            root: Path,
            manifest: JsonObject,
            sourceRootFindings: List<Finding> = emptyList(),
        ): List<Finding> =
            buildSet {
                val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
                addAll(sourceRootFindings)
                if (sourceRootFindings.isEmpty()) {
                    addAll(collectSourceRootFindings(ctx))
                }
                HarnessCheck.entries.filter { check -> check.rule.applies(ctx) }.forEach { check ->
                    addAll(check.rule.validate(ctx))
                }
                addAll(computeAstResults(root, manifest).findings)
            }.toList()

        private fun computeAstResults(
            root: Path,
            manifest: JsonObject,
        ): HarnessAstResults {
            val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
            val astRules = HarnessCheck.entries
                .map { it.rule }
                .filter { rule -> rule.applies(ctx) }
                .filterIsInstance<HarnessAstRule>()
            if (!astRules.any { rule -> ctx.stackSources(rule.category).isNotEmpty() }) {
                return HarnessAstResults(emptyList())
            }
            val outputFile = temporaryDir.toPath() / "ast-format-check-results.json"
            workerExecutor
                .classLoaderIsolation { classpath.from(kotlinCompiler) }
                .apply {
                    submit(HarnessAstWorkAction::class.java) {
                        srcFilePaths.set(emptyList())
                        rootDir.set(root.invariantSeparatorsPathString)
                        manifestText.set(Json.encodeToString(manifest))
                        this.outputFile.set(outputFile.toFile())
                    }
                }.await()
            return Json.decodeFromString<HarnessAstResults>(outputFile.readText())
        }

        private fun formatAstRules(
            root: Path,
            manifest: JsonObject,
            rules: List<HarnessCheckRule>,
        ): List<Path> {
            val astRules = rules.filterIsInstance<HarnessAstRule>()
            if (astRules.isEmpty()) {
                return emptyList()
            }
            val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
            if (!astRules.any { rule -> ctx.stackSources(rule.category).isNotEmpty() }) {
                return emptyList()
            }
            val outputFile = temporaryDir.toPath() / "ast-format-results.json"
            workerExecutor.classLoaderIsolation { classpath.from(kotlinCompiler) }
                .apply {
                    submit(HarnessAstFormatWorkAction::class.java) {
                        srcFilePaths.set(emptyList())
                        rootDir.set(root.invariantSeparatorsPathString)
                        manifestText.set(Json.encodeToString(manifest))
                        this.outputFile.set(outputFile.toFile())
                    }
                }.await()
            val changed = Json.decodeFromString<List<String>>(outputFile.readText())
            return changed.map { Path(it) }.map { root.relativize(it) }
        }
        private fun loadManifest(root: Path): ManifestLoadResult {
            val manifestFile = root / "docs" / "harness" / "manifest.json"
            return when {
                manifestFile.isSymbolicLink() -> {
                    ManifestLoadResult(
                        null,
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "symlinkSafety",
                                "symlink file is not allowed: docs/harness/manifest.json",
                            ),
                        ),
                    )
                }

                !manifestFile.isRegularFile() -> {
                    ManifestLoadResult(
                        null,
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "filePresence",
                                "missing file: docs/harness/manifest.json",
                            ),
                        ),
                    )
                }

                else -> {
                    ManifestLoadResult(Json.parseToJsonElement(manifestFile.readText()).jsonObject, emptyList())
                }
            }
        }
    }

    /**
     * Worker action that runs Kotlin AST scans in an isolated classloader.
     */
    abstract class HarnessAstWorkAction : WorkAction<HarnessAstWorkParameters> {
        /**
         * Runs Kotlin AST scans and writes findings to JSON output.
         *
         * KotlinCoreEnvironment disposal is intentionally omitted: the Disposer.dispose()
         * call in worker threads violates IntelliJ Platform's EDT requirement (write-action
         * context). Since Gradle workers are short-lived task processes, JVM shutdown cleanup
         * is sufficient.
         */
        override fun execute() {
            val root: Path = Path(parameters.rootDir.get())
            System.setProperty("idea.home.path", System.getProperty("java.io.tmpdir"))
            System.setProperty("idea.use.native.fs.for.win", "false")
            val configuration = CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            }
            val environment = Companion.createKotlinCoreEnvironmentViaReflection(configuration)
            val ctx = DefaultRuleContext(
                root,
                DefaultManifest(Json.parseToJsonElement(parameters.manifestText.get()).jsonObject),
                stack = "kotlin",
            )
            val astFactory = KtPsiFactory(environment.project)
            val astRules = HarnessCheck.entries
                .map { check -> check.rule }
                .filter { rule -> rule.applies(ctx) }
                .filterIsInstance<HarnessAstRule>()
            val parseErrorFiles = mutableSetOf<Path>()
            val sourceFiles = astRules
                .flatMap { astRule -> ctx.stackSources(astRule.category) }
                .distinct()
            val parseErrorFindings = buildList {
                sourceFiles.forEach { srcFilePath ->
                    val ktFile = AstSupport.parse(srcFilePath, astFactory)
                    if (ktFile == null) {
                        return@forEach
                    }
                    val parseErrors = AstSupport.parseErrors(ktFile)
                    if (parseErrors.isNotEmpty()) {
                        parseErrorFiles.add(srcFilePath)
                        parseErrors.forEach { parseError ->
                            add(
                                Finding(
                                    severity = Severity.ERROR,
                                    category = "parseError",
                                    message = "kotlin parse error: ${parseError.message}",
                                    file = AstSupport.relativeFilePath(srcFilePath, root),
                                    startLine = parseError.line,
                                    startColumn = 1,
                                ),
                            )
                        }
                    }
                }
            }
            val findings = parseErrorFindings + astRules
                .flatMap { astRule ->
                    astRule.renderAstFindings(
                        ctx,
                        ctx
                            .stackSources(astRule.category)
                            .filterNot { parseErrorFiles.contains(it) }
                            .flatMap { srcFilePath ->
                                astRule.findAstFindings(srcFilePath, ctx, astFactory)
                            },
                    )
                }
            parameters.outputFile
                .get()
                .asFile
                .toPath()
                .writeText(Json.encodeToString(HarnessAstResults(findings)))
        }

        companion object {
            /**
             * Creates a KotlinCoreEnvironment via reflection to access private createForTests factory.
             *
             * A disposable instance is created internally and managed by JVM shutdown; see execute()
             * docstring for why Disposer.dispose() is not called explicitly.
             */
            fun createKotlinCoreEnvironmentViaReflection(
                configuration: CompilerConfiguration,
            ): KotlinCoreEnvironment {
                val method =
                    KotlinCoreEnvironment::class.java.getDeclaredMethod(
                        "createForTests",
                        Disposable::class.java,
                        CompilerConfiguration::class.java,
                        EnvironmentConfigFiles::class.java,
                    )
                method.isAccessible = true
                val result =
                    method.invoke(
                        null,
                        Disposer.newDisposable("HarnessAstWorkAction"),
                        configuration,
                        EnvironmentConfigFiles.JVM_CONFIG_FILES,
                    )
                return checkNotNull(result as? KotlinCoreEnvironment) {
                    "createForTests returned null or incorrect type: ${result?.javaClass}"
                }
            }
        }
    }

    /**
     * Worker action that formats Kotlin source files based on AST rules in an isolated classloader.
     */
    abstract class HarnessAstFormatWorkAction : WorkAction<HarnessAstFormatWorkParameters> {
        /**
         * Formats Kotlin source files and writes list of changed paths to JSON output.
         *
         * KotlinCoreEnvironment disposal is intentionally omitted; see HarnessAstWorkAction.execute()
         * docstring for rationale.
         */
        override fun execute() {
            val root: Path = Path(parameters.rootDir.get())
            System.setProperty("idea.home.path", System.getProperty("java.io.tmpdir"))
            System.setProperty("idea.use.native.fs.for.win", "false")
            val configuration = CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            }
            val environment = HarnessAstWorkAction.Companion.createKotlinCoreEnvironmentViaReflection(configuration)
            val ctx = DefaultRuleContext(
                root,
                DefaultManifest(Json.parseToJsonElement(parameters.manifestText.get()).jsonObject),
                stack = "kotlin",
            )
            val astFactory = KtPsiFactory(environment.project)
            val rootReal = root.toRealPath()
            val astRules = HarnessCheck.entries
                .map { it.rule }
                .filter { rule -> rule.applies(ctx) }
                .filterIsInstance<HarnessAstRule>()
                .filter { astRule -> astRule.applies(ctx) && ctx.stackSources(astRule.category).isNotEmpty() }
            val sourceFiles = astRules
                .flatMap { astRule -> ctx.stackSources(astRule.category) }
                .distinct()
            val parseErrorFiles = sourceFiles
                .filter { srcFilePath ->
                    val ktFile = AstSupport.parse(srcFilePath, astFactory)
                    ktFile != null && AstSupport.parseErrors(ktFile).isNotEmpty()
                }
                .toSet()
            val changed = buildSet {
                astRules
                    .forEach { astRule ->
                        ctx.stackSources(astRule.category)
                            .filterNot { parseErrorFiles.contains(it) }
                            .forEach { filePath ->
                                if (!isContainedRegularSource(root, rootReal, filePath)) {
                                    error("unsafe source path outside project root: $filePath")
                                }
                                val ktFile = AstSupport.parse(filePath, astFactory)
                                if (ktFile != null) {
                                    val newText = astRule.formatAst(filePath, ktFile, ctx)
                                    if (newText != null) {
                                        if (!isContainedRegularSource(root, rootReal, filePath)) {
                                            error("unsafe write path outside project root: $filePath")
                                        }
                                        filePath.writeText(newText)
                                        add(filePath.invariantSeparatorsPathString)
                                    }
                                }
                            }
                    }
            }
            parameters.outputFile
                .get()
                .asFile
                .toPath()
                .writeText(Json.encodeToString(changed.toList().sorted()))
        }

        private fun isContainedRegularSource(root: Path, rootReal: Path, filePath: Path): Boolean {
            val normalizedPath = if (filePath.isAbsolute) filePath else root.resolve(filePath)
            return try {
                val fileReal = normalizedPath.toRealPath(LinkOption.NOFOLLOW_LINKS)
                fileReal.startsWith(rootReal) &&
                    Files.isRegularFile(normalizedPath, LinkOption.NOFOLLOW_LINKS) &&
                    !hasSymlinkSegment(root, normalizedPath)
            } catch (_: Exception) {
                false
            }
        }

        private fun hasSymlinkSegment(root: Path, filePath: Path): Boolean {
            return try {
                val relative = filePath.relativeTo(root)
                var current = root
                for (segment in relative) {
                    if (segment.pathString == ".") {
                        continue
                    }
                    if (segment.pathString == "..") {
                        return true
                    }
                    current /= segment
                    if (Files.isSymbolicLink(current)) {
                        return true
                    }
                }
                false
            } catch (_: Exception) {
                true
            }
        }
    }

    private data class ManifestLoadResult(
        val manifest: JsonObject?,
        val findings: List<Finding>,
    )
}
