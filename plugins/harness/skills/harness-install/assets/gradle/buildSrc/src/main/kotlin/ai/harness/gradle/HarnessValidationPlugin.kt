package ai.harness.gradle

import ai.harness.gradle.HarnessPsiResults.Finding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * Registers the harness validation task on the root project.
 */
abstract class HarnessValidationPlugin : Plugin<Project> {
    companion object {
        /**
         * Maven coordinate prefix for the Kotlin compiler artifact.
         */
        const val KOTLIN_COMPILER_EMBEDDABLE = "org.jetbrains.kotlin:kotlin-compiler-embeddable"

        /**
         * Pinned Kotlin compiler version used by the worker classloader.
         */
        const val KOTLIN_COMPILER_VERSION = "2.1.0"
    }

    override fun apply(target: Project) {
        if (target != target.rootProject) {
            return
        }
        target.pluginManager.apply("base")
        val depScope = target.configurations.create("harnessKotlinCompilerDeps")
        target.dependencies.add(
            depScope.name,
            "$KOTLIN_COMPILER_EMBEDDABLE:$KOTLIN_COMPILER_VERSION",
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
         * Gradle worker executor for running PSI analysis in an isolated classloader.
         */
        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        /**
         * Classpath of the isolated Kotlin compiler used by the PSI worker action.
         */
        @get:Classpath
        abstract val kotlinCompiler: ConfigurableFileCollection

        /**
         * Executes harness validation by scanning PSI results and manifest integrity.
         */
        @TaskAction
        fun validate() {
            val root: Path = project.rootDir.toPath()
            val (manifest, manifestFindings) = loadManifest(root)
            val psiResults = computePsiResults(root, manifest)
            val findings =
                buildSet {
                    addAll(manifestFindings)
                    if (manifest != null) {
                        val knownMetadataKeys =
                            setOf(
                                "name",
                                "description",
                                $$"$schema",
                                "seedFiles",
                                "generatedArtifacts",
                                "harnessEvolution",
                                "teamPatterns",
                            )
                        val unknownKeys =
                            manifest.keys - HarnessCheck.entries.map { check -> check.category() }.toSet() -
                                knownMetadataKeys
                        unknownKeys.forEach { key ->
                            project.logger.warn("unknown manifest key: $key")
                        }
                        HarnessCheck.entries.filter { check -> check.applies(manifest) }.forEach { check ->
                            addAll(check.validate(manifest, root, psiResults))
                        }
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

        private fun computePsiResults(
            root: Path,
            manifest: JsonObject?,
        ): HarnessPsiResults {
            if (manifest == null) {
                return HarnessPsiResults(emptyList())
            }
            val srcRoots =
                listOf(
                    root / "buildSrc" / "src" / "main" / "kotlin",
                    root / "buildSrc" / "src" / "test" / "kotlin",
                ).filter { srcRoot -> srcRoot.isDirectory() }
            val srcFiles =
                srcRoots.flatMap { dir ->
                    dir
                        .walk()
                        .filter { file -> !file.isSymbolicLink() }
                        .filter { file -> file.isRegularFile() }
                        .filter { file -> file.extension == "kt" }
                }
            val outputFile = temporaryDir.toPath() / "psi-results.json"
            val workQueue =
                workerExecutor.classLoaderIsolation {
                    classpath.from(kotlinCompiler)
                }
            workQueue.submit(HarnessPsiWorkAction::class.java) {
                srcFilePaths.set(srcFiles.map { srcFile -> srcFile.toString() })
                rootDir.set(root.toString())
                manifestText.set(Json.encodeToString(manifest))
                this.outputFile.set(outputFile.toFile())
            }
            workQueue.await()
            return Json.decodeFromString<HarnessPsiResults>(outputFile.readText())
        }

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
                    try {
                        Json.parseToJsonElement(manifestFile.readText()).jsonObject to emptyList()
                    } catch (e: Exception) {
                        null to
                            listOf(
                                Finding(
                                    Severity.ERROR,
                                    "filePresence",
                                    "failed to parse manifest: ${e.message}",
                                ),
                            )
                    }
                }
            }
        }
    }

    /**
     * Work parameters for PSI analysis in an isolated classloader.
     */
    interface HarnessPsiWorkParameters : WorkParameters {
        /**
         * Absolute paths of Kotlin source files to scan.
         */
        val srcFilePaths: ListProperty<String>

        /**
         * Root directory path for computing relative file paths.
         */
        val rootDir: Property<String>

        /**
         * Serialized harness manifest used to render PSI findings.
         */
        val manifestText: Property<String>

        /**
         * JSON output sink for serialized HarnessPsiResults.
         */
        val outputFile: RegularFileProperty
    }

    /**
     * Worker action that runs Kotlin PSI scans in an isolated classloader.
     */
    abstract class HarnessPsiWorkAction : WorkAction<HarnessPsiWorkParameters> {
        override fun execute() {
            val srcFiles: List<Path> = parameters.srcFilePaths.get().map { srcFilePath -> Path(srcFilePath) }
            val root: Path = Path(parameters.rootDir.get())
            val manifest = Json.parseToJsonElement(parameters.manifestText.get()).jsonObject
            System.setProperty("idea.home.path", System.getProperty("java.io.tmpdir"))
            System.setProperty("idea.use.native.fs.for.win", "false")
            val disposable: Disposable = Disposer.newDisposable("HarnessPsiWorkAction")
            try {
                val psiFactory: KtPsiFactory? =
                    try {
                        val configuration =
                            CompilerConfiguration().apply {
                                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                            }
                        val environment = createKotlinCoreEnvironmentViaReflection(disposable, configuration)
                        KtPsiFactory(environment.project, true)
                    } catch (error: Throwable) {
                        error.localizedMessage?.let { null } ?: null
                    }
                val rawFindings =
                    srcFiles.flatMap { srcFile ->
                        HarnessCheck.entries.flatMap { check ->
                            check.rule.findPsiFindings(srcFile, root, psiFactory)
                        }
                    }
                val results = HarnessPsiResults(HarnessCheck.renderPsiFindings(rawFindings, manifest))
                parameters.outputFile
                    .get()
                    .asFile
                    .toPath()
                    .writeText(Json.encodeToString(results))
            } finally {
                Disposer.dispose(disposable)
            }
        }

        private fun createKotlinCoreEnvironmentViaReflection(
            disposable: Disposable,
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
            @Suppress("UNCHECKED_CAST")
            return method.invoke(
                null,
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            ) as KotlinCoreEnvironment
        }

    }
}
