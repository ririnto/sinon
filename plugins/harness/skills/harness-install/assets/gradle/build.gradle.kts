import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    base
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

fun findMarkdownlintCommand(): String? =
    ProcessBuilder("sh", "-c", "command -v markdownlint-cli2")
        .redirectErrorStream(true)
        .start()
        .let { process ->
            process.inputStream.bufferedReader().use { reader -> reader.readText().trim() }.takeIf {
                process.waitFor() == 0
            }
        }

allprojects {
    apply(
        plugin =
            rootProject.libs.plugins.ktlint
                .get()
                .pluginId
    )
    configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.cli)
        android.set(false)
        ignoreFailures.set(false)
        reporters {
            reporter(ReporterType.PLAIN)
        }
    }
}

tasks.register("checkMarkdown") {
    outputs.upToDateWhen { false }
    doLast {
        findMarkdownlintCommand()?.let { markdownlint ->
            providers
                .exec {
                    commandLine(markdownlint, ".")
                }.result
                .get()
                .assertNormalExitValue()
        } ?: logger.warn(
            "markdownlint-cli2 not in PATH; skipping Markdown linting; install with: bun add -g markdownlint-cli2"
        )
    }
}

tasks.register("fixMarkdown") {
    outputs.upToDateWhen { false }
    doLast {
        findMarkdownlintCommand()?.let { markdownlint ->
            providers
                .exec {
                    commandLine(markdownlint, "--fix", ".")
                }.result
                .get()
                .assertNormalExitValue()
        } ?: logger.warn(
            "markdownlint-cli2 not in PATH; skipping Markdown fixes; install with: bun add -g markdownlint-cli2"
        )
    }
}

tasks.named("check") {
    dependsOn("checkMarkdown")
}

tasks.named("ktlintCheck") {
    dependsOn("checkMarkdown")
}

tasks.named("ktlintFormat") {
    dependsOn("fixMarkdown")
}

tasks.register("kotlinFormat") {
    dependsOn(tasks.matching { task -> task.name.startsWith("runKtlintFormatOver") })
}
