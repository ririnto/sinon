import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

fun gitTrackedFiles(vararg patterns: String): Set<String> {
    val process =
        ProcessBuilder(
            listOf("git", "ls-files", "--", *patterns),
        ).directory(rootDir)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
    if (process.waitFor() != 0) {
        error("git ls-files failed while listing Kotlin files: $output")
    }
    return output
        .lineSequence()
        .filter { path -> path.isNotBlank() }
        .toSet()
}

val trackedKotlinFiles = gitTrackedFiles("*.kt", "*.kts")

allprojects {
    val projectPathPrefix = if (this == rootProject) "" else "${project.path.removePrefix(":").replace(':', '/')}/"
    apply(
        plugin =
            rootProject.libs.plugins.ktlint
                .get()
                .pluginId,
    )
    configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.cli)
        android.set(false)
        ignoreFailures.set(false)
        filter {
            include(
                trackedKotlinFiles
                    .filter { trackedFile -> projectPathPrefix.isEmpty() || trackedFile.startsWith(projectPathPrefix) }
                    .map { trackedFile -> trackedFile.removePrefix(projectPathPrefix) },
            )
            exclude { element ->
                !element.isDirectory && rootProject.relativePath(element.file) !in trackedKotlinFiles
            }
        }
        reporters {
            reporter(ReporterType.PLAIN)
        }
    }
}

tasks.register("checkMarkdown") {
    doLast {
        providers
            .exec {
                commandLine(
                    "bunx",
                    "markdownlint-cli2",
                )
            }.result
            .get()
            .assertNormalExitValue()
    }
}

tasks.named("ktlintCheck") {
    dependsOn("checkMarkdown")
}

