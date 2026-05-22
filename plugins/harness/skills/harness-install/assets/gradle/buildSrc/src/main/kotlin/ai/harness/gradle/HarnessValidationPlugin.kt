package ai.harness.gradle

import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import kotlin.io.path.isExecutable
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.toPath

/**
 * Registers the harness validation task on the root project.
 */
class HarnessValidationPlugin : Plugin<Project> {
	/**
	 * Applies the plugin to the root project.
	 */
	override fun apply(project: Project) {
		if (project == project.rootProject) {
			project.pluginManager.apply("base")

			project.tasks.register("harnessValidate", HarnessValidationTask::class.java)
			project.tasks.getByName("harnessValidate").apply {
				group = "verification"
				description = "Validate Claude repository harness assets."
			}
			project.tasks.getByName("check").dependsOn("harnessValidate")
		}
	}

	/**
	 * Gradle task that validates installed Claude repository harness assets.
	 */
	abstract class HarnessValidationTask @Inject constructor(
		private val workerExecutor: WorkerExecutor,
	) : DefaultTask() {
		/**
		 * Validates required harness files, generated artifact policy, templates, hooks, and executable shebangs.
		 */
		@TaskAction
		fun validate() {
			val root = project.rootDir
			val outputJsonFile = project.layout.buildDirectory.file("generated/harness-psi-results.json").get().asFile
			outputJsonFile.parentFile.mkdirs()

			val manifestLoad = loadManifest(root)
			val manifest = manifestLoad.first
			val manifestFindings = manifestLoad.second

			val psiResults = computePsiResults(root, outputJsonFile)

			val findings = buildSet<Finding> {
				addAll(manifestFindings)
				if (manifest != null) {
					val knownCategories = HarnessCheck.values().map { it.category }.toSet()
					val knownMetadataKeys = setOf("name", "description", "\$schema", "seedFiles", "generatedArtifacts", "harnessEvolution", "teamPatterns")
					val unknownKeys = manifest.keys - knownCategories - knownMetadataKeys
					unknownKeys.forEach { key ->
						project.logger.warn("unknown manifest key: $key")
					}

					HarnessCheck.values().forEach { check ->
						if (check.applies(manifest)) {
							addAll(check.validate(manifest, root, psiResults))
						}
					}
				}
			}.toList()

			val sortedFindings = findings.sortedWith(compareBy({ it.severity.ordinal }, { findings.indexOf(it) }))
			sortedFindings.forEach { finding ->
				when (finding.severity) {
					Severity.ERROR -> project.logger.error("[${finding.severity}] ${finding.message}")
					Severity.WARN -> project.logger.warn("[${finding.severity}] ${finding.message}")
					Severity.INFO -> project.logger.info("[${finding.severity}] ${finding.message}")
				}
			}

			when {
				sortedFindings.any { it.severity == Severity.ERROR } -> throw GradleException("Harness validation failed")
				else -> project.logger.lifecycle("Harness validation passed")
			}
		}

		private fun computePsiResults(root: java.io.File, outputJsonFile: java.io.File): HarnessPsiResults {
			val kotlinDirs = buildSet {
				collectKotlinDirs(java.io.File(root, "buildSrc/src/main/kotlin"), this)
				collectKotlinDirs(java.io.File(root, "buildSrc/src/test/kotlin"), this)
			}.toList()

			val srcFiles = buildList {
				kotlinDirs.forEach { dir ->
					if (dir.exists() && dir.isDirectory) {
						dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
							add(file)
						}
					}
				}
			}

			if (srcFiles.isEmpty()) {
				return HarnessPsiResults()
			}

			val workQueue = workerExecutor.classLoaderIsolation {
				classpath.from(project.configurations.named("kotlinCompiler"))
			}

			workQueue.submit(HarnessPsiWorkAction::class.java) {
				sourceFiles.set(srcFiles)
				outputJson.set(outputJsonFile)
			}

			workQueue.await()

			return if (outputJsonFile.exists()) {
				try {
					val content = outputJsonFile.readText()
					Json.decodeFromString<HarnessPsiResults>(content)
				} catch (_: Exception) {
					HarnessPsiResults()
				}
			} else {
				HarnessPsiResults()
			}
		}

		private fun collectKotlinDirs(dir: java.io.File, result: MutableSet<java.io.File>) {
			if (dir.exists() && dir.isDirectory) {
				result.add(dir)
			}
		}

		private fun loadManifest(root: java.io.File): Pair<JsonObject?, List<Finding>> {
			val manifestFile = java.io.File(root, "docs/harness/manifest.json")

			return when {
				manifestFile.toPath().isSymbolicLink() -> null to listOf(Finding(Severity.ERROR, "forbidUnsafeSymlinks", "symlink file is not allowed: docs/harness/manifest.json"))
				!manifestFile.exists() -> null to listOf(Finding(Severity.ERROR, "requireFilesExist", "missing file: docs/harness/manifest.json"))
				!manifestFile.isFile -> null to listOf(Finding(Severity.ERROR, "requireFilesExist", "missing file: docs/harness/manifest.json"))
				else -> {
					try {
						val content = manifestFile.readText()
						val json = Json.parseToJsonElement(content).jsonObject
						json to emptyList()
					} catch (e: Exception) {
						null to listOf(Finding(Severity.ERROR, "requireFilesExist", "failed to parse manifest: ${e.message}"))
					}
				}
			}
		}
	}
}
