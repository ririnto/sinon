plugins {
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

repositories {
    mavenCentral()
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
        .map { path -> file(path).canonicalPath }
        .toSet()
}

val trackedKotlinFiles = gitTrackedFiles("*.kt", "*.kts")

fun gitHooksDir(): File? {
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
    return file(output).canonicalFile
}

ktlint {
    version.set("1.8.0")
    android.set(false)
    ignoreFailures.set(false)
    filter {
        exclude { element -> element.file.canonicalPath !in trackedKotlinFiles }
    }
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    }
}

tasks.named("ktlintCheck") {
    doLast {
        val hooksDir = gitHooksDir() ?: return@doLast
        hooksDir.mkdirs()
        listOf("pre-commit", "pre-push").forEach { hookName ->
            val source = layout.projectDirectory.file("docs/harness/git-hooks/$hookName").asFile
            if (source.isFile) {
                val target = hooksDir.resolve(hookName)
                if (
                    !java.nio.file.Files
                        .isSymbolicLink(target.toPath()) &&
                    target.isFile &&
                    target.readBytes().contentEquals(source.readBytes())
                ) {
                    return@forEach
                }
                val temporaryTarget =
                    java.nio.file.Files
                        .createTempFile(hooksDir.toPath(), ".sync-git-hooks-", "-$hookName")
                        .toFile()
                source.copyTo(temporaryTarget, overwrite = false)
                temporaryTarget.setExecutable(true)
                java.nio.file.Files.move(
                    temporaryTarget.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
    }
}
