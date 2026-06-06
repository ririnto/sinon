import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.moveTo
import kotlin.io.path.readBytes
import kotlin.io.path.setPosixFilePermissions

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

fun gitHooksDir(): Path? {
    val process =
        ProcessBuilder(
            listOf("git", "rev-parse", "--git-path", "hooks"),
        ).directory(rootDir)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().use { reader -> reader.readText().trim() }
    if (process.waitFor() != 0 || output.isBlank()) {
        return null
    }
    return rootDir.toPath().resolve(output).normalize()
}

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

tasks.register("checkHarnessMarkdownLinks") {
    doLast {
        providers
            .exec {
                commandLine(
                    "sh",
                    layout.projectDirectory
                        .file("docs/harness/scripts/check-markdown-links.sh")
                        .asFile
                        .path,
                )
            }.result
            .get()
            .assertNormalExitValue()
    }
}

tasks.named("ktlintCheck") {
    dependsOn("checkHarnessMarkdownLinks")
    doLast {
        val hooksDir = gitHooksDir() ?: return@doLast
        hooksDir.createDirectories()
        listOf("pre-commit", "pre-push").forEach { hookName ->
            val source =
                layout.projectDirectory
                    .file("docs/harness/git-hooks/$hookName")
                    .asFile
                    .toPath()
            if (source.isRegularFile()) {
                val target = hooksDir.resolve(hookName)
                if (target.isSymbolicLink()) {
                    return@forEach
                }
                if (
                    target.isRegularFile() &&
                    target.readBytes().contentEquals(source.readBytes())
                ) {
                    return@forEach
                }
                val temporaryTarget =
                    createTempFile(hooksDir, ".sync-git-hooks-", "-$hookName")
                source.copyTo(temporaryTarget, overwrite = true)
                temporaryTarget
                    .also { path ->
                        path.setPosixFilePermissions(
                            setOf(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE,
                                PosixFilePermission.GROUP_READ,
                                PosixFilePermission.GROUP_EXECUTE,
                                PosixFilePermission.OTHERS_READ,
                                PosixFilePermission.OTHERS_EXECUTE,
                            ),
                        )
                    }.moveTo(
                        target,
                        overwrite = true,
                    )
            }
        }
    }
}
