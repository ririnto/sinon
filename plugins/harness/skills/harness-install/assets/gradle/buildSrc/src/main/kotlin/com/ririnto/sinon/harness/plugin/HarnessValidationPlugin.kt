package com.ririnto.sinon.harness.plugin

import com.ririnto.sinon.harness.core.DefaultManifest
import com.ririnto.sinon.harness.core.DefaultRuleContext
import com.ririnto.sinon.harness.core.HarnessCheck
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.ast.HarnessAstResults
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
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
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

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
        target.tasks.register("harnessValidate", HarnessValidationTask::class.java) {
            group = "verification"
            description = "Validate Claude repository harness assets."
            kotlinCompiler.from(
                target.configurations.create("harnessKotlinCompilerResolvable") {
                    extendsFrom(depScope)
                },
            )
        }
        target.tasks.named("check").configure {
            dependsOn("harnessValidate")
        }
    }

    /**
     * Gradle task that validates installed Claude repository harness assets.
     */
    abstract class HarnessValidationTask : DefaultTask() {
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
         * Executes harness validation by scanning AST results and manifest integrity.
         */
        @TaskAction
        fun validate() {
            val root: Path = project.rootDir.toPath()
            val (manifest, manifestFindings) = loadManifest(root)
            val astResults = computeAstResults(root, manifest)
            val findings =
                buildSet {
                    addAll(manifestFindings)
                    manifest?.let { manifest ->
                        (manifest.keys - HarnessCheck.entries.map { check -> check.category() }.toSet() -
                            setOf(
                                "name",
                                "description",
                                $$"$schema",
                                "seedFiles",
                                "generatedArtifacts",
                                "harnessEvolution",
                                "teamPatterns",
                            )).forEach { key ->
                            project.logger.warn("unknown manifest key: $key")
                        }
                        val ctx = DefaultRuleContext(root, DefaultManifest(manifest))
                        HarnessCheck.entries.filter { check -> check.rule.applies(ctx) }.forEach { check ->
                            addAll(check.rule.validate(ctx))
                        }
                        addAll(astResults.findings)
                    }
                }
            findings
                .sortedWith(
                    compareBy({ finding -> finding.severity.ordinal }, { finding -> findings.indexOf(finding) }),
                ).forEach { finding ->
                    when (finding.severity) {
                        Severity.ERROR -> project.logger.error("[${finding.severity}] ${finding.message}")
                        Severity.WARN -> project.logger.warn("[${finding.severity}] ${finding.message}")
                        Severity.INFO -> project.logger.info("[${finding.severity}] ${finding.message}")
                    }
                }
            if (findings.any { finding -> finding.severity == Severity.ERROR }) {
                throw GradleException("Harness validation failed")
            }
            project.logger.lifecycle("Harness validation passed")
        }

        private fun computeAstResults(
            root: Path,
            manifest: JsonObject?,
        ): HarnessAstResults =
            manifest?.let { manifest ->
                val outputFile = temporaryDir.toPath() / "ast-results.json"
                workerExecutor
                    .classLoaderIsolation { classpath.from(kotlinCompiler) }
                    .apply {
                        submit(HarnessAstWorkAction::class.java) {
                            srcFilePaths.set(
                                listOf(
                                    root / "buildSrc" / "src" / "main" / "kotlin",
                                    root / "buildSrc" / "src" / "test" / "kotlin",
                                )
                                    .filter { srcRoot -> srcRoot.isDirectory() }
                                    .flatMap { dir ->
                                        dir
                                            .walk()
                                            .filter { file -> !file.isSymbolicLink() }
                                            .filter { file -> file.isRegularFile() }
                                            .filter { file -> file.extension == "kt" }
                                    }
                                    .map { srcFile -> srcFile.invariantSeparatorsPathString },
                            )
                            rootDir.set(root.invariantSeparatorsPathString)
                            manifestText.set(Json.encodeToString(manifest))
                            this.outputFile.set(outputFile.toFile())
                        }
                    }
                    .await()
                Json.decodeFromString<HarnessAstResults>(outputFile.readText())
            } ?: HarnessAstResults(emptyList())

        private fun loadManifest(root: Path): Pair<JsonObject?, List<Finding>> {
            val manifestFile = root / "docs" / "harness" / "manifest.json"
            return when {
                manifestFile.isSymbolicLink() -> {
                    null to
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "symlinkSafety",
                                "symlink file is not allowed: docs/harness/manifest.json",
                            ),
                        )
                }

                !manifestFile.isRegularFile() -> {
                    null to
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "filePresence",
                                "missing file: docs/harness/manifest.json",
                            ),
                        )
                }

                else -> {
                    Json.parseToJsonElement(manifestFile.readText()).jsonObject to emptyList()
                }
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
            val manifest = Json.parseToJsonElement(parameters.manifestText.get()).jsonObject
            System.setProperty("idea.home.path", System.getProperty("java.io.tmpdir"))
            System.setProperty("idea.use.native.fs.for.win", "false")
            val configuration =
                CompilerConfiguration().apply {
                    put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                }
            val environment = createKotlinCoreEnvironmentViaReflection(configuration)
            val ctx = DefaultRuleContext(root, DefaultManifest(manifest))
            val factory = KtPsiFactory(environment.project)
            val findings = HarnessCheck.entries
                .map { check -> check.rule }
                .filter { rule -> rule.applies(ctx) }
                .filterIsInstance<HarnessAstRule>()
                .flatMap { astRule ->
                    val astFindings = parameters.srcFilePaths.get()
                        .flatMap { srcFilePath ->
                            astRule.findAstFindings(Path(srcFilePath), ctx, factory)
                        }
                    astRule.renderAstFindings(ctx, astFindings)
                }
            val results = HarnessAstResults(findings)
            parameters.outputFile
                .get()
                .asFile
                .toPath()
                .writeText(Json.encodeToString(results))
        }

        /**
         * Creates a KotlinCoreEnvironment via reflection to access private createForTests factory.
         *
         * A disposable instance is created internally and managed by JVM shutdown; see execute()
         * docstring for why Disposer.dispose() is not called explicitly.
         */
        private fun createKotlinCoreEnvironmentViaReflection(
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
            val result = method.invoke(
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
