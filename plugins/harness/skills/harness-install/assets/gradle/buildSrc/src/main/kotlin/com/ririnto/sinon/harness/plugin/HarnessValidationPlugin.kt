package com.ririnto.sinon.harness.plugin

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.DefaultManifest
import com.ririnto.sinon.harness.core.DefaultRuleContext
import com.ririnto.sinon.harness.core.HarnessCheck
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.ktlint.HarnessKotlinCategories
import com.ririnto.sinon.harness.ktlint.HarnessKtlintEngine
import com.ririnto.sinon.harness.reporter.FindingReporter
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.Serializable
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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.relativeTo
import kotlin.io.path.readText
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
        val depScope = target.configurations.create("harnessKtlintDeps")
        target.dependencies.add(
            depScope.name,
            "${BuildConfig.KTLINT_RULE_ENGINE_MODULE}:${BuildConfig.KTLINT_VERSION}",
        )
        target.dependencies.add(
            depScope.name,
            "${BuildConfig.KTLINT_RULE_ENGINE_CORE_MODULE}:${BuildConfig.KTLINT_VERSION}",
        )
        val ktlintClasspath = target.configurations.create("harnessKtlintResolvable") {
            extendsFrom(depScope)
        }
        target.tasks.register("harnessCheck", HarnessCheckTask::class.java) {
            group = "verification"
            description = "Validate Claude repository harness assets."
            rootDirectory.set(target.layout.projectDirectory)
            this.ktlintClasspath.from(ktlintClasspath)
        }
        target.tasks.register("harnessFormat", HarnessFormatTask::class.java) {
            group = "verification"
            description = "Auto-format Claude repository harness assets where rules support it."
            rootDirectory.set(target.layout.projectDirectory)
            this.ktlintClasspath.from(ktlintClasspath)
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
     * Work parameters for ktlint analysis in an isolated classloader.
     */
    interface HarnessKtlintWorkParameters : WorkParameters {
        /**
         * Root directory path for computing relative file paths.
         */
        val rootDir: Property<String>

        /**
         * Serialized harness manifest used to render findings.
         */
        val manifestText: Property<String>

        /**
         * JSON output sink for serialized HarnessAstResults.
         */
        val outputFile: RegularFileProperty
    }

    /**
     * Work parameters for ktlint formatting in an isolated classloader.
     */
    interface HarnessKtlintFormatWorkParameters : WorkParameters {
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
         * Gradle worker executor for running ktlint analysis in an isolated classloader.
         */
        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        /**
         * Classpath of the ktlint rule engine used by the ktlint worker action.
         */
        @get:Classpath
        abstract val ktlintClasspath: ConfigurableFileCollection

        /**
         * Project root directory used by the validation pass.
         */
        @get:Internal
        abstract val rootDirectory: DirectoryProperty

        /**
         * Executes harness validation by scanning findings and manifest integrity.
         */
        @TaskAction
        fun validate() {
            val root: Path = rootDirectory.get().asFile.toPath()
            val (manifest, manifestFindings) = loadManifest(root)
            val findings =
                buildSet {
                    addAll(manifestFindings)
                    manifest?.let { manifest ->
                        val knownKeys = HarnessCheck.entries.map { check -> check.category() }.toSet() +
                            HarnessKotlinCategories.categories +
                            setOf(
                                "name",
                                "description",
                                "\$schema",
                                "seedFiles",
                                "generatedArtifacts",
                                "harnessEvolution",
                                "teamPatterns",
                            )
                        (manifest.keys - knownKeys).forEach { key ->
                            logger.warn("unknown manifest key: $key")
                        }
                        val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
                        addAll(collectSourceRootFindings(ctx))
                        HarnessCheck.entries.filter { check -> check.rule.applies(ctx) }.forEach { check ->
                            addAll(check.rule.validate(ctx))
                        }
                        addAll(computeKotlinFindings(root, manifest))
                    }
                }
            FindingReporter.renderFindings(root, findings.toList()).forEach { line -> logger.lifecycle(line) }
            if (findings.any { finding -> finding.severity == Severity.ERROR }) {
                throw GradleException("Harness validation failed")
            }
        }

        private fun computeKotlinFindings(
            root: Path,
            manifest: JsonObject?,
        ): List<Finding> =
            manifest?.let { manifest ->
                val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
                if (!HarnessKotlinCategories.categories.any { category ->
                    ctx.manifest.isEnabled(category) &&
                        ctx.stackSources(category).isNotEmpty()
                }) {
                    return emptyList()
                }
                val outputFile = temporaryDir.toPath() / "kotlin-findings.json"
                workerExecutor
                    .classLoaderIsolation { classpath.from(ktlintClasspath) }
                    .apply {
                        submit(HarnessKtlintWorkAction::class.java) {
                            rootDir.set(root.invariantSeparatorsPathString)
                            manifestText.set(Json.encodeToString(manifest))
                            this.outputFile.set(outputFile.toFile())
                        }
                    }.await()
                val result = Json.decodeFromString<KotlinFindingsResult>(outputFile.readText())
                result.findings
            } ?: emptyList()

        private fun collectSourceRootFindings(ctx: DefaultRuleContext): List<Finding> =
            buildSet {
                HarnessCheck.entries
                    .filter { check -> check.rule.applies(ctx) }
                    .map { check -> check.rule.category }
                    .distinct()
                    .forEach { category -> addAll(ctx.stackSourceFindings(category)) }
                HarnessKotlinCategories.categories
                    .filter { category -> ctx.manifest.isEnabled(category) }
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
         * Gradle worker executor for running ktlint format in an isolated classloader.
         */
        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        /**
         * Classpath of the ktlint rule engine used by the ktlint format worker action.
         */
        @get:Classpath
        abstract val ktlintClasspath: ConfigurableFileCollection

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
                .flatMap { rule -> rule.format(ctx) }
                .map { absolute -> root.relativize(absolute) }
            val kotlinChangedPaths = formatKotlinRules(root, manifest)
            val relativePaths = (textFsResults + kotlinChangedPaths)
                .map { path -> path.invariantSeparatorsPathString }
                .distinct()
                .sorted()
                .map { pathString -> Path(pathString) }
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
                HarnessKotlinCategories.categories
                    .filter { category -> ctx.manifest.isEnabled(category) }
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
                addAll(computeKotlinFindings(root, manifest))
            }.toList()

        private fun computeKotlinFindings(
            root: Path,
            manifest: JsonObject,
        ): List<Finding> {
            val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
            if (!HarnessKotlinCategories.categories.any { category ->
                ctx.manifest.isEnabled(category) &&
                    ctx.stackSources(category).isNotEmpty()
            }) {
                return emptyList()
            }
            val outputFile = temporaryDir.toPath() / "kotlin-format-check-findings.json"
            workerExecutor
                .classLoaderIsolation { classpath.from(ktlintClasspath) }
                .apply {
                    submit(HarnessKtlintWorkAction::class.java) {
                        rootDir.set(root.invariantSeparatorsPathString)
                        manifestText.set(Json.encodeToString(manifest))
                        this.outputFile.set(outputFile.toFile())
                    }
                }.await()
            val result = Json.decodeFromString<KotlinFindingsResult>(outputFile.readText())
            return result.findings
        }

        private fun formatKotlinRules(
            root: Path,
            manifest: JsonObject,
        ): List<Path> {
            val ctx = DefaultRuleContext(root, DefaultManifest(manifest), stack = "kotlin")
            if (!HarnessKotlinCategories.categories.any { category ->
                ctx.manifest.isEnabled(category) &&
                    ctx.stackSources(category).isNotEmpty()
            }) {
                return emptyList()
            }
            val outputFile = temporaryDir.toPath() / "kotlin-format-results.json"
            workerExecutor.classLoaderIsolation { classpath.from(ktlintClasspath) }
                .apply {
                    submit(HarnessKtlintFormatWorkAction::class.java) {
                        rootDir.set(root.invariantSeparatorsPathString)
                        manifestText.set(Json.encodeToString(manifest))
                        this.outputFile.set(outputFile.toFile())
                    }
                }.await()
            val changed = Json.decodeFromString<List<String>>(outputFile.readText())
            return changed.map { pathString -> Path(pathString) }.map { absolute -> root.relativize(absolute) }
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
     * Worker action that runs ktlint scans in an isolated classloader.
     */
    abstract class HarnessKtlintWorkAction : WorkAction<HarnessKtlintWorkParameters> {
        /**
         * Runs ktlint scans and writes findings to JSON output.
         */
        override fun execute() {
            val root: Path = Path(parameters.rootDir.get())
            val ctx = DefaultRuleContext(
                root,
                DefaultManifest(Json.parseToJsonElement(parameters.manifestText.get()).jsonObject),
                stack = "kotlin",
            )
            val findings = HarnessKtlintEngine.analyze(ctx)
            parameters.outputFile
                .get()
                .asFile
                .toPath()
                .writeText(Json.encodeToString(KotlinFindingsResult(findings)))
        }
    }

    /**
     * Worker action that formats Kotlin source files based on ktlint rules in an isolated classloader.
     */
    abstract class HarnessKtlintFormatWorkAction : WorkAction<HarnessKtlintFormatWorkParameters> {
        /**
         * Formats Kotlin source files and writes list of changed paths to JSON output.
         */
        override fun execute() {
            val root: Path = Path(parameters.rootDir.get())
            val ctx = DefaultRuleContext(
                root,
                DefaultManifest(Json.parseToJsonElement(parameters.manifestText.get()).jsonObject),
                stack = "kotlin",
            )
            val changed = HarnessKtlintEngine.format(ctx)
                .map { path -> path.invariantSeparatorsPathString }
                .sorted()
            parameters.outputFile
                .get()
                .asFile
                .toPath()
                .writeText(Json.encodeToString(changed))
        }
    }

    private data class ManifestLoadResult(
        val manifest: JsonObject?,
        val findings: List<Finding>,
    )

    @Serializable
    private data class KotlinFindingsResult(
        val findings: List<Finding>,
    )
}
